package dev.achmad.finbox.core.llm

import android.util.Log
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.json
import dev.achmad.finbox.util.network.parseAs
import dev.achmad.finbox.util.network.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Talks to one OpenAI-compatible endpoint. */
class LlmClient(
    client: OkHttpClient,
    private val providers: LlmProviderStore,
) {

    /**
     * The shared client with more patience than the app default: a free tier
     * can take minutes to answer a batch, and the default timeout was
     * cancelling requests that were working. Only the timeouts differ.
     */
    private val client: OkHttpClient = client.newBuilder()
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /**
     * The models this endpoint offers, ids only, sorted.
     *
     * Every OpenAI-compatible host serves this, and asking beats making someone
     * type an id correctly from memory. A host that does not answer it is not a
     * failure worth blocking on — the caller falls back to typing one.
     */
    suspend fun models(provider: LlmProvider, apiKey: String?): List<String> =
        withContext(Dispatchers.IO) {
            client.get(provider.modelsUrl, headers = auth(apiKey), cacheControl = null)
                .parseAs<ModelList>()
                .data
                .map { it.id }
                .distinct()
                .sorted()
        }

    /**
     * One chat completion, returning the assistant's message content.
     *
     * [schema] is a JSON Schema the reply must satisfy: the worst a hostile
     * description can then achieve is a wrong category, not an instruction.
     */
    suspend fun complete(
        provider: LlmProvider,
        apiKey: String?,
        system: String,
        user: String,
        schema: JsonObject? = null,
    ): Completion = withContext(Dispatchers.IO) {
        if (schema == null) return@withContext send(provider, apiKey, system, user, null)

        // Ask for the strongest guarantee the endpoint will accept, then walk
        // down: "OpenAI-compatible" is a family, not a specification. Whatever
        // works is remembered, so this probes once per provider, not per batch.
        var mode = supported[provider.id] ?: ResponseFormatMode.SCHEMA
        while (true) {
            try {
                val completion = send(provider, apiKey, system, user, format(mode, schema))
                supported[provider.id] = mode
                return@withContext completion
            } catch (error: Throwable) {
                val next = mode.fallback()
                if (next == null || !looksLikeFormatRejection(error)) throw error
                // Not remembered yet: a rejection here is about this request, and
                // only the mode that actually returns something is worth keeping.
                mode = next
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun send(
        provider: LlmProvider,
        apiKey: String?,
        system: String,
        user: String,
        responseFormat: ResponseFormat?,
    ): Completion {
        val request = ChatRequest(
            model = provider.model,
            messages = listOf(
                Message(role = "system", content = system),
                Message(role = "user", content = user),
            ),
            // Categorization is a lookup, not a creative act. The same signature
            // asked twice should come back the same, or the cache is a lie.
            temperature = 0.0,
            responseFormat = responseFormat,
        )
        val body = json
            .encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)
        // Reading the body ourselves rather than letting the helper throw on a
        // non-2xx: an endpoint that rejects something says why in the body, and
        // "HTTP error 400" alone is not enough to fix anything by.
        val raw = client.post(
            provider.chatUrl,
            body,
            headers = auth(apiKey),
            ensureSuccess = false,
        )
        val text = raw.use { it.body.string() }
        if (!raw.isSuccessful) {
            Log.w(TAG, "${raw.code} from ${provider.chatUrl}: ${text.take(400)}")
            throw LlmHttpException(raw.code, text.take(400))
        }
        val response = json.decodeFromString<ChatResponse>(text)
        return Completion(
            content = response.choices.firstOrNull()?.message?.content.orEmpty(),
            promptTokens = response.usage?.promptTokens ?: 0,
            completionTokens = response.usage?.completionTokens ?: 0,
        )
    }

    private fun format(mode: ResponseFormatMode, schema: JsonObject): ResponseFormat? = when (mode) {
        ResponseFormatMode.SCHEMA ->
            ResponseFormat(type = "json_schema", jsonSchema = JsonSchema(name = "categories", schema = schema))
        // No schema, but still "the reply must be a JSON object", which most
        // hosts that reject a schema will still honour.
        ResponseFormatMode.OBJECT -> ResponseFormat(type = "json_object", jsonSchema = null)
        // Nothing but the prompt. The caller has to be able to cope with a reply
        // that is merely usually JSON, and ours does.
        ResponseFormatMode.NONE -> null
    }

    /**
     * Whether an error reads like the endpoint refusing the response format
     * rather than refusing the request.
     *
     * Deliberately narrow. Downgrading on a rate limit or a bad key would hide
     * a real problem behind a weaker guarantee and a confusing reply.
     */
    private fun looksLikeFormatRejection(error: Throwable): Boolean {
        val failure = error as? LlmHttpException ?: return false
        val code = failure.code
        // Now that the body comes along, an endpoint that names the field it
        // disliked can be believed outright whatever status it chose.
        val body = failure.body.lowercase()
        if ("response_format" in body || "json_schema" in body || "schema" in body) return true
        // A host that dislikes a field in the body says 400, or 422 if it
        // validates. Deliberately narrow: 401 and 429 are a bad key and a rate
        // limit, and quietly asking again with a weaker guarantee would hide
        // both behind a confusing reply.
        //
        return code == 400 || code == 422
    }

    /** What an endpoint turned out to accept, remembered for the session. */
    private val supported = mutableMapOf<String, ResponseFormatMode>()

    private enum class ResponseFormatMode {
        SCHEMA,
        OBJECT,
        NONE,
        ;

        fun fallback(): ResponseFormatMode? = when (this) {
            SCHEMA -> OBJECT
            OBJECT -> NONE
            NONE -> null
        }
    }

    /**
     * Whether the endpoint, key and model actually work together.
     *
     * One real round trip. A key that is wrong, a model the host does not
     * serve, or an endpoint missing its `/v1` all fail here, in a screen the
     * user is looking at, rather than silently in a background backfill.
     */
    suspend fun test(provider: LlmProvider, apiKey: String?): Result<String> = runCatching {
        complete(
            provider = provider,
            apiKey = apiKey,
            system = "Reply with exactly one word.",
            user = "Say OK.",
        ).content.trim().ifEmpty { "(empty reply)" }
    }

    private fun auth(apiKey: String?): Headers = Headers.Builder().apply {
        // Some self-hosted endpoints take no key at all, so an absent one is
        // valid rather than something to reject before asking.
        if (!apiKey.isNullOrBlank()) add("Authorization", "Bearer $apiKey")
    }.build()

    /** The active provider and its key, or null when nothing is set up. */
    fun activeOrNull(): Pair<LlmProvider, String?>? =
        providers.active()?.let { it to providers.key(it) }

    private companion object {
        const val TAG = "LlmClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class ModelList(val data: List<ModelEntry> = emptyList())

@Serializable
private data class ModelEntry(val id: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
private data class Message(val role: String, val content: String)

@Serializable
private data class ResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: JsonSchema? = null,
)

@Serializable
private data class JsonSchema(
    val name: String,
    val schema: JsonObject,
    val strict: Boolean = true,
)

/** A refused request, with what the endpoint said about it. */
class LlmHttpException(val code: Int, val body: String) :
    IllegalStateException("HTTP $code: ${body.take(200)}")

/**
 * What came back, and what it cost.
 *
 * Usage is zero rather than null when the endpoint does not report it: many
 * OpenAI-compatible hosts leave it out, and a run that adds up to zero tokens
 * reads as "not reported" without a second flag to carry it.
 */
data class Completion(
    val content: String,
    val promptTokens: Long,
    val completionTokens: Long,
)

@Serializable
private data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
private data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
)

@Serializable
private data class Choice(val message: Message? = null)

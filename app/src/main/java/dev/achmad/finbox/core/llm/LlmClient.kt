package dev.achmad.finbox.core.llm

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

/**
 * Talks to one OpenAI-compatible endpoint.
 *
 * Deliberately not a `Classifier` interface with several implementations. It is
 * a request builder for a shape of API that a great many hosts speak, and the
 * thing above it decides what to ask.
 */
class LlmClient(
    private val client: OkHttpClient,
    private val providers: LlmProviderStore,
) {

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
     * [schema] is a JSON Schema the reply must satisfy. Supplying it as a schema
     * rather than describing it in the prompt is what keeps a category inside
     * the enum: the worst a hostile transfer description can then achieve is a
     * wrong category, not an instruction the model follows.
     */
    suspend fun complete(
        provider: LlmProvider,
        apiKey: String?,
        system: String,
        user: String,
        schema: JsonObject? = null,
    ): String = withContext(Dispatchers.IO) {
        val request = ChatRequest(
            model = provider.model,
            messages = listOf(
                Message(role = "system", content = system),
                Message(role = "user", content = user),
            ),
            // Categorization is a lookup, not a creative act. The same signature
            // asked twice should come back the same, or the cache is a lie.
            temperature = 0.0,
            responseFormat = schema?.let {
                ResponseFormat(jsonSchema = JsonSchema(name = "categories", schema = it))
            },
        )
        val body = json
            .encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)
        client.post(provider.chatUrl, body, headers = auth(apiKey))
            .parseAs<ChatResponse>()
            .choices
            .firstOrNull()
            ?.message
            ?.content
            .orEmpty()
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
        ).trim().ifEmpty { "(empty reply)" }
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
    val type: String = "json_schema",
    @SerialName("json_schema") val jsonSchema: JsonSchema,
)

@Serializable
private data class JsonSchema(
    val name: String,
    val schema: JsonObject,
    val strict: Boolean = true,
)

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: Message? = null)

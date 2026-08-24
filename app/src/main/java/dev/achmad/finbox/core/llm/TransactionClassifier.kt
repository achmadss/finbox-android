package dev.achmad.finbox.core.llm

import android.util.Log
import dev.achmad.data.model.Signature
import dev.achmad.data.model.TransactionCategory
import dev.achmad.finbox.util.network.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Puts signatures into categories, in batches, over an OpenAI-compatible chat API.
 *
 * A concrete class rather than an interface: there is one implementation, and
 * the second one — if an on-device model ever earns its place — is what should
 * decide what the interface looks like.
 *
 * It classifies [Signature]s and never transactions. Passing rows would leak the
 * ledger into this layer and make the deduplication that pays for the whole
 * thing impossible to express.
 */
class TransactionClassifier(
    private val client: LlmClient,
    private val providers: LlmProviderStore,
) {

    /** Whether anything is set up to classify with. */
    fun isConfigured(): Boolean = providers.active() != null

    /**
     * Categorizes [batch], which should be no larger than [BATCH_SIZE].
     *
     * Signatures with no answer are simply absent from the result. That is the
     * normal way for this to partly fail: the next pass picks those rows up
     * again, because their category is still null.
     */
    suspend fun classify(batch: List<Signature>): Result<Classified> = runCatching {
        val provider = providers.active() ?: error("No AI provider is set up")
        val key = providers.key(provider)
        if (batch.isEmpty()) return@runCatching Classified(emptyMap(), 0, 0, 0)

        // Local indices as ids, mapped back here. Real transaction ids look like
        // accountId:message:messageId:parserId:index, and every character of
        // that is a chance for a model to garble one.
        val prompt = batch.mapIndexed { index, signature -> render(index, signature) }
            .joinToString("\n")

        var completion = client.complete(provider, key, SYSTEM, prompt, schema())
        var parsed = parse(completion.content)
        var requests = 1
        var promptTokens = completion.promptTokens
        var completionTokens = completion.completionTokens
        if (parsed == null) {
            // One retry. Structured output constrains generation, it does not
            // guarantee a well-formed answer, and a single malformed reply is
            // more often a hiccup than a broken endpoint.
            Log.w(TAG, "Malformed reply, retrying once")
            completion = client.complete(provider, key, SYSTEM, prompt, schema())
            parsed = parse(completion.content)
            requests = 2
            promptTokens += completion.promptTokens
            completionTokens += completion.completionTokens
        }
        val answers = parsed ?: run {
            Log.w(TAG, "Still malformed after a retry; the whole batch stays null")
            emptyList()
        }

        val byIndex = mutableMapOf<Int, TransactionCategory>()
        for (answer in answers) {
            val signature = batch.getOrNull(answer.id)
            if (signature == null) {
                // An id nobody asked about. Dropping it is the only safe move —
                // it cannot be matched to anything.
                Log.w(TAG, "Reply names id ${answer.id}, which was not in this batch")
                continue
            }
            // First wins. A duplicate id means the model contradicted itself and
            // there is no principled way to pick the later one.
            if (answer.id in byIndex) continue
            val category = TransactionCategory.fromStringOrNull(answer.category)
            if (category == null) {
                // Outside the enum despite being given it as a schema. Null, not
                // OTHER: OTHER is an answer, and this is the absence of one.
                Log.w(TAG, "Reply has category '${answer.category}', which is not one of ours")
                continue
            }
            byIndex[answer.id] = category
        }

        Classified(
            categories = byIndex.entries.associate { batch[it.key] to it.value },
            requests = requests,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
        )
    }

    /**
     * One line per signature, labelled so a model reads them as fields.
     *
     * The method is included because it is often all there is, and because it
     * is what lets a model say a card rail names no purpose rather than
     * inventing one for it.
     */
    private fun render(index: Int, signature: Signature): String = buildString {
        append(index).append(". ")
        append("direction=").append(signature.direction?.name ?: "UNKNOWN")
        signature.method?.let { append(" | how=").append(it) }
        signature.merchant?.let { append(" | counterparty=").append(it) }
        signature.description?.let { append(" | note=").append(it) }
    }

    /** Null when the reply is not JSON we can use at all. */
    private fun parse(content: String): List<Answer>? = runCatching {
        // Some hosts wrap the object in a fenced block despite the schema.
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
        json.decodeFromString<Reply>(cleaned.trim()).results
    }.getOrNull()

    /**
     * The reply shape, with the category as a schema enum rather than a
     * described one.
     *
     * That is also the whole of the prompt-injection defence. Transfer notes are
     * written by whoever sent the money and land in the model's input; with the
     * category constrained to this list, the worst they can achieve is a wrong
     * category rather than an instruction that gets followed.
     */
    private fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("results") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("id") { put("type", "integer") }
                        putJsonObject("category") {
                            put("type", "string")
                            putJsonArray("enum") {
                                TransactionCategory.entries.forEach { add(it.name) }
                            }
                        }
                    }
                    putJsonArray("required") { add("id"); add("category") }
                    put("additionalProperties", false)
                }
            }
        }
        putJsonArray("required") { add("results") }
        put("additionalProperties", false)
    }

    /** What one batch came back with, and what it cost to ask. */
    data class Classified(
        val categories: Map<Signature, TransactionCategory>,
        val requests: Int,
        val promptTokens: Long,
        val completionTokens: Long,
    )

    @Serializable
    private data class Reply(val results: List<Answer> = emptyList())

    @Serializable
    private data class Answer(
        val id: Int = -1,
        @SerialName("category") val category: String = "",
    )

    companion object {
        /**
         * Big enough that a few thousand transactions is dozens of requests,
         * small enough that one malformed reply does not cost much. An
         * implementation detail of this class, not a number anything else needs.
         */
        const val BATCH_SIZE = 25

        private const val TAG = "Classifier"

        private val SYSTEM = """
            You categorize bank transactions. Each line is one transaction group,
            numbered, with whatever the bank's receipt actually stated.

            Reply with one result per line you were given, using its number as
            the id. Choose exactly one category from the supplied list. Never
            invent a category and never explain yourself.

            Categories, and where their edges are:
            INCOME - salary, refunds and money arriving that is not a transfer between the person's own accounts.
            FOOD - restaurants, cafes, coffee, drinks, food delivery.
            GROCERIES - supermarkets, minimarkets and food shopping to take home.
            SHOPPING - clothes, electronics, household goods, general retail.
            TRANSPORTATION - fuel, ride hailing, tolls, parking, public transport.
            BILLS - electricity, water, internet, phone, subscriptions.
            HOUSING - rent, mortgage, maintenance, furnishing a home.
            ENTERTAINMENT - cinema, games, events, streaming for leisure.
            HEALTH - pharmacy, clinics, hospitals, insurance for health.
            EDUCATION - tuition, courses, books for study.
            TRAVEL - flights, hotels, trips away from home.
            PERSONAL_CARE - haircuts, salons, massage, cosmetics, gyms.
            FINANCIAL - savings, investments, bonds, loan repayments.
            TRANSFER - money moved to another account, wallet or stored-value card without being spent on anything yet.
            FEES - bank charges, admin fees, service charges.
            OTHER - clearly a purchase, but none of the above.
            UNKNOWN - the receipt does not say what the money was for.

            UNKNOWN matters as much as the rest. Many receipts name only how the
            money moved - a debit card, a wallet top up, a payment rail - and
            never what was bought. Those are UNKNOWN. Do not guess a category
            from the payment method: a card is not shopping, and an e-wallet is
            not transport. Answer UNKNOWN whenever nothing in the line names a
            merchant, a purpose or a thing.

            Where a counterparty is a person's name rather than a business,
            prefer TRANSFER over guessing what it was for.
        """.trimIndent()
    }
}

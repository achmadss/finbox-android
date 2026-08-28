package dev.achmad.finbox.extension.lib.jago

import dev.achmad.finbox.extension.core.annotation.SourceEntrypoint
import dev.achmad.finbox.extension.core.source.email.model.Email
import dev.achmad.finbox.extension.core.source.email.EmailSource
import dev.achmad.finbox.extension.core.source.email.model.EmailQuery
import dev.achmad.finbox.extension.core.transaction.ParsedTransaction
import dev.achmad.finbox.extension.core.transaction.TransactionDirection
import dev.achmad.finbox.extension.util.Receipt

/**
 * Jago puts the label on its own line with the value on the next:
 * ```
 * Transaction Summary
 * To
 * BEl Shop
 * Amount
 * Rp 2.000
 * Transaction Date
 * 05 August 2026, 13:23 WIB
 * ```
 */
@SourceEntrypoint(id = "jago", name = "Bank Jago")
class Jago : EmailSource {

    override val query = EmailQuery.from("noreply@jago.com")

    override suspend fun parse(email: Email): List<ParsedTransaction> {
        if ("jago.com" !in email.from.lowercase()) return emptyList()
        val receipt = Receipt.of(email)
        val amount = receipt.transactionAmount() ?: return emptyList()
        val summarised = receipt.hasSummary()

        return listOf(
            ParsedTransaction(
                amount = amount,
                currency = "IDR",
                // Only the summary states its own time; a debit card email has
                // none, so the mail's arrival stands in.
                date = receipt.date(*DATE).takeIf { summarised } ?: email.date,
                direction = directionOf(email.subject),
                merchant = receipt.field(*MERCHANT)?.takeIf { summarised },
                // Jago's mail carries no note, and the subject is the same on
                // every method, so description stays null.
                description = null,
                reference = null,
            ),
        )
    }

    /**
     * A promotion from the same address states amounts too, so a bare currency
     * figure is only trusted in the two shapes known to be a receipt: the
     * summary or the debit card sentence.
     */
    private fun Receipt.transactionAmount(): Long? = when {
        lines.any { INVESTMENT.containsMatchIn(it) } -> null
        hasSummary() -> amount(*AMOUNT)
        lines.any { DEBIT_CARD_LINE in it.lowercase() } -> statedAmount()
        else -> null
    }

    private fun Receipt.hasSummary(): Boolean =
        lines.any { it.startsWith(SUMMARY, ignoreCase = true) }

    /**
     * Which way the money went, from how Jago worded it.
     *
     * This used to sort the same text into 6 methods. Only the incoming test
     * ever decided anything the app kept — the rest named a payment rail, which
     * is not a fact about the money. Outgoing is the default because a receipt
     * a bank sends without saying otherwise is a receipt for money spent.
     */
    private fun directionOf(vararg text: String): TransactionDirection {
        val joined = text.joinToString(" ").lowercase()
        return if (INCOMING_WORDS.containsMatchIn(joined)) {
            TransactionDirection.INCOMING
        } else {
            TransactionDirection.OUTGOING
        }
    }

    private companion object {

        // Jago writes to its customers in English. Word start only, so a word
        // merely ending in one of these cannot flip the direction.
        val INCOMING_WORDS = Regex("\\b(received|incoming|refund|cashback)", RegexOption.IGNORE_CASE)

        const val SUMMARY = "Transaction Summary"
        const val DEBIT_CARD_LINE = "debit card"

        // Not "invest": every Jago footer says "from saving, transacting, to
        // investing", which would drop the lot.
        val INVESTMENT = Regex("investment pocket|stock", RegexOption.IGNORE_CASE)

        val AMOUNT = arrayOf("Amount")
        val DATE = arrayOf("Transaction Date")

        // "To" for a payment or transfer, the partner's name for the rest.
        val MERCHANT = arrayOf("To", "Jago partner")
    }
}

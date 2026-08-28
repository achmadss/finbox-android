package dev.achmad.finbox.source.id.mandiri

import dev.achmad.finbox.source.core.annotation.SourceEntrypoint
import dev.achmad.finbox.source.core.email.Email
import dev.achmad.finbox.source.core.email.EmailSource
import dev.achmad.finbox.source.core.email.EmailQuery
import dev.achmad.finbox.source.core.ParsedTransaction
import dev.achmad.finbox.source.core.TransactionDirection
import dev.achmad.finbox.source.core.util.Receipt

/**
 * A label and its value share one flattened row; the day and the clock are two
 * rows of their own:
 * ```
 * Penerima
 * APOTEK KAWI JAYA BSD
 * Tanggal 27 Jul 2026
 * Jam 15:22:45 WIB
 * Nominal Transaksi Rp 41.000,00
 * No. Referensi 2607271121582462322
 * ```
 */
@SourceEntrypoint
class Mandiri : EmailSource {

    override val id = "mandiri"
    override val name = "Bank Mandiri"
    override val icon = R.drawable.mandiri_icon


    override val query = EmailQuery.from("noreply.livin@bankmandiri.co.id")

    override suspend fun parse(email: Email): List<ParsedTransaction> {
        if ("bankmandiri.co.id" !in email.from.lowercase()) return emptyList()
        val receipt = Receipt.of(email)

        // A labelled amount separates a receipt from a promotion, and an SBN
        // order carries no reference number, so amount is the criterion.
        val amount = receipt.amount(*AMOUNT) ?: return emptyList()

        return listOf(
            ParsedTransaction(
                amount = amount,
                currency = "IDR",
                // Stated as two rows, a day and a clock, hence splitDate.
                date = receipt.splitDate() ?: email.date,
                direction = directionOf(email.subject),
                merchant = receipt.field(*MERCHANT),
                // The subject is the same on every receipt of a kind, and
                // Mandiri's mail knows no note field, so description is null.
                description = null,
                reference = receipt.field(*REFERENCE),
            ),
        )
    }

    /**
     * Which way the money went, from how Livin worded it.
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

        // Word start only, or "Termasuk" reads as money arriving.
        val INCOMING_WORDS = Regex("\\b(masuk|diterima|kredit|refund)", RegexOption.IGNORE_CASE)

        val AMOUNT = arrayOf("Nominal")
        val REFERENCE = arrayOf("No. Referensi", "Nomor Referensi")

        // "Penerima" for a QR payment, the provider's name for a top up.
        val MERCHANT = arrayOf("Penerima", "Penyedia Jasa")
    }
}

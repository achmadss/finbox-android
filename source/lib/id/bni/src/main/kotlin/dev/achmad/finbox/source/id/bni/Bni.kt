package dev.achmad.finbox.source.id.bni

import dev.achmad.finbox.source.core.annotation.SourceEntrypoint
import dev.achmad.finbox.source.core.email.Email
import dev.achmad.finbox.source.core.email.EmailSource
import dev.achmad.finbox.source.core.email.EmailQuery
import dev.achmad.finbox.source.core.ParsedTransaction
import dev.achmad.finbox.source.core.TransactionDirection
import dev.achmad.finbox.source.core.util.Receipt

/**
 * They are html tables grouped under section headings, which flatten to a label
 * and its value on one line:
 * ```
 * Penerima
 * QRIS INDOMARET
 * Tanggal & waktu transaksi
 * Tanggal 11 Agu 2026
 * Waktu 20:43:33 WIB
 * Detail pembayaran
 * Nominal Rp23.500
 * Total Rp23.500
 * Jenis transaksi QRIS
 * Reference ID 202608110843260325
 * ```
 */
@SourceEntrypoint
class Bni : EmailSource {

    override val id = "bni"
    override val name = "Bank BNI"
    override val icon = R.drawable.bni_icon


    // One address for everything; the subject names the method.
    override val query = EmailQuery.from("wondr@bni.co.id")

    override suspend fun parse(email: Email): List<ParsedTransaction> {
        if ("bni.co.id" !in email.from.lowercase()) return emptyList()
        val receipt = Receipt.of(email)

        // A receipt states a reference id and a total; promotions and OTPs
        // state neither.
        val reference = receipt.field(*REFERENCE) ?: return emptyList()
        val amount = receipt.amount(*TOTAL) ?: return emptyList()

        val stated = receipt.field(*TYPE).orEmpty()

        return listOf(
            ParsedTransaction(
                amount = amount,
                currency = "IDR",
                // Stated as two rows, a day and a clock, hence splitDate.
                date = receipt.splitDate() ?: email.date,
                direction = directionOf(stated, email.subject),
                // Absent on a top up, which names the topped-up card instead;
                // an unnamed transfer leaves a trailing dash in the cell.
                merchant = receipt.field(*MERCHANT)?.trim(' ', '-')?.ifBlank { null },
                // BNI's mail knows no note field, and the subject is the same
                // on every method, so description stays null.
                description = null,
                reference = reference,
            ),
        )
    }

    /**
     * Which way the money went, from how wondr worded it.
     *
     * This used to sort the same text into 5 methods. Only the incoming test
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

        val TOTAL = arrayOf("Total")
        val REFERENCE = arrayOf("Reference ID", "Ref ID")
        val TYPE = arrayOf("Jenis transaksi")
        val MERCHANT = arrayOf("Penerima")
    }
}

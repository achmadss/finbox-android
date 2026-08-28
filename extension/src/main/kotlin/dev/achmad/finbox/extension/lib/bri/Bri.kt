package dev.achmad.finbox.extension.lib.bri

import dev.achmad.finbox.extension.core.annotation.SourceEntrypoint
import dev.achmad.finbox.extension.core.source.email.model.Email
import dev.achmad.finbox.extension.core.source.email.EmailSource
import dev.achmad.finbox.extension.core.source.email.model.EmailQuery
import dev.achmad.finbox.extension.core.transaction.ParsedTransaction
import dev.achmad.finbox.extension.core.transaction.TransactionDirection
import dev.achmad.finbox.extension.util.Receipt

/**
 * They arrive as html tables, which flatten to one line per row:
 * ```
 * Nomor Referensi 192779074268
 * Tanggal Transaksi 11 Aug 2026, 10:30:27 WIB
 * Jenis Transaksi QRIS Bayar
 * Nama Merchant Warkop Maharani
 * Nominal Rp13.000
 * Biaya Admin Rp0
 * ```
 */
@SourceEntrypoint(id = "bri", name = "Bank BRI")
class Bri : EmailSource {

    // One address for every notification.
    override val query = EmailQuery.from("BankBRI@bri.co.id")

    override suspend fun parse(email: Email): List<ParsedTransaction> {
        if ("bri.co.id" !in email.from.lowercase()) return emptyList()
        val receipt = Receipt.of(email)

        // A receipt states a reference number and what was charged; OTPs and
        // promotions state neither.
        val reference = receipt.field(*REFERENCE) ?: return emptyList()

        // Nominal is what was spent, Total is that plus the admin fee — the
        // ledger wants what actually left the account.
        val nominal = receipt.amount(*AMOUNT)
        val fee = receipt.amount(*FEE) ?: 0L
        val amount = nominal?.plus(fee) ?: receipt.amount(*TOTAL) ?: return emptyList()

        return listOf(
            ParsedTransaction(
                amount = amount,
                currency = "IDR",
                // The receipt's timestamp is when BRI booked it; the mail's
                // arrival is only a fallback.
                date = receipt.date(*DATE) ?: email.date,
                direction = directionOf(receipt.field(*TYPE).orEmpty(), email.subject),
                merchant = receipt.field(*MERCHANT),
                description = receipt.note(),
                reference = reference,
            ),
        )
    }

    /**
     * Which way the money went, from how BRI worded it.
     *
     * This used to sort the same text into six methods. Only the incoming test
     * ever decided anything the app kept — the rest named a payment rail, which
     * is not a fact about the money. Outgoing is the default because a receipt
     * BRI sends without saying otherwise is a receipt for money spent.
     */
    private fun directionOf(vararg text: String): TransactionDirection {
        val joined = text.joinToString(" ").lowercase()
        return if (INCOMING_WORDS.containsMatchIn(joined)) {
            TransactionDirection.INCOMING
        } else {
            TransactionDirection.OUTGOING
        }
    }

    /**
     * BRImo leaves an empty value in place, so the reader hands back the next
     * line — usually another label. Both a known label and "-" (BRImo for an
     * empty box) are dropped. The kind and subject are the same on every
     * receipt, so this note is the only text that differs.
     */
    private fun Receipt.note(): String? = field(*NOTE)?.trim()?.takeIf { value ->
        value != "-" && value.isNotEmpty() &&
            LABELS.none { value.startsWith(it, ignoreCase = true) }
    }

    private companion object {
        // Word start only, or "Biaya Termasuk PPN" reads as money arriving. No
        // trailing boundary, so "dikreditkan" still counts.
        val INCOMING_WORDS = Regex("\\b(masuk|diterima|kredit|refund)", RegexOption.IGNORE_CASE)

        val AMOUNT = arrayOf("Nominal")
        val FEE = arrayOf("Biaya Admin")
        val TOTAL = arrayOf("Total Transaksi", "Total")
        val REFERENCE = arrayOf("Nomor Referensi", "No. Ref", "No Ref")
        val DATE = arrayOf("Tanggal Transaksi", "Tanggal")
        val TYPE = arrayOf("Jenis Transaksi")
        val MERCHANT = arrayOf("Nama Merchant", "Nama Tujuan")
        val NOTE = arrayOf("Catatan", "Berita")

        /** Everything this extension reads, so a note can be told from a label. */
        val LABELS = AMOUNT + FEE + TOTAL + REFERENCE + DATE + TYPE + MERCHANT + NOTE +
            arrayOf("Sumber Dana", "Rekening Sumber Dana", "Nama Sumber Dana", "Bank Tujuan",
                "Nomor Tujuan", "Alias Penerima", "Lokasi Merchant", "Nomor Kartu")
    }
}

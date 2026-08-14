package dev.achmad.data.export

import dev.achmad.data.model.Transaction
import dev.achmad.data.repository.TransactionRepository
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ledger as CSV, for a spreadsheet.
 *
 * Export only — restoring the app is [dev.achmad.data.backup.FinboxBackup]'s
 * job, and a CSV can't carry the rest of the state.
 */
class CsvExport(private val transactions: TransactionRepository) {

    /** Writes every transaction to [out]. Does not close it. */
    suspend fun exportTo(out: OutputStream) = withContext(Dispatchers.IO) {
        out.bufferedWriter().apply {
            appendLine(row(HEADER))
            transactions.all()
                .filterNot { it.deleted }
                .sortedByDescending { it.date ?: it.createdAt }
                .forEach { appendLine(row(it.fields())) }
            flush()
        }
        Unit
    }

    /** The same content as a string, for a share sheet or a preview. */
    suspend fun exportText(): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine(row(HEADER))
            transactions.all()
                .filterNot { it.deleted }
                .sortedByDescending { it.date ?: it.createdAt }
                .forEach { appendLine(row(it.fields())) }
        }
    }

    private fun Transaction.fields() = listOf(
        id, accountId, sourceId.toString(), emailMessageId, threadId, reference,
        date?.toString(), amount?.toString(), currency, type?.name, category,
        description, merchant, createdAt.toString(), updatedAt.toString(),
    )

    private companion object {
        val HEADER = listOf(
            "id", "account_id", "source_id", "email_message_id", "thread_id", "reference",
            "date", "amount", "currency", "type", "category", "description",
            "merchant", "created_at", "updated_at",
        )

        /** RFC 4180: quote anything holding a comma, quote or newline. */
        fun row(values: List<String?>): String = values.joinToString(",") { value ->
            val v = value.orEmpty()
            if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + v.replace("\"", "\"\"") + "\""
            } else {
                v
            }
        }
    }
}

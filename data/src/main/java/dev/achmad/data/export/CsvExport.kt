package dev.achmad.data.export

import android.content.Context
import android.net.Uri
import dev.achmad.data.model.Transaction
import dev.achmad.data.repository.TransactionRepository
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ledger as CSV, for a spreadsheet.
 *
 * Export only — restoring the app is [dev.achmad.data.backup.BackupManager]'s
 * job, and a CSV can't carry the rest of the state.
 */
class CsvExport(
    private val context: Context,
    private val transactions: TransactionRepository,
) {

    /**
     * Writes every transaction to the document at [uri].
     *
     * Opening it lives here rather than at the call site: a screen that picked
     * the document has no other reason to hold a Context.
     */
    suspend fun exportTo(uri: Uri) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: error("Could not open the file")
        out.use { exportTo(it) }
    }

    /** Writes every transaction to [out]. Does not close it. */
    private suspend fun exportTo(out: OutputStream) = withContext(Dispatchers.IO) {
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
        id, accountId, parserId.toString(), emailMessageId, threadId, reference,
        date?.toString(), amount?.toString(), currency, direction?.name, categoryName,
        description, merchant, createdAt.toString(), updatedAt.toString(),
    )

    private companion object {
        val HEADER = listOf(
            "id", "account_id", "parser_id", "email_message_id", "thread_id", "reference",
            "date", "amount", "currency", "direction", "category", "description",
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

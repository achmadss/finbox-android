package dev.achmad.data.repository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Unrecognized_email
import dev.achmad.domain.model.UnrecognizedEmail
import dev.achmad.domain.model.UnrecognizedStatus
import dev.achmad.domain.repository.UnrecognizedEmailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class UnrecognizedEmailRepositoryImpl(
    private val db: FinboxDatabase,
) : UnrecognizedEmailRepository {

    override fun emails(): Flow<List<UnrecognizedEmail>> =
        db.unrecognizedEmailQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun insertIgnoringDuplicates(email: UnrecognizedEmail): Boolean =
        withContext(Dispatchers.IO) {
            val exists = db.unrecognizedEmailQueries.SELECTById(email.id).executeAsOneOrNull() != null
            if (!exists) {
                db.unrecognizedEmailQueries.INSERTOrIgnore(
                id = email.id,
                account_id = email.accountId,
                email_message_id = email.emailMessageId,
                subject = email.subject,
                sender = email.sender,
                received_at = email.receivedAt,
                reason = email.reason,
                status = email.status.name,
                body_ref = email.bodyRef,
                created_at = email.createdAt,
                )
            }
            !exists
        }

    override suspend fun markReviewed(id: String) = withContext(Dispatchers.IO) {
        db.unrecognizedEmailQueries.MARKReviewed(id)
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.unrecognizedEmailQueries.DELETEById(id)
    }

    private fun Unrecognized_email.toModel() = UnrecognizedEmail(
        id = id,
        accountId = account_id,
        emailMessageId = email_message_id,
        subject = subject,
        sender = sender,
        receivedAt = received_at,
        reason = reason,
        status = runCatching { UnrecognizedStatus.valueOf(status) }.getOrDefault(UnrecognizedStatus.UNREVIEWED),
        bodyRef = body_ref,
        createdAt = created_at,
    )
}

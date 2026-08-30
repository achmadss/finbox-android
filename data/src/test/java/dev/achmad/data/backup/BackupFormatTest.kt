package dev.achmad.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `a pre-refactor backup is refused by name, not by a missing field`() {
        // What the format looked like before parsers became sources. The
        // version number is no help: it did not change, per the pre-release rule.
        val old = """{"version":1,"assignments":[{"accountId":"a","parserId":8172639182736}]}"""

        val error = runCatching { requireRestorable(old) }.exceptionOrNull()

        assertTrue("expected a refusal, got $error", error is IllegalArgumentException)
        assertEquals(PRE_REFACTOR_MESSAGE, error?.message)
    }

    @Test
    fun `a backup with package-name source ids is refused too`() {
        // Written while sources installed as separate apps. Restoring it
        // would file every transaction under an id nothing answers to.
        val packaged =
            """{"version":1,"assignments":[{"accountId":"a","sourceId":"dev.achmad.finbox.source.bri"}]}"""

        val error = runCatching { requireRestorable(packaged) }.exceptionOrNull()

        assertTrue("expected a refusal, got $error", error is IllegalArgumentException)
        assertEquals(PACKAGE_ID_MESSAGE, error?.message)
    }

    @Test
    fun `a current backup is not mistaken for either`() {
        val current = """{"version":1,"assignments":[{"accountId":"a","sourceId":"bri"}]}"""

        requireRestorable(current)
    }

    @Test
    fun `a round trip keeps every field`() {
        val data = BackupData(
            version = FORMAT_VERSION,
            createdAt = 1_700_000_000_000L,
            assignments = listOf(
                BackupAssignment(accountId = "account", sourceId = "bri"),
            ),
            transactions = listOf(
                BackupTransaction(
                    id = "account:message:m1:bri:0",
                    accountId = "account",
                    sourceId = "bri",
                    emailMessageId = "m1",
                    date = 1_700_000_000_000L,
                    amount = 25_000,
                    currency = "IDR",
                    direction = "OUTGOING",
                    merchant = "Kopi Kenangan",
                    description = "Coffee",
                    createdAt = 1_700_000_000_000L,
                    updatedAt = 1_700_000_000_000L,
                ),
            ),
        )

        val text = json.encodeToString(data)
        requireRestorable(text)

        assertEquals(data, json.decodeFromString<BackupData>(text))
    }
}

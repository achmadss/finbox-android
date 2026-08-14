package dev.achmad.finbox.core.gmail

import dev.achmad.finbox.core.gmail.model.Header
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.core.gmail.model.Payload
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailApiTest {

    @Test
    fun `uses the canonical gmail thread id over a header with the same name`() {
        val email = GmailApi.toEmailMessage(
            MessageResponse(
                id = "message",
                threadId = "api-thread",
                payload = Payload(headers = listOf(Header("Thread-ID", "header-thread"))),
            ),
        )

        assertEquals("api-thread", email.threadId)
    }
}

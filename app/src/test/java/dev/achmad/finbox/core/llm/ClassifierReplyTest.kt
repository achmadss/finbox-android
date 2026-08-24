package dev.achmad.finbox.core.llm

import dev.achmad.data.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a classifier does with a reply that is not what it asked for.
 *
 * This is the part worth testing. The happy path is one JSON parse; every case
 * here fails silently in production and produces confidently wrong categories on
 * real money, which is exactly the shape of bug nobody notices.
 */
class ClassifierReplyTest {

    /**
     * The rules, applied to one reply against the ids that were sent.
     *
     * Mirrors the loop in [TransactionClassifier]. Kept here rather than reached
     * into because the class needs a live endpoint to construct, and the logic
     * being tested is the mapping, not the request.
     */
    private fun resolve(sent: Int, answers: List<Pair<Int, String>>): Map<Int, TransactionCategory> {
        val out = mutableMapOf<Int, TransactionCategory>()
        for ((id, name) in answers) {
            if (id !in 0 until sent) continue
            if (id in out) continue
            val category = TransactionCategory.fromStringOrNull(name) ?: continue
            out[id] = category
        }
        return out
    }

    @Test
    fun `answers are matched by id, never by position`() {
        // The model dropped id 1. Zipping by position would file 2's answer
        // under 1 and shift everything after it, and nothing would ever notice.
        val resolved = resolve(sent = 3, answers = listOf(0 to "FOOD", 2 to "TRANSPORTATION"))

        assertEquals(TransactionCategory.FOOD, resolved[0])
        assertNull(resolved[1])
        assertEquals(TransactionCategory.TRANSPORTATION, resolved[2])
    }

    @Test
    fun `an id nobody asked about is dropped`() {
        val resolved = resolve(sent = 2, answers = listOf(0 to "FOOD", 7 to "TRAVEL"))

        assertEquals(1, resolved.size)
        assertEquals(TransactionCategory.FOOD, resolved[0])
    }

    @Test
    fun `a repeated id keeps the first answer`() {
        // The model contradicted itself; there is no principled reason to
        // believe the second one more than the first.
        val resolved = resolve(sent = 1, answers = listOf(0 to "FOOD", 0 to "TRAVEL"))

        assertEquals(TransactionCategory.FOOD, resolved[0])
    }

    @Test
    fun `a category outside the list is no answer at all`() {
        // Not OTHER. OTHER means looked at and miscellaneous, and treating a
        // malformed reply as that would file it under a real category forever.
        val resolved = resolve(sent = 2, answers = listOf(0 to "GROCERIES_AND_FOOD", 1 to "FOOD"))

        assertNull(resolved[0])
        assertEquals(TransactionCategory.FOOD, resolved[1])
    }

    @Test
    fun `an id that was sent but never came back stays unanswered`() {
        val resolved = resolve(sent = 3, answers = listOf(0 to "FOOD"))

        // Null rather than a guess: the row keeps a null category, so the next
        // pass picks it up again.
        assertNull(resolved[1])
        assertNull(resolved[2])
    }

    @Test
    fun `UNKNOWN is a real answer the model is allowed to give`() {
        val resolved = resolve(sent = 1, answers = listOf(0 to "UNKNOWN"))

        assertEquals(TransactionCategory.UNKNOWN, resolved[0])
    }
}

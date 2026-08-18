package dev.achmad.finbox.parser

/**
 * The mail a source wants fetched, as a Gmail search.
 *
 * Built rather than spelled out, so the common case can't be typoed into a
 * query that silently matches nothing — and [raw] is there for everything the
 * helpers don't cover, since Gmail's search language is far larger than the
 * part a parser usually needs.
 */
class EmailQuery private constructor(val value: String) {

    override fun toString(): String = value

    companion object {

        /**
         * Mail from any of these senders — an address, or a bare domain.
         *
         * At least one is required: a source that filters on nothing is asking
         * for the whole mailbox, which is [everything] and should be said out
         * loud.
         */
        fun from(address: String, vararg more: String): EmailQuery {
            val addresses = (listOf(address) + more).map { it.trim() }.filter { it.isNotEmpty() }
            require(addresses.isNotEmpty()) { "from() needs at least one address" }
            val query = addresses.joinToString(" OR ") { "from:$it" }
            return EmailQuery(if (addresses.size > 1) "($query)" else query)
        }

        /** Anything Gmail's search box accepts: `subject:`, `has:attachment`, … */
        fun raw(query: String): EmailQuery = EmailQuery(query.trim())

        /**
         * No filter at all.
         *
         * The app then downloads every message in the window and offers it to
         * this source — twenty quota units each, against five for listing five
         * hundred ids. It also disables narrowing for every other installed
         * source, since their filters would exclude the mail this one came for.
         */
        fun everything(): EmailQuery = EmailQuery("")
    }
}

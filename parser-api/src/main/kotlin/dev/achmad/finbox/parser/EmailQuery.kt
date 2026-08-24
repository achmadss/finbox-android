package dev.achmad.finbox.parser

/**
 * The mail a parser wants fetched, as a Gmail search.
 *
 * Built rather than spelled out, so the common case can't be typoed into a
 * query that silently matches nothing. [raw] covers the rest of Gmail's search
 * language.
 */
@JvmInline
value class EmailQuery private constructor(val value: String) {

    override fun toString(): String = value

    companion object {

        /** Mail from any of these senders — an address, or a bare domain. */
        fun from(address: String, vararg more: String): EmailQuery {
            val addresses = (listOf(address) + more).map { it.trim() }.filter { it.isNotEmpty() }
            // Filtering on nothing is asking for the whole mailbox, which is
            // EVERYTHING and should be said out loud.
            require(addresses.isNotEmpty()) { "from() needs at least one address" }
            val query = addresses.joinToString(" OR ") { "from:$it" }
            return EmailQuery(if (addresses.size > 1) "($query)" else query)
        }

        /** Anything Gmail's search box accepts: `subject:`, `has:attachment`, … */
        fun raw(query: String): EmailQuery = EmailQuery(query.trim())

        /**
         * No filter at all.
         *
         * The app then downloads every message in the window — twenty quota
         * units each, against five for listing five hundred ids — and disables
         * narrowing for every other installed parser, since their filters would
         * exclude the mail this one came for.
         */
        val EVERYTHING = EmailQuery("")
    }
}

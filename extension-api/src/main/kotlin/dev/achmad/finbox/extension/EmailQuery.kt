package dev.achmad.finbox.extension

/** The mail an extension wants fetched, as a Gmail search. */
@JvmInline
value class EmailQuery private constructor(val value: String) {

    override fun toString(): String = value

    companion object {

        /** Mail from any of these senders — an address, or a bare domain. */
        fun from(address: String, vararg more: String): EmailQuery {
            val addresses = (listOf(address) + more).map { it.trim() }.filter { it.isNotEmpty() }
            // An empty filter would silently match the whole mailbox.
            require(addresses.isNotEmpty()) { "from() needs at least one address" }
            val query = addresses.joinToString(" OR ") { "from:$it" }
            return EmailQuery(if (addresses.size > 1) "($query)" else query)
        }

        /** Anything Gmail's search box accepts: `subject:`, `has:attachment`, … */
        fun raw(query: String): EmailQuery = EmailQuery(query.trim())

        /** No filter at all. */
        val EVERYTHING = EmailQuery("")
    }
}

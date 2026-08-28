package dev.achmad.data.model

data class EmailAccount(
    val id: String,
    val email: String,
    val displayName: String?,
    val authTokenRef: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncAt: Long?,
    /** Gmail history cursor: the last historyId whose changes were fully processed. */
    val lastHistoryId: String? = null,
    /**
     * Raw Gmail search narrowing what the initial import downloads. Null means
     * the whole mailbox, at the cost of a fetch per message.
     */
    val syncQuery: String? = null,
    /**
     * Where Gmail's history stood when the initial import began, held until it
     * finishes: promoting it early would switch updates to the incremental
     * path and strand the rest.
     */
    val importCursor: String? = null,
    /**
     * How far back the initial import has got, epoch millis. The import walks
     * the window newest first, so this only moves down.
     */
    val importedBackTo: Long? = null,
    /**
     * Google profile picture, as userinfo gave it at sign-in. Null when the
     * `profile` scope was not asked for, or when the owner has none.
     */
    val photoUrl: String? = null,
)

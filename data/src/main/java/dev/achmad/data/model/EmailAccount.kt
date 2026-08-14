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
     * Raw Gmail search narrowing what the initial import downloads, e.g.
     * `{from:bri.co.id from:jago.com}`. Null means the whole mailbox — correct,
     * but every message costs a 20-unit fetch.
     */
    val syncQuery: String? = null,
    /**
     * Where Gmail's history stood when the initial import began, held until the
     * import finishes — promoting it early would switch updates to the
     * incremental path and strand the rest of the mailbox.
     */
    val importCursor: String? = null,
    /**
     * How far back the initial import has got, epoch millis. The import walks
     * the window newest first, so this only moves down.
     */
    val importedBackTo: Long? = null,
    /**
     * Google profile picture, as the userinfo endpoint gave it at sign-in. Null
     * for an account added before the app asked for the `profile` scope, and for
     * one whose owner has no picture.
     */
    val photoUrl: String? = null,
)

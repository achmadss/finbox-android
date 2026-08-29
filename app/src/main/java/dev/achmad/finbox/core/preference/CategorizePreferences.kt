package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * What the user has done about filing categories themselves, as opposed to
 * letting a model do it.
 */
class CategorizePreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * Whether the "start here: file by group" card was waved away.
     *
     * Shown once a first import has finished and only while groups are still
     * uncategorized, so the ledger says nothing, the card stays off. Dismissing
     * it is permanent — it is an offer, not a checklist entry.
     */
    fun groupOfferDismissed() = preferenceStore.getBoolean("group_offer_dismissed", false)
}

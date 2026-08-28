package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * What onboarding decided, for the parts of the app that run after it —
 * [MainActivity][dev.achmad.finbox.MainActivity] picking a start screen.
 */
class OnboardingPreference(
    private val preferenceStore: PreferenceStore,
) {

    /** Set once every step is behind the user, so the app opens on Home instead. */
    fun onboardingComplete() = preferenceStore.getBoolean("onboarding_complete", false)

    /** The notification prompt only gets shown once, allowed or not. */
    fun notificationPromptSeen() = preferenceStore.getBoolean("onboarding_notification_seen", false)

    /** The AI step only gets offered once, taken up or not. */
    fun aiPromptSeen() = preferenceStore.getBoolean("onboarding_ai_seen", false)
}

package dev.achmad.finbox.core.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.achmad.finbox.util.preference.Preference
import dev.achmad.finbox.util.preference.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One OpenAI-compatible endpoint the user has set up.
 *
 * The API key is deliberately not a field: this is serialized into plain
 * preferences. It lives in [LlmKeyStore], keyed by [id].
 */
@Serializable
data class LlmProvider(
    /** Stable across renames, so the active pointer and the key survive an edit. */
    val id: String,
    val name: String,
    /** Base URL, e.g. `https://api.openai.com/v1`. No trailing slash. */
    val endpoint: String,
    /** Chosen from what the endpoint lists. Empty until it has been. */
    val model: String = "",
) {
    val modelsUrl: String get() = "$endpoint/models"
    val chatUrl: String get() = "$endpoint/chat/completions"

    /** Whether this is complete enough to classify with. */
    val isUsable: Boolean get() = endpoint.isNotBlank() && model.isNotBlank()

    companion object {
        /**
         * Trims and drops a trailing slash so the two URLs above never double
         * one up. Paste-from-docs almost always brings one along.
         */
        fun normalizeEndpoint(raw: String): String = raw.trim().trimEnd('/')
    }
}

/**
 * API keys, in Keystore-backed encrypted preferences.
 *
 * Separate file from the Gmail tokens: unrelated secrets with unrelated
 * lifetimes, and clearing one should never risk the other.
 */
class LlmKeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "llm_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun key(id: String): String? = prefs.getString(id, null)?.takeIf { it.isNotBlank() }

    fun save(id: String, key: String) = prefs.edit { putString(id, key.trim()) }

    fun clear(id: String) = prefs.edit { remove(id) }
}

/**
 * The providers the user has set up, and which one is in use.
 *
 * Several can be saved so switching between them does not mean retyping an
 * endpoint and a key.
 */
class LlmProviderStore(
    private val preferenceStore: PreferenceStore,
    private val keys: LlmKeyStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun providers(): Preference<List<LlmProvider>> = preferenceStore.getObjectFromString(
        key = "llm_providers",
        defaultValue = emptyList(),
        serializer = { json.encodeToString(it) },
        // A provider list that will not parse is a setting, not data: reading it
        // as empty costs the user a re-entry and never blocks the app starting.
        deserializer = { runCatching { json.decodeFromString<List<LlmProvider>>(it) }.getOrDefault(emptyList()) },
    )

    fun activeId(): Preference<String> = preferenceStore.getString("llm_active_provider")

    /**
     * The provider to classify with, or null when there is none to use.
     *
     * Null is the ordinary case, not an error: nobody has to set this up, and
     * everything that depends on it has to degrade to doing nothing.
     */
    fun active(): LlmProvider? {
        val all = providers().get()
        val id = activeId().get()
        return (all.firstOrNull { it.id == id } ?: all.firstOrNull())?.takeIf { it.isUsable }
    }

    fun key(provider: LlmProvider): String? = keys.key(provider.id)

    fun save(provider: LlmProvider, apiKey: String?) {
        val normalized = provider.copy(endpoint = LlmProvider.normalizeEndpoint(provider.endpoint))
        val current = providers().get()
        val updated = if (current.any { it.id == normalized.id }) {
            current.map { if (it.id == normalized.id) normalized else it }
        } else {
            current + normalized
        }
        providers().set(updated)
        // Blank means "leave it alone", which is what an edit screen that shows
        // a masked key has to mean — the alternative is wiping the key every
        // time someone corrects a typo in the name.
        if (!apiKey.isNullOrBlank()) keys.save(normalized.id, apiKey)
        if (activeId().get().isBlank()) activeId().set(normalized.id)
    }

    fun setActive(id: String) = activeId().set(id)

    fun delete(id: String) {
        providers().set(providers().get().filterNot { it.id == id })
        keys.clear(id)
        if (activeId().get() == id) {
            providers().get().firstOrNull()?.let { activeId().set(it.id) } ?: activeId().delete()
        }
    }
}

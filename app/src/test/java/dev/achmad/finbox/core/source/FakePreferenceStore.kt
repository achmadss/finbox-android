package dev.achmad.finbox.core.source

import dev.achmad.finbox.util.preference.Preference
import dev.achmad.finbox.util.preference.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop

/** An in-memory [PreferenceStore], so a test needs no Android. */
class FakePreferenceStore : PreferenceStore {

    private val values = mutableMapOf<String, MutableStateFlow<Any?>>()

    private fun <T> pref(key: String, defaultValue: T): Preference<T> = object : Preference<T> {
        @Suppress("UNCHECKED_CAST")
        private val flow = values.getOrPut(key) { MutableStateFlow(defaultValue) } as MutableStateFlow<T>

        override fun key() = key
        override fun get(): T = flow.value
        override fun set(value: T) { flow.value = value }
        override fun isSet() = key in values
        override fun delete() { values.remove(key) }
        override fun defaultValue(): T = defaultValue
        override fun changes(): Flow<T> = flow.drop(1)
        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow
    }

    override fun getString(key: String, defaultValue: String) = pref(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = pref(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = pref(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = pref(key, defaultValue)
    override fun getBoolean(key: String, defaultValue: Boolean) = pref(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>) = pref(key, defaultValue)
    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ) = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = values.mapValues { it.value.value }
}

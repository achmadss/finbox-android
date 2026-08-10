package com.example.core.preference

import android.content.SharedPreferences
import com.example.core.preference.AndroidPreference.BooleanPrimitive
import com.example.core.preference.AndroidPreference.FloatPrimitive
import com.example.core.preference.AndroidPreference.IntPrimitive
import com.example.core.preference.AndroidPreference.LongPrimitive
import com.example.core.preference.AndroidPreference.Object
import com.example.core.preference.AndroidPreference.StringPrimitive
import com.example.core.preference.AndroidPreference.StringSetPrimitive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class AndroidPreferenceStore(
    private val sharedPreferences: SharedPreferences,
) : PreferenceStore {

    private val keyFlow = sharedPreferences.keyFlow

    override fun getString(key: String, defaultValue: String): Preference<String> {
        return StringPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return LongPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return IntPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return FloatPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return BooleanPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return StringSetPrimitive(sharedPreferences, keyFlow, key, defaultValue)
    }

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return Object(
            preferences = sharedPreferences,
            keyFlow = keyFlow,
            key = key,
            defaultValue = defaultValue,
            serializer = serializer,
            deserializer = deserializer,
        )
    }

    override fun getAll(): Map<String, *> {
        return sharedPreferences.all ?: emptyMap<String, Any>()
    }
}

private val SharedPreferences.keyFlow
    get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
            trySend(
                key,
            )
        }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
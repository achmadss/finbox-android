package dev.achmad.finbox.features.settings.llm

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.finbox.core.llm.LlmClient
import dev.achmad.finbox.core.llm.LlmProvider
import dev.achmad.finbox.core.llm.LlmProviderStore
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsLlmScreenModel(
    private val providers: LlmProviderStore = inject(),
) : ScreenModel {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = State(
            providers = providers.providers().get(),
            activeId = providers.active()?.id.orEmpty(),
        )
    }

    fun setActive(id: String) {
        providers.setActive(id)
        refresh()
    }

    fun delete(id: String) {
        providers.delete(id)
        refresh()
    }

    data class State(
        val providers: List<LlmProvider> = emptyList(),
        val activeId: String = "",
    )
}

/**
 * One provider being added or edited. The model list is fetched rather than
 * typed, so this holds the three states that matter: nothing fetched yet, a
 * list to choose from, and an endpoint that would not list any — where typing
 * an id is the only way through.
 */
class LlmProviderScreenModel(
    private val existingId: String?,
    private val providers: LlmProviderStore = inject(),
    private val client: LlmClient = inject(),
) : ScreenModel {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * The key already on file, so an edit that leaves the field blank still
     * authorizes; otherwise a rename would silently strip it from every request.
     */
    private val storedKey: String? =
        existingId?.let { id -> providers.providers().get().firstOrNull { it.id == id } }
            ?.let { providers.key(it) }

    init {
        val existing = existingId?.let { id -> providers.providers().get().firstOrNull { it.id == id } }
        _state.value = State(
            name = existing?.name.orEmpty(),
            endpoint = existing?.endpoint.orEmpty(),
            model = existing?.model.orEmpty(),
            // Never read back into the field: it is stored encrypted, and showing
            // it would put it somewhere it does not belong.
            hasSavedKey = existing?.let { providers.key(it) != null } == true,
        )
    }

    fun onName(value: String) { _state.value = _state.value.copy(name = value) }
    fun onEndpoint(value: String) { _state.value = _state.value.copy(endpoint = value, models = emptyList()) }
    fun onApiKey(value: String) { _state.value = _state.value.copy(apiKey = value) }
    fun onModel(value: String) { _state.value = _state.value.copy(model = value) }

    fun fetchModels() {
        val current = _state.value
        _state.value = current.copy(busy = true, message = null)
        screenModelScope.launch {
            runCatching { client.models(current.asProvider(existingId), keyFor(current)) }
                .onSuccess { models ->
                    _state.value = _state.value.copy(
                        busy = false,
                        models = models,
                        // A host that lists nothing is not broken; typing an id is then the way through.
                        manualModel = models.isEmpty(),
                        model = _state.value.model.takeIf { it in models } ?: models.firstOrNull().orEmpty(),
                        message = Message.NoModels.takeIf { models.isEmpty() },
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        manualModel = true,
                        message = Message.ConnectFailed(it.message.orEmpty()),
                    )
                }
        }
    }

    /** One real round trip, so a wrong key or model fails here and not mid-backfill. */
    fun test() {
        val current = _state.value
        _state.value = current.copy(busy = true, message = null)
        screenModelScope.launch {
            val result = client.test(current.asProvider(existingId), keyFor(current))
            _state.value = _state.value.copy(
                busy = false,
                message = result.fold(
                    onSuccess = { Message.TestOk(it) },
                    onFailure = { Message.TestFailed(it.message.orEmpty()) },
                ),
            )
        }
    }

    /** What was typed, else what is on file. Blank means "leave it alone". */
    private fun keyFor(state: State): String? = state.apiKey.ifBlank { storedKey }

    fun save() {
        val current = _state.value
        providers.save(current.asProvider(existingId), current.apiKey)
    }

    data class State(
        val name: String = "",
        val endpoint: String = "",
        val apiKey: String = "",
        val model: String = "",
        val models: List<String> = emptyList(),
        val manualModel: Boolean = false,
        val hasSavedKey: Boolean = false,
        val busy: Boolean = false,
        val message: Message? = null,
    ) {
        val canFetch: Boolean get() = endpoint.isNotBlank() && !busy
        val canTest: Boolean get() = endpoint.isNotBlank() && model.isNotBlank() && !busy
        val canSave: Boolean get() = name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()

        internal fun asProvider(id: String?) = LlmProvider(
            // A new provider gets an id that outlives every later rename, so the
            // active pointer and the stored key stay attached to it.
            id = id ?: "llm-${System.currentTimeMillis()}",
            name = name.trim(),
            endpoint = LlmProvider.normalizeEndpoint(endpoint),
            model = model.trim(),
        )
    }

    sealed interface Message {
        data class ConnectFailed(val reason: String) : Message
        object NoModels : Message
        data class TestOk(val reply: String) : Message
        data class TestFailed(val reason: String) : Message
    }
}

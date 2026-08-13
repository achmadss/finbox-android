package dev.achmad.finbox.core.mvi

import cafe.adriel.voyager.core.model.StateScreenModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

abstract class MviScreenModel<State, Event, Effect>(
    initialState: State
) : StateScreenModel<State>(initialState) {

    private val _effect = MutableSharedFlow<Effect>()
    val effect = _effect.asSharedFlow()

    abstract fun handleEvent(event: Event)

    suspend fun emit(effect: Effect) {
        _effect.emit(effect)
    }

    fun tryEmit(effect: Effect) {
        _effect.tryEmit(effect)
    }

}
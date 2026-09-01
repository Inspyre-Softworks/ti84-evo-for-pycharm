package com.inspyresoftworks.ti84evo.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** User-level transfer preferences shared by every PyCharm project. */
@Service(Service.Level.APP)
@State(name = "Ti84EvoSettings", storages = [Storage("ti84-evo.xml")])
class EvoApplicationSettings : PersistentStateComponent<EvoApplicationSettings.State> {
    data class State(
        var optimizeImages: Boolean = true,
        var imageMaxWidth: Int = 320,
        var imageMaxHeight: Int = 210,
        var imageColors: Int = 64,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.sanitized()
    }

    fun snapshot(): State = state.copy().sanitized()

    fun update(value: State) {
        state = value.sanitized()
    }

    private fun State.sanitized(): State = copy(
        imageMaxWidth = imageMaxWidth.coerceIn(16, 320),
        imageMaxHeight = imageMaxHeight.coerceIn(16, 210),
        imageColors = imageColors.takeIf { it in SUPPORTED_COLOR_COUNTS } ?: 64,
    )

    companion object {
        val SUPPORTED_COLOR_COUNTS = listOf(16, 32, 64, 128, 256)
    }
}

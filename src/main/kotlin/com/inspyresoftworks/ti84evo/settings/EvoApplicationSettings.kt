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
        var marketplaceCheckIntervalSeconds: Int = DEFAULT_MARKETPLACE_CHECK_INTERVAL_SECONDS,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        // Preserve low developer intervals so they remain available if Mischief Mode is active.
        // Normal snapshots still enforce the public 60-second minimum.
        this.state = state.sanitized(mischiefMode = true)
    }

    fun snapshot(mischiefMode: Boolean = false): State = state.copy().sanitized(mischiefMode)

    fun update(value: State, mischiefMode: Boolean = false) {
        require(
            value.marketplaceCheckIntervalSeconds >= minimumMarketplaceCheckIntervalSeconds(mischiefMode),
        ) {
            "Marketplace check interval must be at least " +
                "${minimumMarketplaceCheckIntervalSeconds(mischiefMode)} seconds."
        }
        state = value.sanitized(mischiefMode)
    }

    private fun State.sanitized(mischiefMode: Boolean): State = copy(
        imageMaxWidth = imageMaxWidth.coerceIn(16, 320),
        imageMaxHeight = imageMaxHeight.coerceIn(16, 210),
        imageColors = imageColors.takeIf { it in SUPPORTED_COLOR_COUNTS } ?: 64,
        marketplaceCheckIntervalSeconds = marketplaceCheckIntervalSeconds.coerceAtLeast(
            minimumMarketplaceCheckIntervalSeconds(mischiefMode),
        ),
    )

    companion object {
        const val DEFAULT_MARKETPLACE_CHECK_INTERVAL_SECONDS = 3_600
        const val MIN_MARKETPLACE_CHECK_INTERVAL_SECONDS = 60
        const val MISCHIEF_MIN_MARKETPLACE_CHECK_INTERVAL_SECONDS = 1
        val SUPPORTED_COLOR_COUNTS = listOf(16, 32, 64, 128, 256)

        fun minimumMarketplaceCheckIntervalSeconds(mischiefMode: Boolean): Int =
            if (mischiefMode) MISCHIEF_MIN_MARKETPLACE_CHECK_INTERVAL_SECONDS
            else MIN_MARKETPLACE_CHECK_INTERVAL_SECONDS
    }
}

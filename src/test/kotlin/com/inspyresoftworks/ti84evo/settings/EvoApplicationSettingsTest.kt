package com.inspyresoftworks.ti84evo.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvoApplicationSettingsTest {
    @Test
    fun `normal settings reject marketplace intervals below sixty seconds`() {
        val settings = EvoApplicationSettings()
        val invalid = settings.snapshot().copy(marketplaceCheckIntervalSeconds = 59)

        assertFailsWith<IllegalArgumentException> { settings.update(invalid, mischiefMode = false) }
        assertEquals(3_600, settings.snapshot().marketplaceCheckIntervalSeconds)
    }

    @Test
    fun `mischief mode permits a one second marketplace interval`() {
        val settings = EvoApplicationSettings()
        val developer = settings.snapshot(true).copy(marketplaceCheckIntervalSeconds = 1)

        settings.update(developer, mischiefMode = true)

        assertEquals(1, settings.snapshot(true).marketplaceCheckIntervalSeconds)
        assertEquals(60, settings.snapshot(false).marketplaceCheckIntervalSeconds)
    }

    @Test
    fun `loading a developer interval preserves it without exposing it in normal mode`() {
        val settings = EvoApplicationSettings()

        settings.loadState(EvoApplicationSettings.State(marketplaceCheckIntervalSeconds = 2))

        assertEquals(2, settings.snapshot(true).marketplaceCheckIntervalSeconds)
        assertEquals(60, settings.snapshot(false).marketplaceCheckIntervalSeconds)
    }
}

package com.inspyresoftworks.ti84evo.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvoApplicationSettingsTest {
    @Test
    fun `changelog is claimed once per version across loaded settings`() {
        val settings = EvoApplicationSettings()
        assertTrue(settings.claimChangelog("0.6.0"))
        assertFalse(settings.claimChangelog("0.6.0"))
        val savedState = settings.state
        settings.loadState(EvoApplicationSettings.State())
        assertFalse(settings.claimChangelog("0.6.0"))

        val reloaded = EvoApplicationSettings()
        reloaded.loadState(savedState)
        assertFalse(reloaded.claimChangelog("0.6.0"))
        assertTrue(reloaded.claimChangelog("0.7.0"))
    }

    @Test
    fun `always show opens once per IDE session while mischief mode is active`() {
        val settings = EvoApplicationSettings()
        assertTrue(settings.claimChangelog("0.6.0"))
        settings.setAlwaysShowChangelog(true)
        assertFalse(settings.claimChangelog("0.6.0"))
        assertFalse(settings.claimChangelog("0.6.0", mischiefMode = true))

        settings.update(settings.snapshot(true).copy(optimizeImages = false), mischiefMode = true)
        assertTrue(settings.snapshot(true).alwaysShowChangelog)

        val nextSession = EvoApplicationSettings()
        nextSession.loadState(settings.state)
        assertTrue(nextSession.claimChangelog("0.6.0", mischiefMode = true))
        assertFalse(nextSession.claimChangelog("0.6.0", mischiefMode = true))
        nextSession.loadState(settings.state)
        assertFalse(nextSession.claimChangelog("0.6.0", mischiefMode = true))
    }

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

package com.inspyresoftworks.ti84evo.service

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvoMischiefModeTest {
    @Test
    fun `activation requires the exact phrase with only an optional final newline`() {
        val lock = createTempDirectory("ti84-evo-mm").resolve("mm.lock")

        assertFalse(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE)
        assertTrue(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE + "\n")
        assertTrue(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE + "\r\n")
        assertTrue(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE + "\n\n")
        assertFalse(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE.lowercase())
        assertFalse(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, "${EvoMischiefMode.ACTIVATION_PHRASE} ")
        assertFalse(EvoMischiefMode.isActive(lock))
        Files.writeString(lock, EvoMischiefMode.MANAGED_PHRASE)
        assertFalse(EvoMischiefMode.isActive(lock))
    }

    @Test
    fun `mark managed preserves the lock while replacing its contents exactly`() {
        val lock = createTempDirectory("ti84-evo-mm").resolve("mm.lock")
        Files.writeString(lock, EvoMischiefMode.ACTIVATION_PHRASE)

        EvoMischiefMode.markManaged(lock)

        assertTrue(Files.exists(lock))
        assertTrue(Files.readString(lock) == EvoMischiefMode.MANAGED_PHRASE)
        assertFalse(EvoMischiefMode.isActive(lock))
    }
}

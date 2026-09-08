package com.inspyresoftworks.ti84evo.ui

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class EvoBuildInfoTest {
    @Test
    fun packagedVersionMatchesRelease() {
        val releaseVersion = Files.readString(Path.of("VERSION")).trim()
        assertEquals(releaseVersion, EvoBuildInfo.version)
    }
}

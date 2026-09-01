package com.inspyresoftworks.ti84evo.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class EvoBuildInfoTest {
    @Test
    fun packagedVersionMatchesRelease() {
        assertEquals("0.3.0", EvoBuildInfo.version)
    }
}

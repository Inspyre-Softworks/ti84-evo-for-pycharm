package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals

class EvoLinkTest {
    @Test
    fun `system resource is normalized into the hh01 namespace`() {
        assertContentEquals(
            "hh01/get/hh01/sys/screen".encodeToByteArray(),
            buildGetRequest("sys/screen"),
        )
    }

    @Test
    fun `directory resource retains its nested hh01 path`() {
        assertContentEquals(
            "hh01/get/hh01/inf/res?name=directory&gotohome=1".encodeToByteArray(),
            buildGetRequest("hh01/inf/res?name=directory&gotohome=1"),
        )
    }
}

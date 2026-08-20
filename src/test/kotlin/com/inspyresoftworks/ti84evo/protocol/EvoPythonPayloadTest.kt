package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Python transfer fixture tests.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoPythonPayloadTest {
    @Test
    fun `payload matches known type-15 fixture`() {
        val built = EvoPythonPayload.build("TAYEVO", "print('hi')\n")

        assertEquals(
            "bf686d65746144617461bf64747970650f6776657273696f6e01646e616d654c" +
                "13e800e818e804e815e80ee8ff6776657273696f6e016473697a651824646461" +
                "7461582413010000240000000600000054415945564f000c0000027072696e74" +
                "2827686927290a00ff",
            built.bytes.toHex(),
        )
    }

    @Test
    fun `transfer URL tokenizes calculator name`() {
        assertEquals(
            "hh01/xfr/var?name=%EE%A0%93%EE%A0%80%EE%A0%98%EE%A0%84%EE%A0%95%EE%A0%8E" +
                "&type=15&memtarget=0&policy=1",
            EvoPythonPayload.transferUrl("TAYEVO"),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

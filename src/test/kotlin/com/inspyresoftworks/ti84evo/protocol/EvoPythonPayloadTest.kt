package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `downloaded Python payload decodes to its original source`() {
        val source = "from ti_system import recall_value\nprint(recall_value(\"score\"))\n"
        val built = EvoPythonPayload.build("MAIN", source)

        val decoded = EvoPythonPayload.decode(built.bytes)

        assertEquals("MAIN", decoded.programName)
        assertEquals(source, decoded.source)
        assertEquals(source.encodeToByteArray().size, decoded.sourceBytes)
        assertEquals(built.bytes.size, decoded.payloadBytes)
    }

    @Test
    fun `download decoder accepts calculator native length and zero alignment trailer`() {
        val source = "print('native calculator payload')\n"
        val original = EvoPythonPayload.build("MAIN", source).bytes
        val originalAppVarSize = EvoVariableFile.inspect(original).data.size
        val appVarStart = original.indices.first { index ->
            index + 3 < original.size &&
                original[index] == 0x13.toByte() &&
                original[index + 1] == 0x01.toByte() &&
                original[index + 2] == 0.toByte() &&
                original[index + 3] == 0.toByte()
        }
        assertEquals(0x58.toByte(), original[appVarStart - 2])
        assertEquals(originalAppVarSize.toByte(), original[appVarStart - 1])

        val raw = ByteArray(original.size + 2)
        original.copyInto(raw, endIndex = original.lastIndex)
        raw[original.lastIndex] = 0
        raw[original.lastIndex + 1] = 0
        raw[original.lastIndex + 2] = original.last()
        val paddedAppVarSize = originalAppVarSize + 2
        raw[appVarStart - 1] = paddedAppVarSize.toByte()
        val nativeLength = originalAppVarSize
        repeat(4) { byteIndex ->
            raw[appVarStart + 4 + byteIndex] = (nativeLength ushr (byteIndex * 8)).toByte()
        }

        val decoded = EvoPythonPayload.decode(raw)

        assertEquals("MAIN", decoded.programName)
        assertEquals(source, decoded.source)
    }

    @Test
    fun `download decoder rejects a truncated AppVar`() {
        val built = EvoPythonPayload.build("MAIN", "print(1)\n")

        assertFailsWith<EvoProtocolException> {
            EvoPythonPayload.decode(built.bytes.copyOf(built.bytes.size - 1))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvoImagePayloadTest {
    @Test
    fun `desktop image becomes resized compressed IM8C AppVar`() {
        val source = BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().also { graphics ->
                graphics.color = Color(20, 120, 220)
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
            }
        }
        val built = EvoImagePayload.build(
            source,
            "SPLASH",
            EvoApplicationSettings.State(true, 40, 40, 16),
        )

        assertEquals(40, built.width)
        assertEquals(20, built.height)
        assertTrue(built.colors <= 16)
        val envelope = CborReader(built.bytes).readComplete() as Map<*, *>
        assertEquals(8L, (envelope["metaData"] as Map<*, *>)["type"])
        val data = envelope["data"] as ByteArray
        val encodedSize = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        assertEquals(data.size - 2, encodedSize)
        assertEquals("IM8C", data.copyOfRange(2, 6).decodeToString())
        assertEquals(40, readUInt24Le(data, 6))
        assertEquals(20, readUInt24Le(data, 9))
        assertEquals(1, data[12].toInt() and 0xFF)
        assertTrue(EvoImagePayload.isPythonImageVariable(built.bytes))
    }

    @Test
    fun `desktop image can become a native image variable`() {
        val source = BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().also { graphics ->
                graphics.color = Color(220, 120, 20)
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
            }
        }
        val built = EvoImagePayload.build(
            source,
            "Image8",
            EvoApplicationSettings.State(true, 40, 40, 16),
            EvoImagePayload.Format.NATIVE_IMAGE,
        )

        assertEquals(EvoImagePayload.Format.NATIVE_IMAGE, built.format)
        assertEquals(160, built.width)
        assertEquals(105, built.height)
        assertEquals(65536, built.colors)
        val envelope = CborReader(built.bytes).readComplete() as Map<*, *>
        val metadata = envelope["metaData"] as Map<*, *>
        assertEquals(5L, metadata["type"])
        assertContentEquals(byteArrayOf(0xB7.toByte(), 0xE8.toByte()), metadata["name"] as ByteArray)
        val data = envelope["data"] as ByteArray
        assertEquals(33601, data.size)
        assertEquals(0x16, data[0].toInt() and 0xFF)
        // The wide source fills the background instead of acquiring white bars.
        for (offset in 1 until data.size step 2) {
            assertEquals(0xDBC2, readUInt16Le(data, offset))
        }
    }

    @Test
    fun `native background stores bottom row first without mirroring columns`() {
        val source = BufferedImage(160, 105, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, Color.RED.rgb)
            setRGB(159, 0, Color.GREEN.rgb)
            setRGB(0, 104, Color.BLUE.rgb)
            setRGB(159, 104, Color.WHITE.rgb)
        }
        val data = nativeData(source)
        assertEquals(0x001F, readUInt16Le(data, 1))
        assertEquals(0xFFFF, readUInt16Le(data, 1 + 159 * 2))
        assertEquals(0xF800, readUInt16Le(data, 1 + 104 * 160 * 2))
        assertEquals(0x07E0, readUInt16Le(data, 1 + (105 * 160 - 1) * 2))
    }

    @Test
    fun `native backgrounds center crop wide and tall sources`() {
        for ((width, height) in listOf(320 to 105, 160 to 315)) {
            val source = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
                val graphics = createGraphics()
                graphics.color = Color.RED
                graphics.fillRect(0, 0, width, height)
                graphics.color = Color.GREEN
                graphics.fillRect((width - 160) / 2, (height - 105) / 2, 160, 105)
                graphics.dispose()
            }
            val data = nativeData(source)
            for (offset in 1 until data.size step 2) {
                assertEquals(0x07E0, readUInt16Le(data, offset))
            }
        }
    }

    @Test
    fun `large reductions preserve average brightness of fine detail`() {
        // A single-pass reduction samples the black centers and misses the white
        // lines; a filtered reduction should retain their one-quarter coverage.
        val source = BufferedImage(1280, 840, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setRGB(x, y, if (x % 8 < 2) Color.WHITE.rgb else Color.BLACK.rgb)
            }
        }
        val data = nativeData(source)
        val interior = (10 until 150).map { x -> readUInt16Le(data, 1 + (50 * 160 + x) * 2) }
        val averageRed = interior.map { (it shr 11) * 255.0 / 31 }.average()
        assertTrue(averageRed in 50.0..80.0, "Expected quarter-white detail, got $averageRed")
    }

    @Test
    fun `small transparent backgrounds upscale onto white`() {
        val source = BufferedImage(16, 10, BufferedImage.TYPE_INT_ARGB)
        val data = nativeData(source)
        for (offset in 1 until data.size step 2) {
            assertEquals(0xFFFF, readUInt16Le(data, offset))
        }
    }

    private fun nativeData(source: BufferedImage): ByteArray {
        val built = EvoImagePayload.build(source, "Image1", true, format = EvoImagePayload.Format.NATIVE_IMAGE)
        return (CborReader(built.bytes).readComplete() as Map<*, *>)["data"] as ByteArray
    }

    private fun readUInt16Le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `non image AppVar is not reported as a Python image`() {
        val raw = byteArrayOf(
            0xBF.toByte(),
            0x68,
            'm'.code.toByte(),
            'e'.code.toByte(),
            't'.code.toByte(),
            'a'.code.toByte(),
            'D'.code.toByte(),
            'a'.code.toByte(),
            't'.code.toByte(),
            'a'.code.toByte(),
            0xBF.toByte(),
            0x64,
            't'.code.toByte(),
            'y'.code.toByte(),
            'p'.code.toByte(),
            'e'.code.toByte(),
            0x08,
            0xFF.toByte(),
            0x64,
            'd'.code.toByte(),
            'a'.code.toByte(),
            't'.code.toByte(),
            'a'.code.toByte(),
            0x43,
            'n'.code.toByte(),
            'o'.code.toByte(),
            'p'.code.toByte(),
            0xFF.toByte(),
        )

        assertTrue(!EvoImagePayload.isPythonImageVariable(raw))
    }

    private fun readUInt24Le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16)

    @Test
    fun `AppVar transfer URL contains type name archive and overwrite policy`() {
        val url = EvoImagePayload.transferUrl("IMG_1", true, EvoImagePayload.Format.PYTHON_APP_VAR)
        assertTrue("type=8" in url)
        assertTrue("memtarget=1" in url)
        assertTrue("policy=1" in url)
        assertTrue("name=" in url)
    }

    @Test
    fun `native image transfer URL contains type name archive and overwrite policy`() {
        val url = EvoImagePayload.transferUrl("Image8", true, EvoImagePayload.Format.NATIVE_IMAGE)
        assertTrue("type=5" in url)
        assertTrue("memtarget=1" in url)
        assertTrue("policy=1" in url)
        assertTrue("name=" in url)
    }
}

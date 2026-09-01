package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
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
        assertEquals(2, (data[6].toInt() and 0xFF) or ((data[7].toInt() and 0xFF) shl 8))
        assertEquals(40, (data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8))
        assertEquals(20, (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8))
    }

    @Test
    fun `image transfer URL contains type name archive and overwrite policy`() {
        val url = EvoImagePayload.transferUrl("IMG_1", true)
        assertTrue("type=8" in url)
        assertTrue("memtarget=1" in url)
        assertTrue("policy=1" in url)
        assertTrue("name=" in url)
    }
}

package com.inspyresoftworks.ti84evo.ui

import com.inspyresoftworks.ti84evo.model.EvoScreenCapture
import java.awt.image.BufferedImage

/**
 * Evo framebuffer rendering helpers.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
object EvoImage {
    fun toBufferedImage(capture: EvoScreenCapture): BufferedImage {
        require(capture.bitsPerPixel == 16) {
            "only RGB565 16bpp screenshots are supported right now"
        }

        val image = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_RGB)
        var offset = 0
        for (y in 0 until capture.height) {
            for (x in 0 until capture.width) {
                val value =
                    (capture.framebuffer[offset].toInt() and 0xFF) or
                        ((capture.framebuffer[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2

                val r5 = (value ushr 11) and 0x1F
                val g6 = (value ushr 5) and 0x3F
                val b5 = value and 0x1F
                val red = (r5 shl 3) or (r5 ushr 2)
                val green = (g6 shl 2) or (g6 ushr 4)
                val blue = (b5 shl 3) or (b5 ushr 2)
                image.setRGB(x, y, (red shl 16) or (green shl 8) or blue)
            }
        }
        return image
    }
}

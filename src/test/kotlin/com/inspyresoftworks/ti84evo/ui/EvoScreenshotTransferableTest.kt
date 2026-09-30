package com.inspyresoftworks.ti84evo.ui

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EvoScreenshotTransferableTest {
    @Test
    fun `clipboard exposes the original image at full resolution`() {
        val image = BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB)
        val transferable = EvoScreenshotTransferable(image)

        assertContentEquals(arrayOf(DataFlavor.imageFlavor), transferable.transferDataFlavors)
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertSame(image, transferable.getTransferData(DataFlavor.imageFlavor))
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
        assertFailsWith<UnsupportedFlavorException> {
            transferable.getTransferData(DataFlavor.stringFlavor)
        }
    }
}

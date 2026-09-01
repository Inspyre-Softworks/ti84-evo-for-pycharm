package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.settings.EvoApplicationSettings
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/** Converts a desktop image to the Evo's compressed IM8C Python-image AppVar envelope. */
object EvoImagePayload {
    data class Built(
        val name: String,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val width: Int,
        val height: Int,
        val colors: Int,
        val bytes: ByteArray,
    )

    fun isValidName(name: String): Boolean =
        name.length in 1..8 && name.first() in 'A'..'Z' && name.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    fun defaultName(fileStem: String): String {
        val cleaned = fileStem.uppercase().filter { it in 'A'..'Z' || it.isDigit() || it == '_' }.take(8)
        return cleaned.takeIf { it.firstOrNull() in 'A'..'Z' } ?: "IMAGE"
    }

    fun transferUrl(name: String, archived: Boolean): String {
        require(isValidName(name)) { "Invalid Evo image variable name" }
        val encoded = buildString {
            var offset = 0
            val tokens = tokenName(name.uppercase())
            while (offset + 1 < tokens.size) {
                val word = (tokens[offset].toInt() and 0xFF) or ((tokens[offset + 1].toInt() and 0xFF) shl 8)
                word.toChar().toString().toByteArray(StandardCharsets.UTF_8).forEach { byte ->
                    append("%%%02X".format(byte.toInt() and 0xFF))
                }
                offset += 2
            }
        }
        return "hh01/xfr/var?name=$encoded&type=8&memtarget=${if (archived) 1 else 0}&policy=1"
    }

    fun build(source: BufferedImage, requestedName: String, settings: EvoApplicationSettings.State): Built {
        require(source.width > 0 && source.height > 0) { "Image has no pixels" }
        val name = requestedName.trim().uppercase()
        require(isValidName(name)) { "Image variable name must begin with a letter and contain 1–8 letters, digits, or underscores" }

        val maxWidth = if (settings.optimizeImages) settings.imageMaxWidth else 320
        val maxHeight = if (settings.optimizeImages) settings.imageMaxHeight else 210
        var scale = minOf(1.0, maxWidth.toDouble() / source.width, maxHeight.toDouble() / source.height)
        val maxColors = if (settings.optimizeImages) settings.imageColors else 256

        repeat(12) {
            val width = (source.width * scale).roundToInt().coerceAtLeast(1)
            val height = (source.height * scale).roundToInt().coerceAtLeast(1)
            val image = resize(source, width, height)
            val encoded = encodeImage(image, maxColors)
            if (encoded.size <= 0xFFFF) {
                val data = ByteArrayOutputStream().apply {
                    write(encoded.size and 0xFF)
                    write((encoded.size shr 8) and 0xFF)
                    write(encoded.bytes)
                }.toByteArray()
                return Built(
                    name,
                    source.width,
                    source.height,
                    width,
                    height,
                    encoded.paletteSize,
                    wrapAppVar(name, data),
                )
            }
            if (!settings.optimizeImages) {
                error("Converted image exceeds the Evo image-size field; enable image optimization or choose a smaller image")
            }
            scale *= 0.9
        }
        error("Image could not be reduced enough for the Evo image format")
    }

    private data class EncodedImage(val bytes: ByteArray, val paletteSize: Int) {
        val size: Int get() = bytes.size
    }

    private data class Color(val r: Int, val g: Int, val b: Int, val count: Int)

    private fun encodeImage(image: BufferedImage, maxColors: Int): EncodedImage {
        val transparent = (0 until image.height).any { y -> (0 until image.width).any { x -> image.getRGB(x, y).ushr(24) < 128 } }
        val frequencies = linkedMapOf<Int, Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            if (argb.ushr(24) < 128) continue
            val rgb565 = toRgb565(argb)
            frequencies[rgb565] = (frequencies[rgb565] ?: 0) + 1
        }
        val slots = (maxColors - if (transparent) 1 else 0).coerceAtLeast(1)
        val colors = frequencies.map { (rgb565, count) ->
            Color(
                ((rgb565 shr 11) and 0x1F) * 255 / 31,
                ((rgb565 shr 5) and 0x3F) * 255 / 63,
                (rgb565 and 0x1F) * 255 / 31,
                count,
            )
        }
        val palette = mutableListOf<Int>()
        if (transparent) palette += 0
        palette += medianCut(colors, slots).map { toRgb565(0xFF000000.toInt() or (it.r shl 16) or (it.g shl 8) or it.b) }
        if (palette.size == if (transparent) 1 else 0) palette += 0

        val indexCache = mutableMapOf<Int, Int>()
        val indices = ByteArray(image.width * image.height)
        var offset = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            val index = if (transparent && argb.ushr(24) < 128) {
                0
            } else {
                val rgb565 = toRgb565(argb)
                indexCache.getOrPut(rgb565) { nearestPaletteIndex(rgb565, palette, if (transparent) 1 else 0) }
            }
            indices[offset++] = index.toByte()
        }

        val out = ByteArrayOutputStream().apply {
            write("IM8C".toByteArray(StandardCharsets.US_ASCII))
            writeUInt16Le(2) // RLE encoding.
            writeUInt16Le(image.width)
            writeUInt16Le(image.height)
            write(if (transparent) 1 else 0)
            write(0) // Transparent palette index.
            writeUInt16Le(palette.size)
            palette.forEach { writeUInt16Le(it) }
            write(encodeRle(indices))
        }.toByteArray()
        return EncodedImage(out, palette.size)
    }

    private fun medianCut(input: List<Color>, limit: Int): List<Color> {
        if (input.size <= limit) return input
        val boxes = mutableListOf(input)
        while (boxes.size < limit) {
            val index = boxes.indices.maxByOrNull { colorRange(boxes[it]) } ?: break
            val box = boxes.removeAt(index)
            if (box.size < 2) {
                boxes.add(box)
                break
            }
            val channel = widestChannel(box)
            val sorted = box.sortedBy { when (channel) { 0 -> it.r; 1 -> it.g; else -> it.b } }
            val half = sorted.sumOf(Color::count) / 2
            var sum = 0
            var split = 1
            for (i in 0 until sorted.lastIndex) {
                sum += sorted[i].count
                if (sum >= half) { split = i + 1; break }
            }
            boxes += sorted.subList(0, split)
            boxes += sorted.subList(split, sorted.size)
        }
        return boxes.map { box ->
            val total = box.sumOf(Color::count).coerceAtLeast(1)
            Color(
                box.sumOf { it.r * it.count } / total,
                box.sumOf { it.g * it.count } / total,
                box.sumOf { it.b * it.count } / total,
                total,
            )
        }
    }

    private fun colorRange(colors: List<Color>): Int = when (widestChannel(colors)) {
        0 -> colors.maxOf { it.r } - colors.minOf { it.r }
        1 -> colors.maxOf { it.g } - colors.minOf { it.g }
        else -> colors.maxOf { it.b } - colors.minOf { it.b }
    }

    private fun widestChannel(colors: List<Color>): Int {
        val ranges = intArrayOf(
            colors.maxOf { it.r } - colors.minOf { it.r },
            colors.maxOf { it.g } - colors.minOf { it.g },
            colors.maxOf { it.b } - colors.minOf { it.b },
        )
        return ranges.indices.maxByOrNull { ranges[it] } ?: 0
    }

    private fun nearestPaletteIndex(color: Int, palette: List<Int>, start: Int): Int {
        val r = (color shr 11) and 0x1F
        val g = (color shr 5) and 0x3F
        val b = color and 0x1F
        return (start until palette.size).minByOrNull { index ->
            val candidate = palette[index]
            val dr = r - ((candidate shr 11) and 0x1F)
            val dg = g - ((candidate shr 5) and 0x3F)
            val db = b - (candidate and 0x1F)
            dr * dr * 2 + dg * dg + db * db * 2
        } ?: start
    }

    private fun encodeRle(indices: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val literals = mutableListOf<Byte>()
        fun flush() {
            while (literals.isNotEmpty()) {
                val count = minOf(128, literals.size)
                out.write(count - 1)
                repeat(count) { out.write(literals.removeAt(0).toInt() and 0xFF) }
            }
        }
        var index = 0
        while (index < indices.size) {
            var run = 1
            while (run < 128 && index + run < indices.size && indices[index + run] == indices[index]) run++
            if (run >= 2) {
                flush()
                out.write(0x80 + run - 2)
                out.write(indices[index].toInt() and 0xFF)
                index += run
            } else {
                literals += indices[index++]
                if (literals.size == 128) flush()
            }
        }
        flush()
        return out.toByteArray()
    }

    private fun wrapAppVar(name: String, data: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(0xBF)
        write(cborText("metaData"))
        write(0xBF)
        write(cborText("type")); write(cborUnsigned(8))
        write(cborText("version")); write(cborUnsigned(1))
        write(cborText("flags")); write(cborUnsigned(1))
        write(cborText("name")); write(cborBytes(tokenName(name) + byteArrayOf(0, 0)))
        write(0xFF)
        write(cborText("version")); write(cborUnsigned(1))
        write(cborText("size")); write(cborUnsigned(data.size))
        write(cborText("data")); write(cborBytes(data))
        write(0xFF)
    }.toByteArray()

    private fun tokenName(name: String): ByteArray = ByteArrayOutputStream().apply {
        name.forEach { char ->
            val token = when (char) {
                in 'A'..'Z' -> 0xE800 + (char - 'A')
                in '0'..'9' -> 0xE401 + (char - '0')
                '_' -> 0x005F
                else -> error("Unsupported image name character")
            }
            writeUInt16Le(token)
        }
    }.toByteArray()

    private fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { target ->
            val graphics = target.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, width, height, null)
            graphics.dispose()
        }

    private fun toRgb565(argb: Int): Int =
        (((argb shr 19) and 0x1F) shl 11) or (((argb shr 10) and 0x3F) shl 5) or ((argb shr 3) and 0x1F)

    private fun cborText(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8).let {
        KermitPacketCodec.concat(cborLength(3, it.size), it)
    }
    private fun cborBytes(value: ByteArray): ByteArray = KermitPacketCodec.concat(cborLength(2, value.size), value)
    private fun cborUnsigned(value: Int): ByteArray = when {
        value < 24 -> byteArrayOf(value.toByte())
        value <= 0xFF -> byteArrayOf(0x18, value.toByte())
        value <= 0xFFFF -> byteArrayOf(0x19, (value shr 8).toByte(), value.toByte())
        else -> byteArrayOf(0x1A, (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
    }
    private fun cborLength(type: Int, size: Int): ByteArray = when {
        size < 24 -> byteArrayOf(((type shl 5) or size).toByte())
        size <= 0xFF -> byteArrayOf(((type shl 5) or 24).toByte(), size.toByte())
        size <= 0xFFFF -> byteArrayOf(((type shl 5) or 25).toByte(), (size shr 8).toByte(), size.toByte())
        else -> byteArrayOf(((type shl 5) or 26).toByte(), (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte())
    }
    private fun ByteArrayOutputStream.writeUInt16Le(value: Int) {
        write(value and 0xFF); write((value shr 8) and 0xFF)
    }
}

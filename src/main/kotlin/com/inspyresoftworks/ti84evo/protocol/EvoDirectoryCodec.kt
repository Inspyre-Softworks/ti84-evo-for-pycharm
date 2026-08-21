package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.model.EvoDirectoryEntry

/** Decodes the calculator's directory resource into UI-ready entries. */
object EvoDirectoryCodec {
    fun decode(raw: ByteArray): List<EvoDirectoryEntry> {
        val root = CborReader(raw).readComplete() as? Map<*, *>
            ?: throw EvoProtocolException("directory resource did not decode to a map")
        val data = root["data"] as? List<*>
            ?: throw EvoProtocolException("directory resource is missing its data array")

        return data.mapIndexed { index, value ->
            val item = value as? Map<*, *>
                ?: throw EvoProtocolException("directory entry $index is not a map")
            val type = item.optionalNumber("type", 0).toInt()
            val size = item.optionalNumber("size", 0).toLong()
            if (type < 0 || size < 0) {
                throw EvoProtocolException("directory entry $index has a negative type or size")
            }
            val tokenName = item["tokName"] as? ByteArray ?: byteArrayOf()

            EvoDirectoryEntry(
                name = displayName(item, type, tokenName),
                type = type,
                size = size,
                archived = item["mem"] as? Boolean ?: false,
                tokenName = tokenName,
            )
        }
    }

    private fun displayName(item: Map<*, *>, type: Int, tokenName: ByteArray): String {
        val words = tokenWords(tokenName)
        if (type in CUSTOM_NAME_TYPES) {
            decodeCustomName(words)?.let { return it }
        }
        if (type == 1 && words.firstOrNull() == CUSTOM_LIST_PREFIX) {
            decodeCustomName(words.drop(1))?.let { return it }
        }
        if (words.size == 1) {
            commonTokenName(type, words.single())?.let { return it }
        }

        val fallback = item["dispName"] ?: tokenName
        return when (fallback) {
            is ByteArray -> String(fallback, Charsets.UTF_8)
            else -> fallback.toString()
        }.ifBlank { "(unnamed)" }
    }

    private fun commonTokenName(type: Int, word: Int): String? = when (type) {
        0 -> when (word) {
            in 0xE800..0xE819 -> ('A'.code + word - 0xE800).toChar().toString()
            0xE81A -> "theta"
            else -> null
        }
        1 -> if (word in 0xE830..0xE835) "L${word - 0xE830 + 1}" else null
        3 -> when (word) {
            0xE899 -> "GDB0"
            in 0xE890..0xE898 -> "GDB${word - 0xE890 + 1}"
            else -> null
        }
        4 -> indexedTokenName(word, 0xE880, 0xE889, "Pic")
        5 -> indexedTokenName(word, 0xE8B0, 0xE8B9, "Image")
        6 -> if (word in 0xE820..0xE829) "[${('A'.code + word - 0xE820).toChar()}]" else null
        7 -> functionName(word)
        10 -> indexedTokenName(word, 0xE8A0, 0xE8A9, "Str")
        12 -> if (word == 0xE8BA) "Window" else null
        13 -> if (word == 0xE8BB) "RclWindw" else null
        14 -> if (word == 0xE8BC) "TblSet" else null
        else -> null
    }

    private fun indexedTokenName(word: Int, first: Int, zero: Int, prefix: String): String? = when (word) {
        zero -> "${prefix}0"
        in first until zero -> "$prefix${word - first + 1}"
        else -> null
    }

    private fun functionName(word: Int): String? = when (word) {
        in 0xE840..0xE849 -> "Y${if (word == 0xE849) 0 else word - 0xE840 + 1}"
        in 0xE850..0xE85B -> {
            val index = (word - 0xE850) / 2 + 1
            "${if ((word - 0xE850) % 2 == 0) 'X' else 'Y'}${index}T"
        }
        in 0xE860..0xE865 -> "r${word - 0xE860 + 1}"
        in 0xE870..0xE872 -> ('u'.code + word - 0xE870).toChar().toString()
        else -> null
    }

    private fun decodeCustomName(words: List<Int>): String? {
        if (words.isEmpty()) return null
        return buildString {
            for (word in words) {
                append(
                    when (word) {
                        in 0xE800..0xE819 -> ('A'.code + word - 0xE800).toChar()
                        in 0x0061..0x007A, in 0x0041..0x005A, in 0x0030..0x0039 -> word.toChar()
                        in 0xE401..0xE40A -> ('0'.code + word - 0xE401).toChar()
                        0x005F -> '_'
                        CUSTOM_NAME_THETA -> 'θ'
                        else -> return null
                    },
                )
            }
        }
    }

    private fun tokenWords(bytes: ByteArray): List<Int> = buildList {
        var index = 0
        while (index + 1 < bytes.size) {
            val word = (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
            if (word == 0) break
            add(word)
            index += 2
        }
    }

    private fun Map<*, *>.optionalNumber(key: String, default: Number): Number {
        val value = this[key] ?: return default
        return value as? Number ?: throw EvoProtocolException("directory entry $key is not numeric")
    }

    private const val CUSTOM_LIST_PREFIX = 0xE836
    private const val CUSTOM_NAME_THETA = 0xE81A
    private val CUSTOM_NAME_TYPES = setOf(2, 8, 9, 15)
}

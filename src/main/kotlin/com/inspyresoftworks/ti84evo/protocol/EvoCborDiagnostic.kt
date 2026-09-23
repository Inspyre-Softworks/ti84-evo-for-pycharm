package com.inspyresoftworks.ti84evo.protocol

/** Human-readable CBOR rendering that keeps unknown keys and raw byte strings visible. */
object EvoCborDiagnostic {
    fun decodeAndRender(raw: ByteArray): String = render(CborReader(raw).readComplete())

    fun render(value: Any?, indent: Int = 0): String {
        val padding = "  ".repeat(indent)
        return when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is ByteArray -> "h'${value.joinToString("") { "%02X".format(it.toInt() and 0xFF) }}'"
            is Map<*, *> -> if (value.isEmpty()) {
                "{}"
            } else {
                value.entries.joinToString(separator = ",\n", prefix = "{\n", postfix = "\n$padding}") { (key, item) ->
                    "${"  ".repeat(indent + 1)}${render(key)}: ${render(item, indent + 1)}"
                }
            }
            is List<*> -> if (value.isEmpty()) {
                "[]"
            } else {
                value.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n$padding]") { item ->
                    "${"  ".repeat(indent + 1)}${render(item, indent + 1)}"
                }
            }
            else -> value.toString()
        }
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04X".format(char.code)) else append(char)
            }
        }
    }
}

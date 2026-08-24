package com.inspyresoftworks.ti84evo.ui

import java.util.Locale

internal data class EvoAttributeRow(
    val section: String,
    val label: String,
    val key: String,
    val value: String,
)

internal object EvoAttributePresentation {
    private data class Definition(
        val key: String,
        val section: String,
        val label: String,
        val formatter: (Any?) -> String = ::plainValue,
    )

    private val definitions = listOf(
        Definition("product", "Device", "Product Build"),
        Definition("id", "Device", "Device ID"),
        Definition("total-ram", "Memory", "Total RAM", ::byteCount),
        Definition("total-flash", "Memory", "Total Flash", ::byteCount),
        Definition("battery", "Power", "Battery Level", ::batteryLevel),
        Definition("charging", "Power", "Charging", ::yesNo),
        Definition("bl1-version", "Firmware", "Bootloader 1 Version"),
        Definition("bl2-version", "Firmware", "Bootloader 2 Version"),
        Definition("bsp-version", "Firmware", "Board Support Package Version"),
        Definition("pkg-version", "Firmware", "System Package Version"),
        Definition("dev-cert", "Security", "Developer Certificate", ::certificateState),
        Definition("dbg-cert", "Security", "Debug Certificate", ::certificateState),
    )

    fun rows(attributes: Map<String, Any?>): List<EvoAttributeRow> {
        val known = definitions.mapNotNull { definition ->
            if (!attributes.containsKey(definition.key)) return@mapNotNull null
            EvoAttributeRow(
                definition.section,
                definition.label,
                definition.key,
                definition.formatter(attributes[definition.key]),
            )
        }
        val knownKeys = definitions.mapTo(hashSetOf()) { it.key }
        val additional = attributes.entries
            .filterNot { it.key in knownKeys }
            .sortedBy { it.key }
            .map { (key, value) ->
                EvoAttributeRow("Additional", friendlyName(key), key, plainValue(value))
            }
        return known + additional
    }

    fun toMarkdown(attributes: Map<String, Any?>): String {
        val rows = rows(attributes)
        return buildString {
            appendLine("# TI-84 Evo Attributes")
            rows.groupBy { it.section }.forEach { (section, sectionRows) ->
                appendLine()
                appendLine("## $section")
                appendLine()
                appendLine("| Attribute | Value | Protocol key |")
                appendLine("|---|---|---|")
                sectionRows.forEach { row ->
                    appendLine("| ${escapeCell(row.label)} | ${escapeCell(row.value)} | `${escapeCode(row.key)}` |")
                }
            }
            appendLine()
            appendLine("## Raw protocol values")
            appendLine()
            appendLine("```text")
            attributes.forEach { (key, value) -> appendLine("$key: ${rawValue(value)}") }
            append("```")
        }
    }

    private fun byteCount(value: Any?): String {
        val bytes = (value as? Number)?.toLong() ?: return plainValue(value)
        val human = when {
            bytes >= 1024L * 1024L -> "%.2f MiB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> "%.1f KiB".format(Locale.ROOT, bytes / 1024.0)
            else -> "$bytes B"
        }
        return "${"%,d".format(Locale.ROOT, bytes)} bytes ($human)"
    }

    private fun batteryLevel(value: Any?): String =
        (value as? Number)?.let { "Level ${it.toLong()}" } ?: plainValue(value)

    private fun yesNo(value: Any?): String = when (value) {
        true, 1, 1L, "1", "yes", "true" -> "Yes"
        false, 0, 0L, "0", "no", "false" -> "No"
        else -> plainValue(value)
    }

    private fun certificateState(value: Any?): String = when (value.toString().lowercase(Locale.ROOT)) {
        "yes", "true", "1" -> "Installed"
        "no", "false", "0" -> "Not installed"
        else -> plainValue(value)
    }

    private fun friendlyName(key: String): String = key
        .split('-', '_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    private fun plainValue(value: Any?): String = when (value) {
        null -> "Not reported"
        is ByteArray -> "${value.size} bytes"
        else -> value.toString()
    }

    private fun rawValue(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> value.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        else -> value.toString()
    }

    private fun escapeCell(value: String): String = value.replace("|", "\\|").replace("\n", "<br>")

    private fun escapeCode(value: String): String = value.replace("`", "\\`")
}

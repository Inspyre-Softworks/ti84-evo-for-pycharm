package com.inspyresoftworks.ti84evo.smartpad

/**
 * Lossless parser for USB HID report descriptors.
 *
 * The parser deliberately retains every item, including reserved and long
 * items, so future SmartPad firmware fields are not hidden by today's model.
 */
data class SmartPadHidDescriptor(
    val raw: ByteArray,
    val items: List<HidItem>,
    val fields: List<HidReportField>,
    val collections: List<HidCollection>,
) {
    val reportIds: Set<Int>
        get() = fields.mapTo(linkedSetOf()) { it.reportId }

    val hasReportIds: Boolean
        get() = reportIds.any { it != 0 }

    val vendorDefinedUsagePages: Set<Int>
        get() = buildSet {
            fields.mapNotNullTo(this) { it.usagePage?.takeIf(::isVendorDefinedUsagePage) }
            collections.mapNotNullTo(this) { it.usagePage?.takeIf(::isVendorDefinedUsagePage) }
        }

    fun reportBits(type: HidReportType, reportId: Int = 0): Int =
        fields.filter { it.type == type && it.reportId == reportId }
            .maxOfOrNull { it.bitOffset + it.reportSize * it.reportCount }
            ?: 0

    fun reportBytes(type: HidReportType, reportId: Int = 0): Int {
        val payloadBytes = (reportBits(type, reportId) + 7) / 8
        return payloadBytes + if (reportId == 0) 0 else 1
    }

    fun humanReadableDump(): String = buildString {
        appendLine("HID report descriptor (${raw.size} bytes)")
        appendLine("Raw: ${raw.toHex()}")
        items.forEach { item ->
            append("%04X  ".format(item.offset))
            append(item.encoded.toHex().padEnd(18))
            append(item.label)
            if (item.data.isNotEmpty()) {
                append(" = ")
                append(formatItemValue(item))
            }
            appendLine()
        }
        appendLine("Reports:")
        HidReportType.entries.forEach { type ->
            reportIds.ifEmpty { setOf(0) }.sorted().forEach { reportId ->
                val reportFields = fields.filter { it.type == type && it.reportId == reportId }
                if (reportFields.isNotEmpty()) {
                    appendLine("  $type id=$reportId ${reportBits(type, reportId)} bits (${reportBytes(type, reportId)} wire bytes)")
                    reportFields.forEach { field ->
                        append("    bits ${field.bitOffset}..${field.bitOffset + field.reportSize * field.reportCount - 1}")
                        append("  ${field.reportCount} x ${field.reportSize}")
                        append("  page=${field.usagePage?.hex() ?: "unset"}")
                        append("  usages=${field.usageDescription()}")
                        append("  flags=${field.flags.hex()}")
                        appendLine()
                    }
                }
            }
        }
        appendLine(
            "Vendor-defined usage pages: " +
                if (vendorDefinedUsagePages.isEmpty()) "none" else vendorDefinedUsagePages.joinToString { it.hex() },
        )
    }.trimEnd()

    companion object {
        fun parse(raw: ByteArray): SmartPadHidDescriptor {
            val items = HidItemParser.parse(raw)
            val fields = mutableListOf<HidReportField>()
            val collections = mutableListOf<HidCollection>()
            val globals = GlobalState()
            val stack = ArrayDeque<GlobalState>()
            val local = LocalState()
            val offsets = mutableMapOf<Pair<HidReportType, Int>, Int>()

            items.forEach { item ->
                when (item.type) {
                    HidItemType.GLOBAL -> when (item.tag) {
                        0 -> globals.usagePage = item.unsignedValue.toInt()
                        1 -> globals.logicalMinimum = item.signedValue
                        2 -> globals.logicalMaximum = if (globals.logicalMinimum < 0) item.signedValue else item.unsignedValue.toLong()
                        3 -> globals.physicalMinimum = item.signedValue
                        4 -> globals.physicalMaximum = if (globals.physicalMinimum < 0) item.signedValue else item.unsignedValue.toLong()
                        5 -> globals.unitExponent = item.signedValue
                        6 -> globals.unit = item.unsignedValue.toLong()
                        7 -> globals.reportSize = item.unsignedValue.toInt()
                        8 -> {
                            val id = item.unsignedValue.toInt()
                            require(id in 1..255) { "invalid HID Report ID $id at offset ${item.offset}" }
                            globals.reportId = id
                        }
                        9 -> globals.reportCount = item.unsignedValue.toInt()
                        10 -> stack.addLast(globals.copy())
                        11 -> {
                            require(stack.isNotEmpty()) { "HID global Pop without Push at offset ${item.offset}" }
                            globals.copyFrom(stack.removeLast())
                        }
                    }

                    HidItemType.LOCAL -> when (item.tag) {
                        0 -> local.usages += item.expandedUsage(globals.usagePage)
                        1 -> local.usageMinimum = item.expandedUsage(globals.usagePage)
                        2 -> local.usageMaximum = item.expandedUsage(globals.usagePage)
                        else -> local.unknownItems += item
                    }

                    HidItemType.MAIN -> {
                        when (item.tag) {
                            8, 9, 11 -> {
                                val type = when (item.tag) {
                                    8 -> HidReportType.INPUT
                                    9 -> HidReportType.OUTPUT
                                    else -> HidReportType.FEATURE
                                }
                                require(globals.reportSize >= 0) { "missing Report Size before ${type.name} at offset ${item.offset}" }
                                require(globals.reportCount >= 0) { "missing Report Count before ${type.name} at offset ${item.offset}" }
                                val key = type to globals.reportId
                                val bitOffset = offsets.getOrDefault(key, 0)
                                fields += HidReportField(
                                    type = type,
                                    reportId = globals.reportId,
                                    bitOffset = bitOffset,
                                    reportSize = globals.reportSize,
                                    reportCount = globals.reportCount,
                                    usagePage = globals.usagePage,
                                    usages = local.usages.toList(),
                                    usageMinimum = local.usageMinimum,
                                    usageMaximum = local.usageMaximum,
                                    logicalMinimum = globals.logicalMinimum,
                                    logicalMaximum = globals.logicalMaximum,
                                    flags = item.unsignedValue.toInt(),
                                    rawItem = item,
                                    unknownLocalItems = local.unknownItems.toList(),
                                )
                                offsets[key] = bitOffset + globals.reportSize * globals.reportCount
                            }
                            10 -> collections += HidCollection(
                                collectionType = item.unsignedValue.toInt(),
                                usagePage = local.primaryUsage()?.usagePage ?: globals.usagePage,
                                usage = local.primaryUsage()?.usage,
                                rawItem = item,
                            )
                        }
                        local.clear()
                    }

                    HidItemType.RESERVED, HidItemType.LONG -> Unit
                }
            }
            require(stack.isEmpty()) { "HID descriptor ended with ${stack.size} unclosed global Push item(s)" }
            return SmartPadHidDescriptor(raw.copyOf(), items, fields, collections)
        }

        private fun isVendorDefinedUsagePage(page: Int): Boolean = page in 0xFF00..0xFFFF

        private fun formatItemValue(item: HidItem): String = when {
            item.type == HidItemType.GLOBAL && item.tag == 0 -> usagePageName(item.unsignedValue.toInt())
            item.type == HidItemType.MAIN && item.tag in setOf(8, 9, 11) ->
                "${item.unsignedValue.hex()} (${formatMainFlags(item.unsignedValue.toInt())})"
            else -> item.unsignedValue.hex()
        }
    }
}

enum class HidItemType { MAIN, GLOBAL, LOCAL, RESERVED, LONG }

enum class HidReportType { INPUT, OUTPUT, FEATURE }

data class HidItem(
    val offset: Int,
    val prefix: Int,
    val type: HidItemType,
    val tag: Int,
    val data: ByteArray,
    val encoded: ByteArray,
) {
    val unsignedValue: ULong
        get() = data.foldIndexed(0uL) { index, value, byte ->
            value or ((byte.toULong() and 0xFFu) shl (index * 8))
        }

    val signedValue: Long
        get() {
            if (data.isEmpty()) return 0
            val bits = data.size * 8
            val unsigned = unsignedValue.toLong()
            val signBit = 1L shl (bits - 1)
            return if ((unsigned and signBit) == 0L) unsigned else unsigned or (-1L shl bits)
        }

    val label: String
        get() = when (type) {
            HidItemType.MAIN -> mapOf(8 to "Input", 9 to "Output", 10 to "Collection", 11 to "Feature", 12 to "End Collection")[tag]
            HidItemType.GLOBAL -> mapOf(
                0 to "Usage Page", 1 to "Logical Minimum", 2 to "Logical Maximum",
                3 to "Physical Minimum", 4 to "Physical Maximum", 5 to "Unit Exponent",
                6 to "Unit", 7 to "Report Size", 8 to "Report ID", 9 to "Report Count",
                10 to "Push", 11 to "Pop",
            )[tag]
            HidItemType.LOCAL -> mapOf(
                0 to "Usage", 1 to "Usage Minimum", 2 to "Usage Maximum", 3 to "Designator Index",
                4 to "Designator Minimum", 5 to "Designator Maximum", 7 to "String Index",
                8 to "String Minimum", 9 to "String Maximum", 10 to "Delimiter",
            )[tag]
            HidItemType.RESERVED -> "Reserved item"
            HidItemType.LONG -> "Long item tag ${tag.hex()}"
        } ?: "Unknown ${type.name.lowercase()} tag ${tag.hex()}"

    fun expandedUsage(defaultPage: Int?): HidUsage {
        val value = unsignedValue.toLong()
        return if (data.size > 2) {
            HidUsage(((value ushr 16) and 0xFFFF).toInt(), (value and 0xFFFF).toInt())
        } else {
            HidUsage(defaultPage, value.toInt())
        }
    }
}

data class HidUsage(val usagePage: Int?, val usage: Int)

data class HidCollection(
    val collectionType: Int,
    val usagePage: Int?,
    val usage: Int?,
    val rawItem: HidItem,
)

data class HidReportField(
    val type: HidReportType,
    val reportId: Int,
    val bitOffset: Int,
    val reportSize: Int,
    val reportCount: Int,
    val usagePage: Int?,
    val usages: List<HidUsage>,
    val usageMinimum: HidUsage?,
    val usageMaximum: HidUsage?,
    val logicalMinimum: Long,
    val logicalMaximum: Long,
    val flags: Int,
    val rawItem: HidItem,
    val unknownLocalItems: List<HidItem>,
) {
    val isConstant: Boolean get() = flags and 0x01 != 0
    val isVariable: Boolean get() = flags and 0x02 != 0
    val isRelative: Boolean get() = flags and 0x04 != 0

    fun usageDescription(): String = when {
        usages.isNotEmpty() -> usages.joinToString { it.formatted() }
        usageMinimum != null || usageMaximum != null ->
            "${usageMinimum?.formatted() ?: "?"}..${usageMaximum?.formatted() ?: "?"}"
        else -> "unspecified"
    }
}

private object HidItemParser {
    fun parse(raw: ByteArray): List<HidItem> {
        val result = mutableListOf<HidItem>()
        var offset = 0
        while (offset < raw.size) {
            val start = offset
            val prefix = raw[offset++].toInt() and 0xFF
            if (prefix == 0xFE) {
                require(offset + 2 <= raw.size) { "truncated HID long item header at offset $start" }
                val size = raw[offset++].toInt() and 0xFF
                val tag = raw[offset++].toInt() and 0xFF
                require(offset + size <= raw.size) { "truncated HID long item payload at offset $start" }
                val data = raw.copyOfRange(offset, offset + size)
                offset += size
                result += HidItem(start, prefix, HidItemType.LONG, tag, data, raw.copyOfRange(start, offset))
                continue
            }

            val size = when (prefix and 0x03) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 4
            }
            require(offset + size <= raw.size) { "truncated HID short item at offset $start" }
            val type = when ((prefix ushr 2) and 0x03) {
                0 -> HidItemType.MAIN
                1 -> HidItemType.GLOBAL
                2 -> HidItemType.LOCAL
                else -> HidItemType.RESERVED
            }
            val tag = (prefix ushr 4) and 0x0F
            val data = raw.copyOfRange(offset, offset + size)
            offset += size
            result += HidItem(start, prefix, type, tag, data, raw.copyOfRange(start, offset))
        }
        return result
    }
}

private data class GlobalState(
    var usagePage: Int? = null,
    var logicalMinimum: Long = 0,
    var logicalMaximum: Long = 0,
    var physicalMinimum: Long = 0,
    var physicalMaximum: Long = 0,
    var unitExponent: Long = 0,
    var unit: Long = 0,
    var reportSize: Int = -1,
    var reportId: Int = 0,
    var reportCount: Int = -1,
) {
    fun copyFrom(other: GlobalState) {
        usagePage = other.usagePage
        logicalMinimum = other.logicalMinimum
        logicalMaximum = other.logicalMaximum
        physicalMinimum = other.physicalMinimum
        physicalMaximum = other.physicalMaximum
        unitExponent = other.unitExponent
        unit = other.unit
        reportSize = other.reportSize
        reportId = other.reportId
        reportCount = other.reportCount
    }
}

private class LocalState {
    val usages = mutableListOf<HidUsage>()
    var usageMinimum: HidUsage? = null
    var usageMaximum: HidUsage? = null
    val unknownItems = mutableListOf<HidItem>()

    fun primaryUsage(): HidUsage? = usages.firstOrNull() ?: usageMinimum

    fun clear() {
        usages.clear()
        usageMinimum = null
        usageMaximum = null
        unknownItems.clear()
    }
}

internal fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

private fun Number.hex(): String = "0x%X".format(toLong())

private fun ULong.hex(): String = "0x${toString(16).uppercase()}"

private fun HidUsage.formatted(): String = "${usagePage?.hex() ?: "unset"}:${usage.hex()}"

private fun usagePageName(page: Int): String = when (page) {
    0x01 -> "0x01 (Generic Desktop)"
    0x07 -> "0x07 (Keyboard/Keypad)"
    0x08 -> "0x08 (LEDs)"
    in 0xFF00..0xFFFF -> "${page.hex()} (Vendor-defined)"
    else -> page.hex()
}

private fun formatMainFlags(flags: Int): String = listOf(
    if (flags and 0x01 == 0) "Data" else "Constant",
    if (flags and 0x02 == 0) "Array" else "Variable",
    if (flags and 0x04 == 0) "Absolute" else "Relative",
).joinToString(",")

package com.inspyresoftworks.ti84evo.smartpad

/** A decoded conventional HID boot-keyboard report with every source byte retained. */
data class SmartPadKeyState(
    val reportId: Int,
    val modifiers: Set<Int>,
    val keySlots: List<Int>,
    val reservedByte: Int,
    val raw: ByteArray,
) {
    val modifierByte: Int get() = modifiers.fold(0) { byte, usage ->
        byte or (1 shl (usage - 0xE0))
    }
    val usages: Set<Int> get() = (modifiers + keySlots.filter { it != 0 }).toSet()
    val rollover: Boolean get() = keySlots.any { it in 0x01..0x03 }
}

sealed interface SmartPadDecodeResult {
    val raw: ByteArray

    data class Valid(val state: SmartPadKeyState) : SmartPadDecodeResult {
        override val raw: ByteArray get() = state.raw
    }

    data class Malformed(override val raw: ByteArray, val reason: String) : SmartPadDecodeResult
}

enum class SmartPadKeyTransition { DOWN, UP }

data class SmartPadKeyEvent(
    val timestampNanos: Long,
    val reportId: Int,
    val transition: SmartPadKeyTransition,
    val usagePage: Int,
    val usage: Int,
    val hostKey: String,
    val calculatorKey: String?,
    val rawReport: ByteArray,
)

/** A calculator key is identified by the complete HID modifier/usage chord. */
data class SmartPadHidChord(val modifierByte: Int, val usage: Int) {
    init {
        require(modifierByte in 0..0xFF) { "modifier byte must fit in one byte" }
        require(usage in 0..0xFF) { "usage must fit in one byte" }
    }
}

/**
 * Stateful decoder for the boot-compatible eight-byte report advertised by
 * TI-84 Evo OS 7.1. Report-ID-prefixed variants are supported for fixtures and
 * future firmware without discarding unknown usages or malformed source bytes.
 */
class SmartPadReportDecoder(
    private val reportLengths: Map<Int, Int> = mapOf(0 to BOOT_REPORT_BYTES),
    private val calculatorKeyMap: Map<SmartPadHidChord, String> = SmartPadKeyMap.calculatorKeys,
) {
    private val previous = mutableMapOf<Int, SmartPadKeyState>()

    fun decode(report: ByteArray): SmartPadDecodeResult {
        if (report.isEmpty()) return malformed(report, "empty HID report")
        val usesIds = reportLengths.keys.any { it != 0 }
        val reportId = if (usesIds) report[0].toInt() and 0xFF else 0
        val expected = reportLengths[reportId]
            ?: return malformed(report, "unknown report ID $reportId")
        if (report.size != expected) {
            return malformed(report, "report ID $reportId has ${report.size} bytes; expected $expected")
        }
        val payloadOffset = if (usesIds) 1 else 0
        if (report.size - payloadOffset != BOOT_REPORT_BYTES) {
            return malformed(
                report,
                "report ID $reportId payload has ${report.size - payloadOffset} bytes; boot keyboard requires $BOOT_REPORT_BYTES",
            )
        }

        val modifierBits = report[payloadOffset].toInt() and 0xFF
        val modifiers = buildSet {
            repeat(8) { bit -> if (modifierBits and (1 shl bit) != 0) add(0xE0 + bit) }
        }
        val slots = (payloadOffset + 2 until payloadOffset + BOOT_REPORT_BYTES)
            .map { report[it].toInt() and 0xFF }
        return SmartPadDecodeResult.Valid(
            SmartPadKeyState(
                reportId = reportId,
                modifiers = modifiers,
                keySlots = slots,
                reservedByte = report[payloadOffset + 1].toInt() and 0xFF,
                raw = report.copyOf(),
            ),
        )
    }

    fun accept(report: ByteArray, timestampNanos: Long = System.nanoTime()): List<SmartPadKeyEvent> {
        val result = decode(report)
        if (result !is SmartPadDecodeResult.Valid) return emptyList()
        val current = result.state
        val before = previous[current.reportId]
        val oldModifiers = before?.modifiers.orEmpty()
        val newModifiers = current.modifiers
        val oldChords = before?.keyChords.orEmpty()
        val newChords = current.keyChords
        previous[current.reportId] = current

        val releases = (oldChords - newChords).sortedBy(SmartPadHidChord::usage).map { chord ->
            event(timestampNanos, current, SmartPadKeyTransition.UP, chord.usage, chord)
        } + (oldModifiers - newModifiers).sorted().map { usage ->
            event(timestampNanos, current, SmartPadKeyTransition.UP, usage)
        }
        val presses = (newChords - oldChords).sortedBy(SmartPadHidChord::usage).map { chord ->
            event(timestampNanos, current, SmartPadKeyTransition.DOWN, chord.usage, chord)
        } + (newModifiers - oldModifiers).sorted().map { usage ->
            event(timestampNanos, current, SmartPadKeyTransition.DOWN, usage)
        }
        return releases + presses
    }

    fun reset() = previous.clear()

    private fun event(
        timestampNanos: Long,
        state: SmartPadKeyState,
        transition: SmartPadKeyTransition,
        usage: Int,
        chord: SmartPadHidChord? = null,
    ) = SmartPadKeyEvent(
        timestampNanos = timestampNanos,
        reportId = state.reportId,
        transition = transition,
        usagePage = KEYBOARD_USAGE_PAGE,
        usage = usage,
        hostKey = SmartPadKeyMap.hostKeyName(usage),
        calculatorKey = chord?.let(calculatorKeyMap::get),
        rawReport = state.raw.copyOf(),
    )

    private fun malformed(report: ByteArray, reason: String) =
        SmartPadDecodeResult.Malformed(report.copyOf(), reason)

    companion object {
        const val BOOT_REPORT_BYTES = 8
        const val KEYBOARD_USAGE_PAGE = 0x07
    }
}

private val SmartPadKeyState.keyChords: Set<SmartPadHidChord>
    get() = keySlots.asSequence()
        .filter { it != 0 }
        .map { SmartPadHidChord(modifierByte, it) }
        .toSet()

/** Standard HID Keyboard/Keypad names. No calculator mapping is inferred here. */
object SmartPadKeyMap {
    /**
     * Complete physical-key sweep captured from TI-84 Evo OS 7.1.0.4421.
     * The FRAC key is the unlabelled fraction-template key; TOGGLE is the
     * fraction/decimal toggle key immediately to the right of digit 3.
     */
    val calculatorKeys: Map<SmartPadHidChord, String> = mapOf(
        chord(0x00, 0x6F) to "Y=",
        chord(0x05, 0x6C) to "WINDOW",
        chord(0x05, 0x6B) to "ZOOM",
        chord(0x03, 0x6F) to "TRACE",
        chord(0x06, 0x6C) to "GRAPH",
        chord(0x05, 0x6E) to "2nd",
        chord(0x05, 0x6F) to "MODE",
        chord(0x00, 0x4C) to "DEL",
        chord(0x00, 0x52) to "UP",
        chord(0x03, 0x6D) to "ALPHA",
        chord(0x04, 0x6B) to "X,T,θ,n",
        chord(0x01, 0x6C) to "STAT",
        chord(0x00, 0x50) to "LEFT",
        chord(0x00, 0x4F) to "RIGHT",
        chord(0x00, 0x51) to "DOWN",
        chord(0x03, 0x6C) to "MATH",
        chord(0x01, 0x6F) to "FRAC",
        chord(0x01, 0x6B) to "PRGM",
        chord(0x02, 0x6E) to "VARS",
        chord(0x02, 0x6C) to "CLEAR",
        chord(0x02, 0x6B) to "x⁻¹",
        chord(0x01, 0x6E) to "SIN",
        chord(0x02, 0x6F) to "COS",
        chord(0x02, 0x6D) to "TAN",
        chord(0x00, 0x54) to "÷",
        chord(0x04, 0x6F) to "x²",
        chord(0x01, 0x6D) to ",",
        chord(0x06, 0x6E) to "(",
        chord(0x06, 0x6D) to ")",
        chord(0x00, 0x55) to "×",
        chord(0x02, 0x69) to "LOG",
        chord(0x04, 0x6E) to "7",
        chord(0x05, 0x6A) to "8",
        chord(0x03, 0x69) to "9",
        chord(0x00, 0x56) to "−",
        chord(0x04, 0x6D) to "LN",
        chord(0x05, 0x69) to "4",
        chord(0x06, 0x6B) to "5",
        chord(0x00, 0x6D) to "6",
        chord(0x00, 0x57) to "+",
        chord(0x04, 0x6C) to "STO→",
        chord(0x02, 0x6A) to "1",
        chord(0x00, 0x6B) to "2",
        chord(0x00, 0x6C) to "3",
        chord(0x03, 0x6B) to "TOGGLE",
        chord(0x00, 0x29) to "ON",
        chord(0x03, 0x6A) to "0",
        chord(0x00, 0x6E) to ".",
        chord(0x06, 0x6F) to "(−)",
        chord(0x00, 0x28) to "ENTER",
    )

    fun calculatorKeyName(modifierByte: Int, usage: Int): String? =
        calculatorKeys[SmartPadHidChord(modifierByte, usage)]

    fun hostKeyName(usage: Int): String = when (usage) {
        0x00 -> "None"
        0x01 -> "ErrorRollOver"
        0x02 -> "POSTFail"
        0x03 -> "ErrorUndefined"
        in 0x04..0x1D -> ('A'.code + usage - 0x04).toChar().toString()
        in 0x1E..0x26 -> (usage - 0x1D).toString()
        0x27 -> "0"
        0x28 -> "Enter"
        0x29 -> "Escape"
        0x2A -> "Backspace"
        0x2B -> "Tab"
        0x2C -> "Space"
        0x2D -> "Minus"
        0x2E -> "Equal"
        0x2F -> "LeftBracket"
        0x30 -> "RightBracket"
        0x31 -> "Backslash"
        0x33 -> "Semicolon"
        0x34 -> "Quote"
        0x35 -> "Grave"
        0x36 -> "Comma"
        0x37 -> "Period"
        0x38 -> "Slash"
        0x39 -> "CapsLock"
        in 0x3A..0x45 -> "F${usage - 0x39}"
        0x46 -> "PrintScreen"
        0x47 -> "ScrollLock"
        0x48 -> "Pause"
        0x49 -> "Insert"
        0x4A -> "Home"
        0x4B -> "PageUp"
        0x4C -> "Delete"
        0x4D -> "End"
        0x4E -> "PageDown"
        0x4F -> "RightArrow"
        0x50 -> "LeftArrow"
        0x51 -> "DownArrow"
        0x52 -> "UpArrow"
        0x53 -> "NumLock"
        0x54 -> "KeypadSlash"
        0x55 -> "KeypadAsterisk"
        0x56 -> "KeypadMinus"
        0x57 -> "KeypadPlus"
        0x58 -> "KeypadEnter"
        0x59 -> "Keypad1"
        0x5A -> "Keypad2"
        0x5B -> "Keypad3"
        0x5C -> "Keypad4"
        0x5D -> "Keypad5"
        0x5E -> "Keypad6"
        0x5F -> "Keypad7"
        0x60 -> "Keypad8"
        0x61 -> "Keypad9"
        0x62 -> "Keypad0"
        0x63 -> "KeypadDecimal"
        0x64 -> "NonUSBackslash"
        0x65 -> "Application"
        0x66 -> "Power"
        0x67 -> "KeypadEqual"
        in 0x68..0x73 -> "F${usage - 0x5B}"
        in 0xE0..0xE7 -> listOf(
            "LeftControl", "LeftShift", "LeftAlt", "LeftGUI",
            "RightControl", "RightShift", "RightAlt", "RightGUI",
        )[usage - 0xE0]
        else -> "Unknown(0x%02X)".format(usage)
    }

    private fun chord(modifierByte: Int, usage: Int) = SmartPadHidChord(modifierByte, usage)
}

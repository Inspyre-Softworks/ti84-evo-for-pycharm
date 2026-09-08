package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport

/** Restores the calculator's default L1-L6 List Editor column registration. */
class EvoListEditor(private val transport: EvoTransport) {
    fun restoreDefaultColumns(): Int = sendScancodes(RESET_TO_DEFAULT_COLUMNS)

    fun sendScancodes(scancodes: List<Int>): Int = EvoPythonTransfer(transport).uploadPayloads(
        scancodes.map { scancode -> SCANCODE_ENDPOINT to scancodePayload(scancode) },
        delayBetweenMillis = KEY_DELAY_MILLIS,
    )

    companion object {
        internal const val SCANCODE_ENDPOINT = "hh01/sys/scancode"

        // 2nd, Mode/Quit, Stat, 5:SetUpEditor, Enter.
        internal val RESET_TO_DEFAULT_COLUMNS = listOf(0x36, 0x37, 0x20, 0x1B, 0x09)

        internal fun scancodePayload(scancode: Int): ByteArray {
            require(scancode in 0..0xFF) { "scancode must fit in one byte" }
            return if (scancode < 24) {
                byteArrayOf(0x9F.toByte(), scancode.toByte(), 0xFF.toByte())
            } else {
                byteArrayOf(0x9F.toByte(), 0x18, scancode.toByte(), 0xFF.toByte())
            }
        }

        private const val KEY_DELAY_MILLIS = 80L
    }
}

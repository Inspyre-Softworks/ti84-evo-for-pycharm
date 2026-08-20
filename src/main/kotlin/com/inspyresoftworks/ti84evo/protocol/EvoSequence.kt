package com.inspyresoftworks.ti84evo.protocol

/**
 * Printable sequence counter used by one Evo transaction.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
class EvoSequence(start: Int = EvoFrameCodec.PRINTABLE_BASE) {
    private var value = start

    fun take(): Int {
        val current = value
        value += 1
        if (value > EvoFrameCodec.PRINTABLE_MAX) {
            value = EvoFrameCodec.PRINTABLE_BASE
        }
        return current
    }
}

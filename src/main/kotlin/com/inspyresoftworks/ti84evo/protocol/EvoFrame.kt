package com.inspyresoftworks.ti84evo.protocol

/**
 * Logical TI-84 Evo link frame.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
data class EvoFrame(
    val sequence: Int,
    val command: Int,
    val payload: ByteArray = byteArrayOf(),
    val auxiliary: Int? = null,
    val extended: Boolean = false,
    val wirePayload: ByteArray? = null,
) {
    val commandText: String
        get() = if (command in 0x20..0x7E) command.toChar().toString() else "\\x%02x".format(command)
}

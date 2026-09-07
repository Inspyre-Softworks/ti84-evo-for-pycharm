package com.inspyresoftworks.ti84evo.transport

/**
 * Byte transport used by the Evo link protocol.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
interface EvoTransport : AutoCloseable {
    val description: String
    fun open()
    fun write(data: ByteArray)
    fun readPacketBytes(): ByteArray
}

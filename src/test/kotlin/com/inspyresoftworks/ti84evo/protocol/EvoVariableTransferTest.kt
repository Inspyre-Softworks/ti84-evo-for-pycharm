package com.inspyresoftworks.ti84evo.protocol

import com.inspyresoftworks.ti84evo.transport.EvoTransport
import kotlin.test.Test
import kotlin.test.assertEquals

class EvoVariableTransferTest {
    @Test
    fun `temporary list name avoids calculator variables already in use`() {
        val transfer = EvoVariableTransfer(object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readPacketBytes(): ByteArray = error("not used")
        })

        assertEquals("Z0002", transfer.temporaryListName(listOf("z0000", "Z0001", "L1")))
    }

    @Test
    fun `archive transfer targets calculator Archive with overwrite policy`() {
        val transfer = EvoVariableTransfer(object : EvoTransport {
            override val description = "test"
            override fun open() = Unit
            override fun close() = Unit
            override fun write(data: ByteArray) = Unit
            override fun readPacketBytes(): ByteArray = error("not used")
        })

        assertEquals("hh01/xfr/var?memtarget=1&policy=1", transfer.archiveTransferUrl())
    }
}

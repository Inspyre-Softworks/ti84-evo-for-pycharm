package com.inspyresoftworks.ti84evo.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EvoFrameCodecTest {
    @Test
    fun `short D frame round trips escaped bytes`() {
        val frame = EvoFrame(
            sequence = 0x20,
            command = EvoFrameCodec.CMD_D,
            payload = byteArrayOf(0x01, 0x0D, 0x23, 0x55),
        )

        val decoded = EvoFrameCodec.decode(EvoFrameCodec.encode(frame))
        assertEquals(frame.sequence, decoded.sequence)
        assertEquals(frame.command, decoded.command)
        assertContentEquals(frame.payload, decoded.payload)
    }

    @Test
    fun `length announcement round trips`() {
        val value = 153635
        assertEquals(value, EvoFrameCodec.parseLengthAnnouncement(EvoFrameCodec.buildLengthAnnouncement(value)))
    }
}

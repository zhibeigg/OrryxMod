package io.github.orryxmod.core.network

import com.google.common.io.ByteStreams
import io.github.orryxmod.TestHelper
import io.github.orryxmod.feature.bloom.BloomConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PacketCodecTest {

    @BeforeEach
    fun setup() { TestHelper.mockLogger() }

    @AfterEach
    fun teardown() { TestHelper.cleanup() }

    private fun encodeAimResponse(packet: OrryxPacket.AimResponse): ByteArray {
        val out = ByteStreams.newDataOutput()
        PacketCodec.encode(packet, out)
        return out.toByteArray()
    }

    @Test
    fun `AimResponse encode and decode symmetry`() {
        val original = OrryxPacket.AimResponse("fireball", 1.0, 2.0, 3.0, 45f, -30f)
        val bytes = encodeAimResponse(original)
        val input = ByteStreams.newDataInput(bytes)
        val id = input.readInt()
        assertEquals(4, id)
        assertEquals("fireball", input.readUTF())
        assertEquals(1.0, input.readDouble())
        assertEquals(2.0, input.readDouble())
        assertEquals(3.0, input.readDouble())
        assertEquals(45f, input.readFloat())
        assertEquals(-30f, input.readFloat())
    }

    @Test
    fun `decode AimRequest`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(1)
        out.writeUTF("skill1")
        out.writeUTF("module1")
        out.writeDouble(1.5)
        out.writeDouble(10.0)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertTrue(packet is OrryxPacket.AimRequest)
        val aim = packet as OrryxPacket.AimRequest
        assertEquals("skill1", aim.skill)
        assertEquals("module1", aim.module)
        assertEquals(1.5, aim.scale)
        assertEquals(10.0, aim.maxDistance)
    }

    @Test
    fun `decode AimConfirm`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(2)
        out.writeBoolean(true)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertTrue(packet is OrryxPacket.AimConfirm)
        assertTrue((packet as OrryxPacket.AimConfirm).confirmed)
    }

    @Test
    fun `decode MouseControl`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(7)
        out.writeBoolean(false)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertTrue(packet is OrryxPacket.MouseControl)
        assertFalse((packet as OrryxPacket.MouseControl).show)
    }

    @Test
    fun `decode NavigationStart with range coercion`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(10)
        out.writeInt(100)
        out.writeInt(64)
        out.writeInt(-50)
        out.writeInt(999) // should be coerced to 100
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.NavigationStart
        assertEquals(100, packet.range)
    }

    @Test
    fun `decode NavigationStop`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(11)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertSame(OrryxPacket.NavigationStop, packet)
    }

    @Test
    fun `decode GhostEffect with coercion`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(3)
        val uuid = UUID.randomUUID()
        out.writeUTF(uuid.toString())
        out.writeLong(999999) // coerced to 60000
        out.writeInt(100)     // coerced to 50
        out.writeInt(-5)      // coerced to 0
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.GhostEffect
        assertEquals(uuid, packet.uuid)
        assertEquals(60000L, packet.timeout)
        assertEquals(50, packet.density)
        assertEquals(0, packet.gap)
    }

    @Test
    fun `decode FlickerEffect with coercion`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(5)
        val uuid = UUID.randomUUID()
        out.writeUTF(uuid.toString())
        out.writeLong(30000)
        out.writeFloat(0.5f)
        out.writeLong(5000)
        out.writeFloat(2.0f)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.FlickerEffect
        assertEquals(uuid, packet.uuid)
        assertEquals(30000L, packet.timeout)
        assertEquals(0.5f, packet.alpha)
        assertEquals(5000L, packet.duration)
        assertEquals(2.0f, packet.scale)
    }

    @Test
    fun `decode CircleShockwave with radius coercion`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(13)
        out.writeDouble(1.0)
        out.writeDouble(2.0)
        out.writeDouble(3.0)
        out.writeDouble(0.1) // coerced to 0.5
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.CircleShockwave
        assertEquals(0.5, packet.radius)
    }

    @Test
    fun `decode SectorShockwave with angle coercion`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(14)
        out.writeDouble(0.0)
        out.writeDouble(0.0)
        out.writeDouble(0.0)
        out.writeDouble(5.0)
        out.writeDouble(400.0) // coerced to 360
        out.writeDouble(90.0)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.SectorShockwave
        assertEquals(360.0, packet.angle)
    }

    @Test
    fun `decode SquareShockwave`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(12)
        out.writeDouble(1.0)
        out.writeDouble(2.0)
        out.writeDouble(3.0)
        out.writeDouble(5.0)
        out.writeDouble(3.0)
        out.writeDouble(45.0)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.SquareShockwave
        assertEquals(5.0, packet.length)
        assertEquals(3.0, packet.width)
        assertEquals(45.0, packet.yaw)
    }

    @Test
    fun `readSafeUTF rejects overly long string`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(1) // AimRequest
        out.writeUTF("x".repeat(1025)) // exceeds MAX_STRING_LENGTH
        out.writeUTF("module")
        out.writeDouble(1.0)
        out.writeDouble(1.0)
        // decode should catch the exception and return null
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertNull(packet)
    }

    @Test
    fun `readUUID with invalid format returns null`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(3) // GhostEffect
        out.writeUTF("not-a-uuid")
        out.writeLong(1000)
        out.writeInt(5)
        out.writeInt(2)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertNull(packet)
    }

    @Test
    fun `unknown packetId returns null`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(999)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray()))
        assertNull(packet)
    }

    @Test
    fun `BloomConfig serialization and deserialization`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(16) // BloomConfigUpdate
        out.writeUTF("bloom1")
        // BloomConfig fields
        out.writeUTF("Zombie")
        out.writeInt(255)
        out.writeInt(128)
        out.writeInt(0)
        out.writeInt(200)
        out.writeFloat(1.5f)
        out.writeFloat(32f)
        out.writeInt(5)

        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.BloomConfigUpdate
        assertEquals("bloom1", packet.id)
        assertEquals("Zombie", packet.config.name)
        assertArrayEquals(intArrayOf(255, 128, 0, 200), packet.config.color)
        assertEquals(1.5f, packet.config.strength)
        assertEquals(32f, packet.config.radius)
        assertEquals(5, packet.config.priority)
    }

    @Test
    fun `BloomConfigSync decodes multiple configs`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(15)
        out.writeInt(2) // count
        // config 1
        out.writeUTF("id1")
        out.writeUTF("Skeleton")
        out.writeInt(100); out.writeInt(100); out.writeInt(100); out.writeInt(255)
        out.writeFloat(2f); out.writeFloat(16f); out.writeInt(1)
        // config 2
        out.writeUTF("id2")
        out.writeUTF("Creeper")
        out.writeInt(0); out.writeInt(255); out.writeInt(0); out.writeInt(128)
        out.writeFloat(3f); out.writeFloat(64f); out.writeInt(2)

        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.BloomConfigSync
        assertEquals(2, packet.configs.size)
        assertEquals("Skeleton", packet.configs["id1"]!!.name)
        assertEquals("Creeper", packet.configs["id2"]!!.name)
    }

    @Test
    fun `BloomConfigRemove decode`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(17)
        out.writeUTF("removeId")
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.BloomConfigRemove
        assertEquals("removeId", packet.id)
    }

    @Test
    fun `decode EntityShowAdd`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(8)
        val uuid = UUID.randomUUID()
        out.writeUTF(uuid.toString())
        out.writeUTF("group1")
        out.writeDouble(1.0); out.writeDouble(2.0); out.writeDouble(3.0)
        out.writeLong(5000)
        out.writeFloat(10f); out.writeFloat(20f); out.writeFloat(30f)
        out.writeFloat(1.5f)
        out.writeFloat(0.8f)
        out.writeBoolean(true)
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.EntityShowAdd
        assertEquals(uuid, packet.uuid)
        assertEquals("group1", packet.group)
        assertTrue(packet.fadeOut)
    }

    @Test
    fun `decode EntityShowRemove`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(9)
        val uuid = UUID.randomUUID()
        out.writeUTF(uuid.toString())
        out.writeUTF("group2")
        val packet = PacketCodec.decode(ByteStreams.newDataInput(out.toByteArray())) as OrryxPacket.EntityShowRemove
        assertEquals(uuid, packet.uuid)
        assertEquals("group2", packet.group)
    }
}

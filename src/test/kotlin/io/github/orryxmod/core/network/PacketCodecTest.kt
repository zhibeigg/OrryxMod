package io.github.orryxmod.core.network

import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import io.github.orryxmod.TestHelper
import io.github.orryxmod.feature.bloom.BloomConfig
import io.github.orryxmod.feature.collider.ColliderShape
import io.github.orryxmod.feature.collider.ColliderType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.sqrt

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

    private fun decodePacket(writer: ByteArrayDataOutput.() -> Unit): OrryxPacket? {
        val out = ByteStreams.newDataOutput()
        out.writer()
        return PacketCodec.decode(out.toByteArray())
    }

    private fun decodeColliderUpdate(
        type: ColliderType,
        shapeWriter: ByteArrayDataOutput.() -> Unit
    ): OrryxPacket.ColliderUpdate? {
        return decodePacket {
            writeInt(19)
            writeUTF("collider")
            writeInt(type.wireId)
            shapeWriter()
        } as? OrryxPacket.ColliderUpdate
    }

    private fun ByteArrayDataOutput.writeSphereShape(
        cx: Double = 1.0,
        cy: Double = 2.0,
        cz: Double = 3.0,
        radius: Double = 4.0
    ) {
        writeDouble(cx)
        writeDouble(cy)
        writeDouble(cz)
        writeDouble(radius)
    }

    private fun ByteArrayDataOutput.writeCompositeChildHeader(id: String, type: ColliderType) {
        writeUTF(id)
        writeInt(type.wireId)
        writeInt(255)
        writeInt(255)
        writeInt(255)
        writeInt(255)
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
    fun `AimResponse yaw wraps beyond full rotations and pitch remains clamped`() {
        val cases = listOf(
            Triple(450f, 120f, 90f to 90f),
            Triple(-450f, -120f, -90f to -90f)
        )

        cases.forEach { (yaw, pitch, expected) ->
            val input = ByteStreams.newDataInput(
                encodeAimResponse(OrryxPacket.AimResponse("skill", 0.0, 0.0, 0.0, yaw, pitch))
            )
            input.readInt()
            input.readUTF()
            repeat(3) { input.readDouble() }

            assertEquals(expected.first, input.readFloat())
            assertEquals(expected.second, input.readFloat())
        }
    }

    @Test
    fun `decode AimRequest`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(1)
        out.writeUTF("skill1")
        out.writeUTF("module1")
        out.writeDouble(1.5)
        out.writeDouble(10.0)
        val packet = PacketCodec.decode(out.toByteArray())
        assertTrue(packet is OrryxPacket.AimRequest)
        val aim = packet as OrryxPacket.AimRequest
        assertEquals("skill1", aim.skill)
        assertEquals("module1", aim.module)
        assertEquals(1.5, aim.scale)
        assertEquals(10.0, aim.maxDistance)
        assertEquals("texture", aim.indicatorType)
        assertEquals(0xFFFFFF, aim.indicatorColor)
        assertEquals(0.8f, aim.indicatorAlpha)
        assertEquals(1.0, aim.indicatorRadius)
        assertEquals(1.0f, aim.modelScale)
    }

    @Test
    fun `decode PressAimRequest`() {
        val packet = decodePacket {
            writeInt(6)
            writeUTF("charged-skill")
            writeUTF("default")
            writeDouble(0.0)
            writeDouble(5.0)
            writeDouble(20.0)
            writeLong(100L)
        } as OrryxPacket.PressAimRequest

        assertEquals("charged-skill", packet.skill)
        assertEquals("default", packet.picture)
        assertEquals(0.0, packet.minScale)
        assertEquals(5.0, packet.maxScale)
        assertEquals(20.0, packet.maxDistance)
        assertEquals(100L, packet.maxTicks)
    }

    @Test
    fun `invalid PressAimRequest ranges are rejected`() {
        val reversed = decodePacket {
            writeInt(6)
            writeUTF("skill")
            writeUTF("default")
            writeDouble(5.0)
            writeDouble(1.0)
            writeDouble(20.0)
            writeLong(100L)
        }
        val zeroTicks = decodePacket {
            writeInt(6)
            writeUTF("skill")
            writeUTF("default")
            writeDouble(1.0)
            writeDouble(5.0)
            writeDouble(20.0)
            writeLong(0L)
        }

        assertNull(reversed)
        assertNull(zeroTicks)
    }

    @Test
    fun `decode AimConfirm`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(2)
        out.writeBoolean(true)
        val packet = PacketCodec.decode(out.toByteArray())
        assertTrue(packet is OrryxPacket.AimConfirm)
        assertTrue((packet as OrryxPacket.AimConfirm).confirmed)
    }

    @Test
    fun `decode MouseControl`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(7)
        out.writeBoolean(false)
        val packet = PacketCodec.decode(out.toByteArray())
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.NavigationStart
        assertEquals(100, packet.range)
    }

    @Test
    fun `decode NavigationStop`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(11)
        val packet = PacketCodec.decode(out.toByteArray())
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.GhostEffect
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.FlickerEffect
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.CircleShockwave
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.SectorShockwave
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.SquareShockwave
        assertEquals(5.0, packet.length)
        assertEquals(3.0, packet.width)
        assertEquals(45.0, packet.yaw)
    }

    @Test
    fun `decode shockwave yaw wraps beyond full rotations`() {
        val squarePositive = decodePacket {
            writeInt(12)
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(5.0); writeDouble(3.0)
            writeDouble(450.0)
        } as OrryxPacket.SquareShockwave
        val squareNegative = decodePacket {
            writeInt(12)
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(5.0); writeDouble(3.0)
            writeDouble(-450.0)
        } as OrryxPacket.SquareShockwave
        val sectorPositive = decodePacket {
            writeInt(14)
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(5.0); writeDouble(60.0)
            writeDouble(810.0)
        } as OrryxPacket.SectorShockwave
        val sectorNegative = decodePacket {
            writeInt(14)
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(5.0); writeDouble(60.0)
            writeDouble(-810.0)
        } as OrryxPacket.SectorShockwave

        assertEquals(90.0, squarePositive.yaw)
        assertEquals(-90.0, squareNegative.yaw)
        assertEquals(90.0, sectorPositive.yaw)
        assertEquals(-90.0, sectorNegative.yaw)
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
        val packet = PacketCodec.decode(out.toByteArray())
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
        val packet = PacketCodec.decode(out.toByteArray())
        assertNull(packet)
    }

    @Test
    fun `unknown packetId returns null`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(999)
        val packet = PacketCodec.decode(out.toByteArray())
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

        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.BloomConfigUpdate
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

        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.BloomConfigSync
        assertEquals(2, packet.configs.size)
        assertEquals("Skeleton", packet.configs["id1"]!!.name)
        assertEquals("Creeper", packet.configs["id2"]!!.name)
    }

    @Test
    fun `BloomConfigRemove decode`() {
        val out = ByteStreams.newDataOutput()
        out.writeInt(17)
        out.writeUTF("removeId")
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.BloomConfigRemove
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.EntityShowAdd
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
        val packet = PacketCodec.decode(out.toByteArray()) as OrryxPacket.EntityShowRemove
        assertEquals(uuid, packet.uuid)
        assertEquals("group2", packet.group)
    }

    @Test
    fun `decode new AimRequest fields with bounds`() {
        val packet = decodePacket {
            writeInt(1)
            writeUTF("meteor")
            writeUTF("area")
            writeDouble(-5.0)
            writeDouble(10_000.0)
            writeUTF("circle")
            writeInt(0x123456)
            writeFloat(2.0f)
            writeDouble(0.0)
            writeFloat(50.0f)
        } as OrryxPacket.AimRequest

        assertEquals(0.01, packet.scale)
        assertEquals(512.0, packet.maxDistance)
        assertEquals("circle", packet.indicatorType)
        assertEquals(0x123456, packet.indicatorColor)
        assertEquals(1.0f, packet.indicatorAlpha)
        assertEquals(0.1, packet.indicatorRadius)
        assertEquals(10.0f, packet.modelScale)
    }

    @Test
    fun `Aim optional validation errors are not treated as old packets`() {
        val packet = decodePacket {
            writeInt(1)
            writeUTF("skill")
            writeUTF("point")
            writeDouble(1.0)
            writeDouble(20.0)
            writeUTF("x".repeat(1025))
        }

        assertNull(packet)
    }

    @Test
    fun `truncated Aim extension is rejected instead of treated as legacy`() {
        val packet = decodePacket {
            writeInt(1)
            writeUTF("skill")
            writeUTF("point")
            writeDouble(1.0)
            writeDouble(20.0)
            writeShort(4)
            writeByte('c'.code)
        }

        assertNull(packet)
    }

    @Test
    fun `unexpected trailing bytes are rejected`() {
        val packet = decodePacket {
            writeInt(2)
            writeBoolean(true)
            writeByte(0)
        }

        assertNull(packet)
    }

    @Test
    fun `decode rejects non finite values across packet families`() {
        val uuid = UUID.randomUUID().toString()
        val invalidPackets = listOf(
            decodePacket {
                writeInt(1)
                writeUTF("skill")
                writeUTF("point")
                writeDouble(Double.NaN)
                writeDouble(10.0)
            },
            decodePacket {
                writeInt(5)
                writeUTF(uuid)
                writeLong(1000)
                writeFloat(Float.POSITIVE_INFINITY)
            },
            decodePacket {
                writeInt(8)
                writeUTF(uuid)
                writeUTF("group")
                writeDouble(Double.NEGATIVE_INFINITY)
            },
            decodePacket {
                writeInt(13)
                writeDouble(0.0)
                writeDouble(0.0)
                writeDouble(0.0)
                writeDouble(Double.NaN)
            },
            decodePacket {
                writeInt(12)
                writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
                writeDouble(5.0); writeDouble(3.0)
                writeDouble(Double.POSITIVE_INFINITY)
            },
            decodePacket {
                writeInt(14)
                writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
                writeDouble(5.0); writeDouble(60.0)
                writeDouble(Double.NaN)
            },
            decodePacket {
                writeInt(16)
                writeUTF("bloom")
                writeUTF("name")
                repeat(4) { writeInt(255) }
                writeFloat(Float.NaN)
            },
            decodePacket {
                writeInt(19)
                writeUTF("sphere")
                writeInt(ColliderType.SPHERE.wireId)
                writeSphereShape(cx = Double.POSITIVE_INFINITY)
            }
        )

        invalidPackets.forEachIndexed { index, packet ->
            assertNull(packet, "Non-finite packet at index $index should be rejected")
        }
    }

    @Test
    fun `encode rejects non AimResponse packets`() {
        val out = ByteStreams.newDataOutput()
        assertThrows(IllegalArgumentException::class.java) {
            PacketCodec.encode(OrryxPacket.AimConfirm(true), out)
        }
        assertEquals(0, out.toByteArray().size)
    }

    @Test
    fun `encode rejects non finite AimResponse values`() {
        val invalidPackets = listOf(
            OrryxPacket.AimResponse("skill", Double.NaN, 0.0, 0.0, 0f, 0f),
            OrryxPacket.AimResponse("skill", 0.0, 0.0, 0.0, Float.POSITIVE_INFINITY, 0f),
            OrryxPacket.AimResponse("skill", 0.0, 0.0, 0.0, 0f, Float.NaN)
        )

        invalidPackets.forEach { packet ->
            val out = ByteStreams.newDataOutput()
            assertThrows(IllegalArgumentException::class.java) {
                PacketCodec.encode(packet, out)
            }
            assertEquals(0, out.toByteArray().size)
        }
    }

    @Test
    fun `collection limits reject instead of truncating`() {
        val oversizedBloom = decodePacket {
            writeInt(15)
            writeInt(1001)
        }
        val oversizedComposite = decodePacket {
            writeInt(19)
            writeUTF("root")
            writeInt(ColliderType.COMPOSITE.wireId)
            writeInt(51)
        }

        assertNull(oversizedBloom)
        assertNull(oversizedComposite)
    }

    @Test
    fun `Composite total node budget is enforced`() {
        val packet = decodePacket {
            writeInt(19)
            writeUTF("root")
            writeInt(ColliderType.COMPOSITE.wireId)
            writeInt(4)

            repeat(4) { compositeIndex ->
                writeCompositeChildHeader("composite-$compositeIndex", ColliderType.COMPOSITE)
                writeInt(50)
                repeat(50) { sphereIndex ->
                    writeCompositeChildHeader(
                        "sphere-$compositeIndex-$sphereIndex",
                        ColliderType.SPHERE
                    )
                    writeSphereShape()
                }
            }
        }

        assertNull(packet)
    }

    @Test
    fun `Collider wire IDs decode all shape types`() {
        val sphere = decodeColliderUpdate(ColliderType.SPHERE) {
            writeSphereShape(radius = 500.0)
        }
        val aabb = decodeColliderUpdate(ColliderType.AABB) {
            writeDouble(1.0); writeDouble(2.0); writeDouble(3.0)
            writeDouble(4.0); writeDouble(5.0); writeDouble(6.0)
        }
        val obb = decodeColliderUpdate(ColliderType.OBB) {
            writeDouble(1.0); writeDouble(2.0); writeDouble(3.0)
            writeDouble(4.0); writeDouble(5.0); writeDouble(6.0)
            writeFloat(0f); writeFloat(0f); writeFloat(0f); writeFloat(2f)
        }
        val capsule = decodeColliderUpdate(ColliderType.CAPSULE) {
            writeDouble(1.0); writeDouble(2.0); writeDouble(3.0)
            writeDouble(4.0); writeDouble(5.0)
        }
        val ray = decodeColliderUpdate(ColliderType.RAY) {
            writeDouble(1.0); writeDouble(2.0); writeDouble(3.0)
            writeDouble(3.0); writeDouble(4.0); writeDouble(0.0)
            writeDouble(10.0)
        }
        val composite = decodeColliderUpdate(ColliderType.COMPOSITE) {
            writeInt(1)
            writeCompositeChildHeader("child", ColliderType.SPHERE)
            writeSphereShape()
        }

        assertTrue(sphere?.shapeData is ColliderShape.Sphere)
        assertEquals(100.0, (sphere?.shapeData as ColliderShape.Sphere).radius)
        assertTrue(aabb?.shapeData is ColliderShape.AABB)
        assertTrue(obb?.shapeData is ColliderShape.OBB)
        assertTrue(capsule?.shapeData is ColliderShape.Capsule)
        assertTrue(ray?.shapeData is ColliderShape.Ray)
        assertTrue(composite?.shapeData is ColliderShape.Composite)
    }

    @Test
    fun `Ray direction and OBB quaternion are normalized`() {
        val rayPacket = decodeColliderUpdate(ColliderType.RAY) {
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(3.0); writeDouble(4.0); writeDouble(0.0)
            writeDouble(10.0)
        }
        val obbPacket = decodeColliderUpdate(ColliderType.OBB) {
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(1.0); writeDouble(1.0); writeDouble(1.0)
            writeFloat(1f); writeFloat(2f); writeFloat(3f); writeFloat(4f)
        }

        val ray = rayPacket?.shapeData as ColliderShape.Ray
        assertEquals(0.6, ray.dx, 1.0e-9)
        assertEquals(0.8, ray.dy, 1.0e-9)
        assertEquals(1.0, sqrt(ray.dx * ray.dx + ray.dy * ray.dy + ray.dz * ray.dz), 1.0e-9)

        val obb = obbPacket?.shapeData as ColliderShape.OBB
        val quaternionLength = sqrt(
            (obb.qx * obb.qx + obb.qy * obb.qy + obb.qz * obb.qz + obb.qw * obb.qw).toDouble()
        )
        assertEquals(1.0, quaternionLength, 1.0e-6)
    }

    @Test
    fun `zero Ray direction and zero OBB quaternion are rejected`() {
        val ray = decodeColliderUpdate(ColliderType.RAY) {
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(10.0)
        }
        val obb = decodeColliderUpdate(ColliderType.OBB) {
            writeDouble(0.0); writeDouble(0.0); writeDouble(0.0)
            writeDouble(1.0); writeDouble(1.0); writeDouble(1.0)
            writeFloat(0f); writeFloat(0f); writeFloat(0f); writeFloat(0f)
        }

        assertNull(ray)
        assertNull(obb)
    }

    @Test
    fun `unknown Collider wire ID is rejected`() {
        val packet = decodePacket {
            writeInt(19)
            writeUTF("unknown")
            writeInt(999)
        }

        assertNull(packet)
    }
}

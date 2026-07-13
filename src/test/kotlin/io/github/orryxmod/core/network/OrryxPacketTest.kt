package io.github.orryxmod.core.network

import io.github.orryxmod.feature.collider.ColliderShape
import io.github.orryxmod.feature.collider.ColliderType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OrryxPacketTest {

    @Test
    fun `all packetIds are unique`() {
        val packets = listOf(
            OrryxPacket.AimRequest("s", "m", 1.0, 1.0),
            OrryxPacket.AimConfirm(true),
            OrryxPacket.GhostEffect(java.util.UUID.randomUUID(), 1000, 5, 2),
            OrryxPacket.AimResponse("s", 0.0, 0.0, 0.0, 0f, 0f),
            OrryxPacket.FlickerEffect(java.util.UUID.randomUUID(), 1000, 0.5f, 500, 1f),
            OrryxPacket.MouseControl(true),
            OrryxPacket.EntityShowAdd(java.util.UUID.randomUUID(), "g", 0.0, 0.0, 0.0, 1000, 0f, 0f, 0f, 1f),
            OrryxPacket.EntityShowRemove(java.util.UUID.randomUUID(), "g"),
            OrryxPacket.NavigationStart(0, 0, 0, 10),
            OrryxPacket.NavigationStop,
            OrryxPacket.SquareShockwave(0.0, 0.0, 0.0, 5.0, 3.0, 0.0),
            OrryxPacket.CircleShockwave(0.0, 0.0, 0.0, 5.0),
            OrryxPacket.SectorShockwave(0.0, 0.0, 0.0, 5.0, 90.0, 0.0),
            OrryxPacket.BloomConfigSync(emptyMap()),
            OrryxPacket.BloomConfigUpdate("id", io.github.orryxmod.feature.bloom.BloomConfig("n", intArrayOf(0,0,0,0), 1f, 1f, 0)),
            OrryxPacket.BloomConfigRemove("id"),
            OrryxPacket.ColliderShow("show", 255, 255, 255, 255, ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0)),
            OrryxPacket.ColliderUpdate("update", ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0)),
            OrryxPacket.ColliderRemove("remove")
        )

        val ids = packets.map { it.packetId }
        assertEquals(ids.size, ids.toSet().size, "Duplicate packetIds found: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}")
    }

    @Test
    fun `Collider wire IDs are unique and stable`() {
        val types = ColliderType.values().toList()
        val wireIds = types.map { it.wireId }

        assertEquals(wireIds.size, wireIds.toSet().size)
        assertSame(ColliderType.SPHERE, ColliderType.fromWireId(0))
        assertSame(ColliderType.AABB, ColliderType.fromWireId(1))
        assertSame(ColliderType.OBB, ColliderType.fromWireId(2))
        assertSame(ColliderType.CAPSULE, ColliderType.fromWireId(3))
        assertSame(ColliderType.RAY, ColliderType.fromWireId(4))
        assertSame(ColliderType.COMPOSITE, ColliderType.fromWireId(5))
        assertNull(ColliderType.fromWireId(6))
    }

    @Test
    fun `data class equals and hashCode`() {
        val a = OrryxPacket.AimRequest("skill", "mod", 1.0, 2.0)
        val b = OrryxPacket.AimRequest("skill", "mod", 1.0, 2.0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `NavigationStop is singleton`() {
        assertSame(OrryxPacket.NavigationStop, OrryxPacket.NavigationStop)
    }

    @Test
    fun `data class copy creates independent instance`() {
        val original = OrryxPacket.AimConfirm(true)
        val copy = original.copy(confirmed = false)
        assertNotEquals(original, copy)
        assertTrue(original.confirmed)
        assertFalse(copy.confirmed)
    }
}

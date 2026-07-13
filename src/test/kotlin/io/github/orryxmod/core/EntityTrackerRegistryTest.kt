package io.github.orryxmod.core

import io.mockk.every
import io.mockk.mockk
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EntityTrackerRegistryTest {

    @BeforeEach
    fun setUp() {
        EntityTrackerRegistry.clear()
        EntityTrackerRegistry.configureLimits(EntityTrackerRegistry.Limits())
    }

    @AfterEach
    fun tearDown() {
        EntityTrackerRegistry.clear()
        EntityTrackerRegistry.configureLimits(EntityTrackerRegistry.Limits())
    }

    @Test
    fun `sample view is stable and bounded without snapshot copies`() {
        val (player, world) = trackedPlayer(1)
        every { world.getEntityByID(1) } returns player
        EntityTrackerRegistry.configureLimits(
            EntityTrackerRegistry.Limits(maxEntries = 4, maxSamplesPerEntity = 3)
        )

        val entry = EntityTrackerRegistry.getOrCreateEntry(player, 20)
        val view = entry.trackedInfo
        repeat(5) {
            player.posX = it.toDouble()
            EntityTrackerRegistry.tick()
        }

        assertSame(view, entry.trackedInfo)
        assertEquals(3, view.size)
        assertEquals(listOf(2.0, 3.0, 4.0), view.map { it.posX })
    }

    @Test
    fun `entry count is bounded and missing entities are removed on tick`() {
        EntityTrackerRegistry.configureLimits(
            EntityTrackerRegistry.Limits(maxEntries = 2, maxSamplesPerEntity = 4)
        )
        val players = (1..3).map { trackedPlayer(it) }
        players.forEach { (player, world) ->
            every { world.getEntityByID(any()) } returns player
            EntityTrackerRegistry.getOrCreateEntry(player, 4)
        }

        assertEquals(2, EntityTrackerRegistry.entryCount)

        val lastWorld = players.last().second
        every { lastWorld.getEntityByID(any()) } returns null
        EntityTrackerRegistry.tick()

        assertEquals(1, EntityTrackerRegistry.entryCount)
    }

    private fun trackedPlayer(entityId: Int): Pair<EntityPlayer, World> {
        val world = mockk<World>(relaxed = true)
        val player = mockk<EntityPlayer>(relaxed = true)
        every { player.entityId } returns entityId
        player.world = world
        player.isDead = false
        return player to world
    }
}

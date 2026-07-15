package io.github.orryxmod.feature.collider

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColliderManagerTest {

    @AfterEach
    fun cleanup() {
        ColliderManager.clear()
    }

    @Test
    fun `continuous update freezes current interpolation result without jumping back`() {
        ColliderManager.clear()
        ColliderManager.add(ColliderData(
            id = "moving",
            r = 255,
            g = 128,
            b = 64,
            a = 255,
            shape = ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0)
        ))
        ColliderManager.update("moving", ColliderShape.Sphere(10.0, 0.0, 0.0, 1.0))

        val midpoint = ColliderManager.renderView(0.5f).single()
        assertTrue(midpoint.interpolating)
        assertEquals(5.0, (midpoint.shape as ColliderShape.Sphere).cx, 1.0e-9)

        ColliderManager.update("moving", ColliderShape.Sphere(20.0, 0.0, 0.0, 1.0))
        val frozen = ColliderManager.renderView(0.5f).single()
        assertEquals(5.0, (frozen.shape as ColliderShape.Sphere).cx, 1.0e-9)

        ColliderManager.advanceClientTick()
        val completed = ColliderManager.renderView(0.5f).single()
        assertEquals(20.0, (completed.shape as ColliderShape.Sphere).cx, 1.0e-9)
        assertTrue(!completed.interpolating)
    }

    @Test
    fun `world instance change clears previous session before new packets`() {
        val firstWorld = Any()
        val secondWorld = Any()
        ColliderManager.ensureWorld(firstWorld)
        ColliderManager.add(ColliderData(
            "old-world", 255, 255, 255, 255,
            ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0)
        ))

        assertTrue(ColliderManager.ensureWorld(secondWorld))
        assertEquals(0, ColliderManager.size)
        assertTrue(!ColliderManager.ensureWorld(secondWorld))
    }

    @Test
    fun `entry keeps previous current interpolation timing and revision`() {
        ColliderManager.clear()
        val initial = ColliderShape.Capsule(1.0, 2.0, 3.0, 1.0, 2.0)
        val target = ColliderShape.Capsule(2.0, 3.0, 4.0, 2.0, 4.0)
        ColliderManager.add(ColliderData("capsule", 1, 2, 3, 4, initial))
        val initialRevision = ColliderManager.view().single().revision

        ColliderManager.update("capsule", target)
        val entry = ColliderManager.view().single()
        assertEquals(initial, entry.previousShape)
        assertEquals(target, entry.currentShape)
        assertEquals(1.0, entry.interpolationDuration)
        assertTrue(entry.revision > initialRevision)
    }
}

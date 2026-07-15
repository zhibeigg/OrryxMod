package io.github.orryxmod.feature.collider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ColliderInterpolatorTest {

    @Test
    fun `linear shapes interpolate all scalar fields`() {
        val sphere = ColliderInterpolator.interpolate(
            ColliderShape.Sphere(0.0, 2.0, 4.0, 2.0),
            ColliderShape.Sphere(10.0, 6.0, 8.0, 6.0),
            0.25
        ) as ColliderShape.Sphere
        assertEquals(2.5, sphere.cx)
        assertEquals(3.0, sphere.cy)
        assertEquals(5.0, sphere.cz)
        assertEquals(3.0, sphere.radius)

        val capsule = ColliderInterpolator.interpolate(
            ColliderShape.Capsule(0.0, 0.0, 0.0, 1.0, 2.0),
            ColliderShape.Capsule(2.0, 4.0, 6.0, 3.0, 8.0),
            0.5
        ) as ColliderShape.Capsule
        assertEquals(1.0, capsule.cx)
        assertEquals(2.0, capsule.cy)
        assertEquals(3.0, capsule.cz)
        assertEquals(2.0, capsule.radius)
        assertEquals(5.0, capsule.halfHeight)
    }

    @Test
    fun `ray interpolation normalizes direction`() {
        val ray = ColliderInterpolator.interpolate(
            ColliderShape.Ray(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 2.0),
            ColliderShape.Ray(2.0, 4.0, 6.0, 0.0, 1.0, 0.0, 10.0),
            0.5
        ) as ColliderShape.Ray

        assertEquals(1.0, ray.ox)
        assertEquals(2.0, ray.oy)
        assertEquals(3.0, ray.oz)
        assertEquals(6.0, ray.length)
        assertEquals(1.0, sqrt(ray.dx * ray.dx + ray.dy * ray.dy + ray.dz * ray.dz), 1.0e-12)
        assertEquals(ray.dx, ray.dy, 1.0e-12)
    }

    @Test
    fun `quaternion interpolation follows normalized shortest path`() {
        val obb = ColliderInterpolator.interpolate(
            ColliderShape.OBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0f, 0f, 0f, 1f),
            ColliderShape.OBB(2.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0f, 0f, 0f, -1f),
            0.5
        ) as ColliderShape.OBB
        val obbLength = sqrt((obb.qx * obb.qx + obb.qy * obb.qy + obb.qz * obb.qz + obb.qw * obb.qw).toDouble())
        assertEquals(1.0, obbLength, 1.0e-6)
        assertEquals(1.0f, obb.qw)

        val capsule = ColliderInterpolator.interpolate(
            ColliderShape.OrientedCapsule(0.0, 0.0, 0.0, 1.0, 2.0, 0f, 0f, 0f, 1f),
            ColliderShape.OrientedCapsule(4.0, 6.0, 8.0, 3.0, 4.0, 0f, 1f, 0f, 0f),
            0.5
        ) as ColliderShape.OrientedCapsule
        val capsuleLength = sqrt(
            (capsule.qx * capsule.qx + capsule.qy * capsule.qy +
                capsule.qz * capsule.qz + capsule.qw * capsule.qw).toDouble()
        )
        assertEquals(1.0, capsuleLength, 1.0e-6)
        assertEquals(2.0, capsule.cx)
        assertEquals(2.0, capsule.radius)
        assertEquals(3.0, capsule.halfHeight)
    }

    @Test
    fun `stable composite recursively interpolates and uses current child metadata`() {
        val previous = ColliderShape.Composite(listOf(
            ColliderData("child", 1, 2, 3, 4, ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0))
        ))
        val current = ColliderShape.Composite(listOf(
            ColliderData("child", 10, 20, 30, 40, ColliderShape.Sphere(10.0, 0.0, 0.0, 3.0))
        ))

        val result = ColliderInterpolator.interpolate(previous, current, 0.5) as ColliderShape.Composite
        val child = result.children.single()
        val sphere = child.shape as ColliderShape.Sphere
        assertEquals(10, child.r)
        assertEquals(5.0, sphere.cx)
        assertEquals(2.0, sphere.radius)
    }

    @Test
    fun `composite switches to current when id type or structure changes`() {
        val previous = ColliderShape.Composite(listOf(
            ColliderData("old", 1, 2, 3, 4, ColliderShape.Sphere(0.0, 0.0, 0.0, 1.0))
        ))
        val changedId = ColliderShape.Composite(listOf(
            ColliderData("new", 1, 2, 3, 4, ColliderShape.Sphere(10.0, 0.0, 0.0, 1.0))
        ))
        val changedType = ColliderShape.Composite(listOf(
            ColliderData("old", 1, 2, 3, 4, ColliderShape.AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
        ))

        assertSame(changedId, ColliderInterpolator.interpolate(previous, changedId, 0.5))
        assertSame(changedType, ColliderInterpolator.interpolate(previous, changedType, 0.5))
        assertTrue(!ColliderInterpolator.hasStableStructure(previous, changedType))
    }
}

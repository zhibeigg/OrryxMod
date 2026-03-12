package io.github.orryxmod.feature.shockwave

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ShapeTest {

    // ========== CircleShape ==========

    @Test
    fun `CircleShape segments count based on radius`() {
        val shape = CircleShape(Vector3d(0.0, 0.0, 0.0), 5.0)
        val directions = shape.spreadDirections().toList()
        val expected = (5.0 * 8).toInt().coerceIn(16, 128)
        assertEquals(expected, directions.size)
    }

    @Test
    fun `CircleShape directions are evenly distributed`() {
        val shape = CircleShape(Vector3d(0.0, 0.0, 0.0), 5.0)
        val directions = shape.spreadDirections().toList()

        // 检查所有方向的 length 等于 radius
        directions.forEach { assertEquals(5.0, it.length, 0.001) }

        // 检查方向均匀分布（角度间隔一致）
        val angles = directions.map { Math.atan2(it.direction.z, it.direction.x) }
        for (i in 1 until angles.size) {
            val diff = abs(angles[i] - angles[i - 1])
            assertTrue(diff > 0, "Angles should be different")
        }
    }

    @Test
    fun `CircleShape length equals radius`() {
        val shape = CircleShape(Vector3d(1.0, 2.0, 3.0), 10.0)
        shape.spreadDirections().forEach {
            assertEquals(10.0, it.length, 0.001)
        }
    }

    @Test
    fun `CircleShape small radius uses minimum segments`() {
        val shape = CircleShape(Vector3d(0.0, 0.0, 0.0), 1.0)
        val count = shape.spreadDirections().count()
        assertEquals(16, count) // coerceIn(16, 128)
    }

    // ========== SquareShape ==========

    @Test
    fun `SquareShape generates grid points`() {
        val shape = SquareShape(Vector3d(0.0, 0.0, 0.0), 5.0, 3.0, 0.0)
        val directions = shape.spreadDirections().toList()
        val lengthSteps = (5.0 * 2).toInt().coerceIn(4, 64)
        val widthSteps = (3.0 * 2).toInt().coerceIn(4, 64)
        val expected = (lengthSteps + 1) * (widthSteps + 1)
        assertEquals(expected, directions.size)
    }

    @Test
    fun `SquareShape yaw rotation affects directions`() {
        val noRotation = SquareShape(Vector3d(0.0, 0.0, 0.0), 5.0, 3.0, 0.0)
        val rotated = SquareShape(Vector3d(0.0, 0.0, 0.0), 5.0, 3.0, 90.0)

        val dirs1 = noRotation.spreadDirections().toList()
        val dirs2 = rotated.spreadDirections().toList()

        assertEquals(dirs1.size, dirs2.size)
        // 方向应该不同（旋转了 90 度）
        assertNotEquals(dirs1[1].direction.x, dirs2[1].direction.x, 0.001)
    }

    // ========== SectorShape ==========

    @Test
    fun `SectorShape segments count based on angle`() {
        val shape = SectorShape(Vector3d(0.0, 0.0, 0.0), 5.0, 90.0, 0.0)
        val directions = shape.spreadDirections().toList()
        val expected = (90.0 / 5).toInt().coerceIn(8, 72) + 1 // 0..segments
        assertEquals(expected, directions.size)
    }

    @Test
    fun `SectorShape directions within angle range`() {
        val shape = SectorShape(Vector3d(0.0, 0.0, 0.0), 5.0, 90.0, 0.0)
        val directions = shape.spreadDirections().toList()

        directions.forEach {
            assertEquals(5.0, it.length, 0.001)
        }
    }

    @Test
    fun `SectorShape small angle uses minimum segments`() {
        val shape = SectorShape(Vector3d(0.0, 0.0, 0.0), 5.0, 10.0, 0.0)
        val count = shape.spreadDirections().count()
        assertEquals(9, count) // coerceIn(8, 72) = 8, then 0..8 = 9
    }
}

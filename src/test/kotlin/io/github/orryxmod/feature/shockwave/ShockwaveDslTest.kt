package io.github.orryxmod.feature.shockwave

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShockwaveDslTest {

    @Test
    fun `circle DSL builds correct CircleShape`() {
        val shape = circle {
            center(1.0, 2.0, 3.0)
            radius = 10.0
        }
        assertEquals(Vector3d(1.0, 2.0, 3.0), shape.center)
        assertEquals(10.0, shape.radius)
    }

    @Test
    fun `square DSL builds correct SquareShape`() {
        val shape = square {
            center(5.0, 6.0, 7.0)
            length = 8.0
            width = 4.0
            yaw = 45.0
        }
        assertEquals(Vector3d(5.0, 6.0, 7.0), shape.center)
        assertEquals(8.0, shape.length)
        assertEquals(4.0, shape.width)
        assertEquals(45.0, shape.yaw)
    }

    @Test
    fun `sector DSL builds correct SectorShape`() {
        val shape = sector {
            center(0.0, 0.0, 0.0)
            radius = 15.0
            angle = 120.0
            yaw = 90.0
        }
        assertEquals(15.0, shape.radius)
        assertEquals(120.0, shape.angle)
        assertEquals(90.0, shape.yaw)
    }

    @Test
    fun `FractureDsl default values`() {
        val dsl = FractureDsl()
        val config = dsl.build()
        assertEquals(0.1, config.bounceMultiplier)
        assertEquals(200, config.baseLifetime)
        assertEquals(30, config.lifetimeVariance)
    }

    @Test
    fun `FractureDsl custom values`() {
        val dsl = FractureDsl()
        dsl.bounceMultiplier = 0.5
        dsl.lifetime = 100
        dsl.lifetimeVariance = 10
        val config = dsl.build()
        assertEquals(0.5, config.bounceMultiplier)
        assertEquals(100, config.baseLifetime)
        assertEquals(10, config.lifetimeVariance)
    }

    @Test
    fun `RotationDsl randomTilt calculation`() {
        val dsl = RotationDsl()
        dsl.randomTilt(30f)
        val config = dsl.build()
        assertEquals(30f, config.baseTilt)
        assertEquals(10f, config.tiltVariance) // 30 / 3
    }

    @Test
    fun `ParticleDsl builds correct config`() {
        val dsl = ParticleDsl()
        dsl.enabled = false
        dsl.density = 16
        dsl.velocityMultiplier = 1.0f
        val config = dsl.build()
        assertFalse(config.enabled)
        assertEquals(16, config.density)
        assertEquals(1.0f, config.velocityMultiplier)
    }

    @Test
    fun `circle DSL default radius`() {
        val shape = circle { center(0.0, 0.0, 0.0) }
        assertEquals(5.0, shape.radius)
    }
}

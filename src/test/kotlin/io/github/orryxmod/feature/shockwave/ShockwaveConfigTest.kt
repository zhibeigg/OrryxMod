package io.github.orryxmod.feature.shockwave

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShockwaveConfigTest {

    @Test
    fun `FractureConfig default values`() {
        val config = FractureConfig()
        assertEquals(0.1, config.bounceMultiplier)
        assertEquals(200, config.baseLifetime)
        assertEquals(30, config.lifetimeVariance)
        assertEquals(15f, config.rotation.baseTilt)
    }

    @Test
    fun `RotationConfig default values`() {
        val config = RotationConfig()
        assertEquals(15f, config.baseTilt)
        assertEquals(5f, config.tiltVariance)
        assertEquals(20f, config.yawVariance)
        assertEquals(7.5f, config.rollVariance)
    }

    @Test
    fun `ParticleConfig default values`() {
        val config = ParticleConfig()
        assertTrue(config.enabled)
        assertEquals(8, config.density)
        assertEquals(0.5f, config.velocityMultiplier)
    }

    @Test
    fun `ShockwaveConfig copy behavior`() {
        val shape = CircleShape(Vector3d(0.0, 0.0, 0.0), 5.0)
        val config = ShockwaveConfig(shape = shape)
        val copy = config.copy(fracture = FractureConfig(bounceMultiplier = 0.5))

        assertEquals(0.1, config.fracture.bounceMultiplier)
        assertEquals(0.5, copy.fracture.bounceMultiplier)
        assertSame(shape, copy.shape)
    }

    @Test
    fun `FractureConfig copy behavior`() {
        val original = FractureConfig(bounceMultiplier = 0.2, baseLifetime = 100)
        val copy = original.copy(baseLifetime = 300)
        assertEquals(0.2, copy.bounceMultiplier)
        assertEquals(300, copy.baseLifetime)
    }
}

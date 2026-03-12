package io.github.orryxmod.core.api

import io.github.orryxmod.core.render.RenderContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenderableEffectTest {

    class TestTimedEffect(id: String, lifetime: Int) : TimedEffect(id, lifetime) {
        override fun render(context: RenderContext) {}
    }

    @Test
    fun `TimedEffect isActive within lifetime`() {
        val effect = TestTimedEffect("test", 10)
        assertTrue(effect.isActive)

        repeat(9) { effect.update() }
        assertTrue(effect.isActive)
    }

    @Test
    fun `TimedEffect isActive false after lifetime`() {
        val effect = TestTimedEffect("test", 5)
        repeat(5) { effect.update() }
        assertFalse(effect.isActive)
    }

    @Test
    fun `TimedEffect progress calculation`() {
        val effect = TestTimedEffect("test", 10)
        assertEquals(0f, effect.progress)

        repeat(5) { effect.update() }
        assertEquals(0.5f, effect.progress)

        repeat(5) { effect.update() }
        assertEquals(1.0f, effect.progress)
    }

    @Test
    fun `TimedEffect update increments ticksAlive`() {
        val effect = TestTimedEffect("test", 100)
        assertEquals(0f, effect.progress)

        effect.update()
        assertEquals(1f / 100f, effect.progress, 0.001f)

        effect.update()
        assertEquals(2f / 100f, effect.progress, 0.001f)
    }

    @Test
    fun `TimedEffect id is set correctly`() {
        val effect = TestTimedEffect("my-effect", 50)
        assertEquals("my-effect", effect.id)
    }

    @Test
    fun `TimedEffect default renderPriority is 0`() {
        val effect = TestTimedEffect("test", 10)
        assertEquals(0, effect.renderPriority)
    }

    @Test
    fun `TimedEffect dispose does nothing by default`() {
        val effect = TestTimedEffect("test", 10)
        assertDoesNotThrow { effect.dispose() }
    }
}

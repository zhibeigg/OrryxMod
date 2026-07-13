package io.github.orryxmod.core.render

import io.github.orryxmod.TestHelper
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.event.EventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EffectManagerTest {

    @BeforeEach
    fun setup() {
        TestHelper.mockLogger()
        EffectManager.clear()
        EventBus.clear()
    }

    @AfterEach
    fun teardown() {
        EffectManager.clear()
        EventBus.clear()
        TestHelper.cleanup()
    }

    private fun createEffect(
        id: String = "test",
        active: Boolean = true,
        priority: Int = 0
    ): RenderableEffect {
        return mockk<RenderableEffect>(relaxed = true).also {
            every { it.id } returns id
            every { it.isActive } returns active
            every { it.renderPriority } returns priority
        }
    }

    @Test
    fun `add and update moves effect into list`() {
        val effect = createEffect()
        EffectManager.add(effect)

        // 效果在 pendingAdd 中，update 后进入 effects
        EffectManager.update()

        assertEquals(1, EffectManager.size)
        assertTrue(EffectManager.exists("test"))
    }

    @Test
    fun `MAX_EFFECTS limit rejects excess effects`() {
        // 添加 200 个效果
        repeat(200) { i ->
            EffectManager.add(createEffect(id = "e$i"))
        }
        EffectManager.update()

        // 第 201 个应被拒绝
        EffectManager.add(createEffect(id = "overflow"))
        EffectManager.update()

        assertFalse(EffectManager.exists("overflow"))
    }

    @Test
    fun `update cleans up inactive effects and calls dispose`() {
        val effect = mockk<RenderableEffect>(relaxed = true)
        every { effect.id } returns "dying"
        every { effect.renderPriority } returns 0
        // 第一次 update 时 active，第二次 inactive
        every { effect.isActive } returnsMany listOf(true, false)

        EffectManager.add(effect)
        EffectManager.update() // 添加到 effects

        EffectManager.update() // 应该清理

        verify { effect.dispose() }
        assertEquals(0, EffectManager.size)
    }

    @Test
    fun `pendingRemove processing`() {
        val effect = createEffect(id = "removeme")
        EffectManager.add(effect)
        EffectManager.update()

        EffectManager.remove(effect)
        EffectManager.update()

        assertFalse(EffectManager.exists("removeme"))
        verify { effect.dispose() }
    }

    @Test
    fun `persistent effect removed and re-enabled in same tick remains renderable`() {
        var active = true
        val effect = mockk<RenderableEffect>(relaxed = true)
        every { effect.id } returns "persistent-singleton"
        every { effect.renderPriority } returns 0
        every { effect.isActive } answers { active }

        EffectManager.addPersistent(effect)
        EffectManager.update()

        active = false
        EffectManager.remove(effect)
        assertFalse(EffectManager.exists("persistent-singleton"))

        active = true
        assertTrue(EffectManager.addPersistent(effect))
        EffectManager.update()

        assertTrue(EffectManager.exists("persistent-singleton"))
        assertEquals(1, EffectManager.size)
        val context = RenderContext(0f, 0.0, 0.0, 0.0)
        EffectManager.render(context)
        verify { effect.render(context) }
        verify(exactly = 0) { effect.dispose() }

        EffectManager.clearSessionEffects()
        assertTrue(EffectManager.exists("persistent-singleton"))
    }

    @Test
    fun `inactive persistent effect cleanup removes persistent marker`() {
        var active = true
        val effect = mockk<RenderableEffect>(relaxed = true)
        every { effect.id } returns "expired-persistent"
        every { effect.renderPriority } returns 0
        every { effect.isActive } answers { active }

        EffectManager.addPersistent(effect)
        EffectManager.update()
        active = false
        EffectManager.update()

        active = true
        assertTrue(EffectManager.add(effect))
        EffectManager.update()
        EffectManager.clearSessionEffects()

        assertFalse(EffectManager.exists("expired-persistent"))
        verify(exactly = 2) { effect.dispose() }
    }

    @Test
    fun `renderPriority sorting`() {
        val e1 = createEffect(id = "low", priority = 10)
        val e2 = createEffect(id = "high", priority = 1)
        val e3 = createEffect(id = "mid", priority = 5)

        EffectManager.add(e1)
        EffectManager.add(e2)
        EffectManager.add(e3)
        EffectManager.update()

        val ids = EffectManager.effects.map { it.id }
        assertEquals(listOf("high", "mid", "low"), ids)
    }

    @Test
    fun `exists checks both effects and pendingAdd`() {
        val effect = createEffect(id = "pending")
        EffectManager.add(effect)

        // 还没 update，但 exists 应该在 pendingAdd 中找到
        assertTrue(EffectManager.exists("pending"))
    }

    @Test
    fun `removeById removes matching effects`() {
        val e1 = createEffect(id = "target")
        val e2 = createEffect(id = "keep")
        EffectManager.add(e1)
        EffectManager.add(e2)
        EffectManager.update()

        EffectManager.removeById("target")
        EffectManager.update()

        assertFalse(EffectManager.exists("target"))
        assertTrue(EffectManager.exists("keep"))
    }

    @Test
    fun `render only renders active effects`() {
        val active = createEffect(id = "active", active = true)
        val inactive = createEffect(id = "inactive", active = false)

        EffectManager.add(active)
        EffectManager.add(inactive)
        EffectManager.update()

        val ctx = RenderContext(0f, 0.0, 0.0, 0.0)
        EffectManager.render(ctx)

        verify { active.render(ctx) }
        verify(exactly = 0) { inactive.render(any()) }
    }

    @Test
    fun `clear isolates dispose failures`() {
        val failing = createEffect(id = "failing")
        val healthy = createEffect(id = "healthy")
        every { failing.dispose() } throws IllegalStateException("dispose failed")
        EffectManager.add(failing)
        EffectManager.add(healthy)
        EffectManager.update()

        assertDoesNotThrow { EffectManager.clear() }

        verify { failing.dispose() }
        verify { healthy.dispose() }
        assertEquals(0, EffectManager.size)
    }

    @Test
    fun `clear session effects preserves persistent renderers`() {
        val persistent = createEffect(id = "persistent")
        val transient = createEffect(id = "transient")
        EffectManager.addPersistent(persistent)
        EffectManager.add(transient)
        EffectManager.update()

        EffectManager.clearSessionEffects()

        assertTrue(EffectManager.exists("persistent"))
        assertFalse(EffectManager.exists("transient"))
        verify(exactly = 0) { persistent.dispose() }
        verify { transient.dispose() }
    }

    @Test
    fun `clear disposes all effects`() {
        val e1 = createEffect(id = "a")
        val e2 = createEffect(id = "b")
        EffectManager.addPersistent(e1)
        EffectManager.add(e2)
        EffectManager.update()

        EffectManager.clear()

        assertEquals(0, EffectManager.size)
        verify { e1.dispose() }
        verify { e2.dispose() }
    }
}

package io.github.orryxmod.core.render

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RenderContextTest {

    @Test
    fun `constructor stores values correctly`() {
        val ctx = RenderContext(0.5f, 10.0, 20.0, 30.0)
        assertEquals(0.5f, ctx.partialTicks)
        assertEquals(10.0, ctx.viewerX)
        assertEquals(20.0, ctx.viewerY)
        assertEquals(30.0, ctx.viewerZ)
    }

    @Test
    fun `toRelative converts world coordinates to viewer-relative`() {
        val ctx = RenderContext(0f, 10.0, 20.0, 30.0)
        val relative = ctx.toRelative(15.0, 25.0, 35.0)
        assertEquals(5.0, relative.x, 0.001)
        assertEquals(5.0, relative.y, 0.001)
        assertEquals(5.0, relative.z, 0.001)
    }

    @Test
    fun `toRelative with Vector3d`() {
        val ctx = RenderContext(0f, 100.0, 200.0, 300.0)
        val pos = Vector3d(110.0, 210.0, 310.0)
        val relative = ctx.toRelative(pos)
        assertEquals(10.0, relative.x, 0.001)
        assertEquals(10.0, relative.y, 0.001)
        assertEquals(10.0, relative.z, 0.001)
    }

    @Test
    fun `toRelative at viewer position returns zero`() {
        val ctx = RenderContext(0f, 5.0, 5.0, 5.0)
        val relative = ctx.toRelative(5.0, 5.0, 5.0)
        assertEquals(0.0, relative.x, 0.001)
        assertEquals(0.0, relative.y, 0.001)
        assertEquals(0.0, relative.z, 0.001)
    }

    @Test
    fun `data class equals and copy`() {
        val ctx1 = RenderContext(0.5f, 1.0, 2.0, 3.0)
        val ctx2 = RenderContext(0.5f, 1.0, 2.0, 3.0)
        assertEquals(ctx1, ctx2)

        val ctx3 = ctx1.copy(partialTicks = 0.75f)
        assertEquals(0.75f, ctx3.partialTicks)
        assertEquals(1.0, ctx3.viewerX)
    }
}

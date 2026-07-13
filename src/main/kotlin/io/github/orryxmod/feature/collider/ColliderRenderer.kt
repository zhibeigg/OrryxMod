package io.github.orryxmod.feature.collider

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11
import kotlin.math.cos
import kotlin.math.sin

/**
 * 碰撞箱线框渲染器
 */
class ColliderRenderer : RenderableEffect {

    override val id: String = "collider-renderer"
    override val isActive: Boolean = true
    override val renderPriority: Int = 100

    /** 圆环细分段数 */
    private val circleSegments = 32

    override fun render(context: RenderContext) {
        val colliders = ColliderManager.snapshot()
        if (colliders.isEmpty()) return

        RenderUtils.withGlState(blend = true, depth = false, texture = false, lighting = false) {
            GL11.glLineWidth(2.0f)
            for (data in colliders) {
                drawShape(context, data.shape, data.r, data.g, data.b, data.a)
            }
            GL11.glLineWidth(1.0f)
        }
    }

    override fun update() {
        // 碰撞箱由 ColliderManager 管理生命周期，无需 tick 更新
    }

    // ========== 形状绘制分发 ==========

    private fun drawShape(ctx: RenderContext, shape: ColliderShape, r: Int, g: Int, b: Int, a: Int) {
        when (shape) {
            is ColliderShape.Sphere -> drawSphere(ctx, shape, r, g, b, a)
            is ColliderShape.AABB -> drawAABB(ctx, shape, r, g, b, a)
            is ColliderShape.OBB -> drawOBB(ctx, shape, r, g, b, a)
            is ColliderShape.Capsule -> drawCapsule(ctx, shape, r, g, b, a)
            is ColliderShape.Ray -> drawRay(ctx, shape, r, g, b, a)
            is ColliderShape.Composite -> drawComposite(ctx, shape, r, g, b, a)
        }
    }

    // ========== SPHERE: 3 个正交圆环 ==========

    private fun drawSphere(ctx: RenderContext, s: ColliderShape.Sphere, r: Int, g: Int, b: Int, a: Int) {
        val rel = ctx.toRelative(s.cx, s.cy, s.cz)
        val tess = Tessellator.getInstance()
        val buf = tess.buffer

        // XY 平面圆环
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until circleSegments) {
            val angle = 2.0 * Math.PI * i / circleSegments
            buf.pos(rel.x + cos(angle) * s.radius, rel.y + sin(angle) * s.radius, rel.z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // XZ 平面圆环
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until circleSegments) {
            val angle = 2.0 * Math.PI * i / circleSegments
            buf.pos(rel.x + cos(angle) * s.radius, rel.y, rel.z + sin(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // YZ 平面圆环
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until circleSegments) {
            val angle = 2.0 * Math.PI * i / circleSegments
            buf.pos(rel.x, rel.y + cos(angle) * s.radius, rel.z + sin(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()
    }

    // ========== AABB: 12 条边线 ==========

    private fun drawAABB(ctx: RenderContext, s: ColliderShape.AABB, r: Int, g: Int, b: Int, a: Int) {
        val rel = ctx.toRelative(s.cx, s.cy, s.cz)
        val x0 = rel.x - s.hx; val x1 = rel.x + s.hx
        val y0 = rel.y - s.hy; val y1 = rel.y + s.hy
        val z0 = rel.z - s.hz; val z1 = rel.z + s.hz
        drawBox(x0, y0, z0, x1, y1, z1, r, g, b, a)
    }

    // ========== OBB: 12 条边线 + 四元数旋转 ==========

    private fun drawOBB(ctx: RenderContext, s: ColliderShape.OBB, r: Int, g: Int, b: Int, a: Int) {
        val rel = ctx.toRelative(s.cx, s.cy, s.cz)

        GlStateManager.pushMatrix()
        GlStateManager.translate(rel.x, rel.y, rel.z)

        // 四元数 → 旋转矩阵（列主序 4x4，OpenGL 格式）
        val qx = s.qx; val qy = s.qy; val qz = s.qz; val qw = s.qw
        val m = floatArrayOf(
            1 - 2*(qy*qy + qz*qz), 2*(qx*qy + qz*qw),     2*(qx*qz - qy*qw),     0f,
            2*(qx*qy - qz*qw),     1 - 2*(qx*qx + qz*qz), 2*(qy*qz + qx*qw),     0f,
            2*(qx*qz + qy*qw),     2*(qy*qz - qx*qw),     1 - 2*(qx*qx + qy*qy), 0f,
            0f,                     0f,                     0f,                     1f
        )
        val buf = org.lwjgl.BufferUtils.createFloatBuffer(16)
        buf.put(m).flip()
        GL11.glMultMatrix(buf)

        drawBox(-s.hx, -s.hy, -s.hz, s.hx, s.hy, s.hz, r, g, b, a)

        GlStateManager.popMatrix()
    }

    // ========== CAPSULE: 2 个半球 + 4 条连接线 ==========

    private fun drawCapsule(ctx: RenderContext, s: ColliderShape.Capsule, r: Int, g: Int, b: Int, a: Int) {
        val rel = ctx.toRelative(s.cx, s.cy, s.cz)
        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        val topY = rel.y + s.halfHeight
        val botY = rel.y - s.halfHeight
        val halfSeg = circleSegments / 2

        // 顶部半球 (3 个半圆环)
        // XY 半圆
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSeg) {
            val angle = Math.PI * i / halfSeg
            buf.pos(rel.x + cos(angle) * s.radius, topY + sin(angle) * s.radius, rel.z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // YZ 半圆
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSeg) {
            val angle = Math.PI * i / halfSeg
            buf.pos(rel.x, topY + sin(angle) * s.radius, rel.z + cos(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // 顶部水平圆环
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until circleSegments) {
            val angle = 2.0 * Math.PI * i / circleSegments
            buf.pos(rel.x + cos(angle) * s.radius, topY, rel.z + sin(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // 底部半球 (3 个半圆环)
        // XY 半圆
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSeg) {
            val angle = Math.PI + Math.PI * i / halfSeg
            buf.pos(rel.x + cos(angle) * s.radius, botY + sin(angle) * s.radius, rel.z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // YZ 半圆
        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSeg) {
            val angle = Math.PI + Math.PI * i / halfSeg
            buf.pos(rel.x, botY + sin(angle) * s.radius, rel.z + cos(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // 底部水平圆环
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until circleSegments) {
            val angle = 2.0 * Math.PI * i / circleSegments
            buf.pos(rel.x + cos(angle) * s.radius, botY, rel.z + sin(angle) * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        // 4 条垂直连接线
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(rel.x + s.radius, topY, rel.z).color(r, g, b, a).endVertex()
        buf.pos(rel.x + s.radius, botY, rel.z).color(r, g, b, a).endVertex()

        buf.pos(rel.x - s.radius, topY, rel.z).color(r, g, b, a).endVertex()
        buf.pos(rel.x - s.radius, botY, rel.z).color(r, g, b, a).endVertex()

        buf.pos(rel.x, topY, rel.z + s.radius).color(r, g, b, a).endVertex()
        buf.pos(rel.x, botY, rel.z + s.radius).color(r, g, b, a).endVertex()

        buf.pos(rel.x, topY, rel.z - s.radius).color(r, g, b, a).endVertex()
        buf.pos(rel.x, botY, rel.z - s.radius).color(r, g, b, a).endVertex()
        tess.draw()
    }

    // ========== RAY: 线段 + 箭头 ==========

    private fun drawRay(ctx: RenderContext, s: ColliderShape.Ray, r: Int, g: Int, b: Int, a: Int) {
        val start = ctx.toRelative(s.ox, s.oy, s.oz)
        val endX = start.x + s.dx * s.length
        val endY = start.y + s.dy * s.length
        val endZ = start.z + s.dz * s.length

        val tess = Tessellator.getInstance()
        val buf = tess.buffer

        // 主线段
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(start.x, start.y, start.z).color(r, g, b, a).endVertex()
        buf.pos(endX, endY, endZ).color(r, g, b, a).endVertex()
        tess.draw()

        // 箭头（末端十字标记）
        val arrowSize = s.length.coerceAtMost(1.0) * 0.2
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(endX - arrowSize, endY, endZ).color(r, g, b, a).endVertex()
        buf.pos(endX + arrowSize, endY, endZ).color(r, g, b, a).endVertex()
        buf.pos(endX, endY - arrowSize, endZ).color(r, g, b, a).endVertex()
        buf.pos(endX, endY + arrowSize, endZ).color(r, g, b, a).endVertex()
        buf.pos(endX, endY, endZ - arrowSize).color(r, g, b, a).endVertex()
        buf.pos(endX, endY, endZ + arrowSize).color(r, g, b, a).endVertex()
        tess.draw()
    }

    // ========== COMPOSITE: 递归绘制子碰撞体 ==========

    private fun drawComposite(ctx: RenderContext, s: ColliderShape.Composite, r: Int, g: Int, b: Int, a: Int) {
        for (child in s.children) {
            drawShape(ctx, child.shape, child.r, child.g, child.b, child.a)
        }
    }

    // ========== 工具方法 ==========

    /**
     * 绘制轴对齐盒子的 12 条边线（相对坐标）
     */
    private fun drawBox(
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        r: Int, g: Int, b: Int, a: Int
    ) {
        val tess = Tessellator.getInstance()
        val buf = tess.buffer

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        // 底面 4 条边
        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y0, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y0, z1).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y0, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y0, z0).color(r, g, b, a).endVertex()

        // 顶面 4 条边
        buf.pos(x0, y1, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y1, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x1, y1, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y1, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z0).color(r, g, b, a).endVertex()

        // 4 条竖直边
        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z1).color(r, g, b, a).endVertex()

        tess.draw()
    }
}

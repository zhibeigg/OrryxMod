package io.github.orryxmod.feature.collider

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Collider 客户端渲染质量配置。距离单位为方块，仅影响线框显示，不改变 wire 数据。
 */
data class ColliderRenderConfig(
    val highDetailDistance: Double = 24.0,
    val mediumDetailDistance: Double = 64.0,
    val lowDetailDistance: Double = 128.0,
    val maxRenderDistance: Double = 192.0,
    val highSegments: Int = 32,
    val mediumSegments: Int = 24,
    val lowSegments: Int = 16,
    val minimumSegments: Int = 8,
    val highCompositeBudget: Int = 128,
    val mediumCompositeBudget: Int = 96,
    val lowCompositeBudget: Int = 64,
    val minimumCompositeBudget: Int = 32,
    val lowCompositeStride: Int = 2,
    val minimumCompositeStride: Int = 4,
    val maxCompositeDepth: Int = 8
)

/**
 * 碰撞箱线框渲染器
 */
class ColliderRenderer(
    private val configProvider: () -> ColliderRenderConfig = DEFAULT_CONFIG_PROVIDER
) : RenderableEffect {

    override val id: String = "collider-renderer"
    override val isActive: Boolean = true
    override val renderPriority: Int = 100

    private val circleTables = arrayOfNulls<CircleTable>(MAX_CIRCLE_SEGMENTS + 1)
    private val matrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private val compositeBounds = IdentityHashMap<ColliderShape.Composite, Bounds>(64)
    private val compositeBudget = CompositeBudget()
    private var lastGeometryRevision = -1L

    override fun render(context: RenderContext) {
        val revision = ColliderManager.revision
        if (revision != lastGeometryRevision) {
            compositeBounds.clear()
            lastGeometryRevision = revision
        }

        val colliders = ColliderManager.view()
        if (colliders.isEmpty()) return

        val config = configProvider()
        RenderUtils.withGlState(blend = true, depth = false, texture = false, lighting = false) {
            GL11.glLineWidth(2.0f)
            for (data in colliders) {
                drawShape(context, data.shape, data.r, data.g, data.b, data.a, config, null, 0)
            }
            GL11.glLineWidth(1.0f)
        }
    }

    override fun update() {
        // 碰撞箱由 ColliderManager 管理生命周期，无需 tick 更新
    }

    // ========== 形状绘制分发 ==========

    private fun drawShape(
        ctx: RenderContext,
        shape: ColliderShape,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        config: ColliderRenderConfig,
        budget: CompositeBudget?,
        depth: Int
    ) {
        if (budget != null) {
            if (budget.remaining <= 0) return
            budget.remaining--
        }

        val quality = qualityForDistance(distanceToShape(ctx, shape), config) ?: return

        if (shape is ColliderShape.Composite) {
            val activeBudget = if (budget == null) {
                compositeBudget.apply { remaining = compositeBudgetFor(quality, config) }
            } else {
                budget
            }
            drawComposite(ctx, shape, config, activeBudget, quality, depth)
            return
        }

        when (shape) {
            is ColliderShape.Sphere -> drawSphere(ctx, shape, r, g, b, a, segmentsFor(quality, config))
            is ColliderShape.AABB -> drawAABB(ctx, shape, r, g, b, a)
            is ColliderShape.OBB -> drawOBB(ctx, shape, r, g, b, a)
            is ColliderShape.Capsule -> drawCapsule(ctx, shape, r, g, b, a, segmentsFor(quality, config))
            is ColliderShape.Ray -> drawRay(ctx, shape, r, g, b, a, quality)
            is ColliderShape.Composite -> Unit
        }
    }

    // ========== SPHERE: 3 个正交圆环 ==========

    private fun drawSphere(
        ctx: RenderContext,
        s: ColliderShape.Sphere,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        segments: Int
    ) {
        val x = s.cx - ctx.viewerX
        val y = s.cy - ctx.viewerY
        val z = s.cz - ctx.viewerZ
        val table = circleTable(segments)
        val tess = Tessellator.getInstance()
        val buf = tess.buffer

        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until segments) {
            buf.pos(x + table.cos[i] * s.radius, y + table.sin[i] * s.radius, z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until segments) {
            buf.pos(x + table.cos[i] * s.radius, y, z + table.sin[i] * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until segments) {
            buf.pos(x, y + table.cos[i] * s.radius, z + table.sin[i] * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()
    }

    // ========== AABB: 12 条边线 ==========

    private fun drawAABB(ctx: RenderContext, s: ColliderShape.AABB, r: Int, g: Int, b: Int, a: Int) {
        val x = s.cx - ctx.viewerX
        val y = s.cy - ctx.viewerY
        val z = s.cz - ctx.viewerZ
        drawBox(x - s.hx, y - s.hy, z - s.hz, x + s.hx, y + s.hy, z + s.hz, r, g, b, a)
    }

    // ========== OBB: 12 条边线 + 四元数旋转 ==========

    private fun drawOBB(ctx: RenderContext, s: ColliderShape.OBB, r: Int, g: Int, b: Int, a: Int) {
        val x = s.cx - ctx.viewerX
        val y = s.cy - ctx.viewerY
        val z = s.cz - ctx.viewerZ

        GlStateManager.pushMatrix()
        try {
            GlStateManager.translate(x, y, z)

            val qx = s.qx
            val qy = s.qy
            val qz = s.qz
            val qw = s.qw
            matrixBuffer.clear()
            matrixBuffer.put(1 - 2 * (qy * qy + qz * qz)).put(2 * (qx * qy + qz * qw))
                .put(2 * (qx * qz - qy * qw)).put(0f)
            matrixBuffer.put(2 * (qx * qy - qz * qw)).put(1 - 2 * (qx * qx + qz * qz))
                .put(2 * (qy * qz + qx * qw)).put(0f)
            matrixBuffer.put(2 * (qx * qz + qy * qw)).put(2 * (qy * qz - qx * qw))
                .put(1 - 2 * (qx * qx + qy * qy)).put(0f)
            matrixBuffer.put(0f).put(0f).put(0f).put(1f)
            matrixBuffer.flip()
            GL11.glMultMatrix(matrixBuffer)

            drawBox(-s.hx, -s.hy, -s.hz, s.hx, s.hy, s.hz, r, g, b, a)
        } finally {
            GlStateManager.popMatrix()
        }
    }

    // ========== CAPSULE: 2 个半球 + 4 条连接线 ==========

    private fun drawCapsule(
        ctx: RenderContext,
        s: ColliderShape.Capsule,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        segments: Int
    ) {
        val x = s.cx - ctx.viewerX
        val y = s.cy - ctx.viewerY
        val z = s.cz - ctx.viewerZ
        val topY = y + s.halfHeight
        val botY = y - s.halfHeight
        val halfSegments = segments / 2
        val table = circleTable(segments)
        val tess = Tessellator.getInstance()
        val buf = tess.buffer

        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSegments) {
            buf.pos(x + table.cos[i] * s.radius, topY + table.sin[i] * s.radius, z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0..halfSegments) {
            buf.pos(x, topY + table.sin[i] * s.radius, z + table.cos[i] * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        drawHorizontalCircle(x, topY, z, s.radius, r, g, b, a, table, segments)

        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in halfSegments..segments) {
            buf.pos(x + table.cos[i] * s.radius, botY + table.sin[i] * s.radius, z)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR)
        for (i in halfSegments..segments) {
            buf.pos(x, botY + table.sin[i] * s.radius, z + table.cos[i] * s.radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()

        drawHorizontalCircle(x, botY, z, s.radius, r, g, b, a, table, segments)

        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(x + s.radius, topY, z).color(r, g, b, a).endVertex()
        buf.pos(x + s.radius, botY, z).color(r, g, b, a).endVertex()
        buf.pos(x - s.radius, topY, z).color(r, g, b, a).endVertex()
        buf.pos(x - s.radius, botY, z).color(r, g, b, a).endVertex()
        buf.pos(x, topY, z + s.radius).color(r, g, b, a).endVertex()
        buf.pos(x, botY, z + s.radius).color(r, g, b, a).endVertex()
        buf.pos(x, topY, z - s.radius).color(r, g, b, a).endVertex()
        buf.pos(x, botY, z - s.radius).color(r, g, b, a).endVertex()
        tess.draw()
    }

    private fun drawHorizontalCircle(
        x: Double,
        y: Double,
        z: Double,
        radius: Double,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        table: CircleTable,
        segments: Int
    ) {
        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
        for (i in 0 until segments) {
            buf.pos(x + table.cos[i] * radius, y, z + table.sin[i] * radius)
                .color(r, g, b, a).endVertex()
        }
        tess.draw()
    }

    // ========== RAY: 线段 + 按距离简化的箭头 ==========

    private fun drawRay(
        ctx: RenderContext,
        s: ColliderShape.Ray,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        quality: Quality
    ) {
        val startX = s.ox - ctx.viewerX
        val startY = s.oy - ctx.viewerY
        val startZ = s.oz - ctx.viewerZ
        val endX = startX + s.dx * s.length
        val endY = startY + s.dy * s.length
        val endZ = startZ + s.dz * s.length
        val arrowAxes = when (quality) {
            Quality.HIGH -> 3
            Quality.MEDIUM -> 2
            Quality.LOW -> 1
            Quality.MINIMUM -> 0
        }

        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(startX, startY, startZ).color(r, g, b, a).endVertex()
        buf.pos(endX, endY, endZ).color(r, g, b, a).endVertex()

        if (arrowAxes > 0) {
            val arrowSize = s.length.coerceAtMost(1.0) * 0.2
            buf.pos(endX - arrowSize, endY, endZ).color(r, g, b, a).endVertex()
            buf.pos(endX + arrowSize, endY, endZ).color(r, g, b, a).endVertex()
            if (arrowAxes > 1) {
                buf.pos(endX, endY - arrowSize, endZ).color(r, g, b, a).endVertex()
                buf.pos(endX, endY + arrowSize, endZ).color(r, g, b, a).endVertex()
            }
            if (arrowAxes > 2) {
                buf.pos(endX, endY, endZ - arrowSize).color(r, g, b, a).endVertex()
                buf.pos(endX, endY, endZ + arrowSize).color(r, g, b, a).endVertex()
            }
        }
        tess.draw()
    }

    // ========== COMPOSITE: 有预算的递归绘制 ==========

    private fun drawComposite(
        ctx: RenderContext,
        s: ColliderShape.Composite,
        config: ColliderRenderConfig,
        budget: CompositeBudget,
        quality: Quality,
        depth: Int
    ) {
        if (budget.remaining <= 0 || depth >= config.maxCompositeDepth.coerceIn(1, MAX_BOUNDS_DEPTH)) return
        val children = s.children
        if (children.isEmpty()) return

        val qualityStride = when (quality) {
            Quality.HIGH, Quality.MEDIUM -> 1
            Quality.LOW -> config.lowCompositeStride.coerceAtLeast(1)
            Quality.MINIMUM -> config.minimumCompositeStride.coerceAtLeast(1)
        }
        val qualitySamples = ceil(children.size.toDouble() / qualityStride).toInt()
        val sampleCount = minOf(qualitySamples, budget.remaining, children.size)
        if (sampleCount <= 0) return

        for (sample in 0 until sampleCount) {
            if (budget.remaining <= 0) break
            val index = if (sampleCount == 1) {
                0
            } else {
                (sample.toLong() * (children.size - 1) / (sampleCount - 1)).toInt()
            }
            val child = children[index]
            drawShape(ctx, child.shape, child.r, child.g, child.b, child.a, config, budget, depth + 1)
        }
    }

    // ========== LOD、距离与缓存 ==========

    private fun qualityForDistance(distance: Double, config: ColliderRenderConfig): Quality? {
        if (!distance.isFinite()) return null
        val high = finiteNonNegative(config.highDetailDistance)
        val medium = max(high, finiteNonNegative(config.mediumDetailDistance))
        val low = max(medium, finiteNonNegative(config.lowDetailDistance))
        val maxDistance = config.maxRenderDistance
        if (maxDistance.isFinite() && maxDistance > 0.0 && distance > maxDistance) return null
        return when {
            distance <= high -> Quality.HIGH
            distance <= medium -> Quality.MEDIUM
            distance <= low -> Quality.LOW
            else -> Quality.MINIMUM
        }
    }

    private fun segmentsFor(quality: Quality, config: ColliderRenderConfig): Int {
        val requested = when (quality) {
            Quality.HIGH -> config.highSegments
            Quality.MEDIUM -> config.mediumSegments
            Quality.LOW -> config.lowSegments
            Quality.MINIMUM -> config.minimumSegments
        }
        val clamped = requested.coerceIn(MIN_CIRCLE_SEGMENTS, MAX_CIRCLE_SEGMENTS)
        return if (clamped and 1 == 0) clamped else (clamped - 1).coerceAtLeast(MIN_CIRCLE_SEGMENTS)
    }

    private fun compositeBudgetFor(quality: Quality, config: ColliderRenderConfig): Int {
        val requested = when (quality) {
            Quality.HIGH -> config.highCompositeBudget
            Quality.MEDIUM -> config.mediumCompositeBudget
            Quality.LOW -> config.lowCompositeBudget
            Quality.MINIMUM -> config.minimumCompositeBudget
        }
        return requested.coerceIn(1, MAX_COMPOSITE_BUDGET)
    }

    private fun distanceToShape(ctx: RenderContext, shape: ColliderShape): Double = when (shape) {
        is ColliderShape.Sphere -> distanceToSphere(ctx, shape.cx, shape.cy, shape.cz, abs(shape.radius))
        is ColliderShape.AABB -> distanceToSphere(
            ctx, shape.cx, shape.cy, shape.cz,
            sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
        )
        is ColliderShape.OBB -> distanceToSphere(
            ctx, shape.cx, shape.cy, shape.cz,
            sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
        )
        is ColliderShape.Capsule -> distanceToSphere(
            ctx, shape.cx, shape.cy, shape.cz, abs(shape.halfHeight) + abs(shape.radius)
        )
        is ColliderShape.Ray -> distanceToRay(ctx, shape)
        is ColliderShape.Composite -> {
            val bounds = boundsFor(shape, 0)
            if (bounds.valid) distanceToSphere(ctx, bounds.x, bounds.y, bounds.z, bounds.radius) else Double.POSITIVE_INFINITY
        }
    }

    private fun distanceToSphere(ctx: RenderContext, x: Double, y: Double, z: Double, radius: Double): Double {
        val dx = x - ctx.viewerX
        val dy = y - ctx.viewerY
        val dz = z - ctx.viewerZ
        return (sqrt(dx * dx + dy * dy + dz * dz) - radius).coerceAtLeast(0.0)
    }

    private fun distanceToRay(ctx: RenderContext, ray: ColliderShape.Ray): Double {
        val ax = ray.ox
        val ay = ray.oy
        val az = ray.oz
        val vx = ray.dx * ray.length
        val vy = ray.dy * ray.length
        val vz = ray.dz * ray.length
        val lengthSquared = vx * vx + vy * vy + vz * vz
        if (lengthSquared <= 1.0e-12) {
            return distanceToSphere(ctx, ax, ay, az, 0.0)
        }
        val wx = ctx.viewerX - ax
        val wy = ctx.viewerY - ay
        val wz = ctx.viewerZ - az
        val t = ((wx * vx + wy * vy + wz * vz) / lengthSquared).coerceIn(0.0, 1.0)
        val dx = ax + vx * t - ctx.viewerX
        val dy = ay + vy * t - ctx.viewerY
        val dz = az + vz * t - ctx.viewerZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun boundsFor(composite: ColliderShape.Composite, depth: Int): Bounds {
        compositeBounds[composite]?.let { return it }
        if (compositeBounds.size >= MAX_CACHED_COMPOSITES) compositeBounds.clear()

        val bounds = Bounds()
        compositeBounds[composite] = bounds
        if (depth >= MAX_BOUNDS_DEPTH) return bounds
        for (child in composite.children) {
            includeShape(bounds, child.shape, depth + 1)
        }
        return bounds
    }

    private fun includeShape(target: Bounds, shape: ColliderShape, depth: Int) {
        when (shape) {
            is ColliderShape.Sphere -> target.include(shape.cx, shape.cy, shape.cz, abs(shape.radius))
            is ColliderShape.AABB -> target.include(
                shape.cx, shape.cy, shape.cz,
                sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
            )
            is ColliderShape.OBB -> target.include(
                shape.cx, shape.cy, shape.cz,
                sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
            )
            is ColliderShape.Capsule -> target.include(
                shape.cx, shape.cy, shape.cz, abs(shape.halfHeight) + abs(shape.radius)
            )
            is ColliderShape.Ray -> {
                val endX = shape.ox + shape.dx * shape.length
                val endY = shape.oy + shape.dy * shape.length
                val endZ = shape.oz + shape.dz * shape.length
                val dx = endX - shape.ox
                val dy = endY - shape.oy
                val dz = endZ - shape.oz
                target.include(
                    (shape.ox + endX) * 0.5,
                    (shape.oy + endY) * 0.5,
                    (shape.oz + endZ) * 0.5,
                    sqrt(dx * dx + dy * dy + dz * dz) * 0.5
                )
            }
            is ColliderShape.Composite -> {
                val childBounds = boundsFor(shape, depth)
                if (childBounds.valid) target.include(childBounds.x, childBounds.y, childBounds.z, childBounds.radius)
            }
        }
    }

    private fun circleTable(segments: Int): CircleTable {
        val cached = circleTables[segments]
        if (cached != null) return cached

        val cosine = DoubleArray(segments + 1)
        val sine = DoubleArray(segments + 1)
        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            cosine[i] = cos(angle)
            sine[i] = sin(angle)
        }
        return CircleTable(cosine, sine).also { circleTables[segments] = it }
    }

    private fun finiteNonNegative(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    // ========== 线框工具 ==========

    private fun drawBox(
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        r: Int, g: Int, b: Int, a: Int
    ) {
        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y0, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y0, z1).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y0, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y0, z0).color(r, g, b, a).endVertex()

        buf.pos(x0, y1, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y1, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x1, y1, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y1, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z0).color(r, g, b, a).endVertex()

        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z0).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z0).color(r, g, b, a).endVertex()
        buf.pos(x1, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buf.pos(x0, y0, z1).color(r, g, b, a).endVertex(); buf.pos(x0, y1, z1).color(r, g, b, a).endVertex()

        tess.draw()
    }

    private enum class Quality { HIGH, MEDIUM, LOW, MINIMUM }

    private data class CircleTable(val cos: DoubleArray, val sin: DoubleArray)

    private class CompositeBudget(var remaining: Int = 0)

    private class Bounds {
        var valid = false
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var radius = 0.0

        fun include(otherX: Double, otherY: Double, otherZ: Double, otherRadius: Double) {
            val safeRadius = if (otherRadius.isFinite()) abs(otherRadius) else return
            if (!valid) {
                valid = true
                x = otherX
                y = otherY
                z = otherZ
                radius = safeRadius
                return
            }

            val dx = otherX - x
            val dy = otherY - y
            val dz = otherZ - z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance + safeRadius <= radius) return
            if (distance + radius <= safeRadius) {
                x = otherX
                y = otherY
                z = otherZ
                radius = safeRadius
                return
            }
            if (distance <= 1.0e-12) {
                radius = max(radius, safeRadius)
                return
            }

            val newRadius = (radius + distance + safeRadius) * 0.5
            val shift = (newRadius - radius) / distance
            x += dx * shift
            y += dy * shift
            z += dz * shift
            radius = newRadius
        }
    }

    companion object {
        private const val MIN_CIRCLE_SEGMENTS = 8
        private const val MAX_CIRCLE_SEGMENTS = 64
        private const val MAX_COMPOSITE_BUDGET = 4096
        private const val MAX_CACHED_COMPOSITES = 512
        private const val MAX_BOUNDS_DEPTH = 16
        private val DEFAULT_CONFIG = ColliderRenderConfig()
        private val DEFAULT_CONFIG_PROVIDER: () -> ColliderRenderConfig = { DEFAULT_CONFIG }
    }
}

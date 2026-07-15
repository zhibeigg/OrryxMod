package io.github.orryxmod.feature.collider

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.util.math.AxisAlignedBB
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
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
 * 碰撞箱线框渲染器。
 *
 * 插值中的形状每帧动态生成；静止形状按 collider revision 和 LOD 缓存到 VBO。
 */
class ColliderRenderer(
    private val configProvider: () -> ColliderRenderConfig = DEFAULT_CONFIG_PROVIDER
) : RenderableEffect {

    override val id: String = "collider-renderer"
    override val isActive: Boolean = true
    override val renderPriority: Int = 100

    private val circleTables = arrayOfNulls<CircleTable>(MAX_CIRCLE_SEGMENTS + 1)
    private val compositeBounds = IdentityHashMap<ColliderShape.Composite, Bounds>(64)
    private val gpuCache = HashMap<String, CachedGeometry>()
    private val frustum = Frustum()
    private var lastGeometryRevision = -1L

    override fun render(context: RenderContext) {
        if (ColliderManager.ensureWorld(MC.world)) clearGpuCache()
        val revision = ColliderManager.revision
        if (revision != lastGeometryRevision) {
            compositeBounds.clear()
            lastGeometryRevision = revision
        }

        val colliders = ColliderManager.renderView(context.partialTicks)
        if (colliders.isEmpty()) {
            clearGpuCache()
            return
        }

        removeStaleCacheEntries(colliders)
        frustum.setPosition(context.viewerX, context.viewerY, context.viewerZ)
        val config = configProvider()

        RenderUtils.withGlState(blend = true, depth = false, texture = false, lighting = false) {
            GlStateManager.depthMask(false)
            GL11.glLineWidth(2.0f)
            for (data in colliders) {
                drawCollider(context, data, config)
            }
            GL11.glLineWidth(1.0f)
        }
    }

    override fun update() {
        ColliderManager.advanceClientTick()
        if (ColliderManager.ensureWorld(MC.world)) clearGpuCache()
    }

    override fun dispose() {
        clearGpuCache()
    }

    fun clearGpuCache() {
        gpuCache.values.forEach { it.vertexBuffer.deleteGlBuffers() }
        gpuCache.clear()
        compositeBounds.clear()
        lastGeometryRevision = ColliderManager.revision
    }

    private fun drawCollider(
        context: RenderContext,
        data: ColliderRenderData,
        config: ColliderRenderConfig
    ) {
        val bounds = boundsForShape(data.shape, cacheComposite = !data.interpolating)
        if (!bounds.valid) return
        val distance = distanceToShape(context, data.shape, bounds)
        val quality = qualityForDistance(distance, config) ?: return
        if (!frustum.isBoundingBoxInFrustum(bounds.toAabb())) return

        if (data.interpolating || !OpenGlHelper.useVbo()) {
            removeCachedGeometry(data.id)
            drawDynamic(context, data, quality, config)
            return
        }

        val geometryKey = geometryKey(quality, config)
        val cached = gpuCache[data.id]
        val geometry = if (cached != null && cached.revision == data.revision && cached.key == geometryKey) {
            cached
        } else {
            removeCachedGeometry(data.id)
            buildCachedGeometry(data, geometryKey, config, bounds)?.also { gpuCache[data.id] = it }
        }

        if (geometry != null) drawCached(context, geometry)
    }

    private fun drawDynamic(
        context: RenderContext,
        data: ColliderRenderData,
        quality: Quality,
        config: ColliderRenderConfig
    ) {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        val sink = BufferLineSink(buffer, context.viewerX, context.viewerY, context.viewerZ)
        appendShape(
            sink, data.shape, Color(data.r, data.g, data.b, data.a),
            quality, config, null, 0
        )
        if (buffer.vertexCount > 0) {
            tessellator.draw()
        } else {
            buffer.finishDrawing()
            buffer.reset()
        }
    }

    private fun buildCachedGeometry(
        data: ColliderRenderData,
        key: GeometryKey,
        config: ColliderRenderConfig,
        bounds: Bounds
    ): CachedGeometry? {
        val buffer = BufferBuilder(INITIAL_BUFFER_SIZE)
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        val sink = BufferLineSink(buffer, bounds.x, bounds.y, bounds.z)
        appendShape(
            sink, data.shape, Color(data.r, data.g, data.b, data.a),
            key.quality, config, null, 0
        )
        if (buffer.vertexCount <= 0) {
            buffer.finishDrawing()
            return null
        }

        buffer.finishDrawing()
        val vertexBuffer = VertexBuffer(DefaultVertexFormats.POSITION_COLOR)
        try {
            vertexBuffer.bufferData(buffer.byteBuffer)
        } catch (ex: Exception) {
            vertexBuffer.deleteGlBuffers()
            throw ex
        }
        return CachedGeometry(data.revision, key, bounds.x, bounds.y, bounds.z, vertexBuffer)
    }

    private fun drawCached(context: RenderContext, geometry: CachedGeometry) {
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        GlStateManager.pushMatrix()
        try {
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)
            try {
                GlStateManager.translate(
                    geometry.originX - context.viewerX,
                    geometry.originY - context.viewerY,
                    geometry.originZ - context.viewerZ
                )
                geometry.vertexBuffer.bindBuffer()
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
                GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)
                GL11.glVertexPointer(3, GL11.GL_FLOAT, POSITION_COLOR_STRIDE, 0L)
                GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, POSITION_COLOR_STRIDE, POSITION_COLOR_OFFSET.toLong())
                geometry.vertexBuffer.drawArrays(GL11.GL_LINES)
            } finally {
                OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, previousArrayBuffer)
                GL11.glPopClientAttrib()
            }
        } finally {
            GlStateManager.popMatrix()
        }
    }

    private fun appendShape(
        sink: LineSink,
        shape: ColliderShape,
        color: Color,
        quality: Quality,
        config: ColliderRenderConfig,
        budget: CompositeBudget?,
        depth: Int
    ) {
        if (budget != null && !budget.consume()) return

        when (shape) {
            is ColliderShape.Sphere -> appendSphere(sink, shape, color, segmentsFor(quality, config))
            is ColliderShape.AABB -> appendAabb(sink, shape, color)
            is ColliderShape.OBB -> appendObb(sink, shape, color)
            is ColliderShape.Capsule -> appendCapsule(
                sink, shape.cx, shape.cy, shape.cz, shape.radius, shape.halfHeight,
                IDENTITY_QUATERNION, color, segmentsFor(quality, config)
            )
            is ColliderShape.OrientedCapsule -> appendCapsule(
                sink, shape.cx, shape.cy, shape.cz, shape.radius, shape.halfHeight,
                Quaternion(shape.qx, shape.qy, shape.qz, shape.qw),
                color, segmentsFor(quality, config)
            )
            is ColliderShape.Ray -> appendRay(sink, shape, color, quality)
            is ColliderShape.Composite -> appendComposite(sink, shape, quality, config, budget, depth)
        }
    }

    private fun appendSphere(
        sink: LineSink,
        shape: ColliderShape.Sphere,
        color: Color,
        segments: Int
    ) {
        val table = circleTable(segments)
        appendLoop(sink, segments, color) { index ->
            Point(shape.cx + table.cos[index] * shape.radius, shape.cy + table.sin[index] * shape.radius, shape.cz)
        }
        appendLoop(sink, segments, color) { index ->
            Point(shape.cx + table.cos[index] * shape.radius, shape.cy, shape.cz + table.sin[index] * shape.radius)
        }
        appendLoop(sink, segments, color) { index ->
            Point(shape.cx, shape.cy + table.cos[index] * shape.radius, shape.cz + table.sin[index] * shape.radius)
        }
    }

    private fun appendAabb(sink: LineSink, shape: ColliderShape.AABB, color: Color) {
        appendBox(
            sink,
            shape.cx - shape.hx, shape.cy - shape.hy, shape.cz - shape.hz,
            shape.cx + shape.hx, shape.cy + shape.hy, shape.cz + shape.hz,
            color
        )
    }

    private fun appendObb(sink: LineSink, shape: ColliderShape.OBB, color: Color) {
        val q = Quaternion(shape.qx, shape.qy, shape.qz, shape.qw)
        val corners = arrayOf(
            Point(-shape.hx, -shape.hy, -shape.hz), Point(shape.hx, -shape.hy, -shape.hz),
            Point(shape.hx, -shape.hy, shape.hz), Point(-shape.hx, -shape.hy, shape.hz),
            Point(-shape.hx, shape.hy, -shape.hz), Point(shape.hx, shape.hy, -shape.hz),
            Point(shape.hx, shape.hy, shape.hz), Point(-shape.hx, shape.hy, shape.hz)
        ).map { point -> rotateAndTranslate(point, q, shape.cx, shape.cy, shape.cz) }
        for ((from, to) in BOX_EDGES) sink.line(corners[from], corners[to], color)
    }

    private fun appendCapsule(
        sink: LineSink,
        cx: Double,
        cy: Double,
        cz: Double,
        radius: Double,
        halfHeight: Double,
        quaternion: Quaternion,
        color: Color,
        segments: Int
    ) {
        val table = circleTable(segments)
        val halfSegments = segments / 2
        val transform: (Point) -> Point = { point -> rotateAndTranslate(point, quaternion, cx, cy, cz) }

        appendStrip(sink, 0, halfSegments, color) { index ->
            transform(Point(table.cos[index] * radius, halfHeight + table.sin[index] * radius, 0.0))
        }
        appendStrip(sink, 0, halfSegments, color) { index ->
            transform(Point(0.0, halfHeight + table.sin[index] * radius, table.cos[index] * radius))
        }
        appendLoop(sink, segments, color) { index ->
            transform(Point(table.cos[index] * radius, halfHeight, table.sin[index] * radius))
        }
        appendStrip(sink, halfSegments, segments, color) { index ->
            transform(Point(table.cos[index] * radius, -halfHeight + table.sin[index] * radius, 0.0))
        }
        appendStrip(sink, halfSegments, segments, color) { index ->
            transform(Point(0.0, -halfHeight + table.sin[index] * radius, table.cos[index] * radius))
        }
        appendLoop(sink, segments, color) { index ->
            transform(Point(table.cos[index] * radius, -halfHeight, table.sin[index] * radius))
        }

        val lineOffsets = arrayOf(
            Point(radius, 0.0, 0.0), Point(-radius, 0.0, 0.0),
            Point(0.0, 0.0, radius), Point(0.0, 0.0, -radius)
        )
        for (offset in lineOffsets) {
            sink.line(
                transform(Point(offset.x, halfHeight, offset.z)),
                transform(Point(offset.x, -halfHeight, offset.z)),
                color
            )
        }
    }

    private fun appendRay(sink: LineSink, shape: ColliderShape.Ray, color: Color, quality: Quality) {
        val start = Point(shape.ox, shape.oy, shape.oz)
        val end = Point(
            shape.ox + shape.dx * shape.length,
            shape.oy + shape.dy * shape.length,
            shape.oz + shape.dz * shape.length
        )
        sink.line(start, end, color)

        val arrowAxes = when (quality) {
            Quality.HIGH -> 3
            Quality.MEDIUM -> 2
            Quality.LOW -> 1
            Quality.MINIMUM -> 0
        }
        if (arrowAxes <= 0) return
        val size = shape.length.coerceAtMost(1.0) * 0.2
        sink.line(Point(end.x - size, end.y, end.z), Point(end.x + size, end.y, end.z), color)
        if (arrowAxes > 1) {
            sink.line(Point(end.x, end.y - size, end.z), Point(end.x, end.y + size, end.z), color)
        }
        if (arrowAxes > 2) {
            sink.line(Point(end.x, end.y, end.z - size), Point(end.x, end.y, end.z + size), color)
        }
    }

    private fun appendComposite(
        sink: LineSink,
        shape: ColliderShape.Composite,
        quality: Quality,
        config: ColliderRenderConfig,
        existingBudget: CompositeBudget?,
        depth: Int
    ) {
        if (depth >= config.maxCompositeDepth.coerceIn(1, MAX_BOUNDS_DEPTH)) return
        val children = shape.children
        if (children.isEmpty()) return

        val budget = existingBudget ?: CompositeBudget(compositeBudgetFor(quality, config))
        if (budget.remaining <= 0) return
        val stride = when (quality) {
            Quality.HIGH, Quality.MEDIUM -> 1
            Quality.LOW -> config.lowCompositeStride.coerceAtLeast(1)
            Quality.MINIMUM -> config.minimumCompositeStride.coerceAtLeast(1)
        }
        val qualitySamples = ceil(children.size.toDouble() / stride).toInt()
        val sampleCount = minOf(qualitySamples, budget.remaining, children.size)
        if (sampleCount <= 0) return

        for (sample in 0 until sampleCount) {
            if (budget.remaining <= 0) break
            val index = if (sampleCount == 1) 0 else
                (sample.toLong() * (children.size - 1) / (sampleCount - 1)).toInt()
            val child = children[index]
            appendShape(
                sink, child.shape, Color(child.r, child.g, child.b, child.a),
                quality, config, budget, depth + 1
            )
        }
    }

    private fun appendBox(
        sink: LineSink,
        x0: Double, y0: Double, z0: Double,
        x1: Double, y1: Double, z1: Double,
        color: Color
    ) {
        val corners = arrayOf(
            Point(x0, y0, z0), Point(x1, y0, z0), Point(x1, y0, z1), Point(x0, y0, z1),
            Point(x0, y1, z0), Point(x1, y1, z0), Point(x1, y1, z1), Point(x0, y1, z1)
        )
        for ((from, to) in BOX_EDGES) sink.line(corners[from], corners[to], color)
    }

    private inline fun appendLoop(
        sink: LineSink,
        segments: Int,
        color: Color,
        pointAt: (Int) -> Point
    ) {
        for (index in 0 until segments) {
            sink.line(pointAt(index), pointAt((index + 1) % segments), color)
        }
    }

    private inline fun appendStrip(
        sink: LineSink,
        start: Int,
        endInclusive: Int,
        color: Color,
        pointAt: (Int) -> Point
    ) {
        for (index in start until endInclusive) {
            sink.line(pointAt(index), pointAt(index + 1), color)
        }
    }

    private fun rotateAndTranslate(point: Point, q: Quaternion, cx: Double, cy: Double, cz: Double): Point {
        val tx = 2.0 * (q.y * point.z - q.z * point.y)
        val ty = 2.0 * (q.z * point.x - q.x * point.z)
        val tz = 2.0 * (q.x * point.y - q.y * point.x)
        return Point(
            point.x + q.w * tx + (q.y * tz - q.z * ty) + cx,
            point.y + q.w * ty + (q.z * tx - q.x * tz) + cy,
            point.z + q.w * tz + (q.x * ty - q.y * tx) + cz
        )
    }

    private fun distanceToShape(context: RenderContext, shape: ColliderShape, bounds: Bounds): Double = when (shape) {
        is ColliderShape.Ray -> distanceToRay(context, shape)
        else -> distanceToSphere(context, bounds.x, bounds.y, bounds.z, bounds.radius)
    }

    private fun distanceToSphere(context: RenderContext, x: Double, y: Double, z: Double, radius: Double): Double {
        val dx = x - context.viewerX
        val dy = y - context.viewerY
        val dz = z - context.viewerZ
        return (sqrt(dx * dx + dy * dy + dz * dz) - radius).coerceAtLeast(0.0)
    }

    private fun distanceToRay(context: RenderContext, ray: ColliderShape.Ray): Double {
        val vx = ray.dx * ray.length
        val vy = ray.dy * ray.length
        val vz = ray.dz * ray.length
        val lengthSquared = vx * vx + vy * vy + vz * vz
        if (lengthSquared <= 1.0e-12) return distanceToSphere(context, ray.ox, ray.oy, ray.oz, 0.0)
        val wx = context.viewerX - ray.ox
        val wy = context.viewerY - ray.oy
        val wz = context.viewerZ - ray.oz
        val t = ((wx * vx + wy * vy + wz * vz) / lengthSquared).coerceIn(0.0, 1.0)
        val dx = ray.ox + vx * t - context.viewerX
        val dy = ray.oy + vy * t - context.viewerY
        val dz = ray.oz + vz * t - context.viewerZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun boundsForShape(shape: ColliderShape, cacheComposite: Boolean): Bounds = when (shape) {
        is ColliderShape.Sphere -> Bounds(shape.cx, shape.cy, shape.cz, abs(shape.radius))
        is ColliderShape.AABB -> Bounds(
            shape.cx, shape.cy, shape.cz,
            sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
        )
        is ColliderShape.OBB -> Bounds(
            shape.cx, shape.cy, shape.cz,
            sqrt(shape.hx * shape.hx + shape.hy * shape.hy + shape.hz * shape.hz)
        )
        is ColliderShape.Capsule -> Bounds(
            shape.cx, shape.cy, shape.cz, abs(shape.halfHeight) + abs(shape.radius)
        )
        is ColliderShape.OrientedCapsule -> Bounds(
            shape.cx, shape.cy, shape.cz, abs(shape.halfHeight) + abs(shape.radius)
        )
        is ColliderShape.Ray -> {
            val endX = shape.ox + shape.dx * shape.length
            val endY = shape.oy + shape.dy * shape.length
            val endZ = shape.oz + shape.dz * shape.length
            val dx = endX - shape.ox
            val dy = endY - shape.oy
            val dz = endZ - shape.oz
            Bounds(
                (shape.ox + endX) * 0.5,
                (shape.oy + endY) * 0.5,
                (shape.oz + endZ) * 0.5,
                sqrt(dx * dx + dy * dy + dz * dz) * 0.5
            )
        }
        is ColliderShape.Composite -> boundsFor(shape, 0, cacheComposite)
    }

    private fun boundsFor(
        composite: ColliderShape.Composite,
        depth: Int,
        cacheComposite: Boolean
    ): Bounds {
        if (cacheComposite) {
            compositeBounds[composite]?.let { return it }
            if (compositeBounds.size >= MAX_CACHED_COMPOSITES) compositeBounds.clear()
        }
        val bounds = Bounds()
        if (cacheComposite) compositeBounds[composite] = bounds
        if (depth >= MAX_BOUNDS_DEPTH) return bounds
        for (child in composite.children) {
            val childBounds = if (child.shape is ColliderShape.Composite) {
                boundsFor(child.shape, depth + 1, cacheComposite)
            } else {
                boundsForShape(child.shape, cacheComposite)
            }
            if (childBounds.valid) bounds.include(childBounds.x, childBounds.y, childBounds.z, childBounds.radius)
        }
        return bounds
    }

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

    private fun geometryKey(quality: Quality, config: ColliderRenderConfig): GeometryKey {
        val stride = when (quality) {
            Quality.HIGH, Quality.MEDIUM -> 1
            Quality.LOW -> config.lowCompositeStride.coerceAtLeast(1)
            Quality.MINIMUM -> config.minimumCompositeStride.coerceAtLeast(1)
        }
        return GeometryKey(
            quality = quality,
            segments = segmentsFor(quality, config),
            compositeBudget = compositeBudgetFor(quality, config),
            compositeStride = stride,
            maxCompositeDepth = config.maxCompositeDepth.coerceIn(1, MAX_BOUNDS_DEPTH)
        )
    }

    private fun circleTable(segments: Int): CircleTable {
        val cached = circleTables[segments]
        if (cached != null) return cached
        val cosine = DoubleArray(segments + 1)
        val sine = DoubleArray(segments + 1)
        for (index in 0..segments) {
            val angle = 2.0 * Math.PI * index / segments
            cosine[index] = cos(angle)
            sine[index] = sin(angle)
        }
        return CircleTable(cosine, sine).also { circleTables[segments] = it }
    }

    private fun removeStaleCacheEntries(colliders: List<ColliderRenderData>) {
        if (gpuCache.isEmpty()) return
        val activeIds = colliders.mapTo(HashSet(colliders.size)) { it.id }
        val iterator = gpuCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeIds) {
                entry.value.vertexBuffer.deleteGlBuffers()
                iterator.remove()
            }
        }
    }

    private fun removeCachedGeometry(id: String) {
        gpuCache.remove(id)?.vertexBuffer?.deleteGlBuffers()
    }

    private fun finiteNonNegative(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    private interface LineSink {
        fun line(from: Point, to: Point, color: Color)
    }

    private class BufferLineSink(
        private val buffer: BufferBuilder,
        private val originX: Double,
        private val originY: Double,
        private val originZ: Double
    ) : LineSink {
        override fun line(from: Point, to: Point, color: Color) {
            vertex(from, color)
            vertex(to, color)
        }

        private fun vertex(point: Point, color: Color) {
            buffer.pos(point.x - originX, point.y - originY, point.z - originZ)
                .color(color.r, color.g, color.b, color.a)
                .endVertex()
        }
    }

    private enum class Quality { HIGH, MEDIUM, LOW, MINIMUM }

    private data class GeometryKey(
        val quality: Quality,
        val segments: Int,
        val compositeBudget: Int,
        val compositeStride: Int,
        val maxCompositeDepth: Int
    )

    private data class CachedGeometry(
        val revision: Long,
        val key: GeometryKey,
        val originX: Double,
        val originY: Double,
        val originZ: Double,
        val vertexBuffer: VertexBuffer
    )

    private data class Point(val x: Double, val y: Double, val z: Double)
    private data class Color(val r: Int, val g: Int, val b: Int, val a: Int)
    private data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float)
    private data class CircleTable(val cos: DoubleArray, val sin: DoubleArray)

    private class CompositeBudget(var remaining: Int) {
        fun consume(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    private class Bounds() {
        var valid = false
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var radius = 0.0

        constructor(x: Double, y: Double, z: Double, radius: Double) : this() {
            if (x.isFinite() && y.isFinite() && z.isFinite() && radius.isFinite()) {
                valid = true
                this.x = x
                this.y = y
                this.z = z
                this.radius = abs(radius)
            }
        }

        fun include(otherX: Double, otherY: Double, otherZ: Double, otherRadius: Double) {
            if (!otherX.isFinite() || !otherY.isFinite() || !otherZ.isFinite() || !otherRadius.isFinite()) return
            val safeRadius = abs(otherRadius)
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

        fun toAabb(): AxisAlignedBB = AxisAlignedBB(
            x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius
        )
    }

    companion object {
        private const val MIN_CIRCLE_SEGMENTS = 8
        private const val MAX_CIRCLE_SEGMENTS = 64
        private const val MAX_COMPOSITE_BUDGET = 4096
        private const val MAX_CACHED_COMPOSITES = 512
        private const val MAX_BOUNDS_DEPTH = 16
        private const val INITIAL_BUFFER_SIZE = 4096
        private const val POSITION_COLOR_STRIDE = 16
        private const val POSITION_COLOR_OFFSET = 12
        private val DEFAULT_CONFIG = ColliderRenderConfig()
        private val DEFAULT_CONFIG_PROVIDER: () -> ColliderRenderConfig = { DEFAULT_CONFIG }
        private val IDENTITY_QUATERNION = Quaternion(0f, 0f, 0f, 1f)
        private val BOX_EDGES = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
    }
}

package io.github.orryxmod.feature.collider

import io.github.orryxmod.OrryxMod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 单个 Collider 的插值状态。条目不可变，更新通过 ConcurrentHashMap 原子替换。
 */
data class ColliderEntry(
    val id: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
    val previousShape: ColliderShape,
    val currentShape: ColliderShape,
    val interpolationStartTime: Double,
    val interpolationDuration: Double,
    val revision: Long
) {
    fun shapeAt(renderTime: Double): ColliderShape {
        if (previousShape == currentShape || interpolationDuration <= 0.0) return currentShape
        val progress = ((renderTime - interpolationStartTime) / interpolationDuration).coerceIn(0.0, 1.0)
        return ColliderInterpolator.interpolate(previousShape, currentShape, progress)
    }

    fun isInterpolatingAt(renderTime: Double): Boolean =
        previousShape != currentShape && renderTime < interpolationStartTime + interpolationDuration
}

/** 渲染帧使用的稳定快照。 */
data class ColliderRenderData(
    val id: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
    val shape: ColliderShape,
    val interpolating: Boolean,
    val revision: Long
)

/**
 * 碰撞箱状态管理器。
 *
 * 渲染时间使用 clientTick + partialTicks，并通过 lastRenderTime 保证单调。收到连续更新时，
 * 先在当前单调时间冻结上一段插值结果，再将其作为新一段插值的起点，避免回跳。
 */
object ColliderManager {

    private const val MAX_COLLIDERS = 200
    private const val INTERPOLATION_TICKS = 1.0

    private val colliders = ConcurrentHashMap<String, ColliderEntry>()
    private val geometryRevision = AtomicLong()
    private val timeLock = Any()
    private val worldLock = Any()
    private var clientTick = 0L
    private var lastRenderTime = 0.0
    private var worldSessionInitialized = false
    private var worldToken: Any? = null

    /** 添加或替换碰撞箱；Show 直接建立静止条目。 */
    fun add(data: ColliderData) {
        if (!colliders.containsKey(data.id) && colliders.size >= MAX_COLLIDERS) {
            OrryxMod.logger.warn("[ColliderManager] Collider limit reached ($MAX_COLLIDERS), rejecting: ${data.id}")
            return
        }

        val revision = geometryRevision.incrementAndGet()
        val now = mutationTime()
        colliders[data.id] = ColliderEntry(
            id = data.id,
            r = data.r,
            g = data.g,
            b = data.b,
            a = data.a,
            previousShape = data.shape,
            currentShape = data.shape,
            interpolationStartTime = now,
            interpolationDuration = INTERPOLATION_TICKS,
            revision = revision
        )
    }

    /** 更新碰撞箱几何数据（保留原有颜色）。 */
    fun update(id: String, shape: ColliderShape) {
        val now = mutationTime()
        colliders.computeIfPresent(id) { _, existing ->
            val frozenShape = existing.shapeAt(now)
            val revision = geometryRevision.incrementAndGet()
            existing.copy(
                previousShape = frozenShape,
                currentShape = shape,
                interpolationStartTime = now,
                interpolationDuration = INTERPOLATION_TICKS,
                revision = revision
            )
        }
    }

    /** 移除碰撞箱。 */
    fun remove(id: String) {
        if (colliders.remove(id) != null) {
            geometryRevision.incrementAndGet()
        }
    }

    /** 清空所有碰撞箱。 */
    fun clear() {
        colliders.clear()
        geometryRevision.incrementAndGet()
    }

    /**
     * 同步当前世界实例。首次调用只建立会话；后续实例变化会原子清空旧世界数据。
     * 返回 true 表示发生了实际世界切换，调用方应同步释放 GPU 缓存。
     */
    fun ensureWorld(currentWorld: Any?): Boolean = synchronized(worldLock) {
        if (!worldSessionInitialized) {
            worldSessionInitialized = true
            worldToken = currentWorld
            return@synchronized false
        }
        if (worldToken === currentWorld) return@synchronized false

        worldToken = currentWorld
        colliders.clear()
        geometryRevision.incrementAndGet()
        true
    }

    /** 每个 ClientTick.END 推进一次逻辑时钟。 */
    fun advanceClientTick() {
        synchronized(timeLock) {
            clientTick++
            lastRenderTime = max(lastRenderTime, clientTick.toDouble())
        }
    }

    /**
     * 为当前帧生成渲染快照。最多 200 项，复制可避免帧内连续更新造成条目不一致。
     */
    fun renderView(partialTicks: Float): List<ColliderRenderData> {
        val renderTime = renderTime(partialTicks)
        return colliders.values.map { entry ->
            ColliderRenderData(
                id = entry.id,
                r = entry.r,
                g = entry.g,
                b = entry.b,
                a = entry.a,
                shape = entry.shapeAt(renderTime),
                interpolating = entry.isInterpolatingAt(renderTime),
                revision = entry.revision
            )
        }
    }

    /** 获取线程安全的弱一致条目视图，主要用于诊断。 */
    fun view(): Collection<ColliderEntry> = colliders.values

    internal val revision: Long get() = geometryRevision.get()

    /** 当前碰撞箱数量。 */
    val size: Int get() = colliders.size

    private fun mutationTime(): Double = synchronized(timeLock) {
        lastRenderTime = max(lastRenderTime, clientTick.toDouble())
        lastRenderTime
    }

    private fun renderTime(partialTicks: Float): Double = synchronized(timeLock) {
        val partial = if (partialTicks.isFinite()) partialTicks.toDouble().coerceIn(0.0, 1.0) else 0.0
        lastRenderTime = max(lastRenderTime, clientTick.toDouble() + partial)
        lastRenderTime
    }
}

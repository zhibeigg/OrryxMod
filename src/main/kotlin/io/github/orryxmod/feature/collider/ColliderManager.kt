package io.github.orryxmod.feature.collider

import io.github.orryxmod.OrryxMod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 碰撞箱状态管理器
 */
object ColliderManager {

    private const val MAX_COLLIDERS = 200

    private val colliders = ConcurrentHashMap<String, ColliderData>()
    private val geometryRevision = AtomicLong()

    /**
     * 添加或替换碰撞箱
     */
    fun add(data: ColliderData) {
        if (!colliders.containsKey(data.id) && colliders.size >= MAX_COLLIDERS) {
            OrryxMod.logger.warn("[ColliderManager] Collider limit reached ($MAX_COLLIDERS), rejecting: ${data.id}")
            return
        }
        colliders[data.id] = data
        geometryRevision.incrementAndGet()
    }

    /**
     * 更新碰撞箱几何数据（保留原有颜色）
     */
    fun update(id: String, shape: ColliderShape) {
        val existing = colliders[id] ?: return
        colliders[id] = existing.copy(shape = shape)
        geometryRevision.incrementAndGet()
    }

    /**
     * 移除碰撞箱
     */
    fun remove(id: String) {
        if (colliders.remove(id) != null) {
            geometryRevision.incrementAndGet()
        }
    }

    /**
     * 清空所有碰撞箱
     */
    fun clear() {
        colliders.clear()
        geometryRevision.incrementAndGet()
    }

    /**
     * 获取线程安全的弱一致视图，避免渲染帧中复制整个集合。
     */
    fun view(): Collection<ColliderData> = colliders.values

    internal val revision: Long get() = geometryRevision.get()

    /**
     * 当前碰撞箱数量
     */
    val size: Int get() = colliders.size
}

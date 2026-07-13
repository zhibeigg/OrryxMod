package io.github.orryxmod.feature.collider

import io.github.orryxmod.OrryxMod
import java.util.concurrent.ConcurrentHashMap

/**
 * 碰撞箱状态管理器
 */
object ColliderManager {

    private const val MAX_COLLIDERS = 200

    private val colliders = ConcurrentHashMap<String, ColliderData>()

    /**
     * 添加或替换碰撞箱
     */
    fun add(data: ColliderData) {
        if (!colliders.containsKey(data.id) && colliders.size >= MAX_COLLIDERS) {
            OrryxMod.logger.warn("[ColliderManager] Collider limit reached ($MAX_COLLIDERS), rejecting: ${data.id}")
            return
        }
        colliders[data.id] = data
    }

    /**
     * 更新碰撞箱几何数据（保留原有颜色）
     */
    fun update(id: String, shape: ColliderShape) {
        val existing = colliders[id] ?: return
        colliders[id] = existing.copy(shape = shape)
    }

    /**
     * 移除碰撞箱
     */
    fun remove(id: String) {
        colliders.remove(id)
    }

    /**
     * 清空所有碰撞箱
     */
    fun clear() {
        colliders.clear()
    }

    /**
     * 获取所有碰撞箱的快照
     */
    fun snapshot(): Collection<ColliderData> = colliders.values.toList()

    /**
     * 当前碰撞箱数量
     */
    val size: Int get() = colliders.size
}

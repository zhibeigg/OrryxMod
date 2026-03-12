package io.github.orryxmod.core.render

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 效果管理器 - 统一管理所有可渲染效果
 * 使用线程安全的集合以支持多线程环境
 */
object EffectManager {

    /** 最大效果数量，防止恶意服务端通过大量网络包导致内存/渲染爆炸 */
    private const val MAX_EFFECTS = 200

    @PublishedApi
    internal val effects = CopyOnWriteArrayList<RenderableEffect>()
    private val pendingAdd = CopyOnWriteArrayList<RenderableEffect>()
    private val pendingRemove = CopyOnWriteArrayList<RenderableEffect>()

    /**
     * 添加效果
     */
    fun add(effect: RenderableEffect) {
        if (effects.size + pendingAdd.size >= MAX_EFFECTS) {
            OrryxMod.logger.warn("[EffectManager] Effect limit reached ($MAX_EFFECTS), rejecting: ${effect.id}")
            return
        }
        pendingAdd.add(effect)
        EventBus.publish(Events.EffectAdded(effect))
    }

    /**
     * 移除效果
     */
    fun remove(effect: RenderableEffect) {
        pendingRemove.add(effect)
    }

    /**
     * 按 ID 移除效果
     */
    fun removeById(id: String) {
        effects.filter { it.id == id }.forEach { remove(it) }
    }

    /**
     * 按类型移除效果
     */
    inline fun <reified T : RenderableEffect> removeByType() {
        effects.filterIsInstance<T>().forEach { remove(it) }
    }

    /**
     * 获取所有指定类型的效果
     */
    inline fun <reified T : RenderableEffect> getByType(): List<T> {
        return effects.filterIsInstance<T>()
    }

    /**
     * 检查是否存在指定 ID 的效果
     */
    fun exists(id: String): Boolean {
        return effects.any { it.id == id } || pendingAdd.any { it.id == id }
    }

    /**
     * 每 tick 更新
     */
    fun update() {
        // 处理待添加
        if (pendingAdd.isNotEmpty()) {
            // 构建排序后的新列表，避免 clear+addAll 的非原子窗口
            // （渲染线程在 clear 和 addAll 之间调用 render() 会看到空列表）
            val merged = ArrayList<RenderableEffect>(effects.size + pendingAdd.size)
            merged.addAll(effects)
            merged.addAll(pendingAdd)
            merged.sortBy { it.renderPriority }
            effects.clear()
            effects.addAll(merged)
            pendingAdd.clear()
        }

        // 更新所有效果（创建快照避免并发修改）
        val effectsSnapshot = effects.toList()
        effectsSnapshot.forEach { effect ->
            try {
                effect.update()
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error updating effect ${effect.id}", ex)
            }
        }

        // 移除失效的效果
        val expired = effectsSnapshot.filter { !it.isActive }
        expired.forEach { effect ->
            effect.dispose()
            EventBus.publish(Events.EffectRemoved(effect))
        }
        effects.removeAll(expired.toSet())

        // 处理待移除
        if (pendingRemove.isNotEmpty()) {
            val toRemove = pendingRemove.toList()
            pendingRemove.clear()
            toRemove.forEach { effect ->
                if (effects.remove(effect)) {
                    effect.dispose()
                    EventBus.publish(Events.EffectRemoved(effect))
                }
            }
        }
    }

    /**
     * 渲染所有效果
     */
    fun render(context: RenderContext) {
        if (effects.isEmpty()) return

        // 创建快照避免并发修改
        val effectsSnapshot = effects.toList()
        effectsSnapshot.forEach { effect ->
            if (effect.isActive) {
                try {
                    effect.render(context)
                } catch (ex: Exception) {
                    OrryxMod.logger.error("Error rendering effect ${effect.id}", ex)
                }
            }
        }
    }

    /**
     * 清除所有效果
     */
    fun clear() {
        effects.forEach { it.dispose() }
        effects.clear()
        pendingAdd.clear()
        pendingRemove.clear()
    }

    /**
     * 获取当前效果数量
     */
    val size: Int get() = effects.size
}

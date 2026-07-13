package io.github.orryxmod.core.render

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import java.util.Collections
import java.util.IdentityHashMap
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
    private val persistentEffects = Collections.newSetFromMap(
        IdentityHashMap<RenderableEffect, Boolean>()
    )
    private val mutationLock = Any()

    /**
     * 添加效果
     */
    fun add(effect: RenderableEffect): Boolean = addInternal(effect, persistent = false)

    /**
     * 添加跨会话保留的基础渲染器；断线清理仅移除会话效果。
     */
    fun addPersistent(effect: RenderableEffect): Boolean = addInternal(effect, persistent = true)

    private fun addInternal(effect: RenderableEffect, persistent: Boolean): Boolean {
        val accepted = synchronized(mutationLock) {
            val cancelledRemoval = removeIdentity(pendingRemove, effect)
            val alreadyTracked = effects.any { it === effect } || pendingAdd.any { it === effect }

            when {
                alreadyTracked && cancelledRemoval -> {
                    if (persistent) persistentEffects.add(effect)
                    true
                }
                alreadyTracked -> {
                    if (persistent) persistentEffects.add(effect)
                    false
                }
                effects.size + pendingAdd.size >= MAX_EFFECTS -> false
                else -> {
                    pendingAdd.add(effect)
                    if (persistent) persistentEffects.add(effect)
                    true
                }
            }
        }

        if (!accepted) {
            OrryxMod.logger.warn("[EffectManager] Effect rejected: ${effect.id}")
            return false
        }
        EventBus.publish(Events.EffectAdded(effect))
        return true
    }

    /**
     * 移除效果
     */
    fun remove(effect: RenderableEffect) {
        synchronized(mutationLock) {
            persistentEffects.remove(effect)
            if (pendingRemove.none { it === effect }) {
                pendingRemove.add(effect)
            }
        }
    }

    /**
     * 按 ID 移除效果
     */
    fun removeById(id: String) {
        for (effect in effects) {
            if (effect.id == id) remove(effect)
        }
        for (effect in pendingAdd) {
            if (effect.id == id) remove(effect)
        }
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
    fun exists(id: String): Boolean = synchronized(mutationLock) {
        effects.any { it.id == id && pendingRemove.none { pending -> pending === it } } ||
            pendingAdd.any { it.id == id && pendingRemove.none { pending -> pending === it } }
    }

    /**
     * 每 tick 更新
     */
    fun update() {
        synchronized(mutationLock) {
            if (pendingAdd.isNotEmpty()) {
                effects.addAll(pendingAdd)
                pendingAdd.clear()
                effects.sortWith(compareBy { it.renderPriority })
            }
        }

        // CopyOnWriteArrayList 的迭代器本身就是稳定快照，无需额外 toList。
        for (effect in effects) {
            try {
                effect.update()
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error updating effect ${effect.id}", ex)
            }
        }

        for (effect in effects) {
            val removed = synchronized(mutationLock) {
                // isActive 必须在与删除相同的临界区内复检，避免并发恢复后仍被旧快照删除。
                if (!effect.isActive && removeIdentity(effects, effect)) {
                    persistentEffects.remove(effect)
                    removeIdentity(pendingRemove, effect)
                    true
                } else {
                    false
                }
            }
            if (removed) {
                disposeSafely(effect)
                publishRemoved(effect)
            }
        }

        val removals = synchronized(mutationLock) {
            val removedEffects = ArrayList<RenderableEffect>(pendingRemove.size)
            val seen = IdentityHashMap<RenderableEffect, Boolean>()

            for (effect in pendingRemove) {
                val removedFromEffects = removeIdentity(effects, effect)
                val removedFromPendingAdd = removeIdentity(pendingAdd, effect)
                persistentEffects.remove(effect)
                if ((removedFromEffects || removedFromPendingAdd) && seen.put(effect, true) == null) {
                    removedEffects.add(effect)
                }
            }
            pendingRemove.clear()
            removedEffects
        }
        for (effect in removals) {
            disposeSafely(effect)
            publishRemoved(effect)
        }
    }

    /**
     * 渲染所有效果
     */
    fun render(context: RenderContext) {
        if (effects.isEmpty()) return

        for (effect in effects) {
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
     * 清除当前服务器会话创建的效果，保留 Aim/Collider 等基础渲染器。
     */
    fun clearSessionEffects() {
        val toDispose = synchronized(mutationLock) {
            collectAndRemoveEffects { effect -> effect !in persistentEffects }
        }

        for (effect in toDispose) {
            disposeSafely(effect)
        }
    }

    /**
     * 清除所有效果，包括跨会话保留的基础渲染器。
     */
    fun clear() {
        val toDispose = synchronized(mutationLock) {
            val collected = collectAndRemoveEffects { true }
            persistentEffects.clear()
            collected
        }

        for (effect in toDispose) {
            disposeSafely(effect)
        }
    }

    private fun collectAndRemoveEffects(
        shouldRemove: (RenderableEffect) -> Boolean
    ): List<RenderableEffect> {
        val seen = IdentityHashMap<RenderableEffect, Boolean>()
        val collected = ArrayList<RenderableEffect>(effects.size + pendingAdd.size)

        for (effect in effects) {
            if (shouldRemove(effect) && seen.put(effect, true) == null) {
                collected.add(effect)
            }
        }
        for (effect in pendingAdd) {
            if (shouldRemove(effect) && seen.put(effect, true) == null) {
                collected.add(effect)
            }
        }

        for (effect in collected) {
            removeIdentity(effects, effect)
            removeIdentity(pendingAdd, effect)
            removeIdentity(pendingRemove, effect)
            persistentEffects.remove(effect)
        }
        return collected
    }

    private fun removeIdentity(
        collection: CopyOnWriteArrayList<RenderableEffect>,
        effect: RenderableEffect
    ): Boolean = collection.removeIf { it === effect }

    private fun disposeSafely(effect: RenderableEffect) {
        try {
            effect.dispose()
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error disposing effect ${effect.id}", ex)
        }
    }

    private fun publishRemoved(effect: RenderableEffect) {
        try {
            EventBus.publish(Events.EffectRemoved(effect))
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error publishing removal for effect ${effect.id}", ex)
        }
    }

    /**
     * 获取当前效果数量
     */
    val size: Int get() = effects.size
}

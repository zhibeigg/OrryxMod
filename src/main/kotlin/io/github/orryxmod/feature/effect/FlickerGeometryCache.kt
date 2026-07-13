package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.Entity
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flicker Display List 的客户端渲染线程缓存。
 * 只复用同一玩家、同一模型实例和同一姿态帧的几何，避免改变冻结姿态语义。
 */
internal object FlickerGeometryCache {
    const val DEFAULT_MAX_CACHED_GEOMETRIES = 24
    const val DEFAULT_UNUSED_TTL_MILLIS = 5_000L
    private const val HARD_MAX_CACHED_GEOMETRIES = 64
    private const val HARD_MAX_UNUSED_TTL_MILLIS = 60_000L

    data class Limits(
        val maxCachedGeometries: Int = DEFAULT_MAX_CACHED_GEOMETRIES,
        val unusedTtlMillis: Long = DEFAULT_UNUSED_TTL_MILLIS
    ) {
        internal fun normalized(): Limits = Limits(
            maxCachedGeometries = maxCachedGeometries.coerceIn(1, HARD_MAX_CACHED_GEOMETRIES),
            unusedTtlMillis = unusedTtlMillis.coerceIn(0L, HARD_MAX_UNUSED_TTL_MILLIS)
        )
    }

    internal data class GeometryKey(
        val entityUUID: UUID,
        val modelIdentity: Int,
        val tick: Int,
        val partialTickBits: Int,
        val limbSwingBits: Int,
        val limbSwingAmountBits: Int,
        val bodyYawBits: Int,
        val headYawBits: Int,
        val pitchBits: Int,
        val sneaking: Boolean
    )

    private class CacheEntry(
        val geometry: BakedPlayerGeometry,
        var references: Int,
        var lastUnusedAt: Long
    )

    class Handle internal constructor(
        private val key: GeometryKey,
        private val geometry: BakedPlayerGeometry,
        private val entityUUID: UUID,
        private val snapshot: EntityTrackerRegistry.EntityInfo,
        private val textureId: Int
    ) {
        private val released = AtomicBoolean(false)

        fun render(alpha: Float, scale: Float) {
            if (released.get()) return
            geometry.render(entityUUID, snapshot, textureId, alpha, scale)
        }

        fun release() {
            if (released.compareAndSet(false, true)) {
                releaseKey(key)
            }
        }
    }

    private val entries = LinkedHashMap<GeometryKey, CacheEntry>(16, 0.75f, true)
    private var limits = Limits()

    val currentLimits: Limits
        get() = limits

    internal val cachedGeometryCount: Int
        get() = entries.size

    fun configureLimits(newLimits: Limits) {
        runOnRenderThread {
            limits = newLimits.normalized()
            trim(System.currentTimeMillis(), enforceMaximum = true)
        }
    }

    /** 捕获姿态并获取共享 Display List；世界和 GL 访问只发生在渲染线程。 */
    fun acquire(entityUUID: UUID, textureId: Int): Handle? {
        if (!MC.isCallingFromMinecraftThread) return null

        val player = MC.world?.getPlayerEntityByUUID(entityUUID) ?: return null
        val renderer = MC.renderManager.getEntityRenderObject<Entity>(player) as? RenderPlayer ?: return null
        val snapshot = EntityTrackerRegistry.EntityInfo(player)
        val key = GeometryKey(
            entityUUID = entityUUID,
            modelIdentity = System.identityHashCode(renderer.mainModel),
            tick = snapshot.lastTick,
            partialTickBits = floatBits(MC.renderPartialTicks),
            limbSwingBits = floatBits(snapshot.limbSwing),
            limbSwingAmountBits = floatBits(snapshot.limbSwingAmount),
            bodyYawBits = floatBits(snapshot.renderYawOffset),
            headYawBits = floatBits(snapshot.rotationYawHead),
            pitchBits = floatBits(snapshot.rotationPitch),
            sneaking = snapshot.sneaking
        )

        val now = System.currentTimeMillis()
        entries[key]?.let { cached ->
            cached.references++
            return Handle(key, cached.geometry, entityUUID, snapshot, textureId)
        }

        trim(now, enforceMaximum = false)
        if (!makeRoomForNewGeometry()) return null

        val geometry = BakedPlayerGeometry()
        if (!geometry.bake(player, snapshot, renderer)) return null

        entries[key] = CacheEntry(
            geometry = geometry,
            references = 1,
            lastUnusedAt = now
        )
        return Handle(key, geometry, entityUUID, snapshot, textureId)
    }

    fun trim() {
        runOnRenderThread {
            trim(System.currentTimeMillis(), enforceMaximum = true)
        }
    }

    fun clear() {
        runOnRenderThread {
            entries.values.forEach { it.geometry.dispose() }
            entries.clear()
        }
    }

    private fun releaseKey(key: GeometryKey) {
        runOnRenderThread {
            val entry = entries[key] ?: return@runOnRenderThread
            if (entry.references > 0) {
                entry.references--
            }
            if (entry.references == 0) {
                entry.lastUnusedAt = System.currentTimeMillis()
            }
            trim(System.currentTimeMillis(), enforceMaximum = true)
        }
    }

    private fun trim(now: Long, enforceMaximum: Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            val expired = entry.references == 0 && now - entry.lastUnusedAt >= limits.unusedTtlMillis
            if (expired) {
                entry.geometry.dispose()
                iterator.remove()
            }
        }

        if (!enforceMaximum) return
        while (entries.size > limits.maxCachedGeometries) {
            if (!evictOldestUnused()) return
        }
    }

    private fun makeRoomForNewGeometry(): Boolean {
        while (entries.size >= limits.maxCachedGeometries) {
            if (!evictOldestUnused()) return false
        }
        return true
    }

    private fun evictOldestUnused(): Boolean {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.references == 0) {
                entry.geometry.dispose()
                iterator.remove()
                return true
            }
        }
        return false
    }

    private fun runOnRenderThread(action: () -> Unit) {
        if (MC.isCallingFromMinecraftThread) {
            action()
        } else {
            MC.addScheduledTask(action)
        }
    }

    private fun floatBits(value: Float): Int = java.lang.Float.floatToIntBits(value)
}

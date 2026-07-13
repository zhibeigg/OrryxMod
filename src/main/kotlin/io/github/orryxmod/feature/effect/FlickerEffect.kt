package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.FileManager
import net.minecraftforge.client.event.RenderWorldLastEvent
import java.util.UUID

/**
 * Flicker 效果 - 在捕获位置渲染渐隐的冻结玩家残影。
 * 几何由有界 Display List 缓存共享，效果实例只持有姿态位置快照和缓存引用。
 */
class FlickerEffect(
    val entityUUID: UUID,
    private val timeout: Long,
    private val config: FlickerConfig
) {
    private val startTime = System.currentTimeMillis()
    private val fadeDuration = if (config.duration > 0) config.duration else timeout
    private var geometryHandle: FlickerGeometryCache.Handle? = null
    private var initializationAttempted = false

    val isActive: Boolean
        get() = System.currentTimeMillis() - startTime < timeout

    val currentAlpha: Float
        get() {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= fadeDuration) return 0f
            return (1f - elapsed / fadeDuration.toFloat()) * config.alpha
        }

    val scale: Float
        get() = config.scale

    /**
     * 保留原初始化入口；世界查询和 GL 烘焙延迟到第一次渲染线程回调。
     */
    fun initTracker() = Unit

    fun renderFlicker(@Suppress("UNUSED_PARAMETER") event: RenderWorldLastEvent) {
        val textureId = FileManager.pictures["flicker"] ?: return
        if (!isActive) return

        val alpha = currentAlpha
        if (alpha <= 0f) return

        if (!initializationAttempted) {
            initializationAttempted = true
            geometryHandle = FlickerGeometryCache.acquire(entityUUID, textureId)
        }

        geometryHandle?.render(alpha, scale)
    }

    fun dispose() {
        geometryHandle?.release()
        geometryHandle = null
    }
}

package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.FileManager
import net.minecraftforge.client.event.RenderWorldLastEvent
import java.util.UUID

/**
 * Flicker 效果 - 闪影效果
 * 在玩家当前位置渲染一个渐隐的残影
 *
 * 使用 Display List 烘焙技术：
 * - 在第一次渲染时"录制"一次完整的模型渲染到 Display List
 * - 之后直接回放 Display List，完全绕过 Mo' Bends 等动画模组的 hook
 * - 实现真正的"动画冻结"效果
 */
class FlickerEffect(
    val entityUUID: UUID,
    private val timeout: Long,
    private val config: FlickerConfig
) {
    private val startTime = System.currentTimeMillis()
    private val duration = timeout

    /** 烘焙的玩家几何数据 */
    private var bakedGeometry: BakedPlayerGeometry? = null

    /** 是否已完成烘焙 */
    private var baked = false

    val isActive: Boolean
        get() = System.currentTimeMillis() - startTime < timeout

    /**
     * 当前透明度（线性衰减）
     */
    val currentAlpha: Float
        get() {
            val remaining = timeout - (System.currentTimeMillis() - startTime)
            return (remaining.coerceAtLeast(0L) / duration.toFloat()) * config.alpha
        }

    /**
     * 初始化（创建烘焙对象，但不执行烘焙）
     * 烘焙将在第一次渲染时执行（需要 GL 上下文）
     */
    fun initTracker() {
        bakedGeometry = BakedPlayerGeometry(entityUUID)
    }

    /**
     * 在 RenderWorldLastEvent 中渲染闪影
     * 第一次调用时会执行烘焙，之后直接回放 Display List
     */
    fun renderFlicker(@Suppress("UNUSED_PARAMETER") event: RenderWorldLastEvent) {
        val textureId = FileManager.pictures["flicker"] ?: return
        val geometry = bakedGeometry ?: return

        if (!isActive) return

        val alpha = currentAlpha
        if (alpha <= 0) return

        // 第一次渲染时执行烘焙
        if (!baked) {
            baked = true
            if (!geometry.bake(textureId)) {
                // 烘焙失败，清理资源
                bakedGeometry = null
                return
            }
        }

        // 渲染烘焙的几何数据
        geometry.render(alpha)
    }

    /**
     * 清理资源
     * 效果结束时由 EffectFeature 调用
     */
    fun dispose() {
        bakedGeometry?.dispose()
        bakedGeometry = null
    }
}

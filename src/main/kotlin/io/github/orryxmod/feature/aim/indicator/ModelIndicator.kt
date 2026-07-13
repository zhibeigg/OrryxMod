package io.github.orryxmod.feature.aim.indicator

import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimRenderer
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import kotlin.math.sin

/**
 * 模型指示器 — 在瞄准位置渲染玩家实体模型
 * 支持自定义缩放、透明度，带呼吸灯式透明度动画
 */
class ModelIndicator : AimIndicator {

    private var breathTick = 0
    private val breathSpeed = 60 // tick 周期

    override fun render(context: RenderContext, location: AimRenderer.Location, config: AimConfig, partialTicks: Float) {
        val player = MC.player ?: return
        val rel = context.toRelative(location.x, location.y, location.z)

        // 呼吸灯动画：透明度在 baseAlpha 的 60%~100% 之间波动
        val breathProgress = (breathTick + partialTicks) / breathSpeed
        val breathFactor = 0.8 + 0.2 * sin(breathProgress * Math.PI * 2)
        val alpha = (config.indicatorAlpha * breathFactor).toFloat()

        RenderUtils.withGlState(blend = true) {
            GlStateManager.color(1f, 1f, 1f, alpha)
            RenderUtils.renderEntity(
                entity = player,
                x = rel.x,
                y = rel.y,
                z = rel.z,
                yaw = player.rotationYaw,
                scale = config.modelScale
            )
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    override fun update() {
        breathTick = (breathTick + 1) % breathSpeed
    }
}

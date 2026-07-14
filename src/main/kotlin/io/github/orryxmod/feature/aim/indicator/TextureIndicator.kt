package io.github.orryxmod.feature.aim.indicator

import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimRenderer
import io.github.orryxmod.feature.aim.AimState
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager

/**
 * 纹理指示器 — 底部选择圈 + 浮动箭头
 * 从 AimRenderer 原有逻辑迁移而来，复用 RenderUtils 和 RenderContext.toRelative()
 */
class TextureIndicator : AimIndicator {

    private var animationOffset = 0.0
    private var animationDirection = true

    override fun render(context: RenderContext, location: AimRenderer.Location, config: AimConfig, partialTicks: Float) {
        val module = AimState.currentModule.name.lowercase()

        val selectId = FileManager.pictures["select-$module"]
            ?: FileManager.pictures["select-default"] ?: return
        val arrowId = FileManager.pictures["arrow-$module"]
            ?: FileManager.pictures["arrow-default"] ?: return
        val player = MC.player ?: return

        val rel = context.toRelative(location.x, location.y, location.z)
        val scale = config.scale

        RenderUtils.withGlState(blend = true, depth = false) {
            GlStateManager.translate(rel.x, rel.y, rel.z)
            GlStateManager.rotate(-player.rotationYaw, 0.0f, 1.0f, 0.0f)

            // 底部选择圈（正面）
            FileManager.bindTexture(selectId)
            RenderUtils.drawTexturedQuadHorizontal(0.0, 0.05, 0.0, scale, scale)

            // 底部选择圈（反面）
            FileManager.bindTexture(selectId)
            GlStateManager.rotate(180f, 1.0f, 0.0f, 0.0f)
            RenderUtils.drawTexturedQuadHorizontal(0.0, -0.05, 0.0, scale, scale)

            // 浮动箭头
            FileManager.bindTexture(arrowId)
            GlStateManager.rotate(-180f, 1.0f, 0.0f, 0.0f)
            RenderUtils.drawTexturedQuadVertical(0.0, 0.5 + animationOffset / 2000.0, 0.0, scale / 4, scale / 4)
        }
    }

    override fun update() {
        if (animationDirection) {
            if (animationOffset < 500) animationOffset++ else animationDirection = false
        } else {
            if (animationOffset > 0) animationOffset-- else animationDirection = true
        }
    }
}

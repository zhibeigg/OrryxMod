package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import java.util.UUID

/**
 * Flicker 效果 - 实体闪烁效果
 * 使实体呈现半透明闪烁状态
 */
class FlickerEffect(
    private val entityUUID: UUID,
    private val timeout: Long,
    private val config: FlickerConfig
) : RenderableEffect {

    override val id: String = "flicker_$entityUUID"
    override val renderPriority: Int = 5

    private val startTime = System.currentTimeMillis()
    private var flickerPhase = 0

    override val isActive: Boolean
        get() = System.currentTimeMillis() - startTime < timeout

    override fun update() {
        flickerPhase = (flickerPhase + 1) % 10
    }

    override fun render(context: RenderContext) {
        val entity = findEntity() ?: return

        // 计算闪烁透明度
        val baseAlpha = config.alpha
        val flickerMultiplier = if (flickerPhase < 5) 1.0f else 0.7f
        val alpha = baseAlpha * flickerMultiplier

        // 修改实体渲染透明度（通过 OpenGL 状态）
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        GlStateManager.color(1f, 1f, 1f, alpha)
    }

    private fun findEntity(): EntityLivingBase? {
        return MC.world?.loadedEntityList
            ?.filterIsInstance<EntityLivingBase>()
            ?.find { it.uniqueID == entityUUID }
    }
}

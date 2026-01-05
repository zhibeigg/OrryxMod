package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import java.util.UUID

/**
 * Ghost 效果 - 实体残影效果
 * 在实体移动时留下半透明残影
 */
class GhostEffect(
    private val entityUUID: UUID,
    private val timeout: Long,
    private val config: GhostConfig
) : RenderableEffect {

    override val id: String = "ghost_$entityUUID"
    override val renderPriority: Int = 10

    private val startTime = System.currentTimeMillis()
    private val ghostPositions = mutableListOf<GhostPosition>()
    private var tickCounter = 0

    override val isActive: Boolean
        get() = System.currentTimeMillis() - startTime < timeout

    override fun update() {
        val entity = findEntity() ?: return

        tickCounter++
        if (tickCounter >= config.gap) {
            tickCounter = 0

            // 记录当前位置
            ghostPositions.add(
                GhostPosition(
                    x = entity.posX,
                    y = entity.posY,
                    z = entity.posZ,
                    yaw = entity.rotationYaw,
                    timestamp = System.currentTimeMillis()
                )
            )

            // 限制残影数量
            while (ghostPositions.size > config.density) {
                ghostPositions.removeAt(0)
            }
        }
    }

    override fun render(context: RenderContext) {
        val entity = findEntity() ?: return
        if (ghostPositions.isEmpty()) return

        RenderUtils.withGlState(blend = true, lighting = false) {
            ghostPositions.forEachIndexed { index, pos ->
                val alpha = (index + 1).toFloat() / ghostPositions.size * 0.6f
                GlStateManager.color(1f, 1f, 1f, alpha)

                val relPos = context.toRelative(pos.x, pos.y, pos.z)
                RenderUtils.renderEntity(
                    entity,
                    relPos.x, relPos.y, relPos.z,
                    pos.yaw
                )
            }
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    override fun dispose() {
        ghostPositions.clear()
    }

    private fun findEntity(): EntityLivingBase? {
        return MC.world?.loadedEntityList
            ?.filterIsInstance<EntityLivingBase>()
            ?.find { it.uniqueID == entityUUID }
    }

    private data class GhostPosition(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val timestamp: Long
    )
}

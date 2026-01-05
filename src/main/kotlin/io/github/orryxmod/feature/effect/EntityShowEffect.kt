package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * EntityShow 效果 - 实体影子/分身效果
 * 在指定位置显示实体的静态分身
 */
class EntityShowEffect(
    private val entityUUID: UUID
) : RenderableEffect {

    override val id: String = "entityshow_$entityUUID"
    override val renderPriority: Int = 15

    // 按组管理的影子数据
    private val shadows = ConcurrentHashMap<String, ShadowData>()

    override val isActive: Boolean
        get() = shadows.isNotEmpty()

    /**
     * 添加影子
     */
    fun addShadow(
        group: String,
        position: Vector3d,
        rotation: EntityRotation,
        scale: Float,
        timeout: Long
    ) {
        shadows[group] = ShadowData(
            group = group,
            position = position,
            rotation = rotation,
            scale = scale,
            timeout = timeout
        )
    }

    /**
     * 移除指定组的影子
     */
    fun removeShadow(group: String) {
        shadows.remove(group)
    }

    /**
     * 清除所有影子
     */
    fun clearAllShadows() {
        shadows.clear()
    }

    override fun update() {
        // 移除过期的影子
        shadows.entries.removeIf { it.value.isExpired }
    }

    override fun render(context: RenderContext) {
        val entity = findEntity() ?: return
        if (shadows.isEmpty()) return

        RenderUtils.withGlState(blend = true, lighting = false) {
            shadows.values.forEach { shadow ->
                val relPos = context.toRelative(shadow.position)

                GlStateManager.pushMatrix()
                GlStateManager.translate(relPos.x, relPos.y, relPos.z)

                // 应用旋转
                GlStateManager.rotate(shadow.rotation.y, 0f, 1f, 0f)
                GlStateManager.rotate(shadow.rotation.x, 1f, 0f, 0f)
                GlStateManager.rotate(shadow.rotation.z, 0f, 0f, 1f)

                // 应用缩放
                val scale = shadow.scale
                GlStateManager.scale(scale, scale, scale)

                // 渲染实体
                val renderManager = MC.renderManager
                val prevShadow = renderManager.isRenderShadow
                renderManager.isRenderShadow = false
                renderManager.renderEntity(entity, 0.0, 0.0, 0.0, 0f, 1f, false)
                renderManager.isRenderShadow = prevShadow

                GlStateManager.popMatrix()
            }
        }
    }

    override fun dispose() {
        shadows.clear()
    }

    private fun findEntity(): EntityLivingBase? {
        return MC.world?.loadedEntityList
            ?.filterIsInstance<EntityLivingBase>()
            ?.find { it.uniqueID == entityUUID }
    }

    /**
     * 获取影子数量
     */
    val shadowCount: Int get() = shadows.size
}

package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.joml.Vector3d
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max

/**
 * EntityShow 效果 - 实体投影/分身效果
 * 从老模块 modules/EntityShow.kt 迁移完整渲染逻辑
 */
class EntityShowEffect(
    val entityUUID: UUID
) {
    /**
     * 实体追踪数据
     */
    data class EntityTrack(
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val renderYawOffset: Float,
        val rotationYaw: Float,
        val rotationPitch: Float,
        val rotationYawHead: Float,
        val prevRotationYawHead: Float,
        val vectorX: Vector3f,
        val vectorZ: Vector3f
    ) {
        constructor(ent: EntityLivingBase, x: Double, y: Double, z: Double) : this(
            world = ent.world.worldInfo.worldName,
            x = x,
            y = y,
            z = z,
            renderYawOffset = ent.renderYawOffset,
            rotationYaw = ent.rotationYaw,
            rotationPitch = ent.rotationPitch,
            rotationYawHead = ent.rotationYawHead,
            prevRotationYawHead = ent.prevRotationYawHead,
            vectorX = Vector3f(1.0f, 0.0f, 0.0f).rotateY(ent.rotationYaw),
            vectorZ = Vector3f(0.0f, 0.0f, 1.0f).rotateY(ent.rotationYaw)
        )
    }

    /**
     * 影子数据
     */
    data class Shadow(
        val group: String,
        val track: EntityTrack,
        val timeout: Long,
        val rotateX: Float,
        val rotateY: Float,
        val rotateZ: Float,
        val scale: Float,
        val alpha: Float = 1.0f,
        val startTime: Long = System.currentTimeMillis()
    ) {
        fun isEnabled() = System.currentTimeMillis() - startTime < timeout

        /**
         * 获取当前透明度（可选：支持渐隐效果）
         */
        fun getCurrentAlpha(fadeOut: Boolean = false): Float {
            if (!fadeOut) return alpha
            val remaining = timeout - (System.currentTimeMillis() - startTime)
            val progress = (remaining.toFloat() / timeout).coerceIn(0f, 1f)
            return alpha * progress
        }
    }

    // 按组管理的影子
    private val shadows = ConcurrentHashMap<String, Shadow>()

    val isActive: Boolean
        get() = shadows.isNotEmpty()

    /**
     * 添加影子
     * @param group 影子组名
     * @param position 位置
     * @param rotation 旋转
     * @param scale 缩放
     * @param timeout 持续时间(毫秒)
     * @param alpha 透明度 (0.0-1.0)
     * @param fadeOut 是否启用渐隐效果
     */
    fun addShadow(
        group: String,
        position: Vector3d,
        rotation: EntityRotation,
        scale: Float,
        timeout: Long,
        alpha: Float = 1.0f,
        fadeOut: Boolean = false
    ) {
        val entity = MC.world?.loadedEntityList
            ?.filterIsInstance<EntityLivingBase>()
            ?.find { it.uniqueID == entityUUID }
            ?: return

        shadows[group] = Shadow(
            group = group,
            track = EntityTrack(entity, position.x, position.y, position.z),
            timeout = timeout,
            rotateX = rotation.x,
            rotateY = rotation.y,
            rotateZ = rotation.z,
            scale = scale,
            alpha = alpha
        )
        // 保存 fadeOut 设置
        shadowFadeOut[group] = fadeOut
    }

    // 存储每个影子的 fadeOut 设置
    private val shadowFadeOut = ConcurrentHashMap<String, Boolean>()

    /**
     * 移除指定组的影子
     */
    fun removeShadow(group: String) {
        shadows.remove(group)
        shadowFadeOut.remove(group)
    }

    /**
     * 清除所有影子
     */
    fun clearAllShadows() {
        shadows.clear()
        shadowFadeOut.clear()
    }

    /**
     * 更新状态，移除过期的影子
     */
    fun update() {
        val expiredGroups = shadows.entries.filter { !it.value.isEnabled() }.map { it.key }
        expiredGroups.forEach {
            shadows.remove(it)
            shadowFadeOut.remove(it)
        }
    }

    /**
     * 在 RenderWorldLastEvent 中调用此方法渲染影子
     */
    fun renderShadows(@Suppress("UNUSED_PARAMETER") event: RenderWorldLastEvent) {
        val world = MC.world ?: return
        val entity = world.loadedEntityList
            .filterIsInstance<EntityLivingBase>()
            .find { it.uniqueID == entityUUID }
            ?: return

        // 渲染所有有效的影子
        shadows.values.filter { it.isEnabled() }.forEach { shadow ->
            val fadeOut = shadowFadeOut[shadow.group] ?: false
            doRenderEntityLiving(entity, shadow, fadeOut)
        }
    }

    private fun doRenderEntityLiving(ent: EntityLivingBase, shadow: Shadow, fadeOut: Boolean = false) {
        val currentWorld = MC.world?.worldInfo?.worldName ?: return
        if (shadow.track.world != currentWorld) return

        val renderManager = MC.renderManager
        val alpha = shadow.getCurrentAlpha(fadeOut)

        // 如果透明度为0，跳过渲染
        if (alpha <= 0.001f) return

        val needsBlend = alpha < 1.0f
        val originalYawOffset = ent.renderYawOffset
        val originalYaw = ent.rotationYaw
        val originalPitch = ent.rotationPitch
        val originalPrevYawHead = ent.prevRotationYawHead
        val originalYawHead = ent.rotationYawHead
        val originalPlayerViewY = renderManager.playerViewY
        val originalRenderShadow = renderManager.isRenderShadow

        try {
            RenderUtils.withGlState(
                blend = needsBlend,
                depth = true,
                lighting = GL11.glIsEnabled(GL11.GL_LIGHTING),
                texture = true
            ) {
                GlStateManager.enableColorMaterial()

                val shiftX = shadow.track.x - renderManager.viewerPosX
                val shiftY = shadow.track.y - renderManager.viewerPosY
                val shiftZ = shadow.track.z - renderManager.viewerPosZ
                GlStateManager.translate(shiftX, shiftY, shiftZ)

                val scale = shadow.scale
                GlStateManager.scale(scale, scale, scale)

                val entityScale = calculateEntityScale(ent.renderBoundingBox)
                GlStateManager.scale(entityScale, entityScale, entityScale)

                GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f)
                RenderHelper.enableStandardItemLighting()
                GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f)
                GlStateManager.rotate(shadow.rotateX, shadow.track.vectorX.x, shadow.track.vectorX.y, shadow.track.vectorX.z)
                GlStateManager.rotate(shadow.rotateY, 0.0f, 1.0f, 0.0f)
                GlStateManager.rotate(shadow.rotateZ, shadow.track.vectorZ.x, shadow.track.vectorZ.y, shadow.track.vectorZ.z)

                ent.renderYawOffset = shadow.track.renderYawOffset
                ent.rotationYaw = shadow.track.rotationYaw
                ent.rotationPitch = shadow.track.rotationPitch
                ent.rotationYawHead = shadow.track.rotationYawHead
                ent.prevRotationYawHead = shadow.track.prevRotationYawHead

                renderManager.setPlayerViewY(180.0f)
                renderManager.isRenderShadow = false
                GlStateManager.depthMask(true)
                GlStateManager.color(1f, 1f, 1f, alpha)
                renderManager.renderEntity(ent, 0.0, 0.0, 0.0, 0.0f, 1.0f, false)
            }
        } finally {
            ent.renderYawOffset = originalYawOffset
            ent.rotationYaw = originalYaw
            ent.rotationPitch = originalPitch
            ent.prevRotationYawHead = originalPrevYawHead
            ent.rotationYawHead = originalYawHead
            renderManager.setPlayerViewY(originalPlayerViewY)
            renderManager.isRenderShadow = originalRenderShadow
        }
    }

    private fun calculateEntityScale(aabb: net.minecraft.util.math.AxisAlignedBB): Double {
        val baseX = 0.8
        val baseY = 1.9
        val baseZ = 0.8

        val aabbX = floor((aabb.maxX - aabb.minX) * 1000.0) / 1000.0
        val aabbY = floor((aabb.maxY - aabb.minY) * 1000.0) / 1000.0
        val aabbZ = floor((aabb.maxZ - aabb.minZ) * 1000.0) / 1000.0

        var entityScale = 1.0
        entityScale = max(entityScale, aabbX / baseX)
        entityScale = max(entityScale, aabbY / baseY)
        entityScale = max(entityScale, aabbZ / baseZ)

        return 1.0 / entityScale
    }

    /**
     * 获取影子数量
     */
    val shadowCount: Int get() = shadows.size
}

package io.github.orryxmod.feature.effect

import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.client.event.RenderWorldLastEvent
import org.joml.Vector3d
import org.joml.Vector3f
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
        val startTime: Long = System.currentTimeMillis()
    ) {
        fun isEnabled() = System.currentTimeMillis() - startTime < timeout
    }

    // 按组管理的影子
    private val shadows = ConcurrentHashMap<String, Shadow>()

    val isActive: Boolean
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
            scale = scale
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

    /**
     * 更新状态，移除过期的影子
     */
    fun update() {
        shadows.entries.removeIf { !it.value.isEnabled() }
    }

    /**
     * 在 RenderWorldLastEvent 中调用此方法渲染影子
     */
    fun renderShadows(event: RenderWorldLastEvent) {
        val world = MC.world ?: return
        val entity = world.loadedEntityList
            .filterIsInstance<EntityLivingBase>()
            .find { it.uniqueID == entityUUID }
            ?: return

        // 渲染所有有效的影子
        shadows.values.filter { it.isEnabled() }.forEach { shadow ->
            doRenderEntityLiving(entity, shadow)
        }
    }

    private fun doRenderEntityLiving(ent: EntityLivingBase, shadow: Shadow) {
        val currentWorld = MC.world?.worldInfo?.worldName ?: return
        if (shadow.track.world != currentWorld) return

        val renderManager = MC.renderManager

        GlStateManager.enableColorMaterial()
        GlStateManager.pushMatrix()

        try {
            // 计算渲染位置
            val shiftX = shadow.track.x - renderManager.viewerPosX
            val shiftY = shadow.track.y - renderManager.viewerPosY
            val shiftZ = shadow.track.z - renderManager.viewerPosZ

            GlStateManager.translate(shiftX, shiftY, shiftZ)

            // 缩放
            val scale = shadow.scale
            GlStateManager.scale(scale, scale, scale)

            // 计算实体缩放
            val aabb = ent.renderBoundingBox
            val entityScale = calculateEntityScale(aabb)
            GlStateManager.scale(entityScale, entityScale, entityScale)

            // 保存原始旋转值
            val originalYawOffset = ent.renderYawOffset
            val originalYaw = ent.rotationYaw
            val originalPitch = ent.rotationPitch
            val originalPrevYawHead = ent.prevRotationYawHead
            val originalYawHead = ent.rotationYawHead

            // 设置旋转
            GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f)
            RenderHelper.enableStandardItemLighting()
            GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f)
            GlStateManager.rotate(shadow.rotateX, shadow.track.vectorX.x, shadow.track.vectorX.y, shadow.track.vectorX.z)
            GlStateManager.rotate(shadow.rotateY, 0.0f, 1.0f, 0.0f)
            GlStateManager.rotate(shadow.rotateZ, shadow.track.vectorZ.x, shadow.track.vectorZ.y, shadow.track.vectorZ.z)

            // 应用跟踪的旋转
            ent.renderYawOffset = shadow.track.renderYawOffset
            ent.rotationYaw = shadow.track.rotationYaw
            ent.rotationPitch = shadow.track.rotationPitch
            ent.rotationYawHead = shadow.track.rotationYaw
            ent.prevRotationYawHead = shadow.track.rotationYaw

            // 渲染
            renderManager.setPlayerViewY(180.0f)
            renderManager.isRenderShadow = false
            GlStateManager.depthMask(true)
            GlStateManager.color(1f, 1f, 1f, 1f)
            renderManager.renderEntity(ent, 0.0, 0.0, 0.0, 0.0f, 1.0f, false)
            renderManager.isRenderShadow = true

            // 恢复原始旋转值
            ent.renderYawOffset = originalYawOffset
            ent.rotationYaw = originalYaw
            ent.rotationPitch = originalPitch
            ent.prevRotationYawHead = originalPrevYawHead
            ent.rotationYawHead = originalYawHead

        } finally {
            GlStateManager.popMatrix()

            RenderHelper.disableStandardItemLighting()
            GlStateManager.disableRescaleNormal()
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
            GlStateManager.disableTexture2D()
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
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

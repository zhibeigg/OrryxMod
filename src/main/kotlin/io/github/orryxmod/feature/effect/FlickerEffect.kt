package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.util.MC
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.model.ModelPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.RenderPlayerEvent
import org.lwjgl.opengl.GL11
import java.util.UUID

/**
 * Flicker 效果 - 闪影效果
 * 从老模块 modules/Flicker.kt 迁移完整渲染逻辑
 */
class FlickerEffect(
    val entityUUID: UUID,
    private val timeout: Long,
    private val config: FlickerConfig
) {
    private val startTime = System.currentTimeMillis()
    private val duration = timeout
    private var tracker: EntityDummyPlayer? = null
    private var entityInfo: EntityTrackerRegistry.EntityInfo? = null

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
     * 初始化追踪器（需要在创建效果时调用）
     */
    fun initTracker() {
        val player = MC.world?.getPlayerEntityByUUID(entityUUID) ?: return
        entityInfo = EntityTrackerRegistry.EntityInfo(player)
        tracker = EntityDummyPlayer(entityInfo!!)
    }

    /**
     * 在 RenderPlayerEvent.Post 中调用此方法渲染闪影
     */
    fun renderFlicker(event: RenderPlayerEvent.Post) {
        val textureId = FileManager.pictures["flicker"] ?: return
        val originalPlayer = event.entityPlayer ?: return
        val entInfo = entityInfo ?: return
        val shadowTracker = tracker ?: return

        if (entInfo.tracked !== originalPlayer) return
        if (!isActive) return

        val alpha = currentAlpha
        if (alpha <= 0) return

        // 保存原始渲染状态
        GlStateManager.pushMatrix()
        GlStateManager.pushAttrib()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)

        try {
            // 位置插值计算
            val renderPos = calculateRenderPosition(entInfo, originalPlayer, event.partialRenderTick)

            GlStateManager.pushMatrix()
            GlStateManager.translate(renderPos.x, renderPos.y, renderPos.z)
            GlStateManager.rotate(entInfo.renderYawOffset, 0f, -1f, 0f)

            // 模型缩放调整
            GlStateManager.scale(1.0, -1.0, -1.0)
            GlStateManager.translate(0.0f, -1.62f, 0.0f)

            GlStateManager.color(1f, 1f, 1f, alpha)

            FileManager.bindTexture(textureId)

            // 动画参数准备
            val limbSwing = entInfo.limbSwing
            val limbSwingAmount = entInfo.limbSwingAmount
            val ageInTicks = entInfo.lastTick.toFloat()

            // 渲染模型
            shadowTracker.render(
                originalPlayer,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                entInfo.rotationYawHead - entInfo.renderYawOffset,
                entInfo.rotationPitch,
                0.0625f
            )

            GlStateManager.disableCull()

            // 恢复光照贴图
            var i = 0xF000F0
            var j = i % 0x10000
            var k = i / 0x10000
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j.toFloat() / 1.0f, k.toFloat() / 1.0f)

            i = originalPlayer.brightnessForRender
            j = i % 0x10000
            k = i / 0x10000
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j.toFloat() / 1.0f, k.toFloat() / 1.0f)

            GlStateManager.enableCull()
            GlStateManager.popMatrix()
        } finally {
            // 恢复原始渲染状态
            GlStateManager.shadeModel(GL11.GL_FLAT)
            GlStateManager.disableBlend()
            GlStateManager.popAttrib()
            GlStateManager.popMatrix()
        }
    }

    /**
     * 计算渲染位置（平滑插值）
     */
    private fun calculateRenderPosition(
        entInfo: EntityTrackerRegistry.EntityInfo,
        originalPlayer: EntityPlayer,
        partialTicks: Float
    ): Vec3d {
        val x = entInfo.posX - interpolate(originalPlayer.posX, originalPlayer.lastTickPosX, partialTicks)
        val y = entInfo.posY - interpolate(originalPlayer.posY, originalPlayer.lastTickPosY, partialTicks)
        val z = entInfo.posZ - interpolate(originalPlayer.posZ, originalPlayer.lastTickPosZ, partialTicks)
        return Vec3d(x, y, z)
    }

    private fun interpolate(current: Double, prev: Double, partialTicks: Float): Double {
        return prev + (current - prev) * partialTicks.toDouble()
    }

    /**
     * 假人玩家模型 - 复制真实玩家的模型状态
     */
    class EntityDummyPlayer(val info: EntityTrackerRegistry.EntityInfo) :
        ModelPlayer(0.0F, isSlimModel(info.tracked)) {

        init {
            val biped = (MC.renderManager.getEntityRenderObject<Entity>(info.tracked) as RenderPlayer).mainModel
            copyModelAngles(biped.bipedHead, bipedHead)
            copyModelAngles(biped.bipedBody, bipedBody)
            copyModelAngles(biped.bipedLeftArm, bipedLeftArm)
            copyModelAngles(biped.bipedRightArm, bipedRightArm)
            copyModelAngles(biped.bipedLeftLeg, bipedLeftLeg)
            copyModelAngles(biped.bipedRightLeg, bipedRightLeg)
            setModelAttributes(biped)
            leftArmPose = biped.leftArmPose
            rightArmPose = biped.rightArmPose
            isSneak = biped.isSneak
        }

        companion object {
            /**
             * 判断是否为Alex模型
             */
            fun isSlimModel(player: EntityPlayer): Boolean {
                return (player is AbstractClientPlayer) && (player.skinType == "slim")
            }
        }
    }
}

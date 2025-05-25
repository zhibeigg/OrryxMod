package io.github.orryxmod.modules

import io.github.orryxmod.api.Module
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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import java.util.*

object Flicker : Module("Flicker", description = "闪影") {

    // 最大残影数量限制（性能优化）
    private const val MAX_FLICKERS = 20
    private val flickerList = mutableListOf<Shadow>()

    // 纹理加载（建议使用动态路径）
    private val textureId: Int?
        get() = FileManager.pictures["flicker"]

    // 残影数据类
    private class Shadow(
        val tracker: EntityDummyPlayer,
        val timeout: Long,
        var alpha: Float,
        val duration: Long,
    ) {
        fun isEnabled() = System.currentTimeMillis() < timeout

        // 自动更新透明度（线性衰减）
        fun updateAlpha() {
            alpha = (timeout - System.currentTimeMillis()).coerceAtLeast(0L) / duration.toFloat()
        }
    }

    // 添加残影效果（带数量限制）
    fun applyFlickerEffect(uuid: UUID, duration: Long, initialAlpha: Float) {
        flickerList.removeIf { !it.isEnabled() }
        if (flickerList.size >= MAX_FLICKERS) {
            flickerList.removeFirst()
        }

        MC.world?.getPlayerEntityByUUID(uuid)?.let { player ->
            flickerList.add(
                Shadow(
                    tracker = EntityDummyPlayer(EntityTrackerRegistry.EntityInfo(player)),
                    timeout = System.currentTimeMillis() + duration,
                    alpha = initialAlpha,
                    duration = duration
                )
            )
        }
    }

    @SubscribeEvent
    fun renderPlayerGhost(event: RenderPlayerEvent.Post) {
        val player = event.entityPlayer ?: return

        // 保存原始渲染状态
        GlStateManager.pushMatrix()
        GlStateManager.pushAttrib()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)

        try {
            val iterator = flickerList.iterator()
            while (iterator.hasNext()) {
                val shadow = iterator.next().apply { updateAlpha() }

                if (!shadow.isEnabled()) {
                    iterator.remove()
                    continue
                }

                renderSingleShadow(shadow, player, event.partialRenderTick)
            }
        } finally {
            // 恢复原始渲染状态
            GlStateManager.shadeModel(GL11.GL_FLAT)
            GlStateManager.disableBlend()
            GlStateManager.popAttrib()
            GlStateManager.popMatrix()
        }
    }

    private fun renderSingleShadow(shadow: Shadow, originalPlayer: EntityPlayer, partialTicks: Float) {
        textureId ?: return
        val entInfo = shadow.tracker.info
        if (entInfo.tracked !== originalPlayer) return

        // 位置插值计算
        val renderPos = calculateRenderPosition(entInfo, originalPlayer, partialTicks)

        GlStateManager.pushMatrix()
        GlStateManager.translate(renderPos.x, renderPos.y, renderPos.z)
        GlStateManager.rotate(entInfo.renderYawOffset, 0f, -1f, 0f)

        // 模型缩放调整
        GlStateManager.scale(1.0, -1.0, -1.0)
        GlStateManager.translate(0.0f, -1.62f, 0.0f)

        GlStateManager.color(1f, 1f, 1f, shadow.alpha)

        FileManager.bindTexture(textureId!!)

        // 动画参数准备
        val limbSwing = entInfo.limbSwing
        val limbSwingAmount = entInfo.limbSwingAmount
        val ageInTicks = entInfo.lastTick.toFloat()

        // 渲染模型
//        shadow.tracker.render(
//            originalPlayer,
//            limbSwing,
//            limbSwingAmount,
//            ageInTicks,
//            entInfo.rotationYawHead - entInfo.renderYawOffset,
//            entInfo.rotationPitch,
//            0.0625f
//        )
        shadow.tracker.render(originalPlayer, limbSwing, limbSwingAmount, ageInTicks, entInfo.rotationYawHead - entInfo.renderYawOffset, entInfo.rotationPitch, 0.0625f)

        GlStateManager.disableCull()

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
    }

    // 计算渲染位置（平滑插值）
    private fun calculateRenderPosition(
        entInfo: EntityTrackerRegistry.EntityInfo,
        originalPlayer: EntityPlayer,
        partialTicks: Float,
    ): Vec3d {
        val x = entInfo.posX - interpolate(originalPlayer.posX, originalPlayer.lastTickPosX, partialTicks)
        val y = entInfo.posY - interpolate(originalPlayer.posY, originalPlayer.lastTickPosY, partialTicks)
        val z = entInfo.posZ - interpolate(originalPlayer.posZ, originalPlayer.lastTickPosZ, partialTicks)
        return Vec3d(x, y, z)
    }

    private fun interpolate(current: Double, prev: Double, partialTicks: Float): Double {
        return prev + (current - prev) * partialTicks.toDouble()
    }

    class EntityDummyPlayer(val info: EntityTrackerRegistry.EntityInfo) : ModelPlayer(0.0F, isSlimModel(info.tracked)) {

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
    }

    // 判断是否为Alex模型
    private fun isSlimModel(player: EntityPlayer): Boolean {
        return (player is AbstractClientPlayer) && (player.skinType == "slim")
    }
}
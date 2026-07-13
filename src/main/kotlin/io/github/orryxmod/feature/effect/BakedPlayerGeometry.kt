package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.util.MC
import net.minecraft.client.model.ModelPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11
import java.util.UUID

/**
 * 可复用的玩家烘焙几何。Display List 只保存模型几何和姿态，
 * 效果自己的位置、纹理、透明度与缩放在回放时单独应用。
 */
internal class BakedPlayerGeometry {
    private var displayListId: Int = -1

    val isBaked: Boolean
        get() = displayListId != -1

    /** 必须在 Minecraft 客户端渲染线程调用。 */
    fun bake(
        player: EntityPlayer,
        snapshot: EntityTrackerRegistry.EntityInfo,
        renderer: RenderPlayer
    ): Boolean {
        if (!MC.isCallingFromMinecraftThread || isBaked) return isBaked

        val generatedList = GL11.glGenLists(1)
        if (generatedList == 0) return false

        displayListId = generatedList
        GL11.glNewList(displayListId, GL11.GL_COMPILE)

        var success = false
        try {
            bakeModelRender(player, snapshot, renderer.mainModel)
            success = true
        } catch (_: Exception) {
            success = false
        } finally {
            GL11.glEndList()
            if (!success) {
                GL11.glDeleteLists(displayListId, 1)
                displayListId = -1
            }
        }

        return success
    }

    private fun bakeModelRender(
        player: EntityPlayer,
        snapshot: EntityTrackerRegistry.EntityInfo,
        model: ModelPlayer
    ) {
        val scale = 0.0625f
        val limbSwing = snapshot.limbSwing - snapshot.limbSwingAmount
        val limbSwingAmount = snapshot.limbSwingAmount.coerceAtMost(1.0f)
        val ageInTicks = player.ticksExisted.toFloat() + MC.renderPartialTicks
        val headYaw = snapshot.rotationYawHead - snapshot.renderYawOffset
        val previousSneak = model.isSneak

        try {
            model.isSneak = snapshot.sneaking
            model.setRotationAngles(
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                headYaw,
                snapshot.rotationPitch,
                scale,
                player
            )

            model.bipedHead.render(scale)
            model.bipedHeadwear.render(scale)
            model.bipedBody.render(scale)
            model.bipedRightArm.render(scale)
            model.bipedLeftArm.render(scale)
            model.bipedRightLeg.render(scale)
            model.bipedLeftLeg.render(scale)
            model.bipedBodyWear.render(scale)
            model.bipedRightArmwear.render(scale)
            model.bipedLeftArmwear.render(scale)
            model.bipedRightLegwear.render(scale)
            model.bipedLeftLegwear.render(scale)
        } finally {
            model.isSneak = previousSneak
        }
    }

    /** 必须在 Minecraft 客户端渲染线程调用。 */
    fun render(
        entityUUID: UUID,
        snapshot: EntityTrackerRegistry.EntityInfo,
        textureId: Int,
        alpha: Float,
        scale: Float = 1.0f
    ) {
        if (!MC.isCallingFromMinecraftThread || !isBaked) return
        val player = MC.world?.getPlayerEntityByUUID(entityUUID) ?: return
        if (player.isInvisible) return
        if (player === MC.player && MC.gameSettings.thirdPersonView == 0) return

        GlStateManager.bindTexture(textureId)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)
        GlStateManager.disableCull()
        GlStateManager.disableLighting()
        GlStateManager.pushMatrix()

        try {
            val renderManager = MC.renderManager
            GlStateManager.translate(
                snapshot.posX - renderManager.viewerPosX,
                snapshot.posY - renderManager.viewerPosY,
                snapshot.posZ - renderManager.viewerPosZ
            )
            GlStateManager.rotate(180f - snapshot.renderYawOffset, 0f, 1f, 0f)
            GlStateManager.scale(-scale, -scale, scale)
            GlStateManager.translate(0.0f, -1.3f, 0.0f)
            GlStateManager.color(1f, 1f, 1f, alpha)
            GL11.glCallList(displayListId)
        } finally {
            GlStateManager.popMatrix()
            GlStateManager.enableLighting()
            GlStateManager.enableCull()
            GlStateManager.shadeModel(GL11.GL_FLAT)
            GlStateManager.disableBlend()
        }
    }

    /** 必须在 Minecraft 客户端渲染线程调用。 */
    fun dispose() {
        if (!MC.isCallingFromMinecraftThread) return
        if (displayListId != -1) {
            GL11.glDeleteLists(displayListId, 1)
            displayListId = -1
        }
    }
}

package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.RenderPlayerEvent
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.util.UUID
import kotlin.math.acos
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Ghost 效果 - 实体残影效果
 * 从老模块 modules/Ghost.kt 迁移完整渲染逻辑
 */
class GhostEffect(
    val entityUUID: UUID,
    private val timeout: Long,
    private val config: GhostConfig
) {
    companion object {
        private const val MAX_TRACKER = EntityTrackerRegistry.DEFAULT_MAX_SAMPLES_PER_ENTITY
    }

    private val startTime = System.currentTimeMillis()
    private var trackerEntry: EntityTrackerRegistry.Entry? = null

    val isActive: Boolean
        get() = System.currentTimeMillis() - startTime < timeout

    /**
     * 在 RenderPlayerEvent.Post 中调用此方法渲染残影
     */
    fun renderGhost(event: RenderPlayerEvent.Post) {
        val textureId = FileManager.pictures["ghost"] ?: return
        val player = event.entityPlayer ?: return

        if (player.uniqueID != entityUUID) return
        if (!isActive) return
        if (player.isInvisible) return
        if (player === MC.player && MC.gameSettings.thirdPersonView == 0) return

        val density = config.density.coerceIn(0, MAX_TRACKER)
        val gap = config.gap.coerceIn(0, MAX_TRACKER)
        val sampleStep = gap + 1
        val requestedSamples = (density.toLong() * sampleStep + 2L)
            .coerceAtMost(MAX_TRACKER.toLong())
            .toInt()

        val entry = EntityTrackerRegistry.getOrCreateEntry(player, requestedSamples.coerceAtLeast(1))
        trackerEntry = entry
        val loc = entry.trackedInfo
        if (density == 0 || loc.size <= 1) return

        val availableDensity = ((loc.size - 2) / sampleStep).coerceAtLeast(0)
        val renderDensity = density.coerceAtMost(availableDensity)
        if (renderDensity == 0) return

        val renderer = MC.renderManager.getEntityRenderObject<Entity>(player) as? RenderPlayer ?: return
        val biped = renderer.mainModel
        val previousIgnoreFrustumCheck = player.ignoreFrustumCheck

        try {
            player.ignoreFrustumCheck = true
            RenderUtils.withGlState(
                blend = true,
                depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                lighting = GL11.glIsEnabled(GL11.GL_LIGHTING),
                texture = true
            ) {
                GlStateManager.shadeModel(GL11.GL_SMOOTH)
                GL13.glActiveTexture(OpenGlHelper.defaultTexUnit)
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
                GlStateManager.disableCull()
                GlStateManager.translate(event.x, event.y, event.z)

                val tX = player.prevPosX + (player.posX - player.prevPosX) * event.partialRenderTick
                val tY = player.prevPosY + (player.posY - player.prevPosY) * event.partialRenderTick
                val tZ = player.prevPosZ + (player.posZ - player.prevPosZ) * event.partialRenderTick

                var sampleOffset = sampleStep
                repeat(renderDensity) {
                    val i = sampleOffset
                    sampleOffset += sampleStep
                    val entInfo = loc[loc.lastIndex - i]

                    GlStateManager.pushMatrix()
                    try {
                        GlStateManager.translate(entInfo.posX - tX, entInfo.posY - tY, entInfo.posZ - tZ)
                        GlStateManager.rotate(entInfo.renderYawOffset, 0.0f, -1.0f, 0.0f)

                        if (entInfo.elytraFlying) {
                            val f = player.ticksElytraFlying.toFloat() + event.partialRenderTick
                            val f1 = MathHelper.clamp(f * f / 100.0f, 0.0f, 1.0f)
                            GlStateManager.rotate(f1 * (-90.0f - player.rotationPitch), -1.0f, 0.0f, 0.0f)
                            val vec3d = player.getLook(event.partialRenderTick)
                            val d0 = player.motionX * player.motionX + player.motionZ * player.motionZ
                            val d1 = vec3d.x * vec3d.x + vec3d.z * vec3d.z

                            if (d0 > 0.0 && d1 > 0.0) {
                                val d2 = ((player.motionX * vec3d.x + player.motionZ * vec3d.z) /
                                    (sqrt(d0) * sqrt(d1))).coerceIn(-1.0, 1.0)
                                val d3 = player.motionX * vec3d.z - player.motionZ * vec3d.x
                                GlStateManager.rotate(
                                    (sign(d3) * acos(d2)).toFloat() * 180.0f / Math.PI.toFloat(),
                                    0.0f,
                                    1.0f,
                                    0.0f
                                )
                            }
                        }

                        val distanceX = entInfo.posX - tX
                        val distanceZ = entInfo.posZ - tZ
                        val distance = sqrt(distanceX * distanceX + distanceZ * distanceZ)
                        val scale = MathHelper.clamp(100 - distance / 100, 0.0, 0.9375)
                        GlStateManager.scale(scale, -scale, -scale)
                        GlStateManager.translate(0.0f, -1.5f, 0.0f)

                        val alpha = MathHelper.clamp(
                            1 - (i + event.partialRenderTick) / (density * (gap + 1)),
                            0.2f,
                            1.0f
                        )
                        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha)
                        FileManager.bindTexture(textureId)

                        val bodyYaw = entInfo.renderYawOffset
                        val headYaw = entInfo.rotationYawHead
                        val limbSwingAmount = entInfo.limbSwingAmount.coerceAtMost(1.0f)
                        val limbSwing = entInfo.limbSwing - entInfo.limbSwingAmount
                        val age = entInfo.lastTick.toFloat() - i + event.partialRenderTick

                        biped.render(
                            player,
                            limbSwing,
                            limbSwingAmount,
                            age,
                            headYaw - bodyYaw,
                            entInfo.rotationPitch,
                            0.0625f
                        )
                    } finally {
                        GlStateManager.popMatrix()
                    }
                }
            }
        } finally {
            player.ignoreFrustumCheck = previousIgnoreFrustumCheck
        }
    }

    fun dispose() {
        EntityTrackerRegistry.remove(trackerEntry)
        trackerEntry = null
    }
}

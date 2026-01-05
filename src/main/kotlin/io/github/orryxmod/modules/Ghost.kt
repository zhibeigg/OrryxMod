package io.github.orryxmod.modules

import io.github.orryxmod.api.Module
import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.Entity
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.opengl.GL11
import java.util.*
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt


object Ghost : Module("Ghost", description = "鬼影") {

    private val id: Int?
        get() = FileManager.pictures["ghost"]

    private const val MAX_TRACKER = 30

    private val infoMap = mutableMapOf<UUID, Info>()

    class Info(val player: UUID, val timeout: Long, val density: Int, val gap: Int) {

        fun isEnabled(): Boolean {
            return System.currentTimeMillis() < timeout
        }
    }

    fun applyGhostEffect(player: UUID, duration: Long, density: Int, gap: Int) {
        infoMap.filterValues { !it.isEnabled() }.forEach { (k, _) -> infoMap.remove(k) }
        infoMap[player] = Info(player, duration + System.currentTimeMillis(), density, gap)
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END && MC.world != null && !MC.isGamePaused) {
            EntityTrackerRegistry.tick()
        }
    }

    @SubscribeEvent
    fun renderPlayerGhost(event: RenderPlayerEvent.Post) {
        id ?: return
        val player = event.entityPlayer ?: return
        val info = infoMap[player.uniqueID] ?: return

        val loc = EntityTrackerRegistry.getOrCreateEntry(player, MAX_TRACKER).trackedInfo

        if (!info.isEnabled()) {
            infoMap.remove(player.uniqueID)
            return
        }
        if (player.isInvisible) return
        if (player === MC.player && MC.gameSettings.thirdPersonView == 0) return

        val start = 1
        if (loc.size > start) {
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GlStateManager.shadeModel(GL11.GL_SMOOTH)

            player.ignoreFrustumCheck = true

            GlStateManager.pushMatrix()
            GlStateManager.translate(event.x, event.y, event.z)

            val tX = player.prevPosX + (player.posX - player.prevPosX) * event.partialRenderTick
            val tY = player.prevPosY + (player.posY - player.prevPosY) * event.partialRenderTick
            val tZ = player.prevPosZ + (player.posZ - player.prevPosZ) * event.partialRenderTick

            if (loc.size > info.density * ( info.gap + 1 ) + 1) {
                GlStateManager.enableCull()

                val biped = (MC.renderManager.getEntityRenderObject<Entity>(player) as RenderPlayer).mainModel

                var index = info.gap + 1
                for (i in (info.gap + 1) .. (info.density * ( info.gap + 1 ))) {
                    if (index != i) continue
                    index += (info.gap + 1)
                    val entInfo = loc[loc.lastIndex - i]
                    GlStateManager.pushMatrix()

                    GlStateManager.translate(entInfo.posX - tX, entInfo.posY - tY, entInfo.posZ - tZ)
                    GlStateManager.rotate(entInfo.renderYawOffset, 0.0f, -1.0f, 0.0f)

                    //elytra rotation
                    if (entInfo.elytraFlying) {
                        val f = player.ticksElytraFlying.toFloat() + event.partialRenderTick
                        val f1 = MathHelper.clamp(f * f / 100.0f, 0.0f, 1.0f)
                        GlStateManager.rotate(f1 * (-90.0f - player.rotationPitch), -1.0f, 0.0f, 0.0f)
                        val vec3d = player.getLook(event.partialRenderTick)
                        val d0 = player.motionX * player.motionX + player.motionZ * player.motionZ
                        val d1 = vec3d.x * vec3d.x + vec3d.z * vec3d.z

                        if (d0 > 0.0 && d1 > 0.0) {
                            val d2 =
                                (player.motionX * vec3d.x + player.motionZ * vec3d.z) / (sqrt(d0) * sqrt(d1))
                            val d3 = player.motionX * vec3d.z - player.motionZ * vec3d.x
                            GlStateManager.rotate(
                                (sign(d3) * acos(d2)).toFloat() * 180.0f / Math.PI.toFloat(),
                                0.0f,
                                1.0f,
                                0.0f
                            )
                        }
                    }
                    //end elytra rotation
                    val distance = sqrt((entInfo.posX - tX).pow(2) + (entInfo.posZ - tZ).pow(2))
                    val scale = MathHelper.clamp(
                        100 - distance / 100,
                        0.0,
                        0.9375
                    )
                    GlStateManager.scale(scale, -scale, -scale)
                    GlStateManager.translate(0.0f, -1.5f, 0.0f)
                    val alpha = MathHelper.clamp(
                        1 - (i + event.partialRenderTick) / (info.density * ( info.gap + 1 )),
                        0.2f,
                        1.0f
                    )

                    GlStateManager.color(1.0f, 1.0f, 1.0f, alpha)

                    FileManager.bindTexture(id!!)

                    val f2 = entInfo.renderYawOffset
                    val f3 = entInfo.rotationYawHead

                    var f7 = entInfo.limbSwingAmount

                    val f8 = entInfo.limbSwing - entInfo.limbSwingAmount

                    if (f7 > 1.0f) {
                        f7 = 1.0f
                    }

                    val f4 = entInfo.lastTick.toFloat() - i + event.partialRenderTick

                    val f5 = entInfo.rotationPitch

                    biped.render(player, f8, f7, f4, f3 - f2, f5, 0.0625f)

                    GlStateManager.popMatrix()
                }
            }
            GlStateManager.disableCull()

            var i = 0xF000F0
            var j = i % 0x10000
            var k = i / 0x10000
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j.toFloat() / 1.0f, k.toFloat() / 1.0f)

            i = player.brightnessForRender
            j = i % 0x10000
            k = i / 0x10000
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j.toFloat() / 1.0f, k.toFloat() / 1.0f)

            GlStateManager.enableCull()

            GlStateManager.popMatrix()

            GlStateManager.shadeModel(GL11.GL_FLAT)
            GlStateManager.disableBlend()
        }
    }

    override fun test() {
        applyGhostEffect(MC.player.uniqueID, 1000, 5, 0)
    }
}
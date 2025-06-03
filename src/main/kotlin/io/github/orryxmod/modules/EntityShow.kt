package io.github.orryxmod.modules

import io.github.orryxmod.api.Module
import io.github.orryxmod.modules.Aim.enable
import io.github.orryxmod.util.MC
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.joml.Vector3f
import java.util.*
import kotlin.math.floor
import kotlin.math.max

object EntityShow : Module("Show", description = "投影") {

    class EntityTrack(ent: EntityLivingBase, val x: Double, val y: Double, val z: Double) {
        val world: String = ent.world.worldInfo.worldName
        
        val renderYawOffset = ent.renderYawOffset
        val rotationYaw = ent.rotationYaw
        val rotationPitch = ent.rotationPitch
        val rotationYawHead = ent.rotationYawHead
        val prevRotationYawHead = ent.prevRotationYawHead

        val vectorX = Vector3f(1.0f, 0.0f, 0.0f).rotateY(rotationYaw)
        val vectorZ = Vector3f(0.0f, 0.0f, 1.0f).rotateY(rotationYaw)
    }

    // 残影数据类
    class Shadow(
        val group: String,
        val track: EntityTrack,
        val timeout: Long,
        var rotateX: Float,
        var rotateY: Float,
        var rotateZ: Float,
        val scale: Float
    ) {
        fun isEnabled() = System.currentTimeMillis() < timeout
    }

    private val shadowList = mutableMapOf<UUID, MutableList<Shadow>>()

    fun addShadow(uuid: UUID, group: String, x: Double, y: Double, z: Double, timeout: Long, rotateX: Float, rotateY: Float, rotateZ: Float, scale: Float) {
        val entityLivingBase = MC.world.getPlayerEntityByUUID(uuid) ?: return
        shadowList.getOrPut(entityLivingBase.uniqueID) { mutableListOf() } += Shadow(group, EntityTrack(entityLivingBase, x, y, z), System.currentTimeMillis() + timeout, rotateX, rotateY, rotateZ, scale)
    }

    fun removeShadow(uuid: UUID, group: String) {
        shadowList[uuid]?.removeIf { it.group == group }
    }

    @SubscribeEvent
    fun render(e: RenderWorldLastEvent) {
        shadowList.forEach {
            val iterator = it.value.iterator()
            while (iterator.hasNext()) {
                val shadow = iterator.next()
                if (shadow.isEnabled()) {
                    doRenderEntityLiving(MC.world.getPlayerEntityByUUID(it.key) ?: return@forEach, shadow)
                } else {
                    iterator.remove()
                }
            }
        }
        val iterator = shadowList.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun doRenderEntityLiving(ent: EntityLivingBase, shadow: Shadow) {
        if (shadow.track.world != MC.world.worldInfo.worldName) return
        val renderManager = MC.renderManager
        GlStateManager.enableColorMaterial()
        GlStateManager.pushMatrix()
        val shiftX: Double = shadow.track.x - renderManager.viewerPosX
        val shiftY: Double = shadow.track.y - renderManager.viewerPosY
        val shiftZ: Double = shadow.track.z - renderManager.viewerPosZ

        GlStateManager.translate(shiftX, shiftY, shiftZ)

        val scale: Float = shadow.scale

        GlStateManager.scale(scale, scale, scale)
        val aabb = ent.renderBoundingBox
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
        entityScale = 1.0 / entityScale
        GlStateManager.scale(entityScale, entityScale, entityScale)

        val f = ent.renderYawOffset
        val f1 = ent.rotationYaw
        val f2 = ent.rotationPitch
        val f3 = ent.prevRotationYawHead
        val f4 = ent.rotationYawHead
        GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f)
        RenderHelper.enableStandardItemLighting()
        GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(shadow.rotateX, shadow.track.vectorX.x, shadow.track.vectorX.y, shadow.track.vectorX.z)
        GlStateManager.rotate(shadow.rotateY, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(shadow.rotateZ, shadow.track.vectorZ.x, shadow.track.vectorZ.y, shadow.track.vectorZ.z)
        ent.renderYawOffset = shadow.track.renderYawOffset
        ent.rotationYaw = shadow.track.rotationYaw
        ent.rotationPitch = shadow.track.rotationPitch
        ent.rotationYawHead = shadow.track.rotationYaw
        ent.prevRotationYawHead = shadow.track.rotationYaw
        GlStateManager.translate(0.0f, 0.0f, 0.0f)

        renderManager.setPlayerViewY(180.0f)
        renderManager.isRenderShadow = false
        GlStateManager.depthMask(true)
        GlStateManager.color(1f, 1f, 1f, 1f)
        renderManager.renderEntity(ent, 0.0, 0.0, 0.0, 0.0f, 1.0f, false)
        renderManager.isRenderShadow = true
        ent.renderYawOffset = f
        ent.rotationYaw = f1
        ent.rotationPitch = f2
        ent.prevRotationYawHead = f3
        ent.rotationYawHead = f4

        GlStateManager.popMatrix()

        RenderHelper.disableStandardItemLighting()
        GlStateManager.disableRescaleNormal()
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
        GlStateManager.disableTexture2D()
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
    }

    override fun test() {
        addShadow(MC.player.uniqueID, "test", MC.player.posX, MC.player.posY, MC.player.posZ, 1000, 0f, 0f, 0f, 1f)
    }
}
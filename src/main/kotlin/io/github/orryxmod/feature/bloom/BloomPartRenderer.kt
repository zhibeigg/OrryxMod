package io.github.orryxmod.feature.bloom

import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderLivingBase
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Bloom 部件渲染器
 * 检测并渲染名称包含 "bloom" 关键词的模型部件
 */
@SideOnly(Side.CLIENT)
object BloomPartRenderer {

    /**
     * 渲染实体的发光部件
     * 每个骨骼使用其名称匹配的 BloomConfig 进行渲染
     */
    fun renderBloomParts(entity: EntityLivingBase, renderer: RenderLivingBase<EntityLivingBase>) {
        val model = renderer.mainModel ?: return

        // 获取发光部件及其匹配的配置
        val bloomPartsWithConfig = BloomModelDetector.filterBloomPartsWithConfig(model)
        if (bloomPartsWithConfig.isEmpty()) return

        // 按配置分组骨骼
        val groupedByConfig = bloomPartsWithConfig.groupBy { it.second }

        // 为每个配置组注册单独地渲染回调
        for ((config, partsWithConfig) in groupedByConfig) {
            val parts = partsWithConfig.map { it.first }
            BloomFeature.registerGlowRender({ partialTicks ->
                val rm = net.minecraft.client.Minecraft.getMinecraft().renderManager
                val rx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX
                val ry = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY
                val rz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ
                renderEntityBloomParts(entity, model, parts, rx, ry, rz, partialTicks)
            }, config)
        }
    }

    private fun renderEntityBloomParts(
        entity: EntityLivingBase,
        model: ModelBase,
        bloomParts: List<ModelRenderer>,
        x: Double,
        y: Double,
        z: Double,
        partialTicks: Float
    ) {
        GlStateManager.pushMatrix()
        GlStateManager.translate(x, y, z)

        // 应用实体旋转
        GlStateManager.rotate(180f - entity.renderYawOffset, 0f, 1f, 0f)
        GlStateManager.scale(-1f, -1f, 1f)
        GlStateManager.translate(0f, -1.501f, 0f)

        // 设置模型动画参数
        val limbSwing = entity.limbSwing - entity.limbSwingAmount * (1f - partialTicks)
        val limbSwingAmount = entity.prevLimbSwingAmount + (entity.limbSwingAmount - entity.prevLimbSwingAmount) * partialTicks
        val ageInTicks = entity.ticksExisted + partialTicks
        val headYaw = entity.prevRotationYawHead + (entity.rotationYawHead - entity.prevRotationYawHead) * partialTicks - entity.renderYawOffset
        val headPitch = entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks

        model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, 0.0625f, entity)

        // 渲染发光部件
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()

        bloomParts.forEach { part ->
            part.render(0.0625f)
        }

        GlStateManager.enableLighting()
        GlStateManager.enableTexture2D()

        GlStateManager.popMatrix()
    }
}

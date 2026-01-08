package io.github.orryxmod.feature.bloom

import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * 发光部件渲染器
 * 检测并渲染名称包含 "glow" 关键词的模型部件
 */
@SideOnly(Side.CLIENT)
object GlowRenderer {

    /**
     * 渲染实体的发光部件
     */
    fun renderGlowParts(event: RenderLivingEvent.Post<*>) {
        val entity = event.entity

        // 若该实体已被 BloomConfig 匹配并会在 WorldLast 走“整实体配置泛光”流程，
        // 这里再渲染 glow 部件会造成同一实体叠加两次泛光，表现为某个实体亮度显著更高。
        val customName = entity.customNameTag
        val baseName = if (!customName.isNullOrEmpty()) customName else entity.name ?: ""
        val displayName = entity.displayName.unformattedText
        val formattedName = entity.displayName.formattedText
        if (BloomConfigManager.findConfig(baseName) != null ||
            BloomConfigManager.findConfig(displayName) != null ||
            BloomConfigManager.findConfig(formattedName) != null
        ) {
            return
        }

        val renderer = event.renderer
        val model = renderer.mainModel ?: return

        // 获取发光部件
        val glowParts = GlowModelDetector.filterGlowPartsRecursive(model)
        if (glowParts.isEmpty()) return

        // 注册到 BloomFeature 进行泛光渲染
        BloomFeature.registerGlowRender {
            renderEntityGlowParts(entity, model, glowParts, event.x, event.y, event.z, event.partialRenderTick)
        }
    }

    private fun renderEntityGlowParts(
        entity: EntityLivingBase,
        model: ModelBase,
        glowParts: List<ModelRenderer>,
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

        glowParts.forEach { part ->
            part.render(0.0625f)
        }

        GlStateManager.enableLighting()
        GlStateManager.enableTexture2D()

        GlStateManager.popMatrix()
    }
}

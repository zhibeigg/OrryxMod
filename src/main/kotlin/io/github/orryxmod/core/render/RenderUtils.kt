package io.github.orryxmod.core.render

import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11

/**
 * 渲染工具函数
 */
object RenderUtils {

    /**
     * 安全的 GL 状态管理
     * 自动保存和恢复 GL 状态
     */
    inline fun withGlState(
        blend: Boolean = false,
        depth: Boolean = true,
        lighting: Boolean = false,
        texture: Boolean = true,
        block: () -> Unit
    ) {
        GlStateManager.pushMatrix()

        if (blend) {
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        }
        if (!depth) GlStateManager.disableDepth()
        if (!lighting) GlStateManager.disableLighting()
        if (!texture) GlStateManager.disableTexture2D()

        try {
            block()
        } finally {
            if (!texture) GlStateManager.enableTexture2D()
            if (!lighting) GlStateManager.enableLighting()
            if (!depth) GlStateManager.enableDepth()
            if (blend) GlStateManager.disableBlend()

            GlStateManager.popMatrix()
        }
    }

    /**
     * 绘制纹理四边形（水平面）
     */
    fun drawTexturedQuadHorizontal(
        x: Double, y: Double, z: Double,
        width: Double, height: Double,
        u0: Double = 0.0, v0: Double = 0.0,
        u1: Double = 1.0, v1: Double = 1.0
    ) {
        val hw = width / 2
        val hh = height / 2

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - hw, y, z + hh).tex(u0, v1).endVertex()
        buffer.pos(x + hw, y, z + hh).tex(u1, v1).endVertex()
        buffer.pos(x + hw, y, z - hh).tex(u1, v0).endVertex()
        buffer.pos(x - hw, y, z - hh).tex(u0, v0).endVertex()
        tessellator.draw()
    }

    /**
     * 绘制纹理四边形（垂直面，面向观察者）
     */
    fun drawTexturedQuadVertical(
        x: Double, y: Double, z: Double,
        width: Double, height: Double,
        u0: Double = 0.0, v0: Double = 0.0,
        u1: Double = 1.0, v1: Double = 1.0
    ) {
        val hw = width / 2

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - hw, y + height, z).tex(u0, v0).endVertex()
        buffer.pos(x + hw, y + height, z).tex(u1, v0).endVertex()
        buffer.pos(x + hw, y, z).tex(u1, v1).endVertex()
        buffer.pos(x - hw, y, z).tex(u0, v1).endVertex()
        tessellator.draw()
    }

    /**
     * 渲染实体
     */
    fun renderEntity(
        entity: EntityLivingBase,
        x: Double, y: Double, z: Double,
        yaw: Float = 0f,
        scale: Float = 1f
    ) {
        val renderManager = MC.renderManager

        withGlState(blend = true) {
            GlStateManager.translate(x, y, z)
            GlStateManager.scale(scale, scale, scale)

            val prevShadow = renderManager.isRenderShadow
            renderManager.isRenderShadow = false
            renderManager.renderEntity(entity, 0.0, 0.0, 0.0, yaw, 1f, false)
            renderManager.isRenderShadow = prevShadow
        }
    }
}

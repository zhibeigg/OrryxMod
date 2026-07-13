package io.github.orryxmod.core.render

import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20

/**
 * 渲染工具函数
 */
object RenderUtils {

    /**
     * 安全的 GL 状态管理
     * 自动保存和恢复 GL 状态
     */
    fun withGlState(
        blend: Boolean = false,
        depth: Boolean = true,
        lighting: Boolean = false,
        texture: Boolean = true,
        block: () -> Unit
    ) {
        val snapshot = GlStateSnapshot.capture()
        GL11.glMatrixMode(snapshot.matrixMode)
        GL11.glPushMatrix()
        var attributesPushed = false

        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
            attributesPushed = true
            setCapability(blend, GlStateManager::enableBlend, GlStateManager::disableBlend)
            if (blend) {
                GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
                )
            }
            setCapability(depth, GlStateManager::enableDepth, GlStateManager::disableDepth)
            setCapability(lighting, GlStateManager::enableLighting, GlStateManager::disableLighting)
            setCapability(texture, GlStateManager::enableTexture2D, GlStateManager::disableTexture2D)
            block()
        } finally {
            try {
                if (attributesPushed) {
                    GL11.glPopAttrib()
                    snapshot.restore()
                }
            } finally {
                GL11.glMatrixMode(snapshot.matrixMode)
                GL11.glPopMatrix()
                GL11.glMatrixMode(snapshot.matrixMode)
            }
        }
    }

    private data class GlStateSnapshot(
        val matrixMode: Int,
        val activeTexture: Int,
        val alphaEnabled: Boolean,
        val blendEnabled: Boolean,
        val depthEnabled: Boolean,
        val cullEnabled: Boolean,
        val lightingEnabled: Boolean,
        val colorMaterialEnabled: Boolean,
        val normalizeEnabled: Boolean,
        val rescaleNormalEnabled: Boolean,
        val lightEnabled: BooleanArray,
        val color: FloatArray,
        val depthMask: Boolean,
        val depthFunc: Int,
        val blendSrcRgb: Int,
        val blendDstRgb: Int,
        val blendSrcAlpha: Int,
        val blendDstAlpha: Int,
        val blendEquationRgb: Int,
        val blendEquationAlpha: Int,
        val cullFace: Int,
        val textureUnits: List<TextureUnitState>,
        val lightmapBrightnessX: Float,
        val lightmapBrightnessY: Float,
        val shadeModel: Int,
        val lineWidth: Float
    ) {
        fun restore() {
            setCapability(alphaEnabled, GlStateManager::enableAlpha, GlStateManager::disableAlpha)
            setCapability(blendEnabled, GlStateManager::enableBlend, GlStateManager::disableBlend)
            setCapability(depthEnabled, GlStateManager::enableDepth, GlStateManager::disableDepth)
            setCapability(cullEnabled, GlStateManager::enableCull, GlStateManager::disableCull)
            setCapability(lightingEnabled, GlStateManager::enableLighting, GlStateManager::disableLighting)
            setCapability(colorMaterialEnabled, GlStateManager::enableColorMaterial, GlStateManager::disableColorMaterial)
            setCapability(normalizeEnabled, GlStateManager::enableNormalize, GlStateManager::disableNormalize)
            setCapability(rescaleNormalEnabled, GlStateManager::enableRescaleNormal, GlStateManager::disableRescaleNormal)
            for (index in lightEnabled.indices) {
                setCapability(
                    lightEnabled[index],
                    { GlStateManager.enableLight(index) },
                    { GlStateManager.disableLight(index) }
                )
            }

            GlStateManager.color(color[0], color[1], color[2], color[3])
            GlStateManager.depthMask(depthMask)
            GlStateManager.depthFunc(depthFunc)
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha)
            GL11.glCullFace(cullFace)
            GlStateManager.shadeModel(shadeModel)
            GL11.glLineWidth(lineWidth)

            for (textureUnit in textureUnits) {
                textureUnit.restore()
            }
            OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                lightmapBrightnessX,
                lightmapBrightnessY
            )
            restoreActiveTexture(activeTexture)
        }

        companion object {
            fun capture(): GlStateSnapshot {
                val colorBuffer = BufferUtils.createFloatBuffer(4)
                GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colorBuffer)

                val lineWidthBuffer = BufferUtils.createFloatBuffer(1)
                GL11.glGetFloat(GL11.GL_LINE_WIDTH, lineWidthBuffer)

                val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                val textureUnits = captureTextureUnits(activeTexture)

                return GlStateSnapshot(
                    matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE),
                    activeTexture = activeTexture,
                    alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                    blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND),
                    depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING),
                    colorMaterialEnabled = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL),
                    normalizeEnabled = GL11.glIsEnabled(GL11.GL_NORMALIZE),
                    rescaleNormalEnabled = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL),
                    lightEnabled = BooleanArray(8) { index -> GL11.glIsEnabled(GL11.GL_LIGHT0 + index) },
                    color = floatArrayOf(
                        colorBuffer.get(0),
                        colorBuffer.get(1),
                        colorBuffer.get(2),
                        colorBuffer.get(3)
                    ),
                    depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),
                    textureUnits = textureUnits,
                    lightmapBrightnessX = OpenGlHelper.lastBrightnessX,
                    lightmapBrightnessY = OpenGlHelper.lastBrightnessY,
                    shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL),
                    lineWidth = lineWidthBuffer.get(0)
                )
            }
        }
    }

    private data class TextureUnitState(
        val textureUnit: Int,
        val texture2DEnabled: Boolean,
        val textureBinding2D: Int
    ) {
        fun restore() {
            GL13.glActiveTexture(textureUnit)
            GlStateManager.setActiveTexture(textureUnit)

            if (texture2DEnabled) {
                GL11.glEnable(GL11.GL_TEXTURE_2D)
                GlStateManager.enableTexture2D()
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                GlStateManager.disableTexture2D()
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2D)
            GlStateManager.bindTexture(textureBinding2D)
        }
    }

    private fun captureTextureUnits(activeTexture: Int): List<TextureUnitState> {
        val textureUnits = linkedSetOf(
            OpenGlHelper.defaultTexUnit,
            OpenGlHelper.lightmapTexUnit,
            activeTexture
        )

        return try {
            textureUnits.map { textureUnit ->
                GL13.glActiveTexture(textureUnit)
                TextureUnitState(
                    textureUnit = textureUnit,
                    texture2DEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                    textureBinding2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                )
            }
        } finally {
            GL13.glActiveTexture(activeTexture)
        }
    }

    private fun restoreActiveTexture(textureUnit: Int) {
        GL13.glActiveTexture(textureUnit)
        GlStateManager.setActiveTexture(textureUnit)
    }

    private fun setCapability(enabled: Boolean, enable: () -> Unit, disable: () -> Unit) {
        if (enabled) {
            disable()
            enable()
        } else {
            enable()
            disable()
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
            try {
                renderManager.renderEntity(entity, 0.0, 0.0, 0.0, yaw, 1f, false)
            } finally {
                renderManager.isRenderShadow = prevShadow
            }
        }
    }
}

package io.github.orryxmod.feature.bloom

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

/**
 * 泛光效果
 * 简化版实现，使用 Gaussian 模糊算法
 */
@SideOnly(Side.CLIENT)
object BloomEffect {

    // 配置参数
    var strength = 1.5f
    var baseBrightness = 0.1f
    var highBrightnessThreshold = 0.5f
    var lowBrightnessThreshold = 0.5f
    var step = 1.0f
    var fastMode = true  // 快速模式：只使用单级模糊

    // 模糊 FBO
    private var blurH: Framebuffer? = null
    private var blurW: Framebuffer? = null
    private var blurH2: Framebuffer? = null
    private var blurW2: Framebuffer? = null

    // PingPong 缓冲
    private var bufferA: Framebuffer? = null
    private var bufferB: Framebuffer? = null
    private var pingPongFlag = false

    private fun updateBlurSize(width: Int, height: Int) {
        val w8 = width / 8
        val h8 = height / 8
        val w4 = width / 4
        val h4 = height / 4

        if (blurH == null) {
            blurH = Framebuffer(w8, h8, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
            blurW = Framebuffer(w8, h8, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
            blurH2 = Framebuffer(w4, h4, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
            blurW2 = Framebuffer(w4, h4, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
        } else {
            updateFBOSize(blurH!!, w8, h8)
            updateFBOSize(blurW!!, w8, h8)
            updateFBOSize(blurH2!!, w4, h4)
            updateFBOSize(blurW2!!, w4, h4)
        }
    }

    private fun updatePingPongSize(width: Int, height: Int) {
        if (bufferA == null) {
            bufferA = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
            }
            bufferB = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
            }
        } else {
            updateFBOSize(bufferA!!, width, height)
            updateFBOSize(bufferB!!, width, height)
        }
    }

    private fun updateFBOSize(fbo: Framebuffer, width: Int, height: Int): Boolean {
        if (fbo.framebufferWidth != width || fbo.framebufferHeight != height) {
            fbo.createBindFramebuffer(width, height)
            return true
        }
        return false
    }

    private fun swapPingPong(clean: Boolean = false): Framebuffer {
        pingPongFlag = !pingPongFlag
        val buffer = if (pingPongFlag) bufferA!! else bufferB!!
        if (clean) {
            buffer.framebufferClear()
        }
        return buffer
    }

    private fun getCurrentPingPong(): Framebuffer {
        return if (pingPongFlag) bufferA!! else bufferB!!
    }

    /**
     * 只进行模糊处理（不混合背景）
     */
    fun renderBlur(highlightFBO: Framebuffer) {
        if (!ShaderManager.allowedShader()) return

        val width = highlightFBO.framebufferWidth
        val height = highlightFBO.framebufferHeight

        updateBlurSize(width, height)

        // 绑定输入纹理
        highlightFBO.bindFramebufferTexture()

        // 第一级模糊 (1/4 分辨率)
        ShaderManager.renderFullImageInFBO(blurH2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }.bindFramebufferTexture()

        ShaderManager.renderFullImageInFBO(blurW2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }

        // 快速模式下跳过第二级模糊
        if (!fastMode) {
            blurW2!!.bindFramebufferTexture()

            // 第二级模糊 (1/8 分辨率)
            ShaderManager.renderFullImageInFBO(blurH!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform2f(program, "blurDir", 0f, step)
            }.bindFramebufferTexture()

            ShaderManager.renderFullImageInFBO(blurW!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform2f(program, "blurDir", step, 0f)
            }
        }
    }

    /**
     * 获取模糊后的 FBO
     */
    fun getBlurredFBO(): Framebuffer? = if (fastMode) blurW2 else blurW

    /**
     * 渲染 Gaussian 模糊泛光效果
     */
    fun renderGaussian(highlightFBO: Framebuffer, backgroundFBO: Framebuffer) {
        if (!ShaderManager.allowedShader()) return

        val width = backgroundFBO.framebufferWidth
        val height = backgroundFBO.framebufferHeight

        updateBlurSize(width, height)
        updatePingPongSize(width, height)

        // 多级模糊
        highlightFBO.bindFramebufferTexture()

        // 第一级模糊 (1/4 分辨率)
        ShaderManager.renderFullImageInFBO(blurH2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }.bindFramebufferTexture()

        ShaderManager.renderFullImageInFBO(blurW2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }.bindFramebufferTexture()

        // 第二级模糊 (1/8 分辨率)
        ShaderManager.renderFullImageInFBO(blurH!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }.bindFramebufferTexture()

        ShaderManager.renderFullImageInFBO(blurW!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }.bindFramebufferTexture()

        // 混合泛光与背景
        blend(blurW!!, backgroundFBO)
    }

    private fun blend(bloom: Framebuffer, backgroundFBO: Framebuffer) {
        // 绑定背景纹理到 TEXTURE0
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture2D()
        backgroundFBO.bindFramebufferTexture()

        // 绑定泛光纹理到 TEXTURE1
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.enableTexture2D()
        bloom.bindFramebufferTexture()

        // 混合着色器
        ShaderManager.renderFullImageInFBO(swapPingPong(), ShaderManager.PROGRAM_BLOOM_COMBINE) { program ->
            ShaderManager.setUniform1i(program, "buffer_a", 0)
            ShaderManager.setUniform1i(program, "buffer_b", 1)
            ShaderManager.setUniform1f(program, "intensive", strength)
            ShaderManager.setUniform1f(program, "base", baseBrightness)
            ShaderManager.setUniform1f(program, "threshold_up", highBrightnessThreshold)
            ShaderManager.setUniform1f(program, "threshold_down", lowBrightnessThreshold)
        }

        // 清理纹理绑定
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.bindTexture(0)

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.bindTexture(0)

        // 绑定结果纹理
        getCurrentPingPong().bindFramebufferTexture()
    }

    /**
     * 获取最终结果 FBO
     */
    fun getResultFBO(): Framebuffer? = getCurrentPingPong()

    fun cleanup() {
        blurH?.deleteFramebuffer()
        blurW?.deleteFramebuffer()
        blurH2?.deleteFramebuffer()
        blurW2?.deleteFramebuffer()
        bufferA?.deleteFramebuffer()
        bufferB?.deleteFramebuffer()

        blurH = null
        blurW = null
        blurH2 = null
        blurW2 = null
        bufferA = null
        bufferB = null
    }
}

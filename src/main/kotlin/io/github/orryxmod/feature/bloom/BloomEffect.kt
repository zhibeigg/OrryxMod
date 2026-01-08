package io.github.orryxmod.feature.bloom

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
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

    /**
     * 重置 PingPong 状态（每帧开始时调用）
     */
    fun resetPingPong() {
        pingPongFlag = false
    }

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
            // 设置纹理边界处理，避免边缘伪影
            setTextureClampToEdge(blurH!!)
            setTextureClampToEdge(blurW!!)
            setTextureClampToEdge(blurH2!!)
            setTextureClampToEdge(blurW2!!)
        } else {
            updateFBOSize(blurH!!, w8, h8)
            updateFBOSize(blurW!!, w8, h8)
            updateFBOSize(blurH2!!, w4, h4)
            updateFBOSize(blurW2!!, w4, h4)
        }
    }

    /**
     * 设置纹理边界为 CLAMP_TO_EDGE，避免模糊时的边缘伪影
     */
    private fun setTextureClampToEdge(fbo: Framebuffer) {
        GlStateManager.bindTexture(fbo.framebufferTexture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager.bindTexture(0)
    }

    private fun updatePingPongSize(width: Int, height: Int) {
        if (bufferA == null) {
            bufferA = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
            }
            bufferB = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
            }
            // 设置纹理边界处理
            setTextureClampToEdge(bufferA!!)
            setTextureClampToEdge(bufferB!!)
        } else {
            updateFBOSize(bufferA!!, width, height)
            updateFBOSize(bufferB!!, width, height)
        }
    }

    private fun updateFBOSize(fbo: Framebuffer, width: Int, height: Int): Boolean {
        if (fbo.framebufferWidth != width || fbo.framebufferHeight != height) {
            fbo.createBindFramebuffer(width, height)
            // 重新设置纹理边界
            setTextureClampToEdge(fbo)
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
        bindToTexture0(highlightFBO)

        // 第一级模糊 (1/4 分辨率)
        ShaderManager.renderFullImageInFBO(blurH2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }
        bindToTexture0(blurH2!!)

        ShaderManager.renderFullImageInFBO(blurW2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }

        // 快速模式下跳过第二级模糊
        if (!fastMode) {
            bindToTexture0(blurW2!!)

            // 第二级模糊 (1/8 分辨率)
            ShaderManager.renderFullImageInFBO(blurH!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform1i(program, "originalTexture", 0)
                ShaderManager.setUniform2f(program, "blurDir", 0f, step)
            }
            bindToTexture0(blurH!!)

            ShaderManager.renderFullImageInFBO(blurW!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform1i(program, "originalTexture", 0)
                ShaderManager.setUniform2f(program, "blurDir", step, 0f)
            }
        }
    }

    /**
     * 获取模糊后的 FBO
     */
    fun getBlurredFBO(): Framebuffer? = if (fastMode) blurW2 else blurW

    /**
     * 模糊并混合到背景，返回结果 FBO
     * @param highlightFBO 高亮 FBO（实体渲染结果）
     * @param backgroundFBO 背景 FBO（用于混合）
     * @param config 泛光配置（可选，用于颜色着色）
     * @param useAccumulator 是否使用累积模式（从上一次结果继续）
     */
    fun renderBlurAndBlend(highlightFBO: Framebuffer, backgroundFBO: Framebuffer, config: BloomConfig? = null, useAccumulator: Boolean = false): Framebuffer? {
        if (!ShaderManager.allowedShader()) return null

        val width = backgroundFBO.framebufferWidth
        val height = backgroundFBO.framebufferHeight

        updateBlurSize(width, height)
        updatePingPongSize(width, height)

        // 模糊
        bindToTexture0(highlightFBO)

        ShaderManager.renderFullImageInFBO(blurH2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }
        bindToTexture0(blurH2!!)

        ShaderManager.renderFullImageInFBO(blurW2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }

        if (!fastMode) {
            bindToTexture0(blurW2!!)
            ShaderManager.renderFullImageInFBO(blurH!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform1i(program, "originalTexture", 0)
                ShaderManager.setUniform2f(program, "blurDir", 0f, step)
            }
            bindToTexture0(blurH!!)
            ShaderManager.renderFullImageInFBO(blurW!!, ShaderManager.PROGRAM_BLUR) { program ->
                ShaderManager.setUniform1i(program, "originalTexture", 0)
                ShaderManager.setUniform2f(program, "blurDir", step, 0f)
            }
        }

        // 混合 - 如果使用累积模式，从上一次的 PingPong 结果继续
        val blurredFBO = if (fastMode) blurW2!! else blurW!!
        val actualBackground = if (useAccumulator && (bufferA != null || bufferB != null)) {
            // 使用上一次的结果作为背景
            getCurrentPingPong()
        } else {
            backgroundFBO
        }
        blend(blurredFBO, actualBackground, config)

        return getCurrentPingPong()
    }

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
        bindToTexture0(highlightFBO)

        // 第一级模糊 (1/4 分辨率)
        ShaderManager.renderFullImageInFBO(blurH2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }
        bindToTexture0(blurH2!!)

        ShaderManager.renderFullImageInFBO(blurW2!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }
        bindToTexture0(blurW2!!)

        // 第二级模糊 (1/8 分辨率)
        ShaderManager.renderFullImageInFBO(blurH!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", 0f, step)
        }
        bindToTexture0(blurH!!)

        ShaderManager.renderFullImageInFBO(blurW!!, ShaderManager.PROGRAM_BLUR) { program ->
            ShaderManager.setUniform1i(program, "originalTexture", 0)
            ShaderManager.setUniform2f(program, "blurDir", step, 0f)
        }

        // 混合泛光与背景
        blend(blurW!!, backgroundFBO)
    }

    private fun bindToTexture0(fbo: Framebuffer) {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture2D()
        fbo.bindFramebufferTexture()
    }

    private fun blend(bloom: Framebuffer, backgroundFBO: Framebuffer, config: BloomConfig? = null) {
        val useTint = config != null && config.color[3] > 0
        val tintR = if (useTint) config!!.color[0] / 255f else 1f
        val tintG = if (useTint) config!!.color[1] / 255f else 1f
        val tintB = if (useTint) config!!.color[2] / 255f else 1f
        val intensive = config?.strength ?: strength

        // 绑定背景纹理到 TEXTURE0
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture2D()
        backgroundFBO.bindFramebufferTexture()

        // 绑定泛光纹理到 TEXTURE1
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.enableTexture2D()
        bloom.bindFramebufferTexture()

        val targetFBO = swapPingPong()

        // 混合着色器
        ShaderManager.renderFullImageInFBO(targetFBO, ShaderManager.PROGRAM_BLOOM_COMBINE) { program ->
            ShaderManager.setUniform1i(program, "buffer_a", 0)
            ShaderManager.setUniform1i(program, "buffer_b", 1)
            ShaderManager.setUniform1f(program, "intensive", intensive)
            ShaderManager.setUniform1f(program, "base", baseBrightness)
            ShaderManager.setUniform1f(program, "threshold_up", highBrightnessThreshold)
            ShaderManager.setUniform1f(program, "threshold_down", lowBrightnessThreshold)
            // 颜色着色
            ShaderManager.setUniform1f(program, "use_tint", if (useTint) 1f else 0f)
            ShaderManager.setUniform3f(program, "tint_color", tintR, tintG, tintB)
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

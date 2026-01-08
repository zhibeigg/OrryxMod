package io.github.orryxmod.feature.bloom

import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11

/**
 * 双缓冲系统，用于避免 FBO 读写冲突
 */
@SideOnly(Side.CLIENT)
object PingPongBuffer {
    private var bufferA: Framebuffer? = null
    private var bufferB: Framebuffer? = null
    private var flag = false

    fun updateSize(width: Int, height: Int) {
        if (bufferA == null || bufferA!!.framebufferWidth != width || bufferA!!.framebufferHeight != height) {
            bufferA?.deleteFramebuffer()
            bufferB?.deleteFramebuffer()
            bufferA = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
            bufferB = Framebuffer(width, height, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
        }
    }

    fun getCurrentBuffer(clean: Boolean = false): Framebuffer? {
        val buffer = if (flag) bufferA else bufferB
        if (clean) buffer?.framebufferClear()
        return buffer
    }

    fun swap(clean: Boolean = false): Framebuffer? {
        flag = !flag
        return getCurrentBuffer(clean)
    }

    fun bindFramebufferTexture() {
        getCurrentBuffer(false)?.bindFramebufferTexture()
    }

    fun cleanup() {
        bufferA?.deleteFramebuffer()
        bufferB?.deleteFramebuffer()
        bufferA = null
        bufferB = null
        flag = false
    }
}

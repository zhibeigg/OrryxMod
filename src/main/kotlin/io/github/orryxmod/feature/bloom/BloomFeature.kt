package io.github.orryxmod.feature.bloom

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.shader.Framebuffer
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

/**
 * 泛光效果功能模块
 * 为名称包含 "glow" 关键词的模型部件添加泛光效果
 */
@Feature("bloom", description = "泛光效果")
object BloomFeature : FeatureBase() {

    // 配置
    object Config {
        var enabled = true
        var entityBloom = true  // 为名字包含 "bloom" 的实体启用泛光
        var strength = 1.5f
        var baseBrightness = 0.1f
        var highBrightnessThreshold = 0.5f
        var lowBrightnessThreshold = 0.5f
        var step = 1.0f
        var maxBloomDistance = 32.0  // 最大泛光距离
        var maxBloomEntities = 10    // 每帧最大泛光实体数
    }

    // 泛光 FBO
    private var bloomFBO: Framebuffer? = null

    // 临时缓冲区（用于避免读写冲突）
    private var tempFBO: Framebuffer? = null

    // 待渲染的发光回调 (参数: partialTicks)
    private val glowRenderCallbacks = java.util.concurrent.CopyOnWriteArrayList<(Float) -> Unit>()

    // 是否有持续的发光效果
    private var persistentGlow = false

    // 泛光渲染状态
    private var bloomMark = false
    private var mainFBOBackup: Framebuffer? = null

    override fun enable() {
        super.enable()
        MinecraftForge.EVENT_BUS.register(this)

        // 初始化着色器
        if (OpenGlHelper.shadersSupported) {
            ShaderManager.init()
        }
    }

    override fun disable() {
        super.disable()
        MinecraftForge.EVENT_BUS.unregister(this)
        cleanup()
    }

    /**
     * 注册发光渲染回调
     */
    fun registerGlowRender(callback: (Float) -> Unit) {
        if (!Config.enabled || !ShaderManager.allowedShader()) return
        glowRenderCallbacks.add(callback)
    }

    /**
     * 开始泛光渲染
     */
    @JvmStatic
    fun start(): Boolean {
        if (!Config.enabled || !ShaderManager.allowedShader()) return false
        if (bloomMark) return false

        val mainFBO = MC.framebuffer ?: return false
        ensureBloomFBO(mainFBO)
        val glowFBO = bloomFBO ?: return false

        bloomMark = true
        mainFBOBackup = mainFBO

        glowFBO.framebufferClear()
        glowFBO.bindFramebuffer(true)
        return true
    }

    /**
     * 结束泛光渲染并应用效果
     */
    @JvmStatic
    fun end() {
        if (!bloomMark) return
        bloomMark = false

        val mainFBO = mainFBOBackup ?: return
        val glowFBO = bloomFBO ?: return

        BloomEffect.renderBlur(glowFBO)

        val blurredFBO = BloomEffect.getBlurredFBO()
        if (blurredFBO != null) {
            renderBloomToMain(mainFBO, blurredFBO)
        }

        mainFBOBackup = null
    }

    /**
     * 检查是否正在泛光渲染中
     */
    @JvmStatic
    fun isRendering(): Boolean = bloomMark

    /**
     * 设置持续发光测试
     */
    fun setPersistentGlow(enabled: Boolean) {
        persistentGlow = enabled
    }

    @SubscribeEvent
    @Suppress("UNCHECKED_CAST")
    fun onRenderLivingPost(event: RenderLivingEvent.Post<*>) {
        if (!Config.enabled || !ShaderManager.allowedShader()) return
        // entityBloom 模式下在 onRenderWorldLast 中直接遍历实体
        if (!Config.entityBloom) {
            GlowRenderer.renderGlowParts(event)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (!Config.enabled) return
        if (!ShaderManager.allowedShader()) return

        val mainFBO = MC.framebuffer ?: return
        val player = MC.player ?: return
        val world = MC.world ?: return
        val rm = MC.renderManager
        val partialTicks = event.partialTicks

        // 收集需要泛光的实体
        val bloomEntities = mutableListOf<Pair<net.minecraft.entity.EntityLivingBase, net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>>>()

        if (Config.entityBloom) {
            val maxDistSq = Config.maxBloomDistance * Config.maxBloomDistance
            var count = 0
            for (entity in world.loadedEntityList) {
                if (count >= Config.maxBloomEntities) break
                if (entity !is net.minecraft.entity.EntityLivingBase) continue

                // 距离剔除
                val distSq = player.getDistanceSq(entity)
                if (distSq > maxDistSq) continue

                // 名字检测
                val customName = entity.customNameTag
                val name = if (!customName.isNullOrEmpty()) customName else entity.name ?: ""
                if (!name.contains("bloom", ignoreCase = true)) continue

                // 获取渲染器
                @Suppress("UNCHECKED_CAST")
                val renderer = rm.getEntityRenderObject<net.minecraft.entity.EntityLivingBase>(entity) as? net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>
                    ?: continue

                bloomEntities.add(entity to renderer)
                count++
            }
        }

        val hasGlow = persistentGlow || glowRenderCallbacks.isNotEmpty() || bloomEntities.isNotEmpty()
        if (!hasGlow) return

        ensureBloomFBO(mainFBO)
        val glowFBO = bloomFBO ?: return

        // 1. 渲染发光物体到 glowFBO（只清颜色，不清深度，因为深度缓冲是共享的）
        glowFBO.bindFramebuffer(false)
        GL11.glClearColor(0f, 0f, 0f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        glowFBO.bindFramebuffer(true)

        // 启用深度测试但禁止写入深度缓冲（避免穿透其他模型，但不影响后续渲染）
        GlStateManager.enableDepth()
        GlStateManager.depthMask(false)
        // 设置全亮度光照，避免不同角度光晕强度不同
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
        // 启用加法混合，让多个实体的泛光叠加
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)

        if (persistentGlow) {
            renderTestCube()
        }

        // 渲染泛光实体
        for ((entity, renderer) in bloomEntities) {
            try {
                val rx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX
                val ry = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY
                val rz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ
                renderer.doRender(entity, rx, ry, rz, entity.rotationYaw, partialTicks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 处理回调（用于 GlowRenderer 等）
        val callbacks = glowRenderCallbacks.toList()
        glowRenderCallbacks.clear()
        callbacks.forEach { callback ->
            try {
                callback(partialTicks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 恢复状态
        GlStateManager.disableBlend()
        GlStateManager.depthMask(true)

        // 2. 对发光物体进行模糊
        BloomEffect.renderBlur(glowFBO)

        // 3. 将模糊后的泛光叠加到主画面（使用着色器控制强度）
        val blurredFBO = BloomEffect.getBlurredFBO()
        if (blurredFBO != null) {
            renderBloomToMain(mainFBO, blurredFBO)
        }
    }

    /**
     * 使用着色器将泛光叠加到主画面
     */
    private fun renderBloomToMain(mainFBO: Framebuffer, bloomFBO: Framebuffer) {
        // 确保临时 FBO 存在
        ensureTempFBO(mainFBO)
        val temp = tempFBO ?: return

        // 绑定主画面纹理到 TEXTURE0
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture2D()
        mainFBO.bindFramebufferTexture()

        // 绑定泛光纹理到 TEXTURE1
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.enableTexture2D()
        bloomFBO.bindFramebufferTexture()

        // 使用混合着色器渲染到临时 FBO（避免读写冲突）
        ShaderManager.renderFullImageInFBO(temp, ShaderManager.PROGRAM_BLOOM_COMBINE) { program ->
            ShaderManager.setUniform1i(program, "buffer_a", 0)
            ShaderManager.setUniform1i(program, "buffer_b", 1)
            ShaderManager.setUniform1f(program, "intensive", Config.strength)
            ShaderManager.setUniform1f(program, "base", Config.baseBrightness)
            ShaderManager.setUniform1f(program, "threshold_up", Config.highBrightnessThreshold)
            ShaderManager.setUniform1f(program, "threshold_down", Config.lowBrightnessThreshold)
        }

        // 清理纹理绑定
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.bindTexture(0)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)

        // 将临时 FBO 复制回主 FBO
        temp.bindFramebufferTexture()
        ShaderManager.renderFullImageInFBO(mainFBO, ShaderManager.PROGRAM_IMAGE, null)

        GlStateManager.bindTexture(0)
    }

    private fun ensureTempFBO(mainFBO: Framebuffer) {
        if (tempFBO == null ||
            tempFBO!!.framebufferWidth != mainFBO.framebufferWidth ||
            tempFBO!!.framebufferHeight != mainFBO.framebufferHeight
        ) {
            tempFBO?.deleteFramebuffer()
            tempFBO = Framebuffer(mainFBO.framebufferWidth, mainFBO.framebufferHeight, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
        }
    }

    private fun drawFullscreenQuad(width: Int, height: Int, intensity: Float = 1f) {
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, width.toDouble(), height.toDouble(), 0.0, -1.0, 1.0)

        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()

        GlStateManager.disableLighting()
        GL11.glColor4f(intensity, intensity, intensity, 1f)

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0f, 0f)
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, height.toFloat())
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(width.toFloat(), height.toFloat())
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(width.toFloat(), 0f)
        GL11.glEnd()

        GL11.glColor4f(1f, 1f, 1f, 1f)
        GlStateManager.enableLighting()

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.popMatrix()
    }

    /**
     * 渲染测试立方体
     */
    private fun renderTestCube() {
        val player = MC.player ?: return
        val renderManager = MC.renderManager

        val yaw = Math.toRadians(player.rotationYaw.toDouble())
        val x = player.posX - Math.sin(yaw) * 2.0
        val y = player.posY + 1.0
        val z = player.posZ + Math.cos(yaw) * 2.0

        val rx = x - renderManager.viewerPosX
        val ry = y - renderManager.viewerPosY
        val rz = z - renderManager.viewerPosZ

        GlStateManager.pushMatrix()
        GlStateManager.translate(rx, ry, rz)

        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        GL11.glColor4f(1f, 0.5f, 0f, 1f)

        val size = 0.5
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex3d(-size, -size, size); GL11.glVertex3d(size, -size, size); GL11.glVertex3d(size, size, size); GL11.glVertex3d(-size, size, size)
        GL11.glVertex3d(-size, -size, -size); GL11.glVertex3d(-size, size, -size); GL11.glVertex3d(size, size, -size); GL11.glVertex3d(size, -size, -size)
        GL11.glVertex3d(-size, size, -size); GL11.glVertex3d(-size, size, size); GL11.glVertex3d(size, size, size); GL11.glVertex3d(size, size, -size)
        GL11.glVertex3d(-size, -size, -size); GL11.glVertex3d(size, -size, -size); GL11.glVertex3d(size, -size, size); GL11.glVertex3d(-size, -size, size)
        GL11.glVertex3d(size, -size, -size); GL11.glVertex3d(size, size, -size); GL11.glVertex3d(size, size, size); GL11.glVertex3d(size, -size, size)
        GL11.glVertex3d(-size, -size, -size); GL11.glVertex3d(-size, -size, size); GL11.glVertex3d(-size, size, size); GL11.glVertex3d(-size, size, -size)
        GL11.glEnd()

        GlStateManager.enableTexture2D()
        GlStateManager.enableLighting()
        GlStateManager.disableBlend()
        GL11.glColor4f(1f, 1f, 1f, 1f)

        GlStateManager.popMatrix()
    }

    private fun ensureBloomFBO(mainFBO: Framebuffer) {
        if (bloomFBO == null ||
            bloomFBO!!.framebufferWidth != mainFBO.framebufferWidth ||
            bloomFBO!!.framebufferHeight != mainFBO.framebufferHeight
        ) {
            bloomFBO?.deleteFramebuffer()
            bloomFBO = Framebuffer(mainFBO.framebufferWidth, mainFBO.framebufferHeight, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }

            hookDepthBuffer(bloomFBO!!, mainFBO.depthBuffer)
        }
    }

    private fun hookDepthBuffer(fbo: Framebuffer, depthBuffer: Int) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo.framebufferObject)
        OpenGlHelper.glFramebufferRenderbuffer(
            OpenGlHelper.GL_FRAMEBUFFER,
            OpenGlHelper.GL_DEPTH_ATTACHMENT,
            OpenGlHelper.GL_RENDERBUFFER,
            depthBuffer
        )
    }

    @OnDisconnect
    fun onDisconnect() {
        glowRenderCallbacks.clear()
        persistentGlow = false
    }

    private fun cleanup() {
        bloomFBO?.deleteFramebuffer()
        bloomFBO = null
        tempFBO?.deleteFramebuffer()
        tempFBO = null
        BloomEffect.cleanup()
        ShaderManager.cleanup()
        glowRenderCallbacks.clear()
        persistentGlow = false
    }

    /**
     * 切换持续发光测试
     */
    fun togglePersistentGlow(): Boolean {
        persistentGlow = !persistentGlow
        return persistentGlow
    }
}

package io.github.orryxmod.feature.bloom

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.network.PacketDispatcher
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
        var strength = 1.5f
        var baseBrightness = 0.1f
        var highBrightnessThreshold = 0.5f
        var lowBrightnessThreshold = 0.5f
        var step = 1.0f
        var maxBloomEntities = 0     // 每帧最大泛光实体数（<=0 表示不限制）
    }

    // 泛光 FBO
    private var bloomFBO: Framebuffer? = null
    private var bloomFBODepthBuffer: Int = -1

    // 待渲染的发光回调 (参数: partialTicks)
    private val glowRenderCallbacks = java.util.concurrent.CopyOnWriteArrayList<(Float) -> Unit>()

    // 是否有持续的发光效果
    private var persistentGlow = false

    // 泛光渲染状态
    private var bloomMark = false
    private var mainFBOBackup: Framebuffer? = null

    private data class BloomCandidate(
        val entity: net.minecraft.entity.EntityLivingBase,
        val renderer: net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>,
        val config: BloomConfig,
        val distSq: Double
    )

    private fun selectCandidates(candidates: List<BloomCandidate>, maxTotal: Int): List<BloomCandidate> {
        if (candidates.isEmpty()) return emptyList()
        if (maxTotal <= 0) return candidates
        if (candidates.size <= maxTotal) return candidates

        val byConfig = candidates.groupBy { it.config.name }
        val queues = byConfig.values
            .sortedWith(
                compareByDescending<List<BloomCandidate>> { it.first().config.priority }
                    .thenBy { it.first().config.name }
            )
            .map { group -> group.sortedBy { it.distSq }.toMutableList() }

        val selected = ArrayList<BloomCandidate>(maxTotal)
        while (selected.size < maxTotal) {
            var progressed = false
            for (queue in queues) {
                if (selected.size >= maxTotal) break
                val next = queue.removeFirstOrNull() ?: continue
                selected.add(next)
                progressed = true
            }
            if (!progressed) break
        }

        return selected
    }

    override fun enable() {
        super.enable()
        MinecraftForge.EVENT_BUS.register(this)

        // 初始化着色器
        if (OpenGlHelper.shadersSupported) {
            ShaderManager.init()
            io.github.orryxmod.OrryxMod.logger.info("[Bloom] Shader initialized: IMAGE=${ShaderManager.PROGRAM_IMAGE}, BLUR=${ShaderManager.PROGRAM_BLUR}, COMBINE=${ShaderManager.PROGRAM_BLOOM_COMBINE}")
        } else {
            io.github.orryxmod.OrryxMod.logger.warn("[Bloom] Shaders not supported!")
        }

        // 注册配置包处理器
        PacketDispatcher.register<OrryxPacket.BloomConfigSync> { packet ->
            BloomConfigManager.syncAll(packet.configs)
        }
        PacketDispatcher.register<OrryxPacket.BloomConfigUpdate> { packet ->
            BloomConfigManager.update(packet.id, packet.config)
        }
        PacketDispatcher.register<OrryxPacket.BloomConfigRemove> { packet ->
            BloomConfigManager.remove(packet.id)
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

        val resultFBO = BloomEffect.renderBlurAndBlend(glowFBO, mainFBO, null)
        if (resultFBO != null) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.enableTexture2D()
            resultFBO.bindFramebufferTexture()

            GlStateManager.disableBlend()
            GlStateManager.disableDepth()
            GlStateManager.depthMask(false)
            ShaderManager.renderFullImageInFBO(mainFBO, ShaderManager.PROGRAM_IMAGE) { program ->
                ShaderManager.setUniform1i(program, "colourTexture", 0)
            }
            GlStateManager.depthMask(true)
            GlStateManager.enableDepth()
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
        GlowRenderer.renderGlowParts(event)
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

        // 收集需要泛光的实体（使用配置管理器）
        val candidates = mutableListOf<BloomCandidate>()

        if (BloomConfigManager.hasConfigs()) {
            for (entity in world.loadedEntityList) {
                if (entity !is net.minecraft.entity.EntityLivingBase) continue

                val bloomConfig = run {
                    val customName = entity.customNameTag
                    val baseName = if (!customName.isNullOrEmpty()) customName else entity.name ?: ""
                    BloomConfigManager.findConfig(baseName)
                        ?: BloomConfigManager.findConfig(entity.displayName.unformattedText)
                        ?: BloomConfigManager.findConfig(entity.displayName.formattedText)
                } ?: continue

                val maxDistSq = (bloomConfig.radius * bloomConfig.radius).toDouble()
                val distSq = player.getDistanceSq(entity)
                if (distSq > maxDistSq) continue

                @Suppress("UNCHECKED_CAST")
                val renderer = rm.getEntityRenderObject<net.minecraft.entity.EntityLivingBase>(entity) as? net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>
                    ?: continue

                candidates.add(BloomCandidate(entity, renderer, bloomConfig, distSq))
            }
        }

        val bloomEntities = selectCandidates(candidates, Config.maxBloomEntities)
        val hasGlow = persistentGlow || glowRenderCallbacks.isNotEmpty() || bloomEntities.isNotEmpty()
        if (!hasGlow) return

        ensureBloomFBO(mainFBO)
        val glowFBO = bloomFBO ?: return

        // 保存所有 OpenGL 状态
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

        // 按配置分组实体
        val groupedEntities = bloomEntities.groupBy { it.config.name }

        // 重置 PingPong 状态
        BloomEffect.resetPingPong()

        // 第一组使用 mainFBO 作为背景，后续组使用累积结果
        var isFirstGroup = true
        var lastResultFBO: Framebuffer? = null

        // 为每个配置组单独渲染
        for ((_, group) in groupedEntities) {
            val config = group.first().config

            // 1. 渲染该组实体到 glowFBO
            glowFBO.bindFramebuffer(false)
            GL11.glClearColor(0f, 0f, 0f, 0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            glowFBO.bindFramebuffer(true)

            GlStateManager.enableDepth()
            GlStateManager.depthMask(false)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)

            // 以"纯色遮罩"方式写入高亮缓冲，避免同一实体存在多层渲染/多 pass 时叠加导致个别实体异常偏亮
            // 但保留纹理采样以正确处理透明像素
            GlStateManager.disableLighting()
            GlStateManager.disableBlend()
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
            GlStateManager.disableTexture2D()
            GlStateManager.bindTexture(0)
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.enableTexture2D()  // 启用纹理以读取 alpha
            GlStateManager.color(1f, 1f, 1f, 1f)

            // 启用多边形偏移，避免与主渲染的 Z-fighting
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
            GL11.glPolygonOffset(-1f, -1f)

            ShaderManager.useProgram(ShaderManager.PROGRAM_MASK)
            ShaderManager.setUniform4f(ShaderManager.PROGRAM_MASK, "u_color", 1f, 1f, 1f, 1f)
            ShaderManager.setUniform1i(ShaderManager.PROGRAM_MASK, "u_texture", 0)
            ShaderManager.setUniform1f(ShaderManager.PROGRAM_MASK, "u_alphaThreshold", 0.1f)

            for (candidate in group) {
                try {
                    val entity = candidate.entity
                    val renderer = candidate.renderer

                    val rx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX
                    val ry = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY
                    val rz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ

                    // 使用 doRender 渲染完整模型（包括其他 mod 的修改）
                    GlStateManager.color(1f, 1f, 1f, 1f)
                    renderer.doRender(entity, rx, ry, rz, entity.rotationYaw, partialTicks)

                    // doRender 后恢复状态并重新激活着色器
                    glowFBO.bindFramebuffer(false)
                    GlStateManager.depthMask(false)
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
                    GlStateManager.disableLighting()
                    GlStateManager.disableBlend()
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
                    GlStateManager.disableTexture2D()
                    GlStateManager.bindTexture(0)
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
                    GlStateManager.enableTexture2D()
                    GlStateManager.color(1f, 1f, 1f, 1f)

                    // 重新激活遮罩着色器
                    ShaderManager.useProgram(ShaderManager.PROGRAM_MASK)
                    ShaderManager.setUniform4f(ShaderManager.PROGRAM_MASK, "u_color", 1f, 1f, 1f, 1f)
                    ShaderManager.setUniform1i(ShaderManager.PROGRAM_MASK, "u_texture", 0)
                    ShaderManager.setUniform1f(ShaderManager.PROGRAM_MASK, "u_alphaThreshold", 0.1f)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            ShaderManager.releaseProgram()
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)  // 禁用多边形偏移
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
            GlStateManager.disableTexture2D()
            GlStateManager.bindTexture(0)
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.enableTexture2D()
            GlStateManager.bindTexture(0)
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.enableLighting()
            GlStateManager.disableBlend()
            GlStateManager.depthMask(true)

            // 2. 模糊并混合
            // 第一组使用 mainFBO 作为背景，后续组使用累积模式
            lastResultFBO = BloomEffect.renderBlurAndBlend(glowFBO, mainFBO, config, !isFirstGroup)
            isFirstGroup = false
        }

        // 3. 最后一次性复制结果回主 FBO
        if (lastResultFBO != null) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.enableTexture2D()
            lastResultFBO.bindFramebufferTexture()

            GlStateManager.disableBlend()
            GlStateManager.disableDepth()
            GlStateManager.depthMask(false)
            ShaderManager.renderFullImageInFBO(mainFBO, ShaderManager.PROGRAM_IMAGE) { program ->
                ShaderManager.setUniform1i(program, "colourTexture", 0)
            }
            GlStateManager.depthMask(true)
            GlStateManager.enableDepth()
        }

        // 处理测试立方体和回调（无颜色着色）
        if (persistentGlow || glowRenderCallbacks.isNotEmpty()) {
            glowFBO.bindFramebuffer(false)
            GL11.glClearColor(0f, 0f, 0f, 0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
            glowFBO.bindFramebuffer(true)

            GlStateManager.enableDepth()
            GlStateManager.depthMask(false)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)

            if (persistentGlow) {
                renderTestCube()
            }

            val callbacks = glowRenderCallbacks.toList()
            glowRenderCallbacks.clear()
            callbacks.forEach { callback ->
                try {
                    callback(partialTicks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            GlStateManager.disableBlend()
            GlStateManager.depthMask(true)

            val resultFBO = BloomEffect.renderBlurAndBlend(glowFBO, mainFBO, null)
            if (resultFBO != null) {
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
                GlStateManager.enableTexture2D()
                resultFBO.bindFramebufferTexture()

                GlStateManager.disableBlend()
                GlStateManager.disableDepth()
                GlStateManager.depthMask(false)
                ShaderManager.renderFullImageInFBO(mainFBO, ShaderManager.PROGRAM_IMAGE) { program ->
                    ShaderManager.setUniform1i(program, "colourTexture", 0)
                }
                GlStateManager.depthMask(true)
                GlStateManager.enableDepth()

                // 清理纹理绑定状态
                GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
                GlStateManager.bindTexture(0)
            }
        }

        // 恢复所有 OpenGL 状态
        GL11.glPopAttrib()

        // 确保主 FBO 正确绑定（glPopAttrib 不恢复 FBO 绑定）
        mainFBO.bindFramebuffer(true)
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
        val needsResize = bloomFBO == null ||
            bloomFBO!!.framebufferWidth != mainFBO.framebufferWidth ||
            bloomFBO!!.framebufferHeight != mainFBO.framebufferHeight

        if (needsResize) {
            bloomFBO?.deleteFramebuffer()
            bloomFBO = Framebuffer(mainFBO.framebufferWidth, mainFBO.framebufferHeight, false).apply {
                setFramebufferColor(0f, 0f, 0f, 0f)
                setFramebufferFilter(GL11.GL_LINEAR)
            }
            bloomFBODepthBuffer = -1
        }

        // mainFBO 的 depthBuffer 可能在运行过程中被重建（即使分辨率不变），需要重新挂载，否则 bloomFBO 可能变为不完整并停止渲染。
        val depthBuffer = mainFBO.depthBuffer
        val fbo = bloomFBO ?: return
        if (depthBuffer > 0 && bloomFBODepthBuffer != depthBuffer) {
            hookDepthBuffer(fbo, depthBuffer)
            bloomFBODepthBuffer = depthBuffer
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
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0)
    }

    @OnDisconnect
    fun onDisconnect() {
        glowRenderCallbacks.clear()
        persistentGlow = false
        BloomConfigManager.clear()
        bloomFBODepthBuffer = -1
    }

    private fun cleanup() {
        bloomFBO?.deleteFramebuffer()
        bloomFBO = null
        bloomFBODepthBuffer = -1
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

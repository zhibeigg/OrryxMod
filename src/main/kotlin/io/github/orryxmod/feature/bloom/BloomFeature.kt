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
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

/**
 * 泛光效果功能模块
 * 为配置匹配的实体添加泛光效果
 */
@Feature("bloom", description = "泛光效果")
object BloomFeature : FeatureBase() {

    // 配置
    object Config {
        var enabled = true
        var maxBloomEntities = 0     // 每帧最大泛光实体数（<=0 表示不限制）
    }

    // 泛光 FBO
    private var bloomFBO: Framebuffer? = null
    private var bloomFBODepthBuffer: Int = -1

    // 待渲染的发光回调 (参数: partialTicks, 可选配置)
    private data class GlowCallback(val callback: (Float) -> Unit, val config: BloomConfig?)
    private val glowRenderCallbacks = java.util.concurrent.CopyOnWriteArrayList<GlowCallback>()

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
            io.github.orryxmod.OrryxMod.logger.info("[Bloom] Shader initialized")
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
     * @param callback 渲染回调函数
     * @param config 可选的泛光配置，用于指定渲染参数
     */
    fun registerGlowRender(callback: (Float) -> Unit, config: BloomConfig? = null) {
        if (!Config.enabled || !ShaderManager.allowedShader()) return
        glowRenderCallbacks.add(GlowCallback(callback, config))
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
        val hasGlow = glowRenderCallbacks.isNotEmpty() || bloomEntities.isNotEmpty()
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
            glowFBO.bindFramebuffer(true)  // 直接绑定并设置视口
            GL11.glClearColor(0f, 0f, 0f, 0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

            GlStateManager.enableDepth()
            GlStateManager.depthMask(false)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)

            // 以"纯色遮罩"方式写入高亮缓冲
            GlStateManager.disableLighting()
            GlStateManager.disableBlend()
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
            GlStateManager.disableTexture2D()
            GlStateManager.bindTexture(0)
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.enableTexture2D()
            GlStateManager.color(1f, 1f, 1f, 1f)

            // 启用多边形偏移，避免 Z-fighting
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

                    GlStateManager.color(1f, 1f, 1f, 1f)
                    renderer.doRender(entity, rx, ry, rz, entity.rotationYaw, partialTicks)

                    // doRender 后恢复状态
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

                    ShaderManager.useProgram(ShaderManager.PROGRAM_MASK)
                    ShaderManager.setUniform4f(ShaderManager.PROGRAM_MASK, "u_color", 1f, 1f, 1f, 1f)
                    ShaderManager.setUniform1i(ShaderManager.PROGRAM_MASK, "u_texture", 0)
                    ShaderManager.setUniform1f(ShaderManager.PROGRAM_MASK, "u_alphaThreshold", 0.1f)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 异常时恢复 OpenGL 状态
                    ShaderManager.releaseProgram()
                    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
                    GlStateManager.enableLighting()
                    GlStateManager.depthMask(true)
                }
            }

            ShaderManager.releaseProgram()
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
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
            lastResultFBO = BloomEffect.renderBlurAndBlend(glowFBO, mainFBO, config, !isFirstGroup)
            isFirstGroup = false
        }

        // 3. 复制结果回主 FBO
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

        // 处理发光回调
        if (glowRenderCallbacks.isNotEmpty()) {
            val callbacks = glowRenderCallbacks.toList()
            glowRenderCallbacks.clear()

            // 按配置分组回调
            val groupedCallbacks = callbacks.groupBy { it.config?.name }

            for ((_, group) in groupedCallbacks) {
                val config = group.first().config

                glowFBO.bindFramebuffer(true)  // 直接绑定并设置视口
                GL11.glClearColor(0f, 0f, 0f, 0f)
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

                GlStateManager.enableDepth()
                GlStateManager.depthMask(false)
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
                GlStateManager.enableBlend()
                GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)

                group.forEach { glowCallback ->
                    try {
                        glowCallback.callback(partialTicks)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                GlStateManager.disableBlend()
                GlStateManager.depthMask(true)

                val resultFBO = BloomEffect.renderBlurAndBlend(glowFBO, mainFBO, config, !isFirstGroup)
                isFirstGroup = false

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

                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
                    GlStateManager.bindTexture(0)
                }
            }
        }

        // 恢复所有 OpenGL 状态
        GL11.glPopAttrib()

        // 确保主 FBO 正确绑定
        mainFBO.bindFramebuffer(true)
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
    }
}

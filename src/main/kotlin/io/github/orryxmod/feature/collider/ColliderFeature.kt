package io.github.orryxmod.feature.collider

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.render.EffectManager
import io.github.orryxmod.util.MC
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.IResourceManagerReloadListener

/**
 * 碰撞箱渲染功能模块
 */
@Feature("collider", description = "碰撞箱渲染")
object ColliderFeature : FeatureBase(), IResourceManagerReloadListener {

    /** 仅影响客户端线框质量，不改变网络碰撞体数据。 */
    @Volatile
    var renderConfig: ColliderRenderConfig = ColliderRenderConfig()

    private var renderer: ColliderRenderer? = null
    private var reloadListenerRegistered = false

    override fun enable() {
        if (enabled) return
        if (renderer == null) {
            val newRenderer = ColliderRenderer { renderConfig }
            if (!EffectManager.addPersistent(newRenderer)) {
                OrryxMod.logger.error("[ColliderFeature] Failed to register renderer")
                return
            }
            renderer = newRenderer
        }
        if (!reloadListenerRegistered) {
            val resourceManager = MC.resourceManager
            if (resourceManager is IReloadableResourceManager) {
                resourceManager.registerReloadListener(this)
                reloadListenerRegistered = true
            }
        }
        syncWorldSession()
        super.enable()
        OrryxMod.logger.info("[ColliderFeature] Enabled")
    }

    override fun disable() {
        if (!enabled) return
        renderer?.clearGpuCache()
        renderer?.let(EffectManager::remove)
        renderer = null
        ColliderManager.clear()
        super.disable()
        OrryxMod.logger.info("[ColliderFeature] Disabled")
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.ColliderShow::class)
    fun onColliderShow(packet: OrryxPacket.ColliderShow) {
        if (!enabled) return
        syncWorldSession()
        val data = ColliderData(
            id = packet.id,
            r = packet.r,
            g = packet.g,
            b = packet.b,
            a = packet.a,
            shape = packet.shapeData
        )
        ColliderManager.add(data)
    }

    @OnPacket(OrryxPacket.ColliderUpdate::class)
    fun onColliderUpdate(packet: OrryxPacket.ColliderUpdate) {
        if (!enabled) return
        syncWorldSession()
        ColliderManager.update(packet.id, packet.shapeData)
    }

    @OnPacket(OrryxPacket.ColliderRemove::class)
    fun onColliderRemove(packet: OrryxPacket.ColliderRemove) {
        syncWorldSession()
        ColliderManager.remove(packet.id)
    }

    @OnDisconnect
    fun onDisconnect() {
        clearColliderState()
    }

    override fun onResourceManagerReload(resourceManager: IResourceManager) {
        clearColliderState()
    }

    private fun syncWorldSession() {
        if (ColliderManager.ensureWorld(MC.world)) {
            renderer?.clearGpuCache()
        }
    }

    private fun clearColliderState() {
        ColliderManager.clear()
        renderer?.clearGpuCache()
    }
}

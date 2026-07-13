package io.github.orryxmod.feature.collider

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.render.EffectManager

/**
 * 碰撞箱渲染功能模块
 */
@Feature("collider", description = "碰撞箱渲染")
object ColliderFeature : FeatureBase() {

    /** 仅影响客户端线框质量，不改变网络碰撞体数据。 */
    @Volatile
    var renderConfig: ColliderRenderConfig = ColliderRenderConfig()

    private var renderer: ColliderRenderer? = null

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
        super.enable()
        OrryxMod.logger.info("[ColliderFeature] Enabled")
    }

    override fun disable() {
        if (!enabled) return
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
        ColliderManager.update(packet.id, packet.shapeData)
    }

    @OnPacket(OrryxPacket.ColliderRemove::class)
    fun onColliderRemove(packet: OrryxPacket.ColliderRemove) {
        ColliderManager.remove(packet.id)
    }

    @OnDisconnect
    fun onDisconnect() {
        ColliderManager.clear()
    }
}

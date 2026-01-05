package io.github.orryxmod.core.event

import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.render.RenderContext

/**
 * 核心事件定义
 */
object Events {

    /**
     * 功能模块启用
     */
    data class FeatureEnabled(val feature: FeatureBase) : Event

    /**
     * 功能模块禁用
     */
    data class FeatureDisabled(val feature: FeatureBase) : Event

    /**
     * 网络包接收（分发前，可拦截）
     */
    data class PacketReceived(
        val packet: OrryxPacket,
        override var cancelled: Boolean = false
    ) : CancellableEvent

    /**
     * 渲染效果添加
     */
    data class EffectAdded(val effect: RenderableEffect) : Event

    /**
     * 渲染效果移除
     */
    data class EffectRemoved(val effect: RenderableEffect) : Event

    /**
     * 客户端 Tick
     */
    data class ClientTick(val phase: Phase) : Event {
        enum class Phase { START, END }
    }

    /**
     * 世界渲染（RenderWorldLast）
     */
    data class WorldRender(
        val partialTicks: Float,
        val context: RenderContext
    ) : Event

    /**
     * 客户端断开连接
     */
    object ClientDisconnected : Event
}

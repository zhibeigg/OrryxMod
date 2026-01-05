package io.github.orryxmod.core.handler

import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.registry.FeatureRegistry
import io.github.orryxmod.core.render.EffectManager
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

/**
 * 断开连接处理器
 * 处理客户端断开服务器连接时的清理工作
 */
object DisconnectHandler {

    @SubscribeEvent
    fun onDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        // 发布断开连接事件
        EventBus.publish(Events.ClientDisconnected)

        // 通知所有功能模块
        FeatureRegistry.notifyDisconnect()

        // 清除所有渲染效果
        EffectManager.clear()
    }
}

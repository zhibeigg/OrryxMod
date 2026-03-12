package io.github.orryxmod.core.handler

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.registry.FeatureRegistry
import io.github.orryxmod.core.render.EffectManager
import io.github.orryxmod.util.MC
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

        // 取消并重建协程 scope，防止旧协程在断开后继续运行
        OrryxMod.resetScope()

        // 清理断裂方块缓存，防止内存泄漏
        OrryxMod.fractureBlock.blockNodes.clear()

        // 将 EffectManager.clear() 调度到主线程执行，
        // 因为 dispose() 可能涉及 GL 操作（如 glDeleteLists），
        // 在网络线程调用会导致 OpenGL 错误或崩溃
        MC.addScheduledTask {
            EffectManager.clear()
        }
    }
}

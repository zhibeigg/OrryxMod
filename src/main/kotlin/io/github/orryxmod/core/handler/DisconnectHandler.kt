package io.github.orryxmod.core.handler

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.EntityTrackerRegistry
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
    fun onDisconnect(@Suppress("UNUSED_PARAMETER") event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        // Forge 会在网络线程派发断线事件；所有可能触及 GL、LWJGL、Baritone
        // 或世界对象的清理必须整体切回 Minecraft 主线程。
        MC.addScheduledTask {
            runCleanupStep("publish disconnect event") {
                EventBus.publish(Events.ClientDisconnected)
            }
            runCleanupStep("notify feature disconnect") {
                FeatureRegistry.notifyDisconnect()
            }
            runCleanupStep("clear fracture cache") {
                OrryxMod.fractureBlock.blockNodes.clear()
            }
            runCleanupStep("clear render effects") {
                EffectManager.clearSessionEffects()
            }
            runCleanupStep("clear entity trackers") {
                EntityTrackerRegistry.clear()
            }
        }
    }

    private fun runCleanupStep(name: String, action: () -> Unit) {
        try {
            action()
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to $name during disconnect", ex)
        }
    }
}

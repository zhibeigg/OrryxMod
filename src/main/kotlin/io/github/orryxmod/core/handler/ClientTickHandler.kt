package io.github.orryxmod.core.handler

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.render.EffectManager
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * 客户端 Tick 处理器
 * 每个游戏 tick 更新效果状态
 */
object ClientTickHandler {

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        try {
            when (event.phase) {
                TickEvent.Phase.START -> {
                    EventBus.publish(Events.ClientTick(Events.ClientTick.Phase.START))
                }
                TickEvent.Phase.END -> {
                    // 更新所有效果
                    EffectManager.update()

                    EventBus.publish(Events.ClientTick(Events.ClientTick.Phase.END))
                }
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("[ClientTickHandler] Error in tick handler", ex)
        }
    }
}

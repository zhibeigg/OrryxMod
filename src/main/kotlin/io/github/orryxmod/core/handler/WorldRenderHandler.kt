package io.github.orryxmod.core.handler

import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.render.EffectManager
import io.github.orryxmod.core.render.RenderContext
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * 世界渲染处理器
 * 在世界渲染完成后渲染所有效果
 */
object WorldRenderHandler {

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val context = RenderContext.create(event.partialTicks)

        // 发布渲染事件
        EventBus.publish(Events.WorldRender(event.partialTicks, context))

        // 渲染所有效果
        EffectManager.render(context)
    }
}

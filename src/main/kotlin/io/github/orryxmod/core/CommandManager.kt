package io.github.orryxmod.core

import io.github.orryxmod.modules.*
import io.github.orryxmod.modules.fractureblock.Shockwave
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object CommandManager {

    private val modules = listOf(
        Aim,
        EntityShow,
        Flicker,
        Ghost,
        MouseCursor,
        PlayerNavigation,
        Shockwave
    )

    const val PREFIX = "."

    @SubscribeEvent
    fun onChat(event: ClientChatEvent) {
        try {
            val args = event.message.split(" ")

            if (args[0].startsWith(PREFIX)) {
                if (args[0].removePrefix(PREFIX) == "test") {
                    modules.first { it.name == args[1] }.test()
                }

                event.isCanceled = true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
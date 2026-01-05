package io.github.orryxmod.core

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.modules.*
import io.github.orryxmod.modules.fractureblock.Shockwave
import net.minecraftforge.client.event.ClientChatEvent
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
        val message = event.message
        if (!message.startsWith(PREFIX)) return

        event.isCanceled = true

        val args = message.split(" ")
        val command = args[0].removePrefix(PREFIX)

        when (command) {
            "test" -> {
                if (args.size < 2) {
                    OrryxMod.logger.warn("Usage: .test <module_name>")
                    OrryxMod.logger.info("Available modules: ${modules.joinToString { it.name }}")
                    return
                }
                val moduleName = args[1]
                val module = modules.find { it.name.equals(moduleName, ignoreCase = true) }
                if (module != null) {
                    module.test()
                    OrryxMod.logger.info("Tested module: ${module.name}")
                } else {
                    OrryxMod.logger.warn("Module not found: $moduleName")
                    OrryxMod.logger.info("Available modules: ${modules.joinToString { it.name }}")
                }
            }
            "modules" -> {
                OrryxMod.logger.info("Available modules: ${modules.joinToString { it.name }}")
            }
            else -> {
                OrryxMod.logger.warn("Unknown command: $command")
            }
        }
    }
}
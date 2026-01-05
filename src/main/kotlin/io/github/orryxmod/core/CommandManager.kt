package io.github.orryxmod.core

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.registry.FeatureRegistry
import net.minecraftforge.client.event.ClientChatEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object CommandManager {

    const val PREFIX = "."

    @SubscribeEvent
    fun onChat(event: ClientChatEvent) {
        val message = event.message
        if (!message.startsWith(PREFIX)) return

        event.isCanceled = true

        val args = message.split(" ")
        val command = args[0].removePrefix(PREFIX)

        when (command) {
            "features" -> {
                val features = FeatureRegistry.getAll()
                OrryxMod.logger.info("Registered features (${features.size}):")
                features.forEach { feature ->
                    val status = if (feature.enabled) "enabled" else "disabled"
                    OrryxMod.logger.info("  - ${feature.metadata.id}: ${feature.metadata.description} [$status]")
                }
            }
            "enable" -> {
                if (args.size < 2) {
                    OrryxMod.logger.warn("Usage: .enable <feature_id>")
                    return
                }
                val featureId = args[1]
                val feature = FeatureRegistry.get(featureId)
                if (feature != null) {
                    feature.enable()
                    OrryxMod.logger.info("Enabled feature: $featureId")
                } else {
                    OrryxMod.logger.warn("Feature not found: $featureId")
                }
            }
            "disable" -> {
                if (args.size < 2) {
                    OrryxMod.logger.warn("Usage: .disable <feature_id>")
                    return
                }
                val featureId = args[1]
                val feature = FeatureRegistry.get(featureId)
                if (feature != null) {
                    feature.disable()
                    OrryxMod.logger.info("Disabled feature: $featureId")
                } else {
                    OrryxMod.logger.warn("Feature not found: $featureId")
                }
            }
            else -> {
                OrryxMod.logger.warn("Unknown command: $command")
                OrryxMod.logger.info("Available commands: .features, .enable <id>, .disable <id>")
            }
        }
    }
}
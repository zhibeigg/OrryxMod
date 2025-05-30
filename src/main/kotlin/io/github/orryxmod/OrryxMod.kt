package io.github.orryxmod

import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.PacketHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.network.FMLEventChannel
import net.minecraftforge.fml.common.network.NetworkRegistry
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(modid = OrryxMod.MOD_ID, name = OrryxMod.MOD_NAME, version = "1.0", acceptedMinecraftVersions = "[1.12.2]", useMetadata = true, clientSideOnly = true)
class OrryxMod {

    companion object {
        const val MOD_ID = "orryxmod"
        const val MOD_NAME = "OrryxMod Client"
        lateinit var network: FMLEventChannel

        internal val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

        val logger: Logger = LogManager.getLogger("OrryxMod")
    }

    @Mod.EventHandler
    fun onInit(e: FMLInitializationEvent) {
        logger.info("loadTextures...")
        FileManager.loadTextures()
        logger.info("loaded!")
    }

    @Mod.EventHandler
    fun preInit(e: FMLPreInitializationEvent) {
        network = NetworkRegistry.INSTANCE.newEventDrivenChannel("orryxmod:main")
        network.register(PacketHandler)
    }
}
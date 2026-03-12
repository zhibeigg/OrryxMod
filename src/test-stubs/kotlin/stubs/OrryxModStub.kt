package io.github.orryxmod

import net.minecraftforge.fml.common.network.FMLEventChannel
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * OrryxMod stub for testing - provides logger and network without MC dependencies
 */
class OrryxMod {
    companion object {
        val logger: Logger = LogManager.getLogger("OrryxMod")
        const val MOD_ID = "orryxmod"
        lateinit var network: FMLEventChannel
    }
}

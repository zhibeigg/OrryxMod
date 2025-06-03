package io.github.orryxmod

import io.github.orryxmod.core.CommandManager
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.PacketHandler
import io.github.orryxmod.modules.fractureblock.FractureBlock
import io.github.orryxmod.modules.fractureblock.FractureBlockTileEntity
import io.github.orryxmod.modules.fractureblock.RenderFractureBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.network.FMLEventChannel
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.registry.GameRegistry
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
        lateinit var FractureBlock: FractureBlock
    }

    @Mod.EventHandler
    fun onInit(e: FMLInitializationEvent) {
        logger.info("loadTextures...")
        FileManager.loadTextures()
        MinecraftForge.EVENT_BUS.register(CommandManager)
        logger.info("loaded!")
    }

    @Mod.EventHandler
    fun preInit(e: FMLPreInitializationEvent) {
        network = NetworkRegistry.INSTANCE.newEventDrivenChannel("orryxmod:main")
        network.register(PacketHandler)

        // 注册TileEntity
        GameRegistry.registerTileEntity(
            FractureBlockTileEntity::class.java,
            ResourceLocation("orryxmod", "FractureBlock")
        )

        // 注册客户端渲染器
        ClientRegistry.bindTileEntitySpecialRenderer(
            FractureBlockTileEntity::class.java,
            RenderFractureBlock()
        )

        FractureBlock = FractureBlock()
    }
}
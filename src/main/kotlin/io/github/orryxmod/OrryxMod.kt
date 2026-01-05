package io.github.orryxmod

import io.github.orryxmod.core.CommandManager
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.PacketHandler
import io.github.orryxmod.core.handler.ClientTickHandler
import io.github.orryxmod.core.handler.DisconnectHandler
import io.github.orryxmod.core.handler.WorldRenderHandler
import io.github.orryxmod.core.network.NetworkHandler
import io.github.orryxmod.core.registry.FeatureRegistry
import io.github.orryxmod.core.registry.FeatureScanner
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


@Mod(modid = OrryxMod.MOD_ID, name = OrryxMod.MOD_NAME, version = OrryxMod.MOD_VERSION, acceptedMinecraftVersions = "[1.12.2]", useMetadata = true, clientSideOnly = true)
class OrryxMod {

    companion object {
        const val MOD_ID = "orryxmod"
        const val MOD_NAME = "OrryxMod Client"
        const val MOD_VERSION = "1.2.3"

        lateinit var network: FMLEventChannel

        internal val scope by lazy { CoroutineScope(Dispatchers.Default + SupervisorJob()) }

        val logger: Logger = LogManager.getLogger("OrryxMod")
        lateinit var fractureBlock: FractureBlock
    }

    @Mod.EventHandler
    fun onInit(e: FMLInitializationEvent) {
        logger.info("loadTextures...")
        FileManager.loadTextures()

        // 注册核心处理器
        MinecraftForge.EVENT_BUS.register(CommandManager)
        MinecraftForge.EVENT_BUS.register(NetworkHandler)
        MinecraftForge.EVENT_BUS.register(DisconnectHandler)
        MinecraftForge.EVENT_BUS.register(WorldRenderHandler)
        MinecraftForge.EVENT_BUS.register(ClientTickHandler)

        // 扫描并注册所有 @Feature 模块
        logger.info("Scanning for feature modules...")
        FeatureScanner.scanAndRegister()

        // 启用所有功能模块
        logger.info("Enabling all features...")
        FeatureRegistry.enableAll()

        logger.info("OrryxMod initialized with ${FeatureRegistry.getAll().size} features!")
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

        fractureBlock = FractureBlock()
    }
}
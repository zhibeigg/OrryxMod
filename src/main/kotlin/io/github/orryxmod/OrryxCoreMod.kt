package io.github.orryxmod

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import org.apache.logging.log4j.LogManager
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("OrryxCoreMod")
class OrryxCoreMod : IFMLLoadingPlugin {

    init {
        val logger = LogManager.getLogger("OrryxMod")

        MixinBootstrap.init() // 初始化 Mixin
        Mixins.addConfigurations("mixins.orryxmod.json", "mixins.baritone.json")

        MixinEnvironment.getDefaultEnvironment().obfuscationContext = "searge"
        logger.info("OrryxMod and Baritone mixins initialised. (${MixinEnvironment.getDefaultEnvironment().obfuscationContext})")
    }

    override fun getASMTransformerClass() = emptyArray<String>()

    override fun getModContainerClass(): String? = null

    override fun getSetupClass(): String? = null

    override fun injectData(data: Map<String, Any>) = Unit

    override fun getAccessTransformerClass(): String? = null
}
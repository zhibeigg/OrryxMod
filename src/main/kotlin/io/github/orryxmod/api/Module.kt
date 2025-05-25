package io.github.orryxmod.api

import net.minecraftforge.common.MinecraftForge

abstract class Module(
    val name: String,
    val description: String
) {

    init {
        MinecraftForge.EVENT_BUS.register(this)
    }

    open fun test() {}
}
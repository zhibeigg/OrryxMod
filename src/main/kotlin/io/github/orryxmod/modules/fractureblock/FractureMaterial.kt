package io.github.orryxmod.modules.fractureblock

import net.minecraft.block.material.MapColor
import net.minecraft.block.material.Material

class FractureMaterial: Material(MapColor.AIR) {

    override fun isSolid(): Boolean {
        return true
    }

    override fun blocksLight(): Boolean {
        return true
    }
}
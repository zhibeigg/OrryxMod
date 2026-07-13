package io.github.orryxmod.feature.fractureblock

import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import org.joml.Quaternionf
import org.joml.Vector3f

class BlockNode(
    val state: IBlockState,
    translate: Vector3f,
    rotation: Quaternionf,
    val bouncing: Double,
    val maxLifeTime: Int
) {
    val originalBlock: Block = state.block
    val translate: Vector3f = Vector3f(translate)
    val rotation: Quaternionf = Quaternionf(rotation)
    var lifeTime: Int = 0
}

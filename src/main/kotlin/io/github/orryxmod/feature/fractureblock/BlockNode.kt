package io.github.orryxmod.feature.fractureblock

import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState

class BlockNode(s: IBlockState) {

    var state: IBlockState = s
    var originalBlock: Block = s.block
}

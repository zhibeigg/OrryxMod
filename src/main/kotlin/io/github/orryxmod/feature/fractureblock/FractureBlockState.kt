package io.github.orryxmod.feature.fractureblock

import com.google.common.collect.ImmutableMap
import io.github.orryxmod.OrryxMod
import net.minecraft.block.material.MapColor
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess

class FractureBlockState(block: FractureBlock): BlockStateContainer.StateImplementation(block, ImmutableMap.of()) {

    override fun <T : Comparable<T>, V : T?> withProperty(property: IProperty<T>, value: V & Any): IBlockState {
        return super.withProperty(property, value)
    }

    override fun getLightValue(world: IBlockAccess, pos: BlockPos): Int {
        return OrryxMod.fractureBlock.blockNodes[pos]?.state?.getLightValue(world, pos)
            ?: super.getLightValue(world, pos)
    }

    override fun getMapColor(world: IBlockAccess, pos: BlockPos): MapColor {
        return OrryxMod.fractureBlock.blockNodes[pos]?.state?.getMapColor(world, pos)
            ?: super.getMapColor(world, pos)
    }

    override fun getLightOpacity(world: IBlockAccess, pos: BlockPos): Int {
        return OrryxMod.fractureBlock.blockNodes[pos]?.state?.getLightOpacity(world, pos)
            ?: super.getLightOpacity(world, pos)
    }

    override fun shouldSideBeRendered(blockAccess: IBlockAccess, pos: BlockPos, facing: EnumFacing): Boolean {
        return true
    }

    override fun getPackedLightmapCoords(worldIn: IBlockAccess, pos: BlockPos): Int {
        return OrryxMod.fractureBlock.blockNodes[pos]?.state?.getPackedLightmapCoords(worldIn, pos)
            ?: super.getPackedLightmapCoords(worldIn, pos)
    }
}
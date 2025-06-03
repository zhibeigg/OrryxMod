package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.MapColor
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*

class FractureBlock() : BlockContainer(FractureMaterial()) {

    companion object {
        val FRACTURE_AABB: AxisAlignedBB = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    }

    init {
        registryName = ResourceLocation(OrryxMod.MOD_ID, "FractureBlock")
        translucent = true
        defaultState = FractureBlockState(this) as IBlockState
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity {
        return FractureBlockTileEntity(defaultState as FractureBlockState)
    }

    @Deprecated("Deprecated in Java")
    override fun getBoundingBox(state: IBlockState, source: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        return FRACTURE_AABB
    }

    @Deprecated("Deprecated in Java")
    @SideOnly(Side.CLIENT)
    override fun shouldSideBeRendered(
        blockState: IBlockState,
        blockAccess: IBlockAccess,
        pos: BlockPos,
        side: EnumFacing,
    ): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun addCollisionBoxToList(
        state: IBlockState,
        worldIn: World,
        pos: BlockPos,
        entityBox: AxisAlignedBB,
        collidingBoxes: MutableList<AxisAlignedBB?>,
        entityIn: Entity?,
        isActualState: Boolean,
    ) {
    }

    @Deprecated("Deprecated in Java")
    override fun isOpaqueCube(state: IBlockState): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun isFullCube(state: IBlockState): Boolean {
        return true
    }

    override fun quantityDropped(random: Random): Int {
        return 0
    }

    override fun onEntityCollision(worldIn: World, pos: BlockPos, state: IBlockState, entityIn: Entity) {
    }

    @SideOnly(Side.CLIENT)
    override fun randomDisplayTick(stateIn: IBlockState, worldIn: World, pos: BlockPos, rand: Random) {
        val d0 = (pos.x.toFloat() + rand.nextFloat()).toDouble()
        val d1 = (pos.y.toFloat() + 0.8f).toDouble()
        val d2 = (pos.z.toFloat() + rand.nextFloat()).toDouble()
        worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0, d1, d2, 0.0, 0.0, 0.0)
    }

    @Deprecated("Deprecated in Java")
    override fun getItem(worldIn: World, pos: BlockPos, state: IBlockState): ItemStack {
        return ItemStack.EMPTY
    }

    @Deprecated("Deprecated in Java")
    override fun getMapColor(state: IBlockState, worldIn: IBlockAccess, pos: BlockPos): MapColor {
        return MapColor.AIR
    }

    @Deprecated("Deprecated in Java")
    override fun getBlockFaceShape(
        worldIn: IBlockAccess,
        state: IBlockState,
        pos: BlockPos,
        face: EnumFacing,
    ): BlockFaceShape {
        return BlockFaceShape.SOLID
    }
}
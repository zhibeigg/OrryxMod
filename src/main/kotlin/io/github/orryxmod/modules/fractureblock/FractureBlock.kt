package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import net.minecraft.block.BlockContainer
import net.minecraft.block.SoundType
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.Explosion
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.concurrent.ConcurrentHashMap

class FractureBlock() : BlockContainer(FractureMaterial()) {

    init {

        registryName = ResourceLocation(OrryxMod.MOD_ID, "FractureBlock")
        translucent = true
        defaultState = FractureBlockState(this) as IBlockState
    }

    val blockNodes: ConcurrentHashMap<BlockPos, BlockNode> = ConcurrentHashMap<BlockPos, BlockNode>()

    fun copyState(pos: BlockPos, state: IBlockState) {
        if (blockNodes.containsKey(pos)) return

        blockNodes.put(pos, BlockNode(state))
    }

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        side: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
    ): Boolean {
        if (blockNodes.containsKey(pos)) {
            val n: BlockNode = blockNodes[pos]!!

            return try {
                n.originalBlock.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
            } catch (t: Throwable) {
                false
            }
        }

        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
    }

    override fun isNormalCube(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.isNormalCube
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isNormalCube(state, world, pos)
    }

    override fun isAir(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isAir(state, world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isAir(state, world, pos)
    }

    override fun isBed(state: IBlockState, world: IBlockAccess, pos: BlockPos, player: Entity?): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isBed(state, world, pos, player)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBed(state, world, pos, player)
    }

    override fun isBedFoot(world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isBedFoot(world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBedFoot(world, pos)
    }

    override fun isBurning(world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isBurning(world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBurning(world, pos)
    }

    override fun isFlammable(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isFlammable(world, pos, face)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isFlammable(world, pos, face)
    }

    override fun isLadder(state: IBlockState, world: IBlockAccess, pos: BlockPos, entity: EntityLivingBase): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.block.isLadder(state, world, pos, entity)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isLadder(state, world, pos, entity)
    }

    override fun isReplaceable(worldIn: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.material.isReplaceable
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return this.material.isReplaceable
    }

    override fun isPassable(worldIn: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.isPassable(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return !this.material.blocksMovement()
    }

    override fun onEntityCollision(worldIn: World, pos: BlockPos, state: IBlockState, entityIn: Entity) {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                n.originalBlock.onEntityCollision(worldIn, pos, state, entityIn)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB? {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.getCollisionBoundingBox(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return FULL_BLOCK_AABB.offset(pos)
    }

    @Deprecated("Deprecated in Java")
    override fun getBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.getBoundingBox(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return FULL_BLOCK_AABB.offset(pos)
    }

    @Deprecated("Deprecated in Java")
    override fun getSelectedBoundingBox(blockState: IBlockState, worldIn: World, pos: BlockPos): AxisAlignedBB {
        return AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    @Deprecated("Deprecated in Java")
    override fun getBlockHardness(blockState: IBlockState, w: World, pos: BlockPos): Float {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.getBlockHardness(w, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return blockState.getBlockHardness(w, pos)
    }

    @Deprecated("Deprecated in Java")
    override fun addCollisionBoxToList(
        state: IBlockState,
        worldIn: World,
        pos: BlockPos,
        entityBox: AxisAlignedBB,
        collidingBoxes: MutableList<AxisAlignedBB?>,
        entityIn: Entity?,
        b: Boolean,
    ) {
        try {
            if (blockNodes.containsKey(pos)) blockNodes.get(pos)!!.state.addCollisionBoxToList(
                worldIn,
                pos,
                entityBox,
                collidingBoxes,
                entityIn,
                b
            )
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun getExplosionResistance(w: World, p: BlockPos, e: Entity?, ex: Explosion): Float {
        if (blockNodes.containsKey(p)) return blockNodes.get(p)!!.originalBlock.getExplosionResistance(w, p, e, ex)

        return super.getExplosionResistance(w, p, e, ex)
    }

    @Deprecated("Deprecated in Java")
    override fun getExplosionResistance(e: Entity): Float {
        if (blockNodes.containsKey(e.position)) return blockNodes.get(e.position)!!.originalBlock.getExplosionResistance(e)

        return super.getExplosionResistance(e)
    }

    override fun getEnchantPowerBonus(w: World, p: BlockPos): Float {
        if (blockNodes.containsKey(p)) return blockNodes.get(p)!!.originalBlock.getEnchantPowerBonus(w, p)

        return super.getEnchantPowerBonus(w, p)
    }

    override fun getFlammability(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int {
        if (blockNodes.containsKey(pos)) return blockNodes.get(pos)!!.originalBlock.getFlammability(world, pos, face)

        return super.getFlammability(world, pos, face)
    }

    override fun getFireSpreadSpeed(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int {
        if (blockNodes.containsKey(pos)) return blockNodes.get(pos)!!.originalBlock.getFireSpreadSpeed(world, pos, face)

        return super.getFireSpreadSpeed(world, pos, face)
    }

    override fun getWeakChanges(world: IBlockAccess, pos: BlockPos): Boolean {
        if (blockNodes.containsKey(pos)) return blockNodes.get(pos)!!.originalBlock.getWeakChanges(world, pos)

        return super.getWeakChanges(world, pos)
    }

    override fun getPickBlock(
        state: IBlockState,
        target: RayTraceResult,
        world: World,
        pos: BlockPos,
        player: EntityPlayer,
    ): ItemStack {
        try {
            if (blockNodes.containsKey(pos)) {
                val node: BlockNode = blockNodes.get(pos)!!

                if (node.originalBlock !== this && node.state.block === node.originalBlock) return blockNodes.get(pos)!!.originalBlock.getPickBlock(node.state, target, world, pos, player)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return ItemStack(Blocks.AIR)
    }

    @Deprecated("Deprecated in Java")
    override fun getWeakPower(
        blockState: IBlockState,
        blockAccess: IBlockAccess,
        pos: BlockPos,
        side: EnumFacing,
    ): Int {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.getWeakPower(blockAccess, pos, side)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return 0
    }

    override fun canPlaceTorchOnTop(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.canPlaceTorchOnTop(state, world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return true
    }

    override fun canPlaceBlockAt(worldIn: World, pos: BlockPos): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.canPlaceBlockAt(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return false
    }

    override fun canPlaceBlockOnSide(worldIn: World, pos: BlockPos, side: EnumFacing): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.canPlaceBlockOnSide(worldIn, pos, side)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return false
    }

    override fun getExtendedState(s: IBlockState, w: IBlockAccess, p: BlockPos): IBlockState {
        try {
            if (blockNodes.containsKey(p)) {
                val n: BlockNode = blockNodes[p]!!

                return n.originalBlock.getExtendedState(s, w, p)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.getExtendedState(s, w, p)
    }

    override fun getSoundType(state: IBlockState, world: World, pos: BlockPos, entity: Entity?): SoundType {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.getSoundType(n.state, world, pos, entity)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.getSoundType(state, world, pos, entity)
    }

    override fun canConnectRedstone(s: IBlockState, w: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.canConnectRedstone(s, w, pos, side)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return false
    }

    override fun onNeighborChange(w: IBlockAccess, pos: BlockPos, p: BlockPos) {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.onNeighborChange(w, pos, p)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.onBlockAdded(worldIn, pos, state)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getDrops(
        world: IBlockAccess,
        pos: BlockPos,
        state: IBlockState,
        fortune: Int,
    ): MutableList<ItemStack?> {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.getDrops(world, pos, state, fortune)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.getDrops(world, pos, state, fortune)
    }

    override fun getExpDrop(state: IBlockState, world: IBlockAccess, pos: BlockPos, fortune: Int): Int {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.originalBlock.getExpDrop(state, world, pos, fortune)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.getExpDrop(state, world, pos, fortune)
    }

    override fun getRenderType(state: IBlockState): EnumBlockRenderType {
        return EnumBlockRenderType.INVISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun isOpaqueCube(state: IBlockState): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
    @SideOnly(Side.CLIENT)
    override fun getAmbientOcclusionLightValue(state: IBlockState): Float {
        return 1.0f
    }

    @Deprecated("Deprecated in Java")
    override fun isSideSolid(baseState: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
        try {
            if (blockNodes.containsKey(pos)) {
                val n: BlockNode = blockNodes[pos]!!

                return n.state.isSideSolid(world, pos, side)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return true
    }

    override fun doesSideBlockRendering(
        state: IBlockState,
        world: IBlockAccess,
        pos: BlockPos,
        face: EnumFacing,
    ): Boolean {
        return false // 默认不阻挡渲染
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity? {
        return FractureBlockTileEntity(defaultState as FractureBlockState)
    }
}
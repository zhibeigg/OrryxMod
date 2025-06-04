package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import net.minecraft.block.BlockContainer
import net.minecraft.block.SoundType
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
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

class FractureBlock() : BlockContainer(FractureMaterial()) {

    init {
        registryName = ResourceLocation(OrryxMod.MOD_ID, "FractureBlock")
        translucent = true
        defaultState = FractureBlockState(this) as IBlockState
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity {
        return FractureBlockTileEntity(defaultState as FractureBlockState)
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
        if (FractureBlockState.containsKey(pos)) {
            val n = FractureBlockState.get(pos)

            return try {
                n.block.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
            } catch (_: Throwable) {
                false
            }
        }

        return super.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
    }

    override fun isNormalCube(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.isNormalCube
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isNormalCube(state, world, pos)
    }

    override fun isAir(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isAir(state, world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isAir(state, world, pos)
    }

    override fun isBed(state: IBlockState, world: IBlockAccess, pos: BlockPos, player: Entity?): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isBed(state, world, pos, player)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBed(state, world, pos, player)
    }

    override fun isBedFoot(world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isBedFoot(world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBedFoot(world, pos)
    }

    override fun isBurning(world: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isBurning(world, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isBurning(world, pos)
    }

    override fun isFlammable(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isFlammable(world, pos, face)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isFlammable(world, pos, face)
    }

    override fun isLadder(state: IBlockState, world: IBlockAccess, pos: BlockPos, entity: EntityLivingBase): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isLadder(state, world, pos, entity)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return super.isLadder(state, world, pos, entity)
    }

    override fun isReplaceable(worldIn: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.material.isReplaceable
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return this.material.isReplaceable
    }

    override fun isPassable(worldIn: IBlockAccess, pos: BlockPos): Boolean {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.block.isPassable(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return !this.material.blocksMovement()
    }

    override fun onEntityCollision(worldIn: World, pos: BlockPos, state: IBlockState, entityIn: Entity) {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                n.block.onEntityCollision(worldIn, pos, state, entityIn)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB? {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.getCollisionBoundingBox(worldIn, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return FULL_BLOCK_AABB.offset(pos)
    }

    @Deprecated("Deprecated in Java")
    override fun getBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        try {
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.getBoundingBox(worldIn, pos)
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
            if (FractureBlockState.containsKey(pos)) {
                val n = FractureBlockState.get(pos)

                return n.getBlockHardness(w, pos)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        return blockState.getBlockHardness(w, pos)
    }

    override fun onBlockHarvested(worldIn: World, pos: BlockPos, state: IBlockState, player: EntityPlayer) {
        try {
            val node = FractureBlockState.get(pos)

            if (worldIn.isRemote && state.block !== node.block && (worldIn.getBlockState(pos).block is FractureBlock || state.block is FractureBlock)) Minecraft.getMinecraft().effectRenderer.addBlockDestroyEffects(pos, node)

            worldIn.removeTileEntity(pos)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
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
            if (FractureBlockState.containsKey(pos)) {
                FractureBlockState.get(pos).addCollisionBoxToList(
                    worldIn,
                    pos,
                    entityBox,
                    collidingBoxes,
                    entityIn,
                    b
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun getExplosionResistance(w: World, p: BlockPos, e: Entity?, ex: Explosion): Float {
        if (FractureBlockState.containsKey(p)) return FractureBlockState.get(p).block.getExplosionResistance(w, p, e, ex)

        return super.getExplosionResistance(w, p, e, ex)
    }

    @Deprecated("Deprecated in Java")
    override fun getExplosionResistance(e: Entity): Float {
        if (FractureBlockState.containsKey(e.position)) return FractureBlockState.get(e.getPosition()).block.getExplosionResistance(
            e
        )

        return super.getExplosionResistance(e)
    }

    override fun getEnchantPowerBonus(w: World, p: BlockPos): Float {
        if (FractureBlockState.containsKey(p)) return FractureBlockState.get(p).block.getEnchantPowerBonus(w, p)

        return super.getEnchantPowerBonus(w, p)
    }

    override fun getFlammability(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.getFlammability(world, pos, face)

        return super.getFlammability(world, pos, face)
    }

    override fun getFireSpreadSpeed(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.getFireSpreadSpeed(world, pos, face)

        return super.getFireSpreadSpeed(world, pos, face)
    }

    override fun getWeakChanges(world: IBlockAccess, pos: BlockPos): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.getWeakChanges(world, pos)

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
            if (FractureBlockState.containsKey(pos)) {
                val node = FractureBlockState.get(pos)

                if (node.block !== this && node.block === node.block) return FractureBlockState.get(pos).block.getPickBlock(node, target, world, pos, player)
            }
        } catch (t: Throwable) {
            throw RuntimeException(t)
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
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).getWeakPower(blockAccess, pos, side)

        return 0
    }

    override fun canPlaceTorchOnTop(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.canPlaceTorchOnTop(state, world, pos)

        return true
    }

    override fun canPlaceBlockAt(worldIn: World, pos: BlockPos): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.canPlaceBlockAt(worldIn, pos)

        return false
    }

    override fun canPlaceBlockOnSide(worldIn: World, pos: BlockPos, side: EnumFacing): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.canPlaceBlockOnSide(
            worldIn,
            pos,
            side
        )

        return false
    }

    override fun getExtendedState(s: IBlockState, w: IBlockAccess, p: BlockPos): IBlockState {
        if (FractureBlockState.containsKey(p)) return FractureBlockState.get(p).block.getExtendedState(s, w, p)

        return super.getExtendedState(s, w, p)
    }

    override fun getSoundType(state: IBlockState, world: World, pos: BlockPos, entity: Entity?): SoundType {
        if (!FractureBlockState.containsKey(pos)) return SoundType.STONE

        val n = FractureBlockState.get(pos)

        return n.block.getSoundType(n, world, pos, entity)
    }

    override fun canConnectRedstone(s: IBlockState, w: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.canConnectRedstone(s, w, pos, side)

        return false
    }

    override fun onNeighborChange(w: IBlockAccess, pos: BlockPos, p: BlockPos) {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.onNeighborChange(w, pos, p)
        super.onNeighborChange(w, pos, p)
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.onBlockAdded(worldIn, pos, state)
        super.onBlockAdded(worldIn, pos, state)
    }

    @Deprecated("Deprecated in Java")
    override fun getDrops(
        world: IBlockAccess,
        pos: BlockPos,
        state: IBlockState,
        fortune: Int,
    ): MutableList<ItemStack?> {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.getDrops(world, pos, state, fortune)

        return super.getDrops(world, pos, state, fortune)
    }

    override fun getExpDrop(state: IBlockState, world: IBlockAccess, pos: BlockPos, fortune: Int): Int {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.getExpDrop(state, world, pos, fortune)

        return super.getExpDrop(state, world, pos, fortune)
    }

    override fun getRenderType(state: IBlockState): EnumBlockRenderType {
        return EnumBlockRenderType.MODEL
    }

    @Deprecated("Deprecated in Java")
    override fun isOpaqueCube(state: IBlockState): Boolean {
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun isSideSolid(baseState: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
        if (FractureBlockState.containsKey(pos)) return FractureBlockState.get(pos).block.isSideSolid(
            baseState,
            world,
            pos,
            side
        )

        return true
    }

    override fun doesSideBlockRendering(
        state: IBlockState,
        world: IBlockAccess,
        pos: BlockPos,
        face: EnumFacing
    ): Boolean {
        // 仅当有原始方块状态时才委托
        if (FractureBlockState.containsKey(pos)) {
            return FractureBlockState.get(pos).block.doesSideBlockRendering(state, world, pos, face)
        }
        return false // 默认不阻挡渲染
    }

    // 添加光照值委托
    override fun getLightValue(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        if (FractureBlockState.containsKey(pos)) {
            return FractureBlockState.get(pos).block.getLightValue(state, world, pos)
        }
        return super.getLightValue(state, world, pos)
    }

    // 添加光照不透明度委托
    override fun getLightOpacity(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        if (FractureBlockState.containsKey(pos)) {
            return FractureBlockState.get(pos).block.getLightOpacity(state, world, pos)
        }
        return super.getLightOpacity(state, world, pos)
    }
}
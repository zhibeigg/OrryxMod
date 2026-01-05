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

class FractureBlock : BlockContainer(FractureMaterial()) {

    val blockNodes: ConcurrentHashMap<BlockPos, BlockNode> = ConcurrentHashMap()

    init {
        registryName = ResourceLocation(OrryxMod.MOD_ID, "FractureBlock")
        translucent = true
        defaultState = FractureBlockState(this) as IBlockState
    }

    fun copyState(pos: BlockPos, state: IBlockState) {
        blockNodes.putIfAbsent(pos, BlockNode(state))
    }

    // ==================== 通用代理方法 ====================

    /**
     * 安全地代理到原始方块的方法，出错时返回默认值
     */
    private inline fun <T> delegateOrDefault(pos: BlockPos, default: T, delegate: (BlockNode) -> T): T {
        return blockNodes[pos]?.let { node ->
            runCatching { delegate(node) }.getOrElse {
                it.printStackTrace()
                default
            }
        } ?: default
    }

    /**
     * 安全地代理到原始方块的方法，出错时执行默认方法
     */
    private inline fun <T> delegateOrElse(pos: BlockPos, default: () -> T, delegate: (BlockNode) -> T): T {
        return blockNodes[pos]?.let { node ->
            runCatching { delegate(node) }.getOrElse {
                it.printStackTrace()
                default()
            }
        } ?: default()
    }

    // ==================== 方块交互 ====================

    override fun onBlockActivated(
        worldIn: World, pos: BlockPos, state: IBlockState,
        playerIn: EntityPlayer, hand: EnumHand, side: EnumFacing,
        hitX: Float, hitY: Float, hitZ: Float
    ): Boolean = delegateOrDefault(pos, false) { node ->
        node.originalBlock.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
    }

    override fun onEntityCollision(worldIn: World, pos: BlockPos, state: IBlockState, entityIn: Entity) {
        blockNodes[pos]?.let { node ->
            runCatching { node.originalBlock.onEntityCollision(worldIn, pos, state, entityIn) }
                .onFailure { it.printStackTrace() }
        }
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        blockNodes[pos]?.let { node ->
            runCatching { node.originalBlock.onBlockAdded(worldIn, pos, state) }
                .onFailure { it.printStackTrace() }
        }
    }

    override fun onNeighborChange(w: IBlockAccess, pos: BlockPos, p: BlockPos) {
        blockNodes[pos]?.let { node ->
            runCatching { node.originalBlock.onNeighborChange(w, pos, p) }
                .onFailure { it.printStackTrace() }
        }
    }

    // ==================== 方块属性 ====================

    override fun isNormalCube(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrElse(pos, { super.isNormalCube(state, world, pos) }) { it.state.isNormalCube }

    override fun isAir(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrElse(pos, { super.isAir(state, world, pos) }) { it.state.block.isAir(state, world, pos) }

    override fun isBed(state: IBlockState, world: IBlockAccess, pos: BlockPos, player: Entity?): Boolean =
        delegateOrElse(pos, { super.isBed(state, world, pos, player) }) { it.state.block.isBed(state, world, pos, player) }

    override fun isBedFoot(world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrElse(pos, { super.isBedFoot(world, pos) }) { it.state.block.isBedFoot(world, pos) }

    override fun isBurning(world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrElse(pos, { super.isBurning(world, pos) }) { it.state.block.isBurning(world, pos) }

    override fun isFlammable(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Boolean =
        delegateOrElse(pos, { super.isFlammable(world, pos, face) }) { it.state.block.isFlammable(world, pos, face) }

    override fun isLadder(state: IBlockState, world: IBlockAccess, pos: BlockPos, entity: EntityLivingBase): Boolean =
        delegateOrElse(pos, { super.isLadder(state, world, pos, entity) }) { it.state.block.isLadder(state, world, pos, entity) }

    override fun isReplaceable(worldIn: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrDefault(pos, material.isReplaceable) { it.state.material.isReplaceable }

    override fun isPassable(worldIn: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrDefault(pos, !material.blocksMovement()) { it.originalBlock.isPassable(worldIn, pos) }

    // ==================== 碰撞和边界框 ====================

    @Deprecated("Deprecated in Java")
    override fun getCollisionBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB? =
        delegateOrDefault(pos, FULL_BLOCK_AABB.offset(pos)) { it.state.getCollisionBoundingBox(worldIn, pos) }

    @Deprecated("Deprecated in Java")
    override fun getBoundingBox(blockState: IBlockState, worldIn: IBlockAccess, pos: BlockPos): AxisAlignedBB =
        delegateOrDefault(pos, FULL_BLOCK_AABB.offset(pos)) { it.state.getBoundingBox(worldIn, pos) }

    @Deprecated("Deprecated in Java")
    override fun getSelectedBoundingBox(blockState: IBlockState, worldIn: World, pos: BlockPos): AxisAlignedBB =
        AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    @Deprecated("Deprecated in Java")
    override fun addCollisionBoxToList(
        state: IBlockState, worldIn: World, pos: BlockPos,
        entityBox: AxisAlignedBB, collidingBoxes: MutableList<AxisAlignedBB?>,
        entityIn: Entity?, b: Boolean
    ) {
        blockNodes[pos]?.let { node ->
            runCatching {
                node.state.addCollisionBoxToList(worldIn, pos, entityBox, collidingBoxes, entityIn, b)
            }.onFailure { throw RuntimeException(it) }
        }
    }

    // ==================== 方块硬度和爆炸 ====================

    @Deprecated("Deprecated in Java")
    override fun getBlockHardness(blockState: IBlockState, w: World, pos: BlockPos): Float =
        delegateOrElse(pos, { blockState.getBlockHardness(w, pos) }) { it.state.getBlockHardness(w, pos) }

    override fun getExplosionResistance(w: World, p: BlockPos, e: Entity?, ex: Explosion): Float =
        delegateOrElse(p, { super.getExplosionResistance(w, p, e, ex) }) { it.originalBlock.getExplosionResistance(w, p, e, ex) }

    @Deprecated("Deprecated in Java")
    override fun getExplosionResistance(e: Entity): Float =
        delegateOrElse(e.position, { super.getExplosionResistance(e) }) { it.originalBlock.getExplosionResistance(e) }

    // ==================== 附魔和火焰 ====================

    override fun getEnchantPowerBonus(w: World, p: BlockPos): Float =
        delegateOrElse(p, { super.getEnchantPowerBonus(w, p) }) { it.originalBlock.getEnchantPowerBonus(w, p) }

    override fun getFlammability(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int =
        delegateOrElse(pos, { super.getFlammability(world, pos, face) }) { it.originalBlock.getFlammability(world, pos, face) }

    override fun getFireSpreadSpeed(world: IBlockAccess, pos: BlockPos, face: EnumFacing): Int =
        delegateOrElse(pos, { super.getFireSpreadSpeed(world, pos, face) }) { it.originalBlock.getFireSpreadSpeed(world, pos, face) }

    // ==================== 红石 ====================

    @Deprecated("Deprecated in Java")
    override fun getWeakPower(blockState: IBlockState, blockAccess: IBlockAccess, pos: BlockPos, side: EnumFacing): Int =
        delegateOrDefault(pos, 0) { it.state.getWeakPower(blockAccess, pos, side) }

    override fun canConnectRedstone(s: IBlockState, w: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean =
        delegateOrDefault(pos, false) { it.originalBlock.canConnectRedstone(s, w, pos, side) }

    // ==================== 放置 ====================

    override fun canPlaceTorchOnTop(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrDefault(pos, true) { it.originalBlock.canPlaceTorchOnTop(state, world, pos) }

    override fun canPlaceBlockAt(worldIn: World, pos: BlockPos): Boolean =
        delegateOrDefault(pos, false) { it.originalBlock.canPlaceBlockAt(worldIn, pos) }

    override fun canPlaceBlockOnSide(worldIn: World, pos: BlockPos, side: EnumFacing): Boolean =
        delegateOrDefault(pos, false) { it.originalBlock.canPlaceBlockOnSide(worldIn, pos, side) }

    // ==================== 状态和声音 ====================

    override fun getWeakChanges(world: IBlockAccess, pos: BlockPos): Boolean =
        delegateOrElse(pos, { super.getWeakChanges(world, pos) }) { it.originalBlock.getWeakChanges(world, pos) }

    override fun getExtendedState(s: IBlockState, w: IBlockAccess, p: BlockPos): IBlockState =
        delegateOrElse(p, { super.getExtendedState(s, w, p) }) { it.originalBlock.getExtendedState(s, w, p) }

    override fun getSoundType(state: IBlockState, world: World, pos: BlockPos, entity: Entity?): SoundType =
        delegateOrElse(pos, { super.getSoundType(state, world, pos, entity) }) { it.originalBlock.getSoundType(it.state, world, pos, entity) }

    // ==================== 掉落物 ====================

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        return blockNodes[pos]?.let { node ->
            runCatching {
                if (node.originalBlock !== this && node.state.block === node.originalBlock) {
                    node.originalBlock.getPickBlock(node.state, target, world, pos, player)
                } else null
            }.getOrNull()
        } ?: ItemStack(Blocks.AIR)
    }

    @Deprecated("Deprecated in Java")
    override fun getDrops(world: IBlockAccess, pos: BlockPos, state: IBlockState, fortune: Int): MutableList<ItemStack?> =
        delegateOrElse(pos, { super.getDrops(world, pos, state, fortune) }) { it.originalBlock.getDrops(world, pos, state, fortune) }

    override fun getExpDrop(state: IBlockState, world: IBlockAccess, pos: BlockPos, fortune: Int): Int =
        delegateOrElse(pos, { super.getExpDrop(state, world, pos, fortune) }) { it.originalBlock.getExpDrop(state, world, pos, fortune) }

    // ==================== 渲染 ====================

    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE

    @Deprecated("Deprecated in Java")
    override fun isOpaqueCube(state: IBlockState): Boolean = false

    @Deprecated("Deprecated in Java")
    @SideOnly(Side.CLIENT)
    override fun getAmbientOcclusionLightValue(state: IBlockState): Float = 1.0f

    @Deprecated("Deprecated in Java")
    override fun isSideSolid(baseState: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        delegateOrDefault(pos, true) { it.state.isSideSolid(world, pos, side) }

    override fun doesSideBlockRendering(state: IBlockState, world: IBlockAccess, pos: BlockPos, face: EnumFacing): Boolean = false

    // ==================== TileEntity ====================

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity? =
        FractureBlockTileEntity(defaultState as FractureBlockState)
}

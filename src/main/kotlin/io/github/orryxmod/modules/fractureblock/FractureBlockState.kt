package io.github.orryxmod.modules.fractureblock

import com.google.common.collect.ImmutableMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.block.Block
import net.minecraft.block.material.MapColor
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import org.joml.Quaternionf
import org.joml.Vector3f

class FractureBlockState(block: FractureBlock): BlockStateContainer.StateImplementation(block, ImmutableMap.of()) {

    private var translate: Vector3f? = null
    private var rotation: Quaternionf? = null
    private var bouncing = 0.0
    private var maxLifeTime = 0

    companion object {

        val ORIGINAL_BLOCK_STATE_CACHE: Int2ObjectMap<IBlockState> = Int2ObjectOpenHashMap()

        fun remove(blockPos: BlockPos) {
            ORIGINAL_BLOCK_STATE_CACHE.remove(blockPos.hashCode())
        }

        fun get(blockPos: BlockPos): IBlockState {
            return ORIGINAL_BLOCK_STATE_CACHE.get(blockPos.hashCode())
        }

        fun containsKey(blockPos: BlockPos): Boolean {
            return ORIGINAL_BLOCK_STATE_CACHE.containsKey(blockPos.hashCode())
        }

        fun reset() {
            ORIGINAL_BLOCK_STATE_CACHE.clear()
        }
    }

    fun setFractureInfo(
        bp: BlockPos,
        originalState: IBlockState,
        translate: Vector3f?,
        rotation: Quaternionf?,
        bouncing: Double,
        maxLifeTime: Int,
    ) {
        ORIGINAL_BLOCK_STATE_CACHE.put(bp.hashCode(), originalState)
        this.translate = translate
        this.rotation = rotation
        this.bouncing = bouncing
        this.maxLifeTime = maxLifeTime
    }

    fun getTranslate(): Vector3f? {
        return this.translate
    }

    fun getRotation(): Quaternionf? {
        return this.rotation
    }

    fun getOriginalBlockState(blockPos: BlockPos): IBlockState {
        return ORIGINAL_BLOCK_STATE_CACHE.get(blockPos.hashCode())
    }

    fun getBouncing(): Double {
        return this.bouncing
    }

    fun getLifeTime(): Int {
        return this.maxLifeTime
    }

    override fun <T : Comparable<T>, V : T?> withProperty(property: IProperty<T>, value: V & Any): IBlockState {
        return super.withProperty(property, value)
    }

    override fun getLightValue(world: IBlockAccess, pos: BlockPos): Int {
        if (containsKey(pos)) return get(pos).getLightValue(world, pos)
        return super.getLightValue(world, pos)
    }

    override fun getMapColor(world: IBlockAccess, pos: BlockPos): MapColor {
        if (containsKey(pos)) return get(pos).getMapColor(world, pos)
        return super.getMapColor(world, pos)
    }

    override fun getLightOpacity(world: IBlockAccess, pos: BlockPos): Int {
        if (containsKey(pos)) return get(pos).getLightOpacity(world, pos)
        return super.getLightOpacity(world, pos)
    }

    override fun shouldSideBeRendered(blockAccess: IBlockAccess, pos: BlockPos, facing: EnumFacing): Boolean {
        return true
    }

    override fun getPackedLightmapCoords(worldIn: IBlockAccess, pos: BlockPos): Int {
        if (containsKey(pos)) return get(pos).getPackedLightmapCoords(worldIn, pos)
        return super.getPackedLightmapCoords(worldIn, pos)
    }
}
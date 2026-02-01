package io.github.orryxmod.feature.fractureblock

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.MC
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.particle.ParticleDigging
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ITickable
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*

class FractureBlockTileEntity(val fractureBlockState: FractureBlockState): TileEntity(), ITickable {

    val translate: Vector3f = fractureBlockState.getTranslate()!!
    val rotation: Quaternionf = fractureBlockState.getRotation()!!
    val seed = MathHelper.getPositionRandom(pos)

    val originalBlockState: IBlockState? by lazy { fractureBlockState.getOriginalBlockState(pos) }

    val bouncing = fractureBlockState.getBouncing()
    val maxLifeTime = fractureBlockState.getLifeTime()
    var lifeTime = 0

    override fun update() {
        if (world.isRemote) {
            val blockState = originalBlockState ?: return

            if (maxLifeTime - lifeTime < 10) {

                val offsetX = world.rand.nextDouble()
                val offsetY = world.rand.nextDouble() * 0.5 + 1
                val offsetZ = world.rand.nextDouble()

                val blockParticle = ParticleDigging.Factory().createParticle(
                    EnumParticleTypes.BLOCK_CRACK.particleID,
                    world,
                    pos.x.toDouble() + offsetX,
                    pos.y.toDouble() + offsetY,
                    pos.z.toDouble() + offsetZ,
                    (Math.random() - 0.5) * 0.3,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * 0.3,
                    Block.getStateId(blockState)
                ) as ParticleDigging

                blockParticle.setMaxAge(10 + Random().nextInt(60))

                MC.effectRenderer.addEffect(blockParticle)
            }
            if (lifeTime++ > maxLifeTime) {
                // 使用 flag 3 恢复原方块 (会自动触发光照和渲染更新)
                world.setBlockState(pos, blockState, 3)
                OrryxMod.fractureBlock.blockNodes.remove(pos)
            }
        }
    }

    override fun getMaxRenderDistanceSquared(): Double {
        return 4096.0
    }

    override fun getRenderBoundingBox(): AxisAlignedBB {
        return INFINITE_EXTENT_AABB
    }

    override fun shouldRenderInPass(pass: Int): Boolean {
        return true
    }

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newSate: IBlockState): Boolean {
        return newSate != oldState
    }
}
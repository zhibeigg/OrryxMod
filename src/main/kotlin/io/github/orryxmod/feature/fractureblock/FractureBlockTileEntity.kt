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

    val translate: Vector3f = fractureBlockState.getTranslate() ?: Vector3f()
    val rotation: Quaternionf = fractureBlockState.getRotation() ?: Quaternionf()
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
        // 使用合理大小的 AABB 替代 INFINITE_EXTENT_AABB，
        // 允许视锥体剔除，避免大量断裂方块时的渲染性能问题
        return AxisAlignedBB(
            pos.x - 1.0, pos.y - 1.0, pos.z - 1.0,
            pos.x + 2.0, pos.y + 2.0, pos.z + 2.0
        )
    }

    override fun shouldRenderInPass(pass: Int): Boolean {
        return true
    }

    override fun invalidate() {
        super.invalidate()
        // TileEntity 被销毁时（区块卸载、世界切换等），清理 blockNodes 条目
        OrryxMod.fractureBlock.blockNodes.remove(pos)
    }

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newSate: IBlockState): Boolean {
        return newSate != oldState
    }
}
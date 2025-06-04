package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.util.MC
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.particle.ParticleDigging
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ITickable
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.common.util.Constants
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*

class FractureBlockTileEntity(val fractureBlockState: FractureBlockState): TileEntity(), ITickable {

    val translate: Vector3f = fractureBlockState.getTranslate()!!
    val rotation: Quaternionf = fractureBlockState.getRotation()!!

    val originalBlockState: IBlockState by lazy { fractureBlockState.getOriginalBlockState(pos) }

    val bouncing = fractureBlockState.getBouncing()
    val maxLifeTime = fractureBlockState.getLifeTime()
    var lifeTime = 0

    override fun update() {
        if (world.isRemote) {
            if (maxLifeTime - lifeTime < 10) {
                val blockParticle = ParticleDigging.Factory().createParticle(
                    EnumParticleTypes.BLOCK_CRACK.particleID,
                    world,
                    pos.x.toDouble(),
                    pos.y.toDouble(),
                    pos.z.toDouble(),
                    (Math.random() - 0.5) * 0.3,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * 0.3,
                    Block.getStateId(originalBlockState)
                ) as ParticleDigging

                blockParticle.setMaxAge(10 + Random().nextInt(60))

                MC.effectRenderer.addEffect(blockParticle)
            }
            if (lifeTime++ > maxLifeTime) {
                world.removeTileEntity(pos)
                world.setBlockState(pos, originalBlockState, Constants.BlockFlags.RERENDER_MAIN_THREAD)
                FractureBlockState.remove(pos)
            }
        }
    }

    override fun shouldRenderInPass(pass: Int): Boolean {
        return true
    }
}
package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import net.minecraft.block.state.IBlockState
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ITickable
import org.joml.Quaternionf
import org.joml.Vector3f

class FractureBlockTileEntity(val fractureBlockState: FractureBlockState): TileEntity(), ITickable {

    val translate: Vector3f = fractureBlockState.getTranslate()!!
    val rotation: Quaternionf = fractureBlockState.getRotation()!!

    val originalBlockState: IBlockState
        get() = fractureBlockState.getOriginalBlockState(pos)

    val bouncing = fractureBlockState.getBouncing()
    val maxLifeTime = fractureBlockState.getLifeTime()
    var lifeTime = 0

    override fun update() {
        if (world.isRemote) {
            if (lifeTime++ > maxLifeTime) {
                world.removeTileEntity(pos)
                world.setBlockState(pos, originalBlockState, 0)
                FractureBlockState.remove(pos)
            }
            OrryxMod.logger.info("FractureBlockTileEntity update end $lifeTime/$maxLifeTime")
        }
    }
}
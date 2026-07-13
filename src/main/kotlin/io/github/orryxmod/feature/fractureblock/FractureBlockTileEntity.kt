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

class FractureBlockTileEntity : TileEntity(), ITickable {

    private var restoringNode: BlockNode? = null

    val fractureNode: BlockNode?
        get() = if (hasWorld()) OrryxMod.fractureBlock.blockNodes[pos] else null

    val seed: Long
        get() = MathHelper.getPositionRandom(pos)

    override fun update() {
        if (!world.isRemote) return

        val node = fractureNode ?: return
        if (node.maxLifeTime - node.lifeTime < 10) {
            spawnRestoreParticle(node)
        }

        if (node.lifeTime++ > node.maxLifeTime) {
            restoreOriginalState(node)
        }
    }

    private fun spawnRestoreParticle(node: BlockNode) {
        val random = world.rand
        val offsetX = random.nextDouble()
        val offsetY = random.nextDouble() * 0.5 + 1.0
        val offsetZ = random.nextDouble()

        val blockParticle = ParticleDigging.Factory().createParticle(
            EnumParticleTypes.BLOCK_CRACK.particleID,
            world,
            pos.x.toDouble() + offsetX,
            pos.y.toDouble() + offsetY,
            pos.z.toDouble() + offsetZ,
            (random.nextDouble() - 0.5) * 0.3,
            random.nextDouble() * 0.5,
            (random.nextDouble() - 0.5) * 0.3,
            Block.getStateId(node.state)
        ) as? ParticleDigging ?: return

        blockParticle.setMaxAge(10 + random.nextInt(60))
        MC.effectRenderer.addEffect(blockParticle)
    }

    private fun restoreOriginalState(node: BlockNode) {
        val fractureBlock = OrryxMod.fractureBlock
        restoringNode = node
        val restored = try {
            world.setBlockState(pos, node.state, 3)
        } finally {
            restoringNode = null
        }

        if (restored || world.getBlockState(pos).block !== fractureBlock) {
            fractureBlock.blockNodes.remove(pos, node)
        }
    }

    override fun getMaxRenderDistanceSquared(): Double = 4096.0

    override fun getRenderBoundingBox(): AxisAlignedBB {
        return AxisAlignedBB(
            pos.x - 1.0, pos.y - 1.0, pos.z - 1.0,
            pos.x + 2.0, pos.y + 2.0, pos.z + 2.0
        )
    }

    override fun shouldRenderInPass(pass: Int): Boolean = pass == 0

    override fun onChunkUnload() {
        val node = fractureNode
        super.onChunkUnload()
        removeNode(node)
    }

    override fun invalidate() {
        val node = restoringNode ?: fractureNode
        super.invalidate()
        removeNode(node)
    }

    private fun removeNode(node: BlockNode?) {
        if (node != null) {
            OrryxMod.fractureBlock.blockNodes.remove(pos, node)
        }
    }

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newState: IBlockState): Boolean {
        return newState != oldState
    }
}

package io.github.orryxmod.feature.shockwave

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.modules.fractureblock.FractureBlockState
import io.github.orryxmod.util.MC
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.Random

/**
 * 冲击波执行器
 */
object ShockwaveExecutor {

    private val random = Random()

    // 不可断裂的方块
    private val UNBREAKABLE_BLOCKS = setOf(
        Blocks.AIR,
        Blocks.WATER,
        Blocks.FLOWING_WATER,
        Blocks.LAVA,
        Blocks.FLOWING_LAVA,
        Blocks.BEDROCK,
        Blocks.BARRIER
    )

    /**
     * 执行冲击波
     */
    fun execute(world: World, config: ShockwaveConfig): Boolean {
        val shape = config.shape

        // 验证起点
        if (!isValidStartPoint(world, shape.center)) {
            return false
        }

        // 收集所有受影响的方块
        val blocks = collectBlocks(world, shape)
            .distinctBy { it.pos }
            .sortedBy { it.distance }
            .toList()

        if (blocks.isEmpty()) {
            return false
        }

        // 处理每个方块
        blocks.forEach { blockData ->
            processBlock(world, blockData, config)
        }

        return true
    }

    /**
     * 验证起点是否有效
     */
    private fun isValidStartPoint(world: World, center: Vector3d): Boolean {
        val pos = BlockPos(center.x.toInt(), center.y.toInt(), center.z.toInt())
        val state = world.getBlockState(pos)
        return state.block != Blocks.AIR
    }

    /**
     * 收集受影响的方块
     */
    private fun collectBlocks(world: World, shape: Shape): Sequence<BlockData> = sequence {
        for (direction in shape.spreadDirections()) {
            val steps = (direction.length * 2).toInt().coerceIn(1, 200)

            for (step in 0..steps) {
                val progress = step.toDouble() / steps
                val x = direction.origin.x + direction.direction.x * direction.length * progress
                val z = direction.origin.z + direction.direction.z * direction.length * progress

                // 寻找地表
                val groundY = findGroundY(world, x, direction.origin.y, z)
                if (groundY != null) {
                    val pos = BlockPos(x.toInt(), groundY, z.toInt())
                    val state = world.getBlockState(pos)

                    if (isBreakable(state)) {
                        yield(
                            BlockData(
                                pos = pos,
                                state = state,
                                distance = direction.length * progress
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * 寻找地表高度
     */
    private fun findGroundY(world: World, x: Double, startY: Double, z: Double): Int? {
        val searchRange = 5
        val baseY = startY.toInt()

        // 向下搜索
        for (dy in 0..searchRange) {
            val pos = BlockPos(x.toInt(), baseY - dy, z.toInt())
            val state = world.getBlockState(pos)
            val aboveState = world.getBlockState(pos.up())

            if (state.block != Blocks.AIR && aboveState.block == Blocks.AIR) {
                return baseY - dy
            }
        }

        // 向上搜索
        for (dy in 1..searchRange) {
            val pos = BlockPos(x.toInt(), baseY + dy, z.toInt())
            val state = world.getBlockState(pos)
            val aboveState = world.getBlockState(pos.up())

            if (state.block != Blocks.AIR && aboveState.block == Blocks.AIR) {
                return baseY + dy
            }
        }

        return null
    }

    /**
     * 检查方块是否可断裂
     */
    private fun isBreakable(state: IBlockState): Boolean {
        return state.block !in UNBREAKABLE_BLOCKS &&
                state.getBlockHardness(MC.world, BlockPos.ORIGIN) >= 0
    }

    /**
     * 处理单个方块
     */
    private fun processBlock(world: World, blockData: BlockData, config: ShockwaveConfig) {
        val fracture = config.fracture
        val rotation = fracture.rotation

        // 计算生命周期
        val lifetime = fracture.baseLifetime +
                random.nextInt(fracture.lifetimeVariance * 2 + 1) - fracture.lifetimeVariance

        // 计算旋转
        val tiltRad = Math.toRadians((rotation.baseTilt + randomVariance(rotation.tiltVariance)).toDouble()).toFloat()
        val yawRad = Math.toRadians(randomVariance(rotation.yawVariance).toDouble()).toFloat()
        val rollRad = Math.toRadians(randomVariance(rotation.rollVariance).toDouble()).toFloat()

        // 创建四元数旋转
        val quaternion = Quaternionf()
            .rotateX(tiltRad)
            .rotateY(yawRad)
            .rotateZ(rollRad)

        // 计算弹跳高度
        val bounceHeight = fracture.bounceMultiplier * (1.0 - blockData.distance / 20.0)

        // 创建断裂方块
        try {
            val fractureBlockState = OrryxMod.fractureBlock.defaultState as FractureBlockState
            fractureBlockState.setFractureInfo(
                blockData.pos,
                blockData.state,
                Vector3f(0f, 0f, 0f),  // translation
                quaternion,
                bounceHeight,
                lifetime
            )

            world.setBlockState(blockData.pos, fractureBlockState, 2)

            // 更新光照
            val chunk = MC.world?.getChunk(blockData.pos)
            chunk?.resetRelightChecks()
            chunk?.isLightPopulated = true
        } catch (ex: Exception) {
            OrryxMod.logger.debug("Failed to create fracture block at ${blockData.pos}: ${ex.message}")
        }

        // 生成粒子
        if (config.particles.enabled) {
            spawnParticles(world, blockData, config.particles)
        }
    }

    /**
     * 生成粒子效果
     */
    private fun spawnParticles(world: World, blockData: BlockData, config: ParticleConfig) {
        val pos = blockData.pos
        for (i in 0 until config.density) {
            val vx = (random.nextDouble() - 0.5) * config.velocityMultiplier
            val vy = random.nextDouble() * config.velocityMultiplier
            val vz = (random.nextDouble() - 0.5) * config.velocityMultiplier

            world.spawnParticle(
                net.minecraft.util.EnumParticleTypes.BLOCK_CRACK,
                pos.x + 0.5,
                pos.y + 1.0,
                pos.z + 0.5,
                vx, vy, vz,
                net.minecraft.block.Block.getStateId(blockData.state)
            )
        }
    }

    private fun randomVariance(variance: Float): Float {
        return (random.nextFloat() * 2 - 1) * variance
    }

    /**
     * 方块数据
     */
    private data class BlockData(
        val pos: BlockPos,
        val state: IBlockState,
        val distance: Double
    )
}

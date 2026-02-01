package io.github.orryxmod.feature.shockwave

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.feature.fractureblock.FractureBlockState
import io.github.orryxmod.util.MC
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.EnumSkyBlock
import net.minecraft.world.World
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.*

/**
 * 冲击波执行器
 * 从老模块 modules/fractureblock/Shockwave.kt 迁移完整算法
 */
object ShockwaveExecutor {

    // 冲击波方向 (向下)
    private val IMPACT_DIRECTION = Vector3d(0.0, -1.0, 0.0)

    /**
     * 执行冲击波 - 使用新的 DSL 配置（保留兼容性）
     */
    fun execute(world: World, config: ShockwaveConfig): Boolean {
        return when (val shape = config.shape) {
            is CircleShape -> circleSlamFracture(world, shape.center, shape.radius)
            is SquareShape -> squareSlamFracture(world, shape.center, shape.length, shape.width, shape.yaw)
            is SectorShape -> sectorSlamFracture(world, shape.center, shape.radius, shape.angle, shape.yaw)
        }
    }

    /**
     * 圆形冲击波
     */
    fun circleSlamFracture(x: Double, y: Double, z: Double, radius: Double): Boolean {
        val world = MC.world ?: return false
        return circleSlamFracture(world, Vector3d(x, y, z), radius)
    }

    fun circleSlamFracture(world: World, center: Vector3d, radius: Double): Boolean {
        val effectiveRadius = max(0.5, radius)
        val adjustedCenter = adjustCenterToGrid(center)

        val blockPos = BlockPos(adjustedCenter.x, adjustedCenter.y, adjustedCenter.z)
        val originBlockState = world.getBlockState(blockPos)

        if (!canTransferShockwave(world, blockPos, originBlockState)) {
            return false
        }

        val xFrom = floor(adjustedCenter.x - effectiveRadius).toInt()
        val xTo = ceil(adjustedCenter.x + effectiveRadius).toInt()
        val zFrom = floor(adjustedCenter.z - effectiveRadius).toInt()
        val zTo = ceil(adjustedCenter.z + effectiveRadius).toInt()

        // 遍历正方形边缘
        for (i in zFrom..zTo) {
            var j = xFrom
            while (j <= xTo) {
                // 得到径向向外扩散向量
                val direction = Vector3d(j - adjustedCenter.x + 0.1, 0.0, i - adjustedCenter.z)
                spreadShockwave(world, adjustedCenter, direction, effectiveRadius, j, i)
                j += if (i == zFrom || i == zTo) 1 else xTo - xFrom
            }
        }

        return true
    }

    /**
     * 方形冲击波
     */
    fun squareSlamFracture(x: Double, y: Double, z: Double, length: Double, width: Double, yaw: Double): Boolean {
        val world = MC.world ?: return false
        return squareSlamFracture(world, Vector3d(x, y, z), length, width, yaw)
    }

    fun squareSlamFracture(world: World, center: Vector3d, length: Double, width: Double, yaw: Double): Boolean {
        val effectiveLength = max(0.5, length)
        val effectiveWidth = max(0.5, width)
        val adjustedCenter = adjustCenterToGrid(center)

        val blockPos = BlockPos(adjustedCenter.x, adjustedCenter.y, adjustedCenter.z)
        val originBlockState = world.getBlockState(blockPos)

        if (!canTransferShockwave(world, blockPos, originBlockState)) {
            return false
        }

        // 扩散方向
        val direction = Vector3d(0.0, 0.0, 1.0).rotateY(Math.toRadians(-yaw))
        // 左右偏移方向
        val offsetDir = Vector3d(0.0, 1.0, 0.0).cross(direction)

        val offsetL = (-effectiveWidth / 2).toInt()
        val offsetR = (effectiveWidth / 2).toInt()

        for (i in offsetL..offsetR) {
            val newCenter = adjustedCenter.add(offsetDir.normalize(i.toDouble(), Vector3d()), Vector3d())
            val edge = newCenter.add(direction.normalize(effectiveLength, Vector3d()), Vector3d())
            spreadShockwave(world, newCenter, direction, effectiveLength, edge.x.toInt(), edge.z.toInt())
        }

        return true
    }

    /**
     * 扇形冲击波
     */
    fun sectorSlamFracture(x: Double, y: Double, z: Double, radius: Double, angle: Double, yaw: Double): Boolean {
        val world = MC.world ?: return false
        return sectorSlamFracture(world, Vector3d(x, y, z), radius, angle, yaw)
    }

    fun sectorSlamFracture(world: World, center: Vector3d, radius: Double, angle: Double, yaw: Double): Boolean {
        val effectiveRadius = max(0.5, radius)
        val adjustedCenter = adjustCenterToGrid(center)

        val blockPos = BlockPos(adjustedCenter.x, adjustedCenter.y, adjustedCenter.z)
        val originBlockState = world.getBlockState(blockPos)

        if (!canTransferShockwave(world, blockPos, originBlockState)) {
            return false
        }

        // 扩散中线方向
        val midDirection = Vector3d(0.0, 0.0, 1.0).rotateY(Math.toRadians(-yaw))

        val xFrom = floor(adjustedCenter.x - effectiveRadius).toInt()
        val xTo = ceil(adjustedCenter.x + effectiveRadius).toInt()
        val zFrom = floor(adjustedCenter.z - effectiveRadius).toInt()
        val zTo = ceil(adjustedCenter.z + effectiveRadius).toInt()

        // 遍历正方形边缘
        for (i in zFrom..zTo) {
            var j = xFrom
            while (j <= xTo) {
                // 得到径向向外扩散向量
                val direction = Vector3d(j - adjustedCenter.x + 0.1, 0.0, i - adjustedCenter.z)
                if (direction.angle(midDirection) <= Math.toRadians(angle / 2)) {
                    spreadShockwave(world, adjustedCenter, direction, effectiveRadius, j, i)
                }
                j += if (i == zFrom || i == zTo) 1 else xTo - xFrom
            }
        }

        return true
    }

    /**
     * 调整中心点到网格
     */
    private fun adjustCenterToGrid(center: Vector3d): Vector3d {
        val closestEdge = Vector3d(
            center.x.roundToInt().toDouble(),
            floor(center.y),
            center.z.roundToInt().toDouble()
        )
        val centerOfBlock = Vector3d(
            floor(center.x) + 0.5,
            floor(center.y),
            floor(center.z) + 0.5
        )

        return if (closestEdge.distanceSquared(center) < centerOfBlock.distanceSquared(center)) {
            closestEdge
        } else {
            centerOfBlock
        }
    }

    /**
     * 扩散冲击波效果
     */
    private fun spreadShockwave(
        world: World,
        center: Vector3d,
        direction: Vector3d,
        length: Double,
        edgeX: Int,
        edgeZ: Int
    ) {
        // 计算冲击波边缘点
        val normalizedDir = direction.normalize(length, Vector3d())
        val edgeOfShockwave = center.add(normalizedDir, Vector3d())

        // 计算影响区域边界
        val xFrom = min(floor(center.x).toInt(), edgeX)
        val xTo = max(floor(center.x).toInt(), edgeX)
        val zFrom = min(floor(center.z).toInt(), edgeZ)
        val zTo = max(floor(center.z).toInt(), edgeZ)

        // 计算弹跳系数
        val bounceExponentCoef = min(1.0 / (length * length), 0.1)

        // 收集受影响的方块坐标
        val affectedBlocks = mutableListOf<BlockPos>()
        for (z in zFrom..zTo) {
            for (x in xFrom..xTo) {
                val blockCenter = Vector3d(x + 0.5, center.y, z + 0.5)
                if (isBlockOverlapLine(blockCenter, center, edgeOfShockwave)) {
                    affectedBlocks.add(BlockPos(x, center.y.toInt(), z))
                }
            }
        }

        // 按距离中心点距离排序 (由近到远)
        affectedBlocks.sortBy { pos ->
            (pos.x - center.x).pow(2) + (pos.z - center.z).pow(2)
        }

        // 处理每个受影响方块
        var currentY = center.y.toInt()
        for (pos in affectedBlocks) {
            var finalPos = BlockPos(pos.x, currentY, pos.z)
            var state = world.getBlockState(finalPos)

            if (state is FractureBlockState) continue

            // 处理方块上方传递
            val abovePos = finalPos.up()
            val aboveState = world.getBlockState(abovePos)

            if (canTransferShockwave(world, abovePos, aboveState)) {
                val aboveTwoPos = abovePos.up()
                val aboveTwoState = world.getBlockState(aboveTwoPos)

                if (!canTransferShockwave(world, aboveTwoPos, aboveTwoState)) {
                    currentY++
                    finalPos = abovePos
                    state = aboveState
                } else {
                    break
                }
            }

            // 处理方块下方传递
            if (!canTransferShockwave(world, finalPos, state)) {
                val belowPos = finalPos.down()
                val belowState = world.getBlockState(belowPos)

                if (canTransferShockwave(world, belowPos, belowState)) {
                    currentY--
                    finalPos = belowPos
                    state = belowState
                } else {
                    break
                }
            }

            // 距离检查
            val blockCenter = Vector3d(finalPos.x + 0.5, finalPos.y.toDouble(), finalPos.z + 0.5)
            val centerToBlock = blockCenter.sub(center, Vector3d())
            val distance = centerToBlock.length()

            if (distance > length) continue

            // 客户端渲染断裂效果
            if (world.isRemote) {
                if (!canTransferShockwave(world, finalPos, state)) {
                    continue
                }

                // 计算旋转轴 (当 distance 接近 0 时使用默认轴)
                val axis: Vector3f = if (distance < 0.01) {
                    // 中心方块：使用随机轴避免除零
                    Vector3f(
                        world.rand.nextFloat() - 0.5f,
                        0f,
                        world.rand.nextFloat() - 0.5f
                    ).normalize()
                } else {
                    val rotAxis = IMPACT_DIRECTION.cross(centerToBlock, Vector3d()).normalize()
                    Vector3f(rotAxis.x.toFloat(), rotAxis.y.toFloat(), rotAxis.z.toFloat())
                }

                // 计算位移和旋转
                val translator = Vector3f(
                    0f,
                    max(0f, (distance / length).toFloat() - 0.5f) * 0.8f,
                    0f
                )

                // 创建旋转四元数
                val rotator = Quaternionf().rotateAxis(
                    ((distance.toFloat() / length.toFloat()) * 15.0f + world.rand.nextFloat() * 10.0f - 5.0f).toRadians(),
                    axis
                )

                // 添加随机旋转
                rotator.rotateX((world.rand.nextFloat() * 15.0f - 7.5f).toRadians())
                rotator.rotateY((world.rand.nextFloat() * 40.0f - 20.0f).toRadians())
                rotator.rotateZ((world.rand.nextFloat() * 15.0f - 7.5f).toRadians())

                // 计算弹跳效果
                val bouncing = distance.pow(2) * bounceExponentCoef
                val lifetime = (length * 20).toInt() + world.rand.nextInt(30)

                // 创建断裂效果
                createFractureEffect(world, finalPos, state, translator, rotator, bouncing, lifetime)

                // 生成粒子
                spawnBreakParticles(world, finalPos, state)
            }
        }
    }

    /**
     * 检查方块是否与线段重叠
     */
    private fun isBlockOverlapLine(
        blockCenter: Vector3d,
        lineStart: Vector3d,
        lineEnd: Vector3d
    ): Boolean {
        val lineVec = lineEnd.sub(lineStart, Vector3d())
        val pointVec = blockCenter.sub(lineStart, Vector3d())
        val lineLengthSquared = lineVec.lengthSquared()

        if (lineLengthSquared < 1e-7) {
            return blockCenter.distanceSquared(lineStart) < 0.7 * 0.7
        }

        val t = max(0.0, min(1.0, pointVec.dot(lineVec) / lineLengthSquared))
        val projection = lineStart.add(lineVec.mul(t, Vector3d()), Vector3d())

        val distanceSquared = blockCenter.distanceSquared(projection)
        return distanceSquared < 0.7 * 0.7
    }

    /**
     * 能否传递冲击波
     */
    private fun canTransferShockwave(world: World, pos: BlockPos, state: IBlockState): Boolean {
        return state.isOpaqueCube && !state.block.isAir(state, world, pos)
    }

    /**
     * 生成方块破坏粒子
     */
    private fun spawnBreakParticles(world: World, pos: BlockPos, state: IBlockState) {
        repeat(8) {
            val offsetX = world.rand.nextDouble()
            val offsetY = world.rand.nextDouble() * 0.5 + 1
            val offsetZ = world.rand.nextDouble()

            world.spawnParticle(
                EnumParticleTypes.BLOCK_CRACK,
                pos.x + offsetX,
                pos.y + offsetY,
                pos.z + offsetZ,
                (offsetX - 0.5) * 0.5,
                (offsetY - 0.75) * 0.5,
                (offsetZ - 0.5) * 0.5,
                Block.getStateId(state)
            )
        }
    }

    /**
     * 创建断裂效果
     */
    private fun createFractureEffect(
        world: World,
        pos: BlockPos,
        state: IBlockState,
        translation: Vector3f,
        rotation: Quaternionf,
        bounce: Double,
        lifetime: Int
    ) {
        val fractureBlockState = OrryxMod.fractureBlock.defaultState as FractureBlockState
        fractureBlockState.setFractureInfo(pos, state, translation, rotation, bounce, lifetime)

        world.setBlockState(pos, fractureBlockState, 3)

        // 正确触发光照更新
        world.checkLightFor(EnumSkyBlock.BLOCK, pos)
        world.checkLightFor(EnumSkyBlock.SKY, pos)

        // 通知周围方块更新渲染
        world.markBlockRangeForRenderUpdate(
            pos.add(-1, -1, -1),
            pos.add(1, 1, 1)
        )
    }

    /**
     * 角度转弧度
     */
    private fun Float.toRadians(): Float {
        return this * (Math.PI.toFloat() / 180f)
    }
}

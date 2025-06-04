package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.api.Module
import io.github.orryxmod.util.MC
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameRules
import net.minecraft.world.World
import net.minecraftforge.common.util.Constants
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.*

object Shockwave: Module("Shockwave", "地面冲击波") {

    override fun test() {
        circleSlamFracture(MC.world, Vector3d(MC.player.posX, MC.player.posY-0.2, MC.player.posZ), 10.0)
    }

    // 冲击波方向 (向下)
    val IMPACT_DIRECTION = Vector3d(0.0, -1.0, 0.0)

    /**
     * 扩散冲击波效果
     * @param world 当前世界
     * @param center 冲击波中心点
     * @param direction 冲击波方向
     * @param length 冲击波长度
     * @param edgeX 冲击波边缘X坐标
     * @param edgeZ 冲击波边缘Z坐标
     */
    fun spreadShockwave(
        world: World,
        center: Vector3d,
        direction: Vector3d,
        length: Double,
        edgeX: Int,
        edgeZ: Int,
    ) {
        // 计算冲击波边缘点
        val normalizedDir = direction.normalize(length, Vector3d())
        val edgeOfShockwave = center.add(normalizedDir, Vector3d()) // 边缘点

        // 计算影响区域边界
        val xFrom = min(floor(center.x).toInt(), edgeX)
        val xTo = max(floor(center.x).toInt(), edgeX)
        val zFrom = min(floor(center.z).toInt(), edgeZ)
        val zTo = max(floor(center.z).toInt(), edgeZ)

        // 计算弹跳系数
        val bounceExponentCoef = min(1.0 / (length * length), 0.1)

        // 5. 收集受影响的方块坐标
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

            // 处理方块上方
//            val abovePos = finalPos.up()
//            val aboveState = world.getBlockState(abovePos)
//
//            if (canTransferShockwave(world, abovePos, aboveState)) {
//                val aboveTwoPos = abovePos.up()
//                val aboveTwoState = world.getBlockState(aboveTwoPos)
//
//                OrryxMod.logger.info("!canTransferShockwave ${!canTransferShockwave(world, aboveTwoPos, aboveTwoState)}")
//                if (!canTransferShockwave(world, aboveTwoPos, aboveTwoState)) {
//                    currentY++
//                    finalPos = abovePos
//                    state = aboveState
//                } else {
//                    break
//                }
//            } else {
//                val belowPos = finalPos.down()
//                val belowState = world.getBlockState(belowPos)
//
//                OrryxMod.logger.info("canTransferShockwave ${canTransferShockwave(world, belowPos, belowState)}")
//                if (canTransferShockwave(world, belowPos, belowState)) {
//                    currentY--
//                    finalPos = belowPos
//                    state = belowState
//                } else {
//                    break
//                }
//            }

            // 距离检查
            val blockCenter = Vector3d(
                finalPos.x + 0.5,
                finalPos.y.toDouble(),
                finalPos.z + 0.5
            )
            val centerToBlock = blockCenter.sub(center, Vector3d())
            val distance = centerToBlock.length()

            if (distance > length) continue

            // 客户端渲染断裂效果
            if (world.isRemote) {
                if (!canTransferShockwave(world, finalPos, state)) {
                    continue
                }

                // 计算旋转轴
                val rotAxis = IMPACT_DIRECTION.cross(centerToBlock, Vector3d()).normalize()
                val axis = Vector3f(rotAxis.x.toFloat(), rotAxis.y.toFloat(), rotAxis.z.toFloat())

                // 计算位移和旋转
                val translator = Vector3f(
                    0f,
                    max(0f, (distance / length).toFloat() - 0.5f) * 0.5f,
                    0f
                )

                // 创建旋转四元数
                val rotator = Quaternionf().rotateAxis(
                    ((distance.toFloat() / length.toFloat()) * 15.0F + world.rand.nextFloat() * 10.0F - 5.0F).toRadians(),
                    axis
                )

                // 添加随机旋转
                rotator.rotateX((world.rand.nextFloat() * 15.0F - 7.5F).toRadians())
                rotator.rotateY((world.rand.nextFloat() * 40.0F - 20.0F).toRadians())
                rotator.rotateZ((world.rand.nextFloat() * 15.0F - 7.5F).toRadians())

                // 计算弹跳效果
                val bouncing = distance.pow(2) * bounceExponentCoef
                val lifetime = 30 + world.rand.nextInt((length * 80).toInt())

                // 创建断裂效果
                createFractureEffect(world, finalPos, state, translator, rotator, bouncing, lifetime)

                // 生成粒子
                spawnBreakParticles(world, finalPos, state)
            }
        }
    }

    fun circleSlamFracture(world: World, center: Vector3d, radius: Double): Boolean {
        var center = center
        var radius = radius
        val closestEdge = Vector3d(center.x.roundToInt().toDouble(), floor(center.y), center.z.roundToInt().toDouble())
        val centerOfBlock = Vector3d(floor(center.x) + 0.5, floor(center.y), floor(center.z) + 0.5)

        center = if (closestEdge.distanceSquared(center) < centerOfBlock.distanceSquared(center)) {
            closestEdge
        } else {
            centerOfBlock
        }

        val blockPos = BlockPos(center.x, center.y, center.z)
        val originBlockState = world.getBlockState(blockPos)

        if (!canTransferShockwave(world, blockPos, originBlockState)) {
            return false
        }

        radius = max(0.5, radius)

        val xFrom = floor(center.x - radius).toInt()
        val xTo = ceil(center.x + radius).toInt()
        val zFrom = floor(center.z - radius).toInt()
        val zTo = ceil(center.z + radius).toInt()

        // 遍历正方形边缘
        for (i in zFrom..zTo) {
            var j = xFrom
            while (j <= xTo) {
                // 得到径向向外扩散向量
                val direction = Vector3d(j - center.x + 0.1, 0.0, i - center.z)
                spreadShockwave(
                    world,
                    center,
                    direction,
                    radius,
                    j,
                    i
                )
                OrryxMod.logger.info("x: $j z: $i")
                j += if (i == zFrom || i == zTo) 1 else xTo - xFrom
            }
        }

        return true
    }

    /**
     * 检查方块是否与线段重叠
     * @param blockCenter 方块中心点
     * @param lineStart 线段起点
     * @param lineEnd 线段终点
     */
    private fun isBlockOverlapLine(
        blockCenter: Vector3d,
        lineStart: Vector3d,
        lineEnd: Vector3d,
    ): Boolean {
        // 线段起点 --> 终点
        val lineVec = lineEnd.sub(lineStart, Vector3d())
        // 线段起点 --> 方块原点
        val pointVec = blockCenter.sub(lineStart, Vector3d())
        // 线段长度
        val lineLengthSquared = lineVec.lengthSquared()

        // 如果线段长度为0，则直接计算点到点的距离
        if (lineLengthSquared < 1e-7) {
            return blockCenter.distanceSquared(lineStart) < 0.7 * 0.7
        }

        // 计算投影比例
        val t = max(0.0, min(1.0, pointVec.dot(lineVec) / lineLengthSquared))
        val projection = lineStart.add(lineVec.mul(t, Vector3d()), Vector3d())

        // 计算距离平方
        val distanceSquared = blockCenter.distanceSquared(projection)
        return distanceSquared < 0.7 * 0.7
    }

    /**
     * 能否传递冲击波
     * @return 固体方块返回 true
     */
    private fun canTransferShockwave(
        world: World,
        pos: BlockPos,
        state: IBlockState,
    ): Boolean {
        return state.isOpaqueCube && !state.block.isAir(state, world, pos)
    }

    /**
     * 生成方块破坏粒子
     */
    private fun spawnBreakParticles(
        world: World,
        pos: BlockPos,
        state: IBlockState,
    ) {
        repeat(8) {
            val offsetX = world.rand.nextDouble()
            val offsetY = world.rand.nextDouble() * 0.5 + 0.5
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
        lifetime: Int,
    ) {
        val fractureBlockState: FractureBlockState = OrryxMod.FractureBlock.defaultState as FractureBlockState
        fractureBlockState.setFractureInfo(pos, state, translation, rotation, bounce, lifetime)

        world.setBlockState(pos, fractureBlockState, Constants.BlockFlags.RERENDER_MAIN_THREAD)
    }

    /**
     * 角度转弧度
     */
    private fun Float.toRadians(): Float {
        return this * (Math.PI.toFloat() / 180f)
    }
}
package io.github.orryxmod.feature.shockwave

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.feature.fractureblock.BlockNode
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
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 冲击波执行器。所有世界读取与修改均由客户端主线程的 tick 分批完成。
 */
object ShockwaveExecutor {

    private const val MIN_VECTOR_LENGTH_SQUARED = 1e-7

    private val pendingTasks = ArrayDeque<ShockwaveTask>()
    private var taskWorld: World? = null

    /**
     * 校验起点并将冲击波加入主线程队列。
     */
    fun execute(world: World, config: ShockwaveConfig): Boolean {
        val center = adjustCenterToGrid(config.shape.center) ?: return false
        val originPos = BlockPos(center.x, center.y, center.z)
        val originState = world.getBlockState(originPos)
        if (!canTransferShockwave(world, originPos, originState)) return false

        val raySource = createRaySource(config.shape, center) ?: return false
        if (taskWorld !== null && taskWorld !== world) clear()

        val maxQueuedTasks = config.performance.maxQueuedTasks.coerceIn(0, 32)
        if (maxQueuedTasks == 0 || pendingTasks.size >= maxQueuedTasks) {
            OrryxMod.logger.warn("[Shockwave] Task queue limit reached ($maxQueuedTasks), rejecting effect")
            return false
        }

        taskWorld = world
        pendingTasks.addLast(ShockwaveTask(world, config, center, raySource))
        return true
    }

    /**
     * 每个客户端 tick 只处理一个任务批次，避免多个冲击波叠加突破单 tick 预算。
     */
    fun processTick(world: World?) {
        if (world == null) {
            clear()
            return
        }
        if (taskWorld !== null && taskWorld !== world) {
            clear()
            return
        }

        val task = pendingTasks.pollFirst() ?: return
        if (task.world !== world) return
        if (!task.processBatch()) pendingTasks.addLast(task)
        if (pendingTasks.isEmpty()) taskWorld = null
    }

    fun clear() {
        pendingTasks.clear()
        taskWorld = null
    }

    fun circleSlamFracture(x: Double, y: Double, z: Double, radius: Double): Boolean {
        val world = MC.world ?: return false
        return circleSlamFracture(world, Vector3d(x, y, z), radius)
    }

    fun circleSlamFracture(world: World, center: Vector3d, radius: Double): Boolean =
        execute(world, ShockwaveConfig(CircleShape(center, radius)))

    fun squareSlamFracture(x: Double, y: Double, z: Double, length: Double, width: Double, yaw: Double): Boolean {
        val world = MC.world ?: return false
        return squareSlamFracture(world, Vector3d(x, y, z), length, width, yaw)
    }

    fun squareSlamFracture(
        world: World,
        center: Vector3d,
        length: Double,
        width: Double,
        yaw: Double
    ): Boolean = execute(world, ShockwaveConfig(SquareShape(center, length, width, yaw)))

    fun sectorSlamFracture(x: Double, y: Double, z: Double, radius: Double, angle: Double, yaw: Double): Boolean {
        val world = MC.world ?: return false
        return sectorSlamFracture(world, Vector3d(x, y, z), radius, angle, yaw)
    }

    fun sectorSlamFracture(
        world: World,
        center: Vector3d,
        radius: Double,
        angle: Double,
        yaw: Double
    ): Boolean = execute(world, ShockwaveConfig(SectorShape(center, radius, angle, yaw)))

    private data class GridCenter(val x: Double, val y: Double, val z: Double)

    private data class ShockwaveRay(
        val originX: Double,
        val originZ: Double,
        val directionX: Double,
        val directionZ: Double,
        val length: Double
    )

    private data class PlanarNode(val x: Int, val z: Int)

    private interface RaySource {
        fun nextRay(): ShockwaveRay?
    }

    private class BoundaryRaySource(
        private val center: GridCenter,
        private val length: Double,
        private val xFrom: Int,
        private val xTo: Int,
        private val zFrom: Int,
        private val zTo: Int,
        private val acceptsDirection: (Double, Double) -> Boolean
    ) : RaySource {
        private var x = xFrom
        private var z = zFrom
        private var finished = false

        override fun nextRay(): ShockwaveRay? {
            while (!finished) {
                val edgeX = x
                val edgeZ = z
                advance()

                val directionX = edgeX - center.x + 0.1
                val directionZ = edgeZ - center.z
                if (!acceptsDirection(directionX, directionZ)) continue
                return ShockwaveRay(
                    center.x,
                    center.z,
                    directionX,
                    directionZ,
                    length
                )
            }
            return null
        }

        private fun advance() {
            val fullRow = z == zFrom || z == zTo
            if (fullRow && x < xTo) {
                x++
                return
            }
            if (!fullRow && x == xFrom && xTo != xFrom) {
                x = xTo
                return
            }
            if (z == zTo) {
                finished = true
                return
            }
            z++
            x = xFrom
        }
    }

    private class SquareRaySource(
        private val center: GridCenter,
        private val length: Double,
        width: Double,
        yaw: Double
    ) : RaySource {
        private val yawRadians = Math.toRadians(yaw)
        private val directionX = -sin(yawRadians)
        private val directionZ = cos(yawRadians)
        private val offsetX = directionZ
        private val offsetZ = -directionX
        private val offsetRight = (width / 2.0).toInt()
        private var offset = (-width / 2.0).toInt()
        private var finished = false

        override fun nextRay(): ShockwaveRay? {
            if (finished) return null
            val currentOffset = offset
            if (offset == offsetRight) finished = true else offset++

            val originX = center.x + offsetX * currentOffset
            val originZ = center.z + offsetZ * currentOffset
            return ShockwaveRay(originX, originZ, directionX, directionZ, length)
        }
    }

    /**
     * 使用 Bresenham 游标按从近到远顺序遍历射线经过的方块。
     * 每条射线仅产生 O(length) 个节点，避免扫描整块矩形并排序。
     */
    private class RayWork(val ray: ShockwaveRay, centerY: Int) {
        private val directionLengthSquared =
            ray.directionX * ray.directionX + ray.directionZ * ray.directionZ
        private val directionScale = if (directionLengthSquared < MIN_VECTOR_LENGTH_SQUARED) {
            0.0
        } else {
            ray.length / sqrt(directionLengthSquared)
        }
        private val endX = floor(ray.originX + ray.directionX * directionScale).toInt()
        private val endZ = floor(ray.originZ + ray.directionZ * directionScale).toInt()

        private var cursorX = floor(ray.originX).toInt()
        private var cursorZ = floor(ray.originZ).toInt()
        private val deltaX = abs(endX - cursorX)
        private val deltaZ = abs(endZ - cursorZ)
        private val stepX = when {
            cursorX < endX -> 1
            cursorX > endX -> -1
            else -> 0
        }
        private val stepZ = when {
            cursorZ < endZ -> 1
            cursorZ > endZ -> -1
            else -> 0
        }
        private var error = deltaX - deltaZ
        private var finished = false

        var currentY = centerY

        fun hasRemainingNodes(): Boolean = !finished

        fun nextNode(): PlanarNode {
            check(!finished) { "Shockwave ray is already exhausted" }
            val node = PlanarNode(cursorX, cursorZ)

            if (cursorX == endX && cursorZ == endZ) {
                finished = true
                return node
            }

            val doubledError = error * 2
            if (doubledError > -deltaZ) {
                error -= deltaZ
                cursorX += stepX
            }
            if (doubledError < deltaX) {
                error += deltaX
                cursorZ += stepZ
            }
            return node
        }

        fun discardRemainingNodes() {
            finished = true
        }
    }

    private data class PendingParticles(
        val pos: BlockPos,
        val stateId: Int,
        var remaining: Int
    )

    private enum class NodeResult {
        SKIPPED,
        FRACTURED,
        END_RAY
    }

    private class ShockwaveTask(
        val world: World,
        private val config: ShockwaveConfig,
        private val center: GridCenter,
        private val raySource: RaySource
    ) {
        private val performance = config.performance
        private val maxPropagationNodes = performance.maxPropagationNodes.coerceIn(0, 65_536)
        private val maxFractureBlocks = performance.maxFractureBlocks.coerceIn(0, 1_024)
        private val maxActiveFractureBlocks = performance.maxActiveFractureBlocks.coerceIn(0, 2_048)
        private val maxParticles = performance.maxParticles.coerceIn(0, 4_096)
        private val propagationNodesPerTick = performance.propagationNodesPerTick.coerceIn(1, 2_048)
        private val fractureBlocksPerTick = performance.fractureBlocksPerTick.coerceIn(1, 64)
        private val particlesPerTick = performance.particlesPerTick.coerceIn(1, 256)
        private val centerY = center.y.toInt()

        private var currentRay: RayWork? = null
        private var propagationNodes = 0
        private var fractureBlocks = 0
        private var particles = 0
        private var pendingParticles: PendingParticles? = null

        fun processBatch(): Boolean {
            var nodeBudget = propagationNodesPerTick
            var fractureBudget = fractureBlocksPerTick
            var particleBudget = particlesPerTick

            while (true) {
                val pending = pendingParticles
                if (pending != null) {
                    while (pending.remaining > 0 && particles < maxParticles && particleBudget > 0) {
                        spawnBreakParticle(world, pending.pos, pending.stateId, config.particles.velocityMultiplier)
                        pending.remaining--
                        particles++
                        particleBudget--
                    }
                    if (pending.remaining > 0 && particles < maxParticles) return false
                    pendingParticles = null
                }

                if (fractureBlocks >= maxFractureBlocks ||
                    propagationNodes >= maxPropagationNodes ||
                    OrryxMod.fractureBlock.blockNodes.size >= maxActiveFractureBlocks
                ) {
                    return pendingParticles == null
                }

                var work = currentRay
                if (work == null) {
                    val ray = raySource.nextRay() ?: return true
                    work = RayWork(ray, centerY)
                    currentRay = work
                }

                while (work.hasRemainingNodes()) {
                    if (nodeBudget <= 0 || fractureBudget <= 0) return false

                    val result = processNode(work)
                    propagationNodes++
                    nodeBudget--

                    when (result) {
                        NodeResult.SKIPPED -> Unit
                        NodeResult.END_RAY -> {
                            work.discardRemainingNodes()
                            break
                        }
                        NodeResult.FRACTURED -> {
                            fractureBlocks++
                            fractureBudget--
                            queueParticlesIfNeeded()
                            if (pendingParticles != null) break
                            if (fractureBlocks >= maxFractureBlocks) break
                        }
                    }

                    if (propagationNodes >= maxPropagationNodes) break
                }

                if (pendingParticles != null) continue
                if (!work.hasRemainingNodes()) currentRay = null
                if (nodeBudget <= 0 || fractureBudget <= 0) return false
            }
        }

        private fun processNode(work: RayWork): NodeResult {
            val node = work.nextNode()
            var finalPos = BlockPos(node.x, work.currentY, node.z)
            var state = world.getBlockState(finalPos)

            if (state.block === OrryxMod.fractureBlock) return NodeResult.SKIPPED

            val abovePos = finalPos.up()
            val aboveState = world.getBlockState(abovePos)
            if (canTransferShockwave(world, abovePos, aboveState)) {
                val aboveTwoPos = abovePos.up()
                val aboveTwoState = world.getBlockState(aboveTwoPos)
                if (!canTransferShockwave(world, aboveTwoPos, aboveTwoState)) {
                    work.currentY++
                    finalPos = abovePos
                    state = aboveState
                } else {
                    return NodeResult.END_RAY
                }
            }

            if (!canTransferShockwave(world, finalPos, state)) {
                val belowPos = finalPos.down()
                val belowState = world.getBlockState(belowPos)
                if (canTransferShockwave(world, belowPos, belowState)) {
                    work.currentY--
                    finalPos = belowPos
                    state = belowState
                } else {
                    return NodeResult.END_RAY
                }
            }

            val dx = finalPos.x + 0.5 - work.ray.originX
            val dy = finalPos.y - center.y
            val dz = finalPos.z + 0.5 - work.ray.originZ
            val distanceSquared = dx * dx + dy * dy + dz * dz
            val lengthSquared = work.ray.length * work.ray.length
            if (distanceSquared > lengthSquared || !world.isRemote || hasTileEntity(world, finalPos, state)) {
                return NodeResult.SKIPPED
            }

            val distance = sqrt(distanceSquared)
            val axis = createRotationAxis(world, dx, dz)
            val length = work.ray.length
            val distanceRatio = if (length <= 0.0) 0f else (distance / length).toFloat()
            val translator = Vector3f(0f, max(0f, distanceRatio - 0.5f) * 0.8f, 0f)
            val rotationConfig = config.fracture.rotation
            val rotator = Quaternionf().rotateAxis(
                (distanceRatio * rotationConfig.baseTilt +
                    world.rand.nextFloat() * rotationConfig.tiltVariance * 2f - rotationConfig.tiltVariance).toRadians(),
                axis
            )
            rotator.rotateX(randomSigned(world, rotationConfig.rollVariance).toRadians())
            rotator.rotateY(randomSigned(world, rotationConfig.yawVariance).toRadians())
            rotator.rotateZ(randomSigned(world, rotationConfig.rollVariance).toRadians())

            val bounceCoefficient = min(
                if (lengthSquared <= 0.0) config.fracture.bounceMultiplier else 1.0 / lengthSquared,
                config.fracture.bounceMultiplier.coerceAtLeast(0.0)
            )
            val bouncing = distanceSquared * bounceCoefficient
            val variance = config.fracture.lifetimeVariance.coerceAtLeast(0)
            val lifetime = config.fracture.baseLifetime.coerceAtLeast(1) +
                if (variance == 0) 0 else world.rand.nextInt(variance)

            return if (createFractureEffect(world, finalPos, state, translator, rotator, bouncing, lifetime)) {
                lastFracturedPos = finalPos
                lastFracturedState = state
                NodeResult.FRACTURED
            } else {
                NodeResult.SKIPPED
            }
        }

        private var lastFracturedPos: BlockPos? = null
        private var lastFracturedState: IBlockState? = null

        private fun queueParticlesIfNeeded() {
            if (!config.particles.enabled || config.particles.density <= 0 || particles >= maxParticles) return
            val pos = lastFracturedPos ?: return
            val state = lastFracturedState ?: return
            val count = min(config.particles.density, maxParticles - particles)
            if (count > 0) pendingParticles = PendingParticles(pos, Block.getStateId(state), count)
        }
    }

    private fun createRaySource(shape: Shape, center: GridCenter): RaySource? {
        return when (shape) {
            is CircleShape -> {
                if (!shape.radius.isFinite()) return null
                val radius = max(0.5, shape.radius)
                boundarySource(center, radius) { _, _ -> true }
            }
            is SquareShape -> {
                if (!shape.length.isFinite() || !shape.width.isFinite() || !shape.yaw.isFinite()) return null
                SquareRaySource(center, max(0.5, shape.length), max(0.5, shape.width), shape.yaw)
            }
            is SectorShape -> {
                if (!shape.radius.isFinite() || !shape.angle.isFinite() || !shape.yaw.isFinite()) return null
                val radius = max(0.5, shape.radius)
                val halfAngle = Math.toRadians(shape.angle.coerceIn(0.0, 360.0) / 2.0)
                val yawRadians = Math.toRadians(shape.yaw)
                val midX = -sin(yawRadians)
                val midZ = cos(yawRadians)
                val minimumDot = cos(halfAngle)
                boundarySource(center, radius) { directionX, directionZ ->
                    val lengthSquared = directionX * directionX + directionZ * directionZ
                    lengthSquared >= MIN_VECTOR_LENGTH_SQUARED &&
                        directionX * midX + directionZ * midZ >= minimumDot * sqrt(lengthSquared)
                }
            }
        }
    }

    private fun boundarySource(
        center: GridCenter,
        radius: Double,
        acceptsDirection: (Double, Double) -> Boolean
    ): RaySource {
        val xFrom = floor(center.x - radius).toInt()
        val xTo = ceil(center.x + radius).toInt()
        val zFrom = floor(center.z - radius).toInt()
        val zTo = ceil(center.z + radius).toInt()
        return BoundaryRaySource(center, radius, xFrom, xTo, zFrom, zTo, acceptsDirection)
    }

    private fun adjustCenterToGrid(center: Vector3d): GridCenter? {
        if (!center.x.isFinite() || !center.y.isFinite() || !center.z.isFinite()) return null

        val closestEdgeX = center.x.roundToInt().toDouble()
        val closestEdgeY = floor(center.y)
        val closestEdgeZ = center.z.roundToInt().toDouble()
        val blockCenterX = floor(center.x) + 0.5
        val blockCenterZ = floor(center.z) + 0.5

        val edgeDx = closestEdgeX - center.x
        val edgeDz = closestEdgeZ - center.z
        val blockDx = blockCenterX - center.x
        val blockDz = blockCenterZ - center.z
        return if (edgeDx * edgeDx + edgeDz * edgeDz < blockDx * blockDx + blockDz * blockDz) {
            GridCenter(closestEdgeX, closestEdgeY, closestEdgeZ)
        } else {
            GridCenter(blockCenterX, closestEdgeY, blockCenterZ)
        }
    }

    private fun canTransferShockwave(world: World, pos: BlockPos, state: IBlockState): Boolean =
        state.isOpaqueCube && !state.block.isAir(state, world, pos)

    private fun hasTileEntity(world: World, pos: BlockPos, state: IBlockState): Boolean =
        world.getTileEntity(pos) != null || state.block.hasTileEntity(state)

    private fun createRotationAxis(world: World, dx: Double, dz: Double): Vector3f {
        val horizontalLengthSquared = dx * dx + dz * dz
        if (horizontalLengthSquared < MIN_VECTOR_LENGTH_SQUARED) {
            return Vector3f(
                world.rand.nextFloat() - 0.5f,
                0f,
                world.rand.nextFloat() - 0.5f
            ).normalize()
        }
        val inverseLength = (1.0 / sqrt(horizontalLengthSquared)).toFloat()
        return Vector3f((-dz).toFloat() * inverseLength, 0f, dx.toFloat() * inverseLength)
    }

    private fun randomSigned(world: World, magnitude: Float): Float =
        world.rand.nextFloat() * magnitude * 2f - magnitude

    private fun spawnBreakParticle(world: World, pos: BlockPos, stateId: Int, velocityMultiplier: Float) {
        val offsetX = world.rand.nextDouble()
        val offsetY = world.rand.nextDouble() * 0.5 + 1.0
        val offsetZ = world.rand.nextDouble()
        val velocity = velocityMultiplier.toDouble()
        world.spawnParticle(
            EnumParticleTypes.BLOCK_CRACK,
            pos.x + offsetX,
            pos.y + offsetY,
            pos.z + offsetZ,
            (offsetX - 0.5) * velocity,
            (offsetY - 0.75) * velocity,
            (offsetZ - 0.5) * velocity,
            stateId
        )
    }

    private fun createFractureEffect(
        world: World,
        pos: BlockPos,
        state: IBlockState,
        translation: Vector3f,
        rotation: Quaternionf,
        bounce: Double,
        lifetime: Int
    ): Boolean {
        val fractureBlock = OrryxMod.fractureBlock
        val node = BlockNode(state, translation, rotation, bounce, lifetime)
        fractureBlock.registerNode(pos, node)

        if (!world.setBlockState(pos, fractureBlock.defaultState, 3)) {
            fractureBlock.blockNodes.remove(pos, node)
            return false
        }

        world.checkLightFor(EnumSkyBlock.BLOCK, pos)
        world.checkLightFor(EnumSkyBlock.SKY, pos)
        world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1), pos.add(1, 1, 1))
        return true
    }

    private fun Float.toRadians(): Float = this * (Math.PI.toFloat() / 180f)
}

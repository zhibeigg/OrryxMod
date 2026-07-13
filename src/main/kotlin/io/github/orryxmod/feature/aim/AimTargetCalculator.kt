package io.github.orryxmod.feature.aim

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * 一次瞄准计算同时产出渲染位置与网络回传值，避免两套逻辑产生偏差。
 */
data class AimTargetCalculation(
    val renderX: Double,
    val renderY: Double,
    val renderZ: Double,
    val responseX: Double,
    val responseY: Double,
    val responseZ: Double,
    val yaw: Float,
    val pitch: Float
)

object AimTargetCalculator {

    private const val MIN_DISTANCE = 0.1
    private const val MAX_DISTANCE = 512.0

    fun calculate(
        player: EntityPlayer,
        world: World,
        module: AimModule,
        config: AimConfig,
        partialTicks: Float
    ): AimTargetCalculation {
        val maxDistance = if (config.maxDistance.isFinite()) {
            config.maxDistance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        } else {
            AimConfig().maxDistance
        }
        val eyePosition = player.getPositionEyes(partialTicks)
        val lookDirection = player.getLook(partialTicks).normalize()
        val renderPosition = resolveRenderPosition(world, eyePosition, lookDirection, maxDistance)
        val yaw = player.rotationYaw
        val pitch = player.rotationPitch

        return when (module) {
            AimModule.POINT, AimModule.AREA -> AimTargetCalculation(
                renderX = renderPosition.x,
                renderY = renderPosition.y,
                renderZ = renderPosition.z,
                responseX = renderPosition.x,
                responseY = renderPosition.y,
                responseZ = renderPosition.z,
                yaw = yaw,
                pitch = pitch
            )
            AimModule.DIRECTION -> AimTargetCalculation(
                renderX = renderPosition.x,
                renderY = renderPosition.y,
                renderZ = renderPosition.z,
                responseX = lookDirection.x,
                responseY = lookDirection.y,
                responseZ = lookDirection.z,
                yaw = yaw,
                pitch = pitch
            )
        }
    }

    private fun resolveRenderPosition(
        world: World,
        eyePosition: Vec3d,
        lookDirection: Vec3d,
        maxDistance: Double
    ): Vec3d {
        val forwardEnd = eyePosition.add(lookDirection.scale(maxDistance))
        world.rayTraceBlocks(eyePosition, forwardEnd, false, true, false)?.hitVec?.let {
            return it
        }

        // 没有直接命中时，从最远点向下寻找地面，保留原 Aim 指示器的落地行为。
        val groundSearchEnd = forwardEnd.add(Vec3d(0.0, -maxDistance / 2.0, 0.0))
        return world.rayTraceBlocks(forwardEnd, groundSearchEnd, false, true, false)?.hitVec
            ?: forwardEnd
    }
}

package io.github.orryxmod.feature.aim

import io.github.orryxmod.util.MC
import net.minecraft.util.math.RayTraceResult

/**
 * Aim 状态管理
 */
object AimState {

    /**
     * 是否正在瞄准
     */
    var isAiming: Boolean = false
        private set

    /**
     * 当前技能名称
     */
    var currentSkill: String = ""
        private set

    /**
     * 当前模块类型
     */
    var currentModule: AimModule = AimModule.POINT
        private set

    /**
     * 当前配置
     */
    var currentConfig: AimConfig = AimConfig()
        private set

    /**
     * 开始瞄准
     */
    fun startAiming(skill: String, module: AimModule, config: AimConfig) {
        isAiming = true
        currentSkill = skill
        currentModule = module
        currentConfig = config
    }

    /**
     * 停止瞄准
     */
    fun stopAiming() {
        isAiming = false
        currentSkill = ""
        currentModule = AimModule.POINT
        currentConfig = AimConfig()
    }

    /**
     * 获取当前瞄准结果
     */
    fun getCurrentResult(): AimResult? {
        if (!isAiming) return null

        val player = MC.player ?: return null
        val world = MC.world ?: return null

        // 根据模块类型计算结果
        return when (currentModule) {
            AimModule.POINT -> getPointResult(player, world)
            AimModule.DIRECTION -> getDirectionResult(player)
            AimModule.AREA -> getAreaResult(player, world)
        }
    }

    private fun getPointResult(
        player: net.minecraft.entity.player.EntityPlayer,
        world: net.minecraft.world.World
    ): AimResult {
        val eyePos = player.getPositionEyes(1f)
        val lookVec = player.getLook(1f)
        val maxDist = currentConfig.maxDistance

        val endPos = eyePos.add(
            lookVec.x * maxDist,
            lookVec.y * maxDist,
            lookVec.z * maxDist
        )

        val rayTrace = world.rayTraceBlocks(eyePos, endPos, false, true, false)

        return if (rayTrace != null && rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
            AimResult(
                skill = currentSkill,
                x = rayTrace.hitVec.x,
                y = rayTrace.hitVec.y,
                z = rayTrace.hitVec.z,
                yaw = player.rotationYaw,
                pitch = player.rotationPitch
            )
        } else {
            AimResult(
                skill = currentSkill,
                x = endPos.x,
                y = endPos.y,
                z = endPos.z,
                yaw = player.rotationYaw,
                pitch = player.rotationPitch
            )
        }
    }

    private fun getDirectionResult(player: net.minecraft.entity.player.EntityPlayer): AimResult {
        val lookVec = player.getLook(1f)
        return AimResult(
            skill = currentSkill,
            x = lookVec.x,
            y = lookVec.y,
            z = lookVec.z,
            yaw = player.rotationYaw,
            pitch = player.rotationPitch
        )
    }

    private fun getAreaResult(
        player: net.minecraft.entity.player.EntityPlayer,
        world: net.minecraft.world.World
    ): AimResult {
        // 区域模式：获取脚下位置
        val pos = player.position
        return AimResult(
            skill = currentSkill,
            x = pos.x + 0.5,
            y = pos.y.toDouble(),
            z = pos.z + 0.5,
            yaw = player.rotationYaw,
            pitch = 0f
        )
    }
}

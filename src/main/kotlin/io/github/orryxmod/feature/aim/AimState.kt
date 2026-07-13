package io.github.orryxmod.feature.aim

import io.github.orryxmod.feature.aim.indicator.AimIndicator
import io.github.orryxmod.feature.aim.indicator.CircleIndicator
import io.github.orryxmod.feature.aim.indicator.ModelIndicator
import io.github.orryxmod.feature.aim.indicator.TextureIndicator
import io.github.orryxmod.util.MC

/**
 * Aim 状态管理
 */
object AimState {

    var isAiming: Boolean = false
        private set

    var currentSkill: String = ""
        private set

    var currentModule: AimModule = AimModule.POINT
        private set

    var currentConfig: AimConfig = AimConfig()
        private set

    var currentIndicator: AimIndicator? = null
        private set

    private var currentTarget: AimTargetCalculation? = null

    /**
     * 开始瞄准，根据 IndicatorType 创建对应指示器
     */
    fun startAiming(skill: String, module: AimModule, config: AimConfig) {
        currentIndicator?.dispose()

        isAiming = true
        currentSkill = skill
        currentModule = module
        currentConfig = config
        currentTarget = null
        currentIndicator = createIndicator(config.indicatorType)
    }

    /**
     * 停止瞄准，释放指示器资源
     */
    fun stopAiming() {
        currentIndicator?.dispose()
        currentIndicator = null
        isAiming = false
        currentSkill = ""
        currentModule = AimModule.POINT
        currentConfig = AimConfig()
        currentTarget = null
    }

    private fun createIndicator(type: IndicatorType): AimIndicator = when (type) {
        IndicatorType.TEXTURE -> TextureIndicator()
        IndicatorType.MODEL -> ModelIndicator()
        IndicatorType.CIRCLE -> CircleIndicator()
    }

    /**
     * 使用渲染和确认共用的目标计算器生成当前目标。
     */
    fun calculateCurrentTarget(partialTicks: Float): AimTargetCalculation? {
        if (!isAiming) return null

        val player = MC.player ?: return null
        val world = MC.world ?: return null
        val target = AimTargetCalculator.calculate(
            player = player,
            world = world,
            module = currentModule,
            config = currentConfig,
            partialTicks = partialTicks
        )
        currentTarget = target
        return target
    }

    /**
     * 获取当前瞄准结果。
     */
    fun getCurrentResult(): AimResult? {
        if (!isAiming) return null
        val target = currentTarget ?: calculateCurrentTarget(1f) ?: return null
        return AimResult(
            skill = currentSkill,
            x = target.responseX,
            y = target.responseY,
            z = target.responseZ,
            yaw = target.yaw,
            pitch = target.pitch
        )
    }
}

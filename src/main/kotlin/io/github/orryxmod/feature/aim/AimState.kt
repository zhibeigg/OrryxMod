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
    private var pressBaseConfig: AimConfig? = null
    private var pressMinScale = 0.0
    private var pressMaxScale = 0.0
    private var pressDurationTicks = 0L
    private var pressElapsedTicks = 0L

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
        pressBaseConfig = null
        pressDurationTicks = 0L
        pressElapsedTicks = 0L
        currentIndicator = createIndicator(config.indicatorType)
    }

    fun startPressAiming(
        skill: String,
        module: AimModule,
        config: AimConfig,
        minScale: Double,
        maxScale: Double,
        durationTicks: Long
    ) {
        require(minScale.isFinite() && maxScale.isFinite() && maxScale >= minScale)
        require(durationTicks > 0L)
        startAiming(skill, module, config.copy(scale = minScale))
        pressBaseConfig = config
        pressMinScale = minScale
        pressMaxScale = maxScale
        pressDurationTicks = durationTicks
        pressElapsedTicks = 0L
    }

    fun updatePressProgress() {
        val baseConfig = pressBaseConfig ?: return
        if (!isAiming || pressDurationTicks <= 0L) return
        pressElapsedTicks = (pressElapsedTicks + 1L).coerceAtMost(pressDurationTicks)
        val progress = pressElapsedTicks.toDouble() / pressDurationTicks.toDouble()
        currentConfig = baseConfig.copy(scale = pressMinScale + (pressMaxScale - pressMinScale) * progress)
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
        pressBaseConfig = null
        pressDurationTicks = 0L
        pressElapsedTicks = 0L
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

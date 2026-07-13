package io.github.orryxmod.feature.aim.indicator

import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimRenderer

/**
 * 瞄准指示器抽象接口
 * 不同类型的指示器实现不同的渲染方式
 */
sealed interface AimIndicator {

    /**
     * 渲染指示器
     */
    fun render(context: RenderContext, location: AimRenderer.Location, config: AimConfig, partialTicks: Float)

    /**
     * 每 tick 更新动画状态
     */
    fun update()

    /**
     * 释放资源
     */
    fun dispose() {}
}

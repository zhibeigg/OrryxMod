package io.github.orryxmod.feature.aim

import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext

/**
 * Aim 渲染器 - 委托给当前指示器进行渲染。
 */
object AimRenderer : RenderableEffect {

    override val id: String = "aim_renderer"
    override val renderPriority: Int = 100

    override val isActive: Boolean
        get() = true

    override fun update() {
        if (!AimState.isAiming) return
        AimState.currentIndicator?.update()
    }

    override fun render(context: RenderContext) {
        if (!AimState.isAiming) return

        val indicator = AimState.currentIndicator ?: return
        val target = AimState.calculateCurrentTarget(context.partialTicks) ?: return
        val location = Location(
            x = target.renderX,
            y = target.renderY,
            z = target.renderZ,
            yaw = target.yaw,
            pitch = target.pitch
        )
        indicator.render(context, location, AimState.currentConfig, context.partialTicks)
    }

    data class Location(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    )
}

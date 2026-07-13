package io.github.orryxmod.feature.aim.indicator

import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.feature.aim.AimConfig
import io.github.orryxmod.feature.aim.AimRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11
import kotlin.math.cos
import kotlin.math.sin

/**
 * 圆环指示器 — 纯 GL 线条绘制的动态圆环
 * 支持自定义颜色、半径，带脉冲扩散动画
 */
class CircleIndicator : AimIndicator {

    private var pulseTick = 0
    private val pulseSpeed = 40 // tick 周期

    /** 线段数量，越大越圆滑 */
    private val segments = 64

    override fun render(context: RenderContext, location: AimRenderer.Location, config: AimConfig, partialTicks: Float) {
        val rel = context.toRelative(location.x, location.y, location.z)

        val baseRadius = config.indicatorRadius
        // 脉冲动画：半径在 baseRadius 的 90%~110% 之间波动
        val pulseProgress = (pulseTick + partialTicks) / pulseSpeed
        val pulseFactor = 1.0 + 0.1 * sin(pulseProgress * Math.PI * 2)
        val radius = baseRadius * pulseFactor

        val r = (config.indicatorColor shr 16 and 0xFF) / 255f
        val g = (config.indicatorColor shr 8 and 0xFF) / 255f
        val b = (config.indicatorColor and 0xFF) / 255f
        val a = config.indicatorAlpha

        RenderUtils.withGlState(blend = true, texture = false, depth = false) {
            GlStateManager.translate(rel.x, rel.y + 0.05, rel.z)
            GlStateManager.color(r, g, b, a)
            GL11.glLineWidth(2.0f)

            val tessellator = Tessellator.getInstance()
            val buffer = tessellator.buffer

            // 主圆环
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION)
            for (i in 0 until segments) {
                val angle = Math.PI * 2 * i / segments
                buffer.pos(cos(angle) * radius, 0.0, sin(angle) * radius).endVertex()
            }
            tessellator.draw()

            // 脉冲扩散环（更大、更透明）
            val outerRadius = radius * (1.0 + 0.3 * sin(pulseProgress * Math.PI * 2))
            val outerAlpha = a * 0.4f
            GlStateManager.color(r, g, b, outerAlpha)
            buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION)
            for (i in 0 until segments) {
                val angle = Math.PI * 2 * i / segments
                buffer.pos(cos(angle) * outerRadius, 0.0, sin(angle) * outerRadius).endVertex()
            }
            tessellator.draw()

            GL11.glLineWidth(1.0f)
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    override fun update() {
        pulseTick = (pulseTick + 1) % pulseSpeed
    }
}

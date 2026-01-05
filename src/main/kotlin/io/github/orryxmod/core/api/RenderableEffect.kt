package io.github.orryxmod.core.api

import io.github.orryxmod.core.render.RenderContext

/**
 * 可渲染效果接口
 * 所有需要渲染的视觉效果都应实现此接口
 */
interface RenderableEffect {

    /**
     * 效果唯一 ID
     */
    val id: String

    /**
     * 是否仍然活跃
     */
    val isActive: Boolean

    /**
     * 渲染优先级（越大越后渲染，即在上层）
     */
    val renderPriority: Int get() = 0

    /**
     * 执行渲染
     * @param context 渲染上下文
     */
    fun render(context: RenderContext)

    /**
     * 更新状态（每 tick 调用）
     */
    fun update()

    /**
     * 效果结束时清理资源
     */
    fun dispose() {}
}

/**
 * 带生命周期的效果基类
 * 用于有固定存活时间的效果
 */
abstract class TimedEffect(
    override val id: String,
    protected val lifetime: Int
) : RenderableEffect {

    protected var ticksAlive: Int = 0
        private set

    override val isActive: Boolean
        get() = ticksAlive < lifetime

    /**
     * 获取生命周期进度 (0.0 ~ 1.0)
     */
    val progress: Float
        get() = ticksAlive.toFloat() / lifetime.toFloat()

    override fun update() {
        ticksAlive++
    }
}

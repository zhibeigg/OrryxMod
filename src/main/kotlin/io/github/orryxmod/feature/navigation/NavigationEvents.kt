package io.github.orryxmod.feature.navigation

import io.github.orryxmod.core.event.Event
import net.minecraft.util.math.BlockPos

/**
 * 导航事件定义
 */
sealed class NavigationEvent : Event {

    /**
     * 导航开始事件
     */
    data class Started(
        val target: BlockPos,
        val range: Int
    ) : NavigationEvent()

    /**
     * 导航完成事件 (成功到达目标)
     */
    data class Completed(
        val target: BlockPos,
        val elapsedTime: Long
    ) : NavigationEvent()

    /**
     * 导航失败事件
     */
    data class Failed(
        val target: BlockPos?,
        val reason: FailureReason
    ) : NavigationEvent() {

        enum class FailureReason {
            /** 路径计算失败 */
            PATH_NOT_FOUND,
            /** 导航超时 */
            TIMEOUT,
            /** Baritone 异常停止 */
            BARITONE_STOPPED,
            /** 未知原因 */
            UNKNOWN
        }
    }

    /**
     * 导航取消事件
     */
    data class Cancelled(
        val target: BlockPos?
    ) : NavigationEvent()

    /**
     * 导航进度更新事件
     */
    data class Progress(
        val target: BlockPos,
        val distance: Double,
        val elapsedTime: Long
    ) : NavigationEvent()
}

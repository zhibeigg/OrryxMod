package io.github.orryxmod.feature.navigation

import baritone.api.pathing.goals.GoalNear
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.api.Subscribe
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.BaritoneUtils
import net.minecraft.util.math.BlockPos

/**
 * Navigation 功能模块
 * 使用 Baritone 进行自动寻路，提供状态同步和事件通知
 */
@Feature("navigation", description = "自动寻路")
object NavigationFeature : FeatureBase() {

    override fun enable() {
        if (enabled) return
        BaritoneUtils.initialize()
        super.enable()
    }

    override fun disable() {
        if (!enabled) return
        cancelAndReset()
        super.disable()
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.NavigationStart::class)
    fun onNavigationStart(packet: OrryxPacket.NavigationStart) {
        startNavigation(
            BlockPos(packet.x, packet.y, packet.z),
            packet.range
        )
    }

    @OnPacket(OrryxPacket.NavigationStop::class)
    fun onNavigationStop(@Suppress("UNUSED_PARAMETER") packet: OrryxPacket.NavigationStop) {
        stopNavigation()
    }

    // ========== 事件处理 ==========

    @Subscribe
    fun onClientTick(event: Events.ClientTick) {
        if (event.phase != Events.ClientTick.Phase.END) return
        if (!NavigationState.isNavigating) return

        // 更新状态并检测变化
        val statusChanged = NavigationState.update()

        if (statusChanged) {
            handleStatusChange()
        } else if (NavigationState.shouldPublishProgress) {
            // 仅在节流周期发布进度事件
            publishProgressEvent()
        }
    }

    // ========== 公共 API ==========

    /**
     * 开始导航到目标位置
     * @param target 目标位置
     * @param range 到达判定距离 (默认 0 = 精确到达)
     * @param config 导航配置
     */
    fun startNavigation(
        target: BlockPos,
        range: Int = 0,
        config: NavigationConfig = NavigationConfig()
    ) {
        try {
            // 先取消现有导航并清理旧状态
            cancelBaritone()
            NavigationState.reset()

            val baritone = BaritoneUtils.primary ?: run {
                OrryxMod.logger.error("Baritone not available")
                NavigationState.stopNavigation(NavigationStatus.FAILED)
                publishFailedEvent(target, NavigationEvent.Failed.FailureReason.UNKNOWN)
                return
            }

            // 开始 Baritone 寻路
            baritone.customGoalProcess.setGoalAndPath(GoalNear(target, range))

            // Baritone 启动成功后，更新状态并发布事件
            NavigationState.startNavigation(target, range, config)
            EventBus.publish(NavigationEvent.Started(target, range))

            OrryxMod.logger.info("Navigation started to $target (range: $range)")
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to start navigation", ex)
            cancelBaritone()
            NavigationState.stopNavigation(NavigationStatus.FAILED)
            publishFailedEvent(target, NavigationEvent.Failed.FailureReason.UNKNOWN)
        }
    }

    /**
     * 停止导航
     */
    fun stopNavigation() {
        val target = NavigationState.targetPos
        cancelBaritone()
        NavigationState.stopNavigation(NavigationStatus.CANCELLED)

        try {
            EventBus.publish(NavigationEvent.Cancelled(target))
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to publish navigation cancellation", ex)
        }

        OrryxMod.logger.info("Navigation stopped")
    }

    /**
     * 检查是否正在导航
     */
    val isActive: Boolean
        get() = NavigationState.isNavigating

    /**
     * 获取当前目标位置
     */
    val target: BlockPos?
        get() = NavigationState.targetPos

    /**
     * 获取当前导航状态
     */
    val status: NavigationStatus
        get() = NavigationState.status

    /**
     * 获取距离目标的距离
     */
    val distanceToTarget: Double
        get() = NavigationState.distanceToTarget

    /**
     * 获取导航已用时间 (毫秒)
     */
    val elapsedTime: Long
        get() = NavigationState.elapsedTime

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        cancelAndReset(publishCancellation = NavigationState.isNavigating)
    }

    // ========== 私有方法 ==========

    /**
     * 处理状态变化
     */
    private fun handleStatusChange() {
        val target = NavigationState.targetPos
        val elapsed = NavigationState.elapsedTime

        when (NavigationState.status) {
            NavigationStatus.ARRIVED -> {
                if (target != null) {
                    EventBus.publish(NavigationEvent.Completed(target, elapsed))
                    OrryxMod.logger.info("Navigation completed to $target in ${elapsed}ms")
                }
            }
            NavigationStatus.FAILED -> {
                cancelBaritone()
                publishFailedEvent(target, determineFailureReason())
                OrryxMod.logger.warn("Navigation failed after ${elapsed}ms")
            }
            NavigationStatus.CANCELLED -> {
                // 已在 stopNavigation 中处理
            }
            else -> {}
        }
    }

    /**
     * 发布进度事件
     */
    private fun publishProgressEvent() {
        val target = NavigationState.targetPos ?: return
        EventBus.publish(
            NavigationEvent.Progress(
                target = target,
                distance = NavigationState.distanceToTarget,
                elapsedTime = NavigationState.elapsedTime
            )
        )
    }

    /**
     * 发布失败事件
     */
    private fun publishFailedEvent(target: BlockPos?, reason: NavigationEvent.Failed.FailureReason) {
        EventBus.publish(NavigationEvent.Failed(target, reason))
    }

    private fun cancelAndReset(publishCancellation: Boolean = false) {
        val target = NavigationState.targetPos
        cancelBaritone()
        NavigationState.reset()

        if (publishCancellation) {
            try {
                EventBus.publish(NavigationEvent.Cancelled(target))
            } catch (ex: Exception) {
                OrryxMod.logger.error("Failed to publish navigation cancellation", ex)
            }
        }
    }

    private fun cancelBaritone() {
        try {
            BaritoneUtils.cancelEverything()
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to cancel Baritone navigation", ex)
        }
    }

    /**
     * 判断失败原因
     */
    private fun determineFailureReason(): NavigationEvent.Failed.FailureReason {
        val config = NavigationState.config
        return when {
            config.timeout > 0 && NavigationState.elapsedTime >= config.timeout ->
                NavigationEvent.Failed.FailureReason.TIMEOUT
            !BaritoneUtils.isActive && !BaritoneUtils.isPathing ->
                NavigationEvent.Failed.FailureReason.BARITONE_STOPPED
            else ->
                NavigationEvent.Failed.FailureReason.UNKNOWN
        }
    }
}

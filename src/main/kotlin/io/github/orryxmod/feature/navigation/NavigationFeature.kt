package io.github.orryxmod.feature.navigation

import baritone.api.BaritoneAPI
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import net.minecraft.util.math.BlockPos

/**
 * Navigation 功能模块
 * 使用 Baritone 进行自动寻路
 */
@Feature("navigation", description = "自动寻路")
object NavigationFeature : FeatureBase() {

    private var isNavigating = false
    private var targetPos: BlockPos? = null

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.NavigationStart::class)
    fun onNavigationStart(packet: OrryxPacket.NavigationStart) {
        startNavigation(
            BlockPos(packet.x, packet.y, packet.z),
            packet.range
        )
    }

    @OnPacket(OrryxPacket.NavigationStop::class)
    fun onNavigationStop(packet: OrryxPacket.NavigationStop) {
        stopNavigation()
    }

    // ========== 公共 API ==========

    /**
     * 开始导航到目标位置
     */
    fun startNavigation(target: BlockPos, range: Int = 0) {
        try {
            val baritone = BaritoneAPI.getProvider().primaryBaritone

            // 设置目标范围
            if (range > 0) {
                baritone.customGoalProcess.setGoalAndPath(
                    baritone.customGoalProcess.goal
                )
            }

            // 开始寻路
            baritone.customGoalProcess.setGoalAndPath(
                baritone.customGoalProcess.goal
            )

            isNavigating = true
            targetPos = target

            OrryxMod.logger.info("Navigation started to $target")
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to start navigation", ex)
        }
    }

    /**
     * 停止导航
     */
    fun stopNavigation() {
        try {
            val baritone = BaritoneAPI.getProvider().primaryBaritone
            baritone.pathingBehavior.cancelEverything()

            isNavigating = false
            targetPos = null

            OrryxMod.logger.info("Navigation stopped")
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to stop navigation", ex)
        }
    }

    /**
     * 检查是否正在导航
     */
    val isActive: Boolean get() = isNavigating

    /**
     * 获取当前目标位置
     */
    val target: BlockPos? get() = targetPos

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        stopNavigation()
    }
}

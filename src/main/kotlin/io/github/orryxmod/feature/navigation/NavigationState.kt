package io.github.orryxmod.feature.navigation

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.BaritoneUtils
import io.github.orryxmod.util.MC
import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

/**
 * Navigation 状态管理
 * 管理导航状态并与 Baritone 实际状态同步
 */
object NavigationState {

    /** 到达判定容差距离 */
    private const val ARRIVAL_TOLERANCE = 0.5

    /**
     * 当前导航状态
     */
    var status: NavigationStatus = NavigationStatus.IDLE
        private set(value) {
            if (field != value) {
                OrryxMod.logger.debug("Navigation status: {} -> {}", field, value)
                field = value
            }
        }

    /**
     * 目标位置
     */
    var targetPos: BlockPos? = null
        private set

    /**
     * 到达距离
     */
    var arrivalRange: Int = 0
        private set

    /**
     * 当前配置
     */
    var config: NavigationConfig = NavigationConfig()
        private set

    /**
     * 导航开始时间 (用于超时检测)
     */
    private var startTime: Long = 0L

    /**
     * 状态检查计数器
     */
    private var tickCounter: Int = 0

    /**
     * 是否正在导航
     */
    val isNavigating: Boolean
        get() = status == NavigationStatus.NAVIGATING

    /**
     * 是否应该发布进度事件（节流后）
     */
    val shouldPublishProgress: Boolean
        get() = isNavigating && tickCounter == 0

    /**
     * 获取当前距离目标的距离
     */
    val distanceToTarget: Double
        get() {
            val target = targetPos ?: return Double.MAX_VALUE
            val player = MC.player ?: return Double.MAX_VALUE
            val dx = player.posX - (target.x + 0.5)
            val dy = player.posY - (target.y + 0.5)
            val dz = player.posZ - (target.z + 0.5)
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

    /**
     * 获取当前导航时间 (毫秒)
     */
    val elapsedTime: Long
        get() = if (startTime > 0) System.currentTimeMillis() - startTime else 0L

    /**
     * 开始导航
     */
    fun startNavigation(target: BlockPos, range: Int, navigationConfig: NavigationConfig = NavigationConfig()) {
        require(navigationConfig.checkInterval > 0) { "checkInterval must be positive" }

        targetPos = target
        arrivalRange = range
        config = navigationConfig
        status = NavigationStatus.NAVIGATING
        startTime = System.currentTimeMillis()
        tickCounter = 0
    }

    /**
     * 更新状态 - 在 ClientTick 中调用
     * @return 如果状态发生变化返回 true
     */
    fun update(): Boolean {
        if (status != NavigationStatus.NAVIGATING) return false

        // 节流：每 N tick 检查一次
        tickCounter++
        if (tickCounter < config.checkInterval) return false
        tickCounter = 0

        val previousStatus = status

        // 按优先级依次检查
        checkTimeout() || checkBaritoneStatus() || checkArrival()

        return previousStatus != status
    }

    /**
     * 检查超时
     */
    private fun checkTimeout(): Boolean {
        if (config.timeout in 1..<elapsedTime) {
            status = NavigationStatus.FAILED
            return true
        }
        return false
    }

    /**
     * 检查 Baritone 状态
     * 优先检查 Baritone 是否异常停止，避免误判
     */
    private fun checkBaritoneStatus(): Boolean {
        val baritoneActive = BaritoneUtils.isActive || BaritoneUtils.isPathing
        if (baritoneActive) return false

        // Baritone 停止了，根据距离判断是成功还是失败
        val target = targetPos
        status = if (target != null && distanceToTarget <= arrivalRange + ARRIVAL_TOLERANCE) {
            NavigationStatus.ARRIVED
        } else {
            NavigationStatus.FAILED
        }
        return true
    }

    /**
     * 检查是否到达目标
     */
    private fun checkArrival(): Boolean {
        targetPos ?: return false
        if (distanceToTarget <= arrivalRange + ARRIVAL_TOLERANCE) {
            status = NavigationStatus.ARRIVED
            return true
        }
        return false
    }

    /**
     * 停止导航
     * @param reason 停止原因
     */
    fun stopNavigation(reason: NavigationStatus = NavigationStatus.CANCELLED) {
        status = reason
    }

    /**
     * 重置状态
     */
    fun reset() {
        status = NavigationStatus.IDLE
        targetPos = null
        arrivalRange = 0
        config = NavigationConfig()
        startTime = 0L
        tickCounter = 0
    }
}

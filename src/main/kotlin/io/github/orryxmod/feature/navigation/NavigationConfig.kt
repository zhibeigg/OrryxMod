package io.github.orryxmod.feature.navigation

/**
 * Navigation 配置数据类
 */
data class NavigationConfig(
    /** 到达目标的判定距离 (默认 0 = 精确到达) */
    val arrivalRange: Int = 0,
    /** 状态检查间隔 (tick) */
    val checkInterval: Int = 10,
    /** 导航超时时间 (毫秒, 0 = 无超时) */
    val timeout: Long = 0L
)

/**
 * 导航状态枚举
 */
enum class NavigationStatus {
    /** 空闲状态 */
    IDLE,
    /** 正在寻路中 */
    NAVIGATING,
    /** 已到达目标 */
    ARRIVED,
    /** 导航失败 */
    FAILED,
    /** 导航被取消 */
    CANCELLED
}

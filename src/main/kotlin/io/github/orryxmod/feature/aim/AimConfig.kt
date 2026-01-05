package io.github.orryxmod.feature.aim

/**
 * Aim 配置数据类
 */
data class AimConfig(
    val scale: Double = 1.0,
    val maxDistance: Double = 100.0
)

/**
 * Aim 模块类型
 */
enum class AimModule {
    POINT,      // 点选
    DIRECTION,  // 方向
    AREA        // 区域
}

/**
 * Aim 结果数据
 */
data class AimResult(
    val skill: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
)

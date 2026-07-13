package io.github.orryxmod.feature.aim

/**
 * Aim 配置数据类
 */
data class AimConfig(
    val scale: Double = 1.0,
    val maxDistance: Double = 100.0,
    val indicatorType: IndicatorType = IndicatorType.TEXTURE,
    val indicatorColor: Int = 0xFFFFFF,
    val indicatorAlpha: Float = 0.8f,
    val indicatorRadius: Double = 1.0,
    val modelScale: Float = 1.0f
)

enum class IndicatorType {
    TEXTURE, MODEL, CIRCLE;

    companion object {
        fun fromString(name: String): IndicatorType = when (name.lowercase()) {
            "texture" -> TEXTURE
            "model" -> MODEL
            "circle" -> CIRCLE
            else -> TEXTURE
        }
    }
}

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

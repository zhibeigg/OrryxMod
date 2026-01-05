package io.github.orryxmod.feature.effect

import org.joml.Vector3d

/**
 * Ghost 效果配置
 */
data class GhostConfig(
    val density: Int = 10,
    val gap: Int = 2
)

/**
 * Flicker 效果配置
 */
data class FlickerConfig(
    val alpha: Float = 0.5f
)

/**
 * EntityShow 效果配置
 */
data class EntityShowConfig(
    val scale: Float = 1.0f,
    val rotateX: Float = 0f,
    val rotateY: Float = 0f,
    val rotateZ: Float = 0f
)

/**
 * 实体旋转数据
 */
data class EntityRotation(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

/**
 * EntityShow 影子数据
 */
data class ShadowData(
    val group: String,
    val position: Vector3d,
    val rotation: EntityRotation,
    val scale: Float,
    val timeout: Long,
    val startTime: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() - startTime > timeout
}

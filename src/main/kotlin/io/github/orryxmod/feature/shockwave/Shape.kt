package io.github.orryxmod.feature.shockwave

import org.joml.Vector3d
import kotlin.math.cos
import kotlin.math.sin

/**
 * 形状基类
 */
sealed class Shape {
    abstract val center: Vector3d

    /**
     * 生成扩散方向序列
     */
    abstract fun spreadDirections(): Sequence<SpreadDirection>
}

/**
 * 扩散方向数据
 */
data class SpreadDirection(
    val origin: Vector3d,
    val direction: Vector3d,
    val length: Double
)

/**
 * 圆形冲击波
 */
data class CircleShape(
    override val center: Vector3d,
    val radius: Double
) : Shape() {

    override fun spreadDirections(): Sequence<SpreadDirection> = sequence {
        val segments = (radius * 8).toInt().coerceIn(16, 128)
        val angleStep = Math.PI * 2 / segments

        for (i in 0 until segments) {
            val angle = i * angleStep
            val dx = cos(angle)
            val dz = sin(angle)

            yield(
                SpreadDirection(
                    origin = Vector3d(center.x, center.y, center.z),
                    direction = Vector3d(dx, 0.0, dz),
                    length = radius
                )
            )
        }
    }
}

/**
 * 方形冲击波
 */
data class SquareShape(
    override val center: Vector3d,
    val length: Double,
    val width: Double,
    val yaw: Double
) : Shape() {

    override fun spreadDirections(): Sequence<SpreadDirection> = sequence {
        val yawRad = Math.toRadians(yaw)
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)

        // 前方向（长度方向）
        val forwardX = cosYaw
        val forwardZ = sinYaw

        // 右方向（宽度方向）
        val rightX = -sinYaw
        val rightZ = cosYaw

        val halfLength = length / 2
        val halfWidth = width / 2

        // 生成网格点
        val lengthSteps = (length * 2).toInt().coerceIn(4, 64)
        val widthSteps = (width * 2).toInt().coerceIn(4, 64)

        for (li in 0..lengthSteps) {
            for (wi in 0..widthSteps) {
                val lFactor = (li.toDouble() / lengthSteps - 0.5) * 2
                val wFactor = (wi.toDouble() / widthSteps - 0.5) * 2

                val offsetX = forwardX * lFactor * halfLength + rightX * wFactor * halfWidth
                val offsetZ = forwardZ * lFactor * halfLength + rightZ * wFactor * halfWidth

                yield(
                    SpreadDirection(
                        origin = Vector3d(center.x, center.y, center.z),
                        direction = Vector3d(offsetX, 0.0, offsetZ).normalize(),
                        length = Vector3d(offsetX, 0.0, offsetZ).length()
                    )
                )
            }
        }
    }
}

/**
 * 扇形冲击波
 */
data class SectorShape(
    override val center: Vector3d,
    val radius: Double,
    val angle: Double,
    val yaw: Double
) : Shape() {

    override fun spreadDirections(): Sequence<SpreadDirection> = sequence {
        val halfAngle = Math.toRadians(angle / 2)
        val centerYaw = Math.toRadians(yaw)

        val segments = (angle / 5).toInt().coerceIn(8, 72)
        val angleStep = Math.toRadians(angle) / segments

        for (i in 0..segments) {
            val currentAngle = centerYaw - halfAngle + i * angleStep
            val dx = cos(currentAngle)
            val dz = sin(currentAngle)

            yield(
                SpreadDirection(
                    origin = Vector3d(center.x, center.y, center.z),
                    direction = Vector3d(dx, 0.0, dz),
                    length = radius
                )
            )
        }
    }
}

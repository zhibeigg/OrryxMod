package io.github.orryxmod.feature.collider

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collider 几何的纯 Kotlin 插值器，不依赖 Minecraft 或渲染 API。
 */
object ColliderInterpolator {

    private const val EPSILON = 1.0e-12

    fun interpolate(previous: ColliderShape, current: ColliderShape, progress: Double): ColliderShape {
        val t = progress.coerceIn(0.0, 1.0)
        if (t <= 0.0) return previous
        if (t >= 1.0) return current

        return when {
            previous is ColliderShape.Sphere && current is ColliderShape.Sphere -> ColliderShape.Sphere(
                lerp(previous.cx, current.cx, t),
                lerp(previous.cy, current.cy, t),
                lerp(previous.cz, current.cz, t),
                lerp(previous.radius, current.radius, t)
            )
            previous is ColliderShape.AABB && current is ColliderShape.AABB -> ColliderShape.AABB(
                lerp(previous.cx, current.cx, t),
                lerp(previous.cy, current.cy, t),
                lerp(previous.cz, current.cz, t),
                lerp(previous.hx, current.hx, t),
                lerp(previous.hy, current.hy, t),
                lerp(previous.hz, current.hz, t)
            )
            previous is ColliderShape.OBB && current is ColliderShape.OBB -> {
                val q = interpolateQuaternion(
                    previous.qx, previous.qy, previous.qz, previous.qw,
                    current.qx, current.qy, current.qz, current.qw,
                    t
                )
                ColliderShape.OBB(
                    lerp(previous.cx, current.cx, t),
                    lerp(previous.cy, current.cy, t),
                    lerp(previous.cz, current.cz, t),
                    lerp(previous.hx, current.hx, t),
                    lerp(previous.hy, current.hy, t),
                    lerp(previous.hz, current.hz, t),
                    q[0], q[1], q[2], q[3]
                )
            }
            previous is ColliderShape.Capsule && current is ColliderShape.Capsule -> ColliderShape.Capsule(
                lerp(previous.cx, current.cx, t),
                lerp(previous.cy, current.cy, t),
                lerp(previous.cz, current.cz, t),
                lerp(previous.radius, current.radius, t),
                lerp(previous.halfHeight, current.halfHeight, t)
            )
            previous is ColliderShape.OrientedCapsule && current is ColliderShape.OrientedCapsule -> {
                val q = interpolateQuaternion(
                    previous.qx, previous.qy, previous.qz, previous.qw,
                    current.qx, current.qy, current.qz, current.qw,
                    t
                )
                ColliderShape.OrientedCapsule(
                    lerp(previous.cx, current.cx, t),
                    lerp(previous.cy, current.cy, t),
                    lerp(previous.cz, current.cz, t),
                    lerp(previous.radius, current.radius, t),
                    lerp(previous.halfHeight, current.halfHeight, t),
                    q[0], q[1], q[2], q[3]
                )
            }
            previous is ColliderShape.Ray && current is ColliderShape.Ray -> {
                val direction = normalizeVector(
                    lerp(previous.dx, current.dx, t),
                    lerp(previous.dy, current.dy, t),
                    lerp(previous.dz, current.dz, t),
                    current.dx,
                    current.dy,
                    current.dz
                )
                ColliderShape.Ray(
                    lerp(previous.ox, current.ox, t),
                    lerp(previous.oy, current.oy, t),
                    lerp(previous.oz, current.oz, t),
                    direction[0], direction[1], direction[2],
                    lerp(previous.length, current.length, t)
                )
            }
            previous is ColliderShape.Composite && current is ColliderShape.Composite -> {
                if (!hasStableStructure(previous, current)) return current
                ColliderShape.Composite(previous.children.indices.map { index ->
                    val oldChild = previous.children[index]
                    val newChild = current.children[index]
                    newChild.copy(shape = interpolate(oldChild.shape, newChild.shape, t))
                })
            }
            else -> current
        }
    }

    fun hasStableStructure(previous: ColliderShape.Composite, current: ColliderShape.Composite): Boolean {
        if (previous.children.size != current.children.size) return false
        return previous.children.indices.all { index ->
            val oldChild = previous.children[index]
            val newChild = current.children[index]
            oldChild.id == newChild.id && sameStructure(oldChild.shape, newChild.shape)
        }
    }

    private fun sameStructure(previous: ColliderShape, current: ColliderShape): Boolean {
        if (previous::class != current::class) return false
        return if (previous is ColliderShape.Composite && current is ColliderShape.Composite) {
            hasStableStructure(previous, current)
        } else {
            true
        }
    }

    private fun interpolateQuaternion(
        ax: Float,
        ay: Float,
        az: Float,
        aw: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bw: Float,
        t: Double
    ): FloatArray {
        var targetX = bx.toDouble()
        var targetY = by.toDouble()
        var targetZ = bz.toDouble()
        var targetW = bw.toDouble()
        val dot = ax * bx + ay * by + az * bz + aw * bw
        if (dot < 0f) {
            targetX = -targetX
            targetY = -targetY
            targetZ = -targetZ
            targetW = -targetW
        }

        return normalizeQuaternion(
            lerp(ax.toDouble(), targetX, t),
            lerp(ay.toDouble(), targetY, t),
            lerp(az.toDouble(), targetZ, t),
            lerp(aw.toDouble(), targetW, t),
            bx.toDouble(), by.toDouble(), bz.toDouble(), bw.toDouble()
        )
    }

    private fun normalizeQuaternion(
        x: Double,
        y: Double,
        z: Double,
        w: Double,
        fallbackX: Double,
        fallbackY: Double,
        fallbackZ: Double,
        fallbackW: Double
    ): FloatArray {
        val magnitude = stableMagnitude(x, y, z, w)
        if (magnitude > EPSILON) {
            return floatArrayOf(
                (x / magnitude).toFloat(),
                (y / magnitude).toFloat(),
                (z / magnitude).toFloat(),
                (w / magnitude).toFloat()
            )
        }

        val fallbackMagnitude = stableMagnitude(fallbackX, fallbackY, fallbackZ, fallbackW)
        if (fallbackMagnitude > EPSILON) {
            return floatArrayOf(
                (fallbackX / fallbackMagnitude).toFloat(),
                (fallbackY / fallbackMagnitude).toFloat(),
                (fallbackZ / fallbackMagnitude).toFloat(),
                (fallbackW / fallbackMagnitude).toFloat()
            )
        }
        return floatArrayOf(0f, 0f, 0f, 1f)
    }

    private fun normalizeVector(
        x: Double,
        y: Double,
        z: Double,
        fallbackX: Double,
        fallbackY: Double,
        fallbackZ: Double
    ): DoubleArray {
        val magnitude = stableMagnitude(x, y, z)
        if (magnitude > EPSILON) {
            return doubleArrayOf(x / magnitude, y / magnitude, z / magnitude)
        }

        val fallbackMagnitude = stableMagnitude(fallbackX, fallbackY, fallbackZ)
        if (fallbackMagnitude > EPSILON) {
            return doubleArrayOf(
                fallbackX / fallbackMagnitude,
                fallbackY / fallbackMagnitude,
                fallbackZ / fallbackMagnitude
            )
        }
        return doubleArrayOf(0.0, 1.0, 0.0)
    }

    private fun stableMagnitude(vararg values: Double): Double {
        val maximum = values.fold(0.0) { result, value -> maxOf(result, abs(value)) }
        if (maximum <= EPSILON) return 0.0
        val scaledSquared = values.sumOf { value ->
            val scaled = value / maximum
            scaled * scaled
        }
        return maximum * sqrt(scaledSquared)
    }

    private fun lerp(start: Double, end: Double, progress: Double): Double =
        start + (end - start) * progress
}

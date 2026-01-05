package io.github.orryxmod.shared.math

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.joml.Vector3d
import kotlin.math.sqrt

/**
 * Vector3d 扩展函数
 */

/**
 * 转换为 Minecraft Vec3d
 */
fun Vector3d.toVec3d(): Vec3d = Vec3d(x, y, z)

/**
 * 转换为 BlockPos
 */
fun Vector3d.toBlockPos(): BlockPos = BlockPos(x.toInt(), y.toInt(), z.toInt())

/**
 * 计算到另一个向量的距离
 */
fun Vector3d.distanceTo(other: Vector3d): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * 计算到另一个向量的平方距离（避免开方运算）
 */
fun Vector3d.distanceSquaredTo(other: Vector3d): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return dx * dx + dy * dy + dz * dz
}

/**
 * 计算水平距离（忽略 Y 轴）
 */
fun Vector3d.horizontalDistanceTo(other: Vector3d): Double {
    val dx = x - other.x
    val dz = z - other.z
    return sqrt(dx * dx + dz * dz)
}

/**
 * Vec3d 扩展函数
 */

/**
 * 转换为 JOML Vector3d
 */
fun Vec3d.toVector3d(): Vector3d = Vector3d(x, y, z)

/**
 * BlockPos 扩展函数
 */

/**
 * 转换为 JOML Vector3d（中心点）
 */
fun BlockPos.toVector3d(): Vector3d = Vector3d(x + 0.5, y.toDouble(), z + 0.5)

/**
 * 转换为 JOML Vector3d（精确位置）
 */
fun BlockPos.toVector3dExact(): Vector3d = Vector3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * 工具函数
 */

/**
 * 创建 Vector3d
 */
fun vec3d(x: Double, y: Double, z: Double): Vector3d = Vector3d(x, y, z)

/**
 * 创建 Vector3d（从整数）
 */
fun vec3d(x: Int, y: Int, z: Int): Vector3d = Vector3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * 线性插值
 */
fun Vector3d.lerp(target: Vector3d, t: Double): Vector3d {
    return Vector3d(
        x + (target.x - x) * t,
        y + (target.y - y) * t,
        z + (target.z - z) * t
    )
}

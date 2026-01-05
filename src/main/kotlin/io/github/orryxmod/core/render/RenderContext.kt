package io.github.orryxmod.core.render

import io.github.orryxmod.util.MC
import org.joml.Vector3d

/**
 * 渲染上下文 - 封装渲染时的常用数据
 */
data class RenderContext(
    val partialTicks: Float,
    val viewerX: Double,
    val viewerY: Double,
    val viewerZ: Double
) {
    /**
     * 将世界坐标转换为相对于观察者的坐标
     */
    fun toRelative(x: Double, y: Double, z: Double): Vector3d {
        return Vector3d(x - viewerX, y - viewerY, z - viewerZ)
    }

    /**
     * 将世界坐标转换为相对于观察者的坐标
     */
    fun toRelative(pos: Vector3d): Vector3d {
        return toRelative(pos.x, pos.y, pos.z)
    }

    companion object {
        /**
         * 从当前渲染状态创建上下文
         */
        fun create(partialTicks: Float): RenderContext {
            val rm = MC.renderManager
            return RenderContext(
                partialTicks = partialTicks,
                viewerX = rm.viewerPosX,
                viewerY = rm.viewerPosY,
                viewerZ = rm.viewerPosZ
            )
        }
    }
}

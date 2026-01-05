package io.github.orryxmod.feature.shockwave

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.MC

/**
 * Shockwave 功能模块
 * 地面冲击波效果
 */
@Feature("shockwave", description = "地面冲击波")
object ShockwaveFeature : FeatureBase() {

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.CircleShockwave::class)
    fun onCircleShockwave(packet: OrryxPacket.CircleShockwave) {
        circleSlamFracture(packet.x, packet.y, packet.z, packet.radius)
    }

    @OnPacket(OrryxPacket.SquareShockwave::class)
    fun onSquareShockwave(packet: OrryxPacket.SquareShockwave) {
        squareSlamFracture(packet.x, packet.y, packet.z, packet.length, packet.width, packet.yaw)
    }

    @OnPacket(OrryxPacket.SectorShockwave::class)
    fun onSectorShockwave(packet: OrryxPacket.SectorShockwave) {
        sectorSlamFracture(packet.x, packet.y, packet.z, packet.radius, packet.angle, packet.yaw)
    }

    // ========== 公共 API ==========

    /**
     * 圆形冲击波
     */
    fun circleSlamFracture(x: Double, y: Double, z: Double, radius: Double): Boolean {
        val world = MC.world ?: return false

        return shockwave(world) {
            shape = circle {
                center(x, y, z)
                this.radius = radius
            }
        }
    }

    /**
     * 方形冲击波
     */
    fun squareSlamFracture(
        x: Double, y: Double, z: Double,
        length: Double, width: Double, yaw: Double
    ): Boolean {
        val world = MC.world ?: return false

        return shockwave(world) {
            shape = square {
                center(x, y, z)
                this.length = length
                this.width = width
                this.yaw = yaw
            }
        }
    }

    /**
     * 扇形冲击波
     */
    fun sectorSlamFracture(
        x: Double, y: Double, z: Double,
        radius: Double, angle: Double, yaw: Double
    ): Boolean {
        val world = MC.world ?: return false

        return shockwave(world) {
            shape = sector {
                center(x, y, z)
                this.radius = radius
                this.angle = angle
                this.yaw = yaw
            }
        }
    }

    /**
     * 自定义冲击波（完整 DSL）
     */
    fun customShockwave(block: ShockwaveDsl.() -> Unit): Boolean {
        val world = MC.world ?: return false
        return shockwave(world, block)
    }
}

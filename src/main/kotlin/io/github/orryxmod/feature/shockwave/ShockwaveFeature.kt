package io.github.orryxmod.feature.shockwave

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.api.Subscribe
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.MC

/**
 * Shockwave 功能模块
 * 地面冲击波效果
 */
@Feature("shockwave", description = "地面冲击波")
object ShockwaveFeature : FeatureBase() {

    /** 冲击波参数上限，防止恶意服务端发送超大参数导致客户端卡死 */
    private const val MAX_SHOCKWAVE_RADIUS = 50.0

    @Volatile
    var performanceConfig: ShockwavePerformanceConfig = ShockwavePerformanceConfig()

    override fun disable() {
        ShockwaveExecutor.clear()
        super.disable()
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.CircleShockwave::class)
    fun onCircleShockwave(packet: OrryxPacket.CircleShockwave) {
        circleSlamFracture(packet.x, packet.y, packet.z, packet.radius.coerceIn(0.5, MAX_SHOCKWAVE_RADIUS))
    }

    @OnPacket(OrryxPacket.SquareShockwave::class)
    fun onSquareShockwave(packet: OrryxPacket.SquareShockwave) {
        squareSlamFracture(
            packet.x, packet.y, packet.z,
            packet.length.coerceIn(0.5, MAX_SHOCKWAVE_RADIUS),
            packet.width.coerceIn(0.5, MAX_SHOCKWAVE_RADIUS),
            packet.yaw
        )
    }

    @OnPacket(OrryxPacket.SectorShockwave::class)
    fun onSectorShockwave(packet: OrryxPacket.SectorShockwave) {
        sectorSlamFracture(
            packet.x, packet.y, packet.z,
            packet.radius.coerceIn(0.5, MAX_SHOCKWAVE_RADIUS),
            packet.angle.coerceIn(0.0, 360.0),
            packet.yaw
        )
    }

    @Subscribe
    fun onClientTick(event: Events.ClientTick) {
        if (event.phase == Events.ClientTick.Phase.END) {
            ShockwaveExecutor.processTick(MC.world)
        }
    }

    @OnDisconnect
    fun onDisconnect() {
        ShockwaveExecutor.clear()
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
            performance(ShockwaveFeature.performanceConfig)
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
            performance(ShockwaveFeature.performanceConfig)
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
            performance(ShockwaveFeature.performanceConfig)
        }
    }

    /**
     * 自定义冲击波（完整 DSL）
     */
    fun customShockwave(block: ShockwaveDsl.() -> Unit): Boolean {
        val world = MC.world ?: return false
        return shockwave(world) {
            performance(ShockwaveFeature.performanceConfig)
            block()
        }
    }
}

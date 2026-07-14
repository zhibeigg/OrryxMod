package io.github.orryxmod.core.network

import io.github.orryxmod.feature.collider.ColliderShape
import java.util.UUID

/**
 * 协议密封类 - 所有网络包的基类
 * 使用密封类确保类型安全和 when 穷举
 */
sealed class OrryxPacket {
    abstract val packetId: Int

    // ========== 瞄准系统 ==========

    data class AimRequest(
        val skill: String,
        val module: String,
        val scale: Double,
        val maxDistance: Double,
        val indicatorType: String = "texture",
        val indicatorColor: Int = 0xFFFFFF,
        val indicatorAlpha: Float = 0.8f,
        val indicatorRadius: Double = 1.0,
        val modelScale: Float = 1.0f
    ) : OrryxPacket() {
        override val packetId = 1
    }

    data class AimConfirm(
        val confirmed: Boolean
    ) : OrryxPacket() {
        override val packetId = 2
    }

    data class PressAimRequest(
        val skill: String,
        val picture: String,
        val minScale: Double,
        val maxScale: Double,
        val maxDistance: Double,
        val maxTicks: Long
    ) : OrryxPacket() {
        override val packetId = 6
    }

    data class AimResponse(
        val skill: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) : OrryxPacket() {
        override val packetId = 4
    }

    // ========== 实体效果 ==========

    data class GhostEffect(
        val uuid: UUID,
        val timeout: Long,
        val density: Int,
        val gap: Int
    ) : OrryxPacket() {
        override val packetId = 3
    }

    data class FlickerEffect(
        val uuid: UUID,
        val timeout: Long,
        val alpha: Float,
        val duration: Long,
        val scale: Float
    ) : OrryxPacket() {
        override val packetId = 5
    }

    data class EntityShowAdd(
        val uuid: UUID,
        val group: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val timeout: Long,
        val rotateX: Float,
        val rotateY: Float,
        val rotateZ: Float,
        val scale: Float,
        val alpha: Float = 1.0f,
        val fadeOut: Boolean = false
    ) : OrryxPacket() {
        override val packetId = 8
    }

    data class EntityShowRemove(
        val uuid: UUID,
        val group: String
    ) : OrryxPacket() {
        override val packetId = 9
    }

    // ========== 鼠标控制 ==========

    data class MouseControl(
        val show: Boolean
    ) : OrryxPacket() {
        override val packetId = 7
    }

    // ========== 导航系统 ==========

    data class NavigationStart(
        val x: Int,
        val y: Int,
        val z: Int,
        val range: Int
    ) : OrryxPacket() {
        override val packetId = 10
    }

    object NavigationStop : OrryxPacket() {
        override val packetId = 11
    }

    // ========== 冲击波系统 ==========

    data class SquareShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val length: Double,
        val width: Double,
        val yaw: Double
    ) : OrryxPacket() {
        override val packetId = 12
    }

    data class CircleShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val radius: Double
    ) : OrryxPacket() {
        override val packetId = 13
    }

    data class SectorShockwave(
        val x: Double,
        val y: Double,
        val z: Double,
        val radius: Double,
        val angle: Double,
        val yaw: Double
    ) : OrryxPacket() {
        override val packetId = 14
    }

    // ========== Bloom 配置 ==========

    data class BloomConfigSync(
        val configs: Map<String, io.github.orryxmod.feature.bloom.BloomConfig>
    ) : OrryxPacket() {
        override val packetId = 15
    }

    data class BloomConfigUpdate(
        val id: String,
        val config: io.github.orryxmod.feature.bloom.BloomConfig
    ) : OrryxPacket() {
        override val packetId = 16
    }

    data class BloomConfigRemove(
        val id: String
    ) : OrryxPacket() {
        override val packetId = 17
    }

    // ========== 碰撞箱系统 ==========

    data class ColliderShow(
        val id: String,
        val r: Int,
        val g: Int,
        val b: Int,
        val a: Int,
        val shapeData: ColliderShape
    ) : OrryxPacket() {
        override val packetId = 18
    }

    data class ColliderUpdate(
        val id: String,
        val shapeData: ColliderShape
    ) : OrryxPacket() {
        override val packetId = 19
    }

    data class ColliderRemove(
        val id: String
    ) : OrryxPacket() {
        override val packetId = 20
    }
}

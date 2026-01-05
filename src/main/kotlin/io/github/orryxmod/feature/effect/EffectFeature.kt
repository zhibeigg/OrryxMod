package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.render.EffectManager
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Effect 功能模块
 * 统一管理 Ghost/Flicker/EntityShow 三种实体视觉效果
 */
@Feature("effect", description = "实体视觉效果")
object EffectFeature : FeatureBase() {

    // 按 UUID 管理的 EntityShow 效果
    private val entityShowEffects = ConcurrentHashMap<UUID, EntityShowEffect>()

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.GhostEffect::class)
    fun onGhostPacket(packet: OrryxPacket.GhostEffect) {
        applyGhost(
            packet.uuid,
            packet.timeout,
            GhostConfig(
                density = packet.density,
                gap = packet.gap
            )
        )
    }

    @OnPacket(OrryxPacket.FlickerEffect::class)
    fun onFlickerPacket(packet: OrryxPacket.FlickerEffect) {
        applyFlicker(
            packet.uuid,
            packet.timeout,
            FlickerConfig(alpha = packet.alpha)
        )
    }

    @OnPacket(OrryxPacket.EntityShowAdd::class)
    fun onEntityShowAdd(packet: OrryxPacket.EntityShowAdd) {
        addShadow(
            uuid = packet.uuid,
            group = packet.group,
            position = Vector3d(packet.x, packet.y, packet.z),
            rotation = EntityRotation(packet.rotateX, packet.rotateY, packet.rotateZ),
            scale = packet.scale,
            timeout = packet.timeout
        )
    }

    @OnPacket(OrryxPacket.EntityShowRemove::class)
    fun onEntityShowRemove(packet: OrryxPacket.EntityShowRemove) {
        removeShadow(packet.uuid, packet.group)
    }

    // ========== 公共 API ==========

    /**
     * 应用 Ghost 效果
     */
    fun applyGhost(uuid: UUID, timeout: Long, config: GhostConfig = GhostConfig()) {
        val effect = GhostEffect(uuid, timeout, config)
        EffectManager.add(effect)
    }

    /**
     * 应用 Flicker 效果
     */
    fun applyFlicker(uuid: UUID, timeout: Long, config: FlickerConfig = FlickerConfig()) {
        val effect = FlickerEffect(uuid, timeout, config)
        EffectManager.add(effect)
    }

    /**
     * 添加 EntityShow 影子
     */
    fun addShadow(
        uuid: UUID,
        group: String,
        position: Vector3d,
        rotation: EntityRotation = EntityRotation(),
        scale: Float = 1.0f,
        timeout: Long = 60_000
    ) {
        val effect = entityShowEffects.getOrPut(uuid) {
            EntityShowEffect(uuid).also { EffectManager.add(it) }
        }

        effect.addShadow(group, position, rotation, scale, timeout)
    }

    /**
     * 移除 EntityShow 影子
     */
    fun removeShadow(uuid: UUID, group: String) {
        entityShowEffects[uuid]?.removeShadow(group)
    }

    /**
     * 清除指定实体的所有影子
     */
    fun clearShadows(uuid: UUID) {
        entityShowEffects[uuid]?.clearAllShadows()
        entityShowEffects.remove(uuid)
    }

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        entityShowEffects.clear()
    }
}

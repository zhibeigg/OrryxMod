package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.MC
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.joml.Vector3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Effect 功能模块
 * 统一管理 Ghost/Flicker/EntityShow 三种实体视觉效果
 * 从老模块迁移完整渲染逻辑
 */
@Feature("effect", description = "实体视觉效果")
object EffectFeature : FeatureBase() {

    // Ghost 效果列表
    private val ghostEffects = ConcurrentHashMap<UUID, GhostEffect>()

    // Flicker 效果列表
    private val flickerEffects = mutableListOf<FlickerEffect>()

    // EntityShow 效果（按 UUID 管理）
    private val entityShowEffects = ConcurrentHashMap<UUID, EntityShowEffect>()

    // 最大 Flicker 效果数量
    private const val MAX_FLICKERS = 20

    override fun enable() {
        super.enable()
        // 注册 Forge 事件监听
        MinecraftForge.EVENT_BUS.register(this)
    }

    override fun disable() {
        super.disable()
        // 注销 Forge 事件监听
        MinecraftForge.EVENT_BUS.unregister(this)
    }

    // ========== Forge 事件处理 ==========

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (MC.world == null || MC.isGamePaused) return

        // 更新 EntityTracker
        EntityTrackerRegistry.tick()

        // 清理过期的 Ghost 效果
        ghostEffects.entries.removeIf { !it.value.isActive }

        // 清理过期的 Flicker 效果（释放 Display List 资源）
        flickerEffects.removeIf { effect ->
            if (!effect.isActive) {
                effect.dispose()
                true
            } else {
                false
            }
        }

        // 更新 EntityShow 效果
        entityShowEffects.values.forEach { it.update() }
        entityShowEffects.entries.removeIf { !it.value.isActive }
    }

    @SubscribeEvent
    fun onRenderPlayerPost(event: RenderPlayerEvent.Post) {
        // 渲染 Ghost 效果
        ghostEffects.values.forEach { effect ->
            if (effect.isActive) {
                effect.renderGhost(event)
            }
        }
    }

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        // 渲染 EntityShow 效果
        entityShowEffects.values.forEach { effect ->
            if (effect.isActive) {
                effect.renderShadows(event)
            }
        }

        // 渲染 Flicker 效果（在 RenderWorldLastEvent 中渲染以绕过 Mo' Bends）
        flickerEffects.forEach { effect ->
            if (effect.isActive) {
                effect.renderFlicker(event)
            }
        }
    }

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
            timeout = packet.timeout,
            alpha = packet.alpha,
            fadeOut = packet.fadeOut
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
        // 移除过期的效果
        ghostEffects.filterValues { !it.isActive }.forEach { (k, _) -> ghostEffects.remove(k) }
        // 添加新效果
        ghostEffects[uuid] = GhostEffect(uuid, timeout, config)
    }

    /**
     * 应用 Flicker 效果
     */
    fun applyFlicker(uuid: UUID, timeout: Long, config: FlickerConfig = FlickerConfig()) {
        // 移除过期的效果（释放 Display List 资源）
        flickerEffects.removeIf { effect ->
            if (!effect.isActive) {
                effect.dispose()
                true
            } else {
                false
            }
        }

        // 限制效果数量（释放最旧的 Display List）
        if (flickerEffects.size >= MAX_FLICKERS) {
            flickerEffects.removeFirst().dispose()
        }

        // 创建并初始化效果
        val effect = FlickerEffect(uuid, timeout, config)
        effect.initTracker()
        flickerEffects.add(effect)
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
        timeout: Long = 60_000,
        alpha: Float = 1.0f,
        fadeOut: Boolean = false
    ) {
        val effect = entityShowEffects.getOrPut(uuid) {
            EntityShowEffect(uuid)
        }

        effect.addShadow(group, position, rotation, scale, timeout, alpha, fadeOut)
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
        ghostEffects.clear()
        // 释放所有 Flicker 效果的 Display List 资源
        flickerEffects.forEach { it.dispose() }
        flickerEffects.clear()
        entityShowEffects.clear()
    }

    // ========== 测试方法 ==========

    /**
     * 测试 Ghost 效果
     */
    fun testGhost() {
        val player = MC.player ?: return
        applyGhost(player.uniqueID, 1000, GhostConfig(density = 5, gap = 0))
    }

    /**
     * 测试 Flicker 效果
     */
    fun testFlicker() {
        val player = MC.player ?: return
        applyFlicker(player.uniqueID, 1000, FlickerConfig(alpha = 0.5f))
    }

    /**
     * 测试 EntityShow 效果（带透明度渐隐）
     */
    fun testEntityShow() {
        val player = MC.player ?: return
        addShadow(
            uuid = player.uniqueID,
            group = "test",
            position = Vector3d(player.posX, player.posY, player.posZ),
            rotation = EntityRotation(0f, 0f, 0f),
            scale = 1f,
            timeout = 2000,
            alpha = 0.8f,
            fadeOut = true
        )
    }
}

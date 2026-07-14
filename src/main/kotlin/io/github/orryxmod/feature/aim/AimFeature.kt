package io.github.orryxmod.feature.aim

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.api.Subscribe
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.network.PacketDispatcher
import io.github.orryxmod.core.render.EffectManager
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * Aim 功能模块
 * 技能辅助瞄准系统
 */
@Feature("aim", description = "技能辅助瞄准")
object AimFeature : FeatureBase() {

    private var leftButtonWasDown = false
    private var rightButtonWasDown = false
    private var escapeWasDown = false

    override fun enable() {
        super.enable()
        syncInputState()
        if (!EffectManager.exists(AimRenderer.id)) {
            EffectManager.addPersistent(AimRenderer)
        }
    }

    override fun disable() {
        super.disable()
        AimState.stopAiming()
        EffectManager.remove(AimRenderer)
        leftButtonWasDown = false
        rightButtonWasDown = false
        escapeWasDown = false
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.AimRequest::class)
    fun onAimRequest(packet: OrryxPacket.AimRequest) {
        if (!enabled) return

        val module = parseModule(packet.module)
        val config = AimConfig(
            scale = packet.scale,
            maxDistance = packet.maxDistance,
            indicatorType = IndicatorType.fromString(packet.indicatorType),
            indicatorColor = packet.indicatorColor,
            indicatorAlpha = packet.indicatorAlpha,
            indicatorRadius = packet.indicatorRadius,
            modelScale = packet.modelScale
        )

        startAiming(packet.skill, module, config)
    }

    @OnPacket(OrryxPacket.PressAimRequest::class)
    fun onPressAimRequest(packet: OrryxPacket.PressAimRequest) {
        if (!enabled) return
        val config = AimConfig(
            scale = packet.minScale,
            maxDistance = packet.maxDistance,
            indicatorType = IndicatorType.TEXTURE
        )
        AimState.startPressAiming(
            skill = packet.skill,
            module = AimModule.POINT,
            config = config,
            minScale = packet.minScale,
            maxScale = packet.maxScale,
            durationTicks = packet.maxTicks
        )
    }

    @OnPacket(OrryxPacket.AimConfirm::class)
    fun onAimConfirm(packet: OrryxPacket.AimConfirm) {
        if (!enabled) return

        if (packet.confirmed) {
            confirm()
        } else {
            cancel()
        }
    }

    // ========== 事件处理 ==========

    @Subscribe
    fun onClientTick(event: Events.ClientTick) {
        if (event.phase != Events.ClientTick.Phase.END) return

        val leftButtonDown = Mouse.isCreated() && Mouse.isButtonDown(0)
        val rightButtonDown = Mouse.isCreated() && Mouse.isButtonDown(1)
        val escapeDown = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)

        val leftButtonPressed = leftButtonDown && !leftButtonWasDown
        val rightButtonPressed = rightButtonDown && !rightButtonWasDown
        val escapePressed = escapeDown && !escapeWasDown

        leftButtonWasDown = leftButtonDown
        rightButtonWasDown = rightButtonDown
        escapeWasDown = escapeDown

        if (!enabled || !AimState.isAiming) return

        if (leftButtonPressed) {
            confirm()
            return
        }

        if (rightButtonPressed || escapePressed) {
            cancel()
        }
    }

    // ========== 公共 API ==========

    /**
     * 开始瞄准
     */
    fun startAiming(skill: String, module: AimModule = AimModule.POINT, config: AimConfig = AimConfig()) {
        if (!enabled) return
        AimState.startAiming(skill, module, config)
    }

    /**
     * 确认瞄准
     */
    fun confirm() {
        val result = AimState.getCurrentResult() ?: return

        PacketDispatcher.send(
            OrryxPacket.AimResponse(
                skill = result.skill,
                x = result.x,
                y = result.y,
                z = result.z,
                yaw = result.yaw,
                pitch = result.pitch
            )
        )

        AimState.stopAiming()
    }

    /**
     * 取消瞄准
     */
    fun cancel() {
        AimState.stopAiming()
    }

    /**
     * 检查是否正在瞄准
     */
    val isAiming: Boolean
        get() = AimState.isAiming

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        AimState.stopAiming()
    }

    // ========== 工具方法 ==========

    private fun syncInputState() {
        leftButtonWasDown = Mouse.isCreated() && Mouse.isButtonDown(0)
        rightButtonWasDown = Mouse.isCreated() && Mouse.isButtonDown(1)
        escapeWasDown = Keyboard.isCreated() && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)
    }

    private fun parseModule(moduleName: String): AimModule {
        return when (moduleName.lowercase()) {
            "point", "点选" -> AimModule.POINT
            "direction", "方向" -> AimModule.DIRECTION
            "area", "区域" -> AimModule.AREA
            else -> AimModule.POINT
        }
    }
}

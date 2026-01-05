package io.github.orryxmod.feature.aim

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.api.Subscribe
import io.github.orryxmod.core.event.Events
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.network.PacketDispatcher
import io.github.orryxmod.util.MC
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * Aim 功能模块
 * 技能辅助瞄准系统
 */
@Feature("aim", description = "技能辅助瞄准")
object AimFeature : FeatureBase() {

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.AimRequest::class)
    fun onAimRequest(packet: OrryxPacket.AimRequest) {
        val module = parseModule(packet.module)
        val config = AimConfig(
            scale = packet.scale,
            maxDistance = packet.maxDistance
        )

        startAiming(packet.skill, module, config)
    }

    @OnPacket(OrryxPacket.AimConfirm::class)
    fun onAimConfirm(packet: OrryxPacket.AimConfirm) {
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
        if (!AimState.isAiming) return

        // 检测鼠标左键确认
        if (Mouse.isButtonDown(0)) {
            confirm()
            return
        }

        // 检测右键或 ESC 取消
        if (Mouse.isButtonDown(1) || Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
            cancel()
            return
        }
    }

    // ========== 公共 API ==========

    /**
     * 开始瞄准
     */
    fun startAiming(skill: String, module: AimModule = AimModule.POINT, config: AimConfig = AimConfig()) {
        AimState.startAiming(skill, module, config)

        // 可选：隐藏鼠标指针，启用自由视角
        // MC.gameSettings.pauseOnLostFocus = false
    }

    /**
     * 确认瞄准
     */
    fun confirm() {
        val result = AimState.getCurrentResult() ?: return

        // 发送结果到服务器
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

    private fun parseModule(moduleName: String): AimModule {
        return when (moduleName.lowercase()) {
            "point", "点选" -> AimModule.POINT
            "direction", "方向" -> AimModule.DIRECTION
            "area", "区域" -> AimModule.AREA
            else -> AimModule.POINT
        }
    }
}

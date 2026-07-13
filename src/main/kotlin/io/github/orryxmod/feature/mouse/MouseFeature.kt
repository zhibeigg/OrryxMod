package io.github.orryxmod.feature.mouse

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.MC
import org.lwjgl.input.Mouse

/**
 * Mouse 功能模块
 * 控制鼠标指针显示/隐藏
 *
 * 使用透传 GUI 覆盖层实现：
 * - mc.currentScreen 不为 null（模拟聊天栏打开状态）
 * - Mixin 阻止视角控制
 * - 鼠标事件传递到 Forge 事件总线，其他 mod 可以响应
 */
@Feature("mouse", description = "鼠标控制")
object MouseFeature : FeatureBase() {

    private var _isVisible = false

    override fun disable() {
        if (!enabled) return
        hideCursor()
        super.disable()
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.MouseControl::class)
    fun onMouseControl(packet: OrryxPacket.MouseControl) {
        setCursorVisible(packet.show)
    }

    // ========== 公共 API ==========

    /**
     * 设置鼠标指针可见性
     */
    fun setCursorVisible(visible: Boolean) {
        if (visible) {
            showCursor()
        } else {
            hideCursor()
        }
    }

    /**
     * 显示鼠标指针
     * 打开透传覆盖层，模拟聊天栏行为
     */
    fun showCursor() {
        if (!enabled) return
        if (!TransparentOverlay.show()) {
            _isVisible = false
            return
        }

        _isVisible = true
        Mouse.setGrabbed(false)
    }

    /**
     * 隐藏鼠标指针
     */
    fun hideCursor() {
        _isVisible = false
        TransparentOverlay.hide()

        // 只在游戏中时抓取鼠标
        if (MC.player != null && MC.currentScreen == null) {
            Mouse.setGrabbed(true)
        }
    }

    /**
     * 切换鼠标指针可见性
     */
    fun toggleCursor() {
        if (_isVisible) {
            hideCursor()
        } else {
            showCursor()
        }
    }

    /**
     * 检查鼠标指针是否可见
     * Mixin 通过此方法判断是否阻止视角控制
     */
    fun isVisible(): Boolean = _isVisible

    internal fun onOverlayClosed() {
        _isVisible = false
    }

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        hideCursor()
    }
}

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
 */
@Feature("mouse", description = "鼠标控制")
object MouseFeature : FeatureBase() {

    private var isCursorVisible = false

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
        isCursorVisible = visible

        if (visible) {
            showCursor()
        } else {
            hideCursor()
        }
    }

    /**
     * 显示鼠标指针
     */
    fun showCursor() {
        Mouse.setGrabbed(false)
        isCursorVisible = true
    }

    /**
     * 隐藏鼠标指针
     */
    fun hideCursor() {
        // 只在游戏中且没有打开 GUI 时隐藏
        if (MC.player != null && MC.currentScreen == null) {
            Mouse.setGrabbed(true)
        }
        isCursorVisible = false
    }

    /**
     * 切换鼠标指针可见性
     */
    fun toggleCursor() {
        setCursorVisible(!isCursorVisible)
    }

    /**
     * 检查鼠标指针是否可见
     */
    val isVisible: Boolean get() = isCursorVisible

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        // 断开连接时恢复默认状态
        hideCursor()
    }
}

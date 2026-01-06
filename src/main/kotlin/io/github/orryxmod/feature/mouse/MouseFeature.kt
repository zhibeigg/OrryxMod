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
 * 控制鼠标指针显示/隐藏，支持 HUD 交互
 */
@Feature("mouse", description = "鼠标控制")
object MouseFeature : FeatureBase() {

    private var isCursorVisible = false

    /**
     * 鼠标点击回调 (用于 HUD 交互)
     */
    var onMouseClick: ((x: Int, y: Int, button: Int) -> Boolean)? = null

    /**
     * 鼠标释放回调
     */
    var onMouseRelease: ((x: Int, y: Int, button: Int) -> Unit)? = null

    /**
     * 鼠标移动回调
     */
    var onMouseMove: ((x: Int, y: Int) -> Unit)? = null

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.MouseControl::class)
    fun onMouseControl(packet: OrryxPacket.MouseControl) {
        setCursorVisible(packet.show)
    }

    // ========== 公共 API ==========

    /**
     * 设置鼠标指针可见性
     * @param visible 是否可见
     * @param interactive 是否启用 HUD 交互（显示透明覆盖层）
     */
    fun setCursorVisible(visible: Boolean, interactive: Boolean = true) {
        isCursorVisible = visible

        if (visible) {
            if (interactive) {
                showOverlay()
            } else {
                showCursor()
            }
        } else {
            hideOverlay()
            hideCursor()
        }
    }

    /**
     * 显示透明覆盖层（启用 HUD 交互）
     */
    fun showOverlay(): HudOverlayScreen {
        isCursorVisible = true
        val screen = HudOverlayScreen.show()

        // 绑定回调
        screen.onMouseClick = onMouseClick
        screen.onMouseRelease = onMouseRelease
        screen.onMouseMove = onMouseMove
        screen.onClose = {
            isCursorVisible = false
        }

        return screen
    }

    /**
     * 隐藏覆盖层
     */
    fun hideOverlay() {
        HudOverlayScreen.hide()
    }

    /**
     * 仅显示鼠标指针（不启用交互）
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
    fun toggleCursor(interactive: Boolean = true) {
        setCursorVisible(!isCursorVisible, interactive)
    }

    /**
     * 检查鼠标指针是否可见
     */
    val isVisible: Boolean get() = isCursorVisible

    /**
     * 检查覆盖层是否显示（HUD 交互模式）
     */
    val isInteractive: Boolean get() = HudOverlayScreen.isShowing

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        // 断开连接时恢复默认状态
        hideOverlay()
        hideCursor()
    }
}

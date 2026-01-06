package io.github.orryxmod.feature.mouse

import io.github.orryxmod.util.MC
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

/**
 * 透明 HUD 覆盖层
 * 用于在游戏中显示鼠标并处理 HUD 交互
 */
class HudOverlayScreen : GuiScreen() {

    /**
     * 鼠标点击回调
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

    /**
     * 关闭回调
     */
    var onClose: (() -> Unit)? = null

    override fun initGui() {
        super.initGui()
        // 启用键盘重复事件
        Keyboard.enableRepeatEvents(true)
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        Keyboard.enableRepeatEvents(false)
        onClose?.invoke()
    }

    /**
     * 不暂停游戏
     */
    override fun doesGuiPauseGame(): Boolean = false

    /**
     * 不绘制默认背景
     */
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 不调用 super.drawScreen() 以避免绘制背景
        // 只通知鼠标移动
        onMouseMove?.invoke(mouseX, mouseY)

        // 绘制子元素（如果有）
        for (button in buttonList) {
            button.drawButton(mc, mouseX, mouseY, partialTicks)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        // 先让回调处理，如果回调处理了就不再传递
        val handled = onMouseClick?.invoke(mouseX, mouseY, mouseButton) ?: false
        if (!handled) {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        onMouseRelease?.invoke(mouseX, mouseY, state)
        super.mouseReleased(mouseX, mouseY, state)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // ESC 关闭覆盖层
        if (keyCode == Keyboard.KEY_ESCAPE) {
            MouseFeature.hideOverlay()
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        // 可以在这里处理鼠标滚轮等
    }

    companion object {
        /**
         * 当前实例
         */
        var current: HudOverlayScreen? = null
            private set

        /**
         * 显示覆盖层
         */
        fun show(): HudOverlayScreen {
            val screen = HudOverlayScreen()
            current = screen
            MC.displayGuiScreen(screen)
            return screen
        }

        /**
         * 隐藏覆盖层
         */
        fun hide() {
            if (MC.currentScreen is HudOverlayScreen) {
                MC.displayGuiScreen(null)
            }
            current = null
        }

        /**
         * 检查覆盖层是否显示
         */
        val isShowing: Boolean
            get() = MC.currentScreen is HudOverlayScreen
    }
}

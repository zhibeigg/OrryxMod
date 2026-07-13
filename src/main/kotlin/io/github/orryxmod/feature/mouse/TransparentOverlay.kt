package io.github.orryxmod.feature.mouse

import io.github.orryxmod.core.handler.KeyBindingHandler
import io.github.orryxmod.util.MC
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

/**
 * GUI 覆盖层
 *
 * 模拟聊天栏打开时的行为
 */
class TransparentOverlay : GuiScreen() {

    override fun initGui() {
        super.initGui()
        Keyboard.enableRepeatEvents(true)
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        Keyboard.enableRepeatEvents(false)
        instanceClosed(this)
        MouseFeature.onOverlayClosed()
    }

    /**
     * 不暂停游戏
     */
    override fun doesGuiPauseGame(): Boolean = false

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // ESC 或玩家设置的鼠标切换键关闭
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == KeyBindingHandler.keyToggleMouse.keyCode) {
            MouseFeature.hideCursor()
            return
        }
        // 其他按键正常传递
        super.keyTyped(typedChar, keyCode)
    }

    companion object {
        private var instance: TransparentOverlay? = null

        fun show(): Boolean {
            val current = MC.currentScreen
            if (current === instance) return true
            if (current != null) return false

            val overlay = TransparentOverlay()
            instance = overlay
            MC.displayGuiScreen(overlay)
            return MC.currentScreen === overlay
        }

        fun hide() {
            val overlay = instance
            if (overlay != null && MC.currentScreen === overlay) {
                MC.displayGuiScreen(null)
            }
            instance = null
        }

        private fun instanceClosed(overlay: TransparentOverlay) {
            if (instance === overlay) {
                instance = null
            }
        }

        val isShowing: Boolean
            get() = instance != null && MC.currentScreen === instance
    }
}

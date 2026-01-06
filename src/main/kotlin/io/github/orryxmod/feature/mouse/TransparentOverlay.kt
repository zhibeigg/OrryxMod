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

        fun show() {
            if (MC.currentScreen !is TransparentOverlay) {
                instance = TransparentOverlay()
                MC.displayGuiScreen(instance)
            }
        }

        fun hide() {
            if (MC.currentScreen is TransparentOverlay) {
                MC.displayGuiScreen(null)
            }
            instance = null
        }

        val isShowing: Boolean
            get() = MC.currentScreen is TransparentOverlay
    }
}

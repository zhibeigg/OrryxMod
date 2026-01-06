package io.github.orryxmod.core.handler

import io.github.orryxmod.feature.mouse.MouseFeature
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard

/**
 * 按键绑定处理器
 * 管理模组的所有快捷键
 */
object KeyBindingHandler {

    private const val CATEGORY = "OrryxMod"

    /**
     * 鼠标指针切换按键 (默认: M)
     */
    val keyToggleMouse = KeyBinding(
        "Toggle Mouse Cursor",
        Keyboard.KEY_M,
        CATEGORY
    )

    /**
     * 注册所有按键绑定
     */
    fun register() {
        ClientRegistry.registerKeyBinding(keyToggleMouse)
    }

    @SubscribeEvent
    fun onKeyInput(@Suppress("UNUSED_PARAMETER") event: InputEvent.KeyInputEvent) {
        // 鼠标指针切换
        if (keyToggleMouse.isPressed) {
            MouseFeature.toggleCursor()
        }
    }
}

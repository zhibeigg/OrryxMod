package io.github.orryxmod.feature.mouse

import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisconnect
import io.github.orryxmod.core.api.OnPacket
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.util.MC
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
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

    private const val OVERLAY_RETRY_DELAY_TICKS = 20

    private var _isVisible = false
    private var desiredVisible = false
    private var pendingVisibility: Boolean? = null
    private var overlayRetryTicks = 0

    override fun enable() {
        if (enabled) return
        desiredVisible = false
        pendingVisibility = null
        overlayRetryTicks = 0
        MinecraftForge.EVENT_BUS.register(this)
        super.enable()
    }

    override fun disable() {
        if (!enabled) return
        MinecraftForge.EVENT_BUS.unregister(this)
        hideCursor()
        super.disable()
    }

    // ========== 网络包处理 ==========

    @OnPacket(OrryxPacket.MouseControl::class)
    fun onMouseControl(packet: OrryxPacket.MouseControl) {
        requestCursorVisible(packet.show)
    }

    // ========== 公共 API ==========

    /**
     * 设置鼠标指针可见性
     */
    fun requestCursorVisible(visible: Boolean) {
        if (!enabled) {
            desiredVisible = false
            pendingVisibility = null
            overlayRetryTicks = 0
            return
        }

        desiredVisible = visible
        if (!visible || MC.currentScreen == null || TransparentOverlay.isShowing) {
            pendingVisibility = null
            setCursorVisible(visible)
        } else {
            pendingVisibility = true
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (overlayRetryTicks > 0) overlayRetryTicks--

        val visible = pendingVisibility
        if (visible != null) {
            if (visible && MC.currentScreen != null && !TransparentOverlay.isShowing) return
            if (visible && overlayRetryTicks > 0) {
                maintainReleasedMouse()
                return
            }
            pendingVisibility = null
            setCursorVisible(visible)
            return
        }

        // 某些整合包会清空自定义 GuiScreen；此时持续保持游戏失焦和鼠标释放，
        // 并按节流间隔在安全的空界面 tick 尝试恢复透明覆盖层。
        if (desiredVisible) {
            if (MC.currentScreen == null && !TransparentOverlay.isShowing && overlayRetryTicks == 0) {
                if (!TransparentOverlay.show()) {
                    overlayRetryTicks = OVERLAY_RETRY_DELAY_TICKS
                }
            }
            maintainReleasedMouse()
        }
    }

    fun setCursorVisible(visible: Boolean) {
        if (visible) {
            showCursor()
        } else {
            hideCursor()
        }
    }

    private fun maintainReleasedMouse() {
        if (MC.inGameHasFocus) {
            MC.setIngameNotInFocus()
        }
        if (Mouse.isGrabbed()) {
            Mouse.setGrabbed(false)
        }
    }

    /**
     * 显示鼠标指针
     * 打开透传覆盖层，模拟聊天栏行为
     */
    fun showCursor() {
        if (!enabled) return

        desiredVisible = true
        _isVisible = true
        if (!TransparentOverlay.show()) {
            pendingVisibility = true
            overlayRetryTicks = OVERLAY_RETRY_DELAY_TICKS
        } else {
            pendingVisibility = null
            overlayRetryTicks = 0
        }

        maintainReleasedMouse()
    }

    /**
     * 隐藏鼠标指针
     */
    fun hideCursor() {
        desiredVisible = false
        pendingVisibility = null
        overlayRetryTicks = 0
        _isVisible = false
        TransparentOverlay.hide()

        // 只在游戏中且没有其他 GUI 时恢复原版焦点与鼠标抓取。
        if (MC.player != null && MC.currentScreen == null) {
            MC.setIngameFocus()
        }
    }

    /**
     * 切换鼠标指针可见性
     */
    fun toggleCursor() {
        requestCursorVisible(!desiredVisible)
    }

    /**
     * 返回调用方期望的鼠标可见状态；实际覆盖层可能仍在等待其他 GUI 关闭。
     */
    fun isVisible(): Boolean = desiredVisible

    internal fun onOverlayClosed() {
        if (_isVisible) {
            pendingVisibility = true
            overlayRetryTicks = maxOf(overlayRetryTicks, OVERLAY_RETRY_DELAY_TICKS)
        }
    }

    // ========== 生命周期 ==========

    @OnDisconnect
    fun onDisconnect() {
        hideCursor()
    }
}

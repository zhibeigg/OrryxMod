package io.github.orryxmod.core

import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket

/**
 * 旧版协议处理器 - 仅保留发送功能用于兼容
 *
 * @deprecated 使用 core.network.PacketDispatcher 代替
 */
@Deprecated("Use core.network.PacketDispatcher instead")
object PacketHandler {

    // 协议类型定义（保留用于兼容旧代码）
    internal sealed class PacketType(val header: Int) {
        data object AimRequest : PacketType(1)
        data object AimConfirm : PacketType(2)
        data object Ghost : PacketType(3)
        data object AimResponse : PacketType(4)
        data object Flicker : PacketType(5)
        data object PressAimRequest : PacketType(6)
        data object MouseRequest : PacketType(7)
        data object EntityShow : PacketType(8)
        data object EntityShowRemove : PacketType(9)
        data object PlayerNavigation : PacketType(10)
        data object PlayerNavigationStop : PacketType(11)
        data object SquareShockwave : PacketType(12)
        data object CircleShockwave : PacketType(13)
        data object SectorShockwave : PacketType(14)
    }

    /**
     * 发送数据包（保留用于兼容）
     * @deprecated 使用 PacketDispatcher.send() 代替
     */
    @Deprecated("Use PacketDispatcher.send() instead")
    internal inline fun sendDataPacket(
        type: PacketType,
        block: ByteArrayDataOutput.() -> Unit,
    ) {
        try {
            val output = ByteStreams.newDataOutput().apply {
                writeInt(type.header)
                block()
            }
            OrryxMod.network.sendToServer(
                FMLProxyPacket(
                    PacketBuffer(Unpooled.wrappedBuffer(output.toByteArray())),
                    "${OrryxMod.MOD_ID}:main"
                )
            )
        } catch (ex: Exception) {
            OrryxMod.logger.error("发送数据包失败: ${ex.message}")
        }
    }
}

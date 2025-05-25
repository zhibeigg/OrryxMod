package io.github.orryxmod.modules

import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import io.netty.buffer.Unpooled
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.OrryxMod.Companion.MOD_ID
import io.github.orryxmod.modules.Aim.AimPacket
import io.github.orryxmod.util.MC
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import java.util.*

object PacketHandler {

    // 协议类型定义
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
    }

    @SubscribeEvent
    fun onPacket(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        try {
            val packet = event.packet
            // 安全读取字节数据
            val buffer = packet.payload()
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)
            val input = ByteStreams.newDataInput(bytes)

            if (packet.channel() == "$MOD_ID:main") {
                val header = input.readInt()
                when (header) {
                    PacketType.AimConfirm.header -> {
                        val bool = input.readBoolean()
                        MC.addScheduledTask {
                            if (bool) {
                                Aim.confirm()
                            } else {
                                Aim.cancel()
                            }
                        }
                    }
                    PacketType.AimRequest.header -> {
                        val aimPacket = AimPacket(
                            input.readUTF(),
                            input.readUTF(),
                            true,
                            input.readDouble(),
                            input.readDouble()
                        )
                        MC.addScheduledTask {
                            Aim.skill = aimPacket.skill
                            Aim.max = aimPacket.max
                            Aim.module = aimPacket.module
                            Aim.scale = aimPacket.scale
                            Aim.enable = aimPacket.enable
                        }
                    }
                    PacketType.Ghost.header -> {
                        val uuid = UUID.fromString(input.readUTF())
                        val timeout = input.readLong()
                        val density = input.readInt()
                        val gap = input.readInt()
                        MC.addScheduledTask {
                            Ghost.applyGhostEffect(uuid, timeout, density, gap)
                        }
                    }
                    PacketType.Flicker.header -> {
                        val uuid = UUID.fromString(input.readUTF())
                        val timeout = input.readLong()
                        val alpha = input.readFloat()
                        MC.addScheduledTask {
                            Flicker.applyFlickerEffect(uuid, timeout, alpha)
                        }
                    }
                    PacketType.MouseRequest.header -> {
                        val show = input.readBoolean()
                        MC.addScheduledTask {
                            if (show) {
                                MouseCursor.show()
                            } else {
                                MouseCursor.hide()
                            }
                        }
                    }
                    PacketType.EntityShow.header -> {
                        val uuid = UUID.fromString(input.readUTF())
                        val id = input.readUTF()
                        val x = input.readDouble()
                        val y = input.readDouble()
                        val z = input.readDouble()
                        val timeout = input.readLong()
                        val rotateX = input.readFloat()
                        val rotateY = input.readFloat()
                        val rotateZ = input.readFloat()
                        val scale = input.readFloat()
                        MC.addScheduledTask {
                            EntityShow.addShadow(uuid, id, x, y, z, timeout, rotateX, rotateY, rotateZ, scale)
                        }
                    }
                    PacketType.EntityShowRemove.header -> {
                        val uuid = UUID.fromString(input.readUTF())
                        val group = input.readUTF()
                        MC.addScheduledTask {
                            EntityShow.removeShadow(uuid, group)
                        }
                    }
                    PacketType.PlayerNavigation.header -> {
                        val x = input.readInt()
                        val y = input.readInt()
                        val z = input.readInt()
                        val range = input.readInt()
                        MC.addScheduledTask {
                            PlayerNavigation.start(x, y, z, range)
                        }
                    }
                    PacketType.PlayerNavigationStop.header -> {
                        MC.addScheduledTask {
                            PlayerNavigation.stop()
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("处理数据包时发生错误", ex)
        }
    }

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
                    "$MOD_ID:main"
                )
            )
        } catch (ex: Exception) {
            OrryxMod.logger.error("发送数据包失败: ${ex.message}")
        }
    }
}
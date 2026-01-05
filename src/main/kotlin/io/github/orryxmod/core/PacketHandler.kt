package io.github.orryxmod.core

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.modules.Aim
import io.github.orryxmod.modules.EntityShow
import io.github.orryxmod.modules.Flicker
import io.github.orryxmod.modules.Ghost
import io.github.orryxmod.modules.MouseCursor
import io.github.orryxmod.modules.PlayerNavigation
import io.github.orryxmod.modules.fractureblock.Shockwave
import io.github.orryxmod.util.MC
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import java.util.UUID

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
        data object SquareShockwave : PacketType(12)
        data object CircleShockwave : PacketType(13)
        data object SectorShockwave : PacketType(14)
    }

    // 数据包处理器接口
    private fun interface PacketProcessor {
        fun process(input: ByteArrayDataInput)
    }

    // 处理器注册表
    private val processors: Map<Int, PacketProcessor> = mapOf(
        PacketType.AimConfirm.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive AimConfirm")
            val bool = input.readBoolean()
            MC.addScheduledTask {
                if (bool) Aim.confirm() else Aim.cancel()
            }
        },

        PacketType.AimRequest.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive AimRequest")
            val skill = input.readUTF()
            val picture = input.readUTF()
            val scale = input.readDouble()
            val max = input.readDouble()
            MC.addScheduledTask {
                Aim.skill = skill
                Aim.max = max
                Aim.module = picture
                Aim.scale = scale
                Aim.enable = true
            }
        },

        PacketType.Ghost.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive Ghost")
            val uuid = parseUUID(input.readUTF()) ?: return@PacketProcessor
            val timeout = input.readLong().coerceIn(0, 60_000)
            val density = input.readInt().coerceIn(1, 50)
            val gap = input.readInt().coerceIn(0, 20)
            MC.addScheduledTask {
                Ghost.applyGhostEffect(uuid, timeout, density, gap)
            }
        },

        PacketType.Flicker.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive Flicker")
            val uuid = parseUUID(input.readUTF()) ?: return@PacketProcessor
            val timeout = input.readLong().coerceIn(0, 60_000)
            val alpha = input.readFloat().coerceIn(0f, 1f)
            MC.addScheduledTask {
                Flicker.applyFlickerEffect(uuid, timeout, alpha)
            }
        },

        PacketType.MouseRequest.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive MouseRequest")
            val show = input.readBoolean()
            MC.addScheduledTask {
                if (show) MouseCursor.show() else MouseCursor.hide()
            }
        },

        PacketType.EntityShow.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive EntityShow")
            val uuid = parseUUID(input.readUTF()) ?: return@PacketProcessor
            val id = input.readUTF()
            val x = input.readDouble()
            val y = input.readDouble()
            val z = input.readDouble()
            val timeout = input.readLong().coerceIn(0, 300_000)
            val rotateX = input.readFloat()
            val rotateY = input.readFloat()
            val rotateZ = input.readFloat()
            val scale = input.readFloat().coerceIn(0.01f, 10f)
            MC.addScheduledTask {
                EntityShow.addShadow(uuid, id, x, y, z, timeout, rotateX, rotateY, rotateZ, scale)
            }
        },

        PacketType.EntityShowRemove.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive EntityShowRemove")
            val uuid = parseUUID(input.readUTF()) ?: return@PacketProcessor
            val group = input.readUTF()
            MC.addScheduledTask {
                EntityShow.removeShadow(uuid, group)
            }
        },

        PacketType.PlayerNavigation.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive PlayerNavigation")
            val x = input.readInt()
            val y = input.readInt()
            val z = input.readInt()
            val range = input.readInt().coerceIn(0, 100)
            MC.addScheduledTask {
                PlayerNavigation.start(x, y, z, range)
            }
        },

        PacketType.PlayerNavigationStop.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive PlayerNavigationStop")
            MC.addScheduledTask {
                PlayerNavigation.stop()
            }
        },

        PacketType.SquareShockwave.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive SquareShockwave")
            val x = input.readDouble()
            val y = input.readDouble()
            val z = input.readDouble()
            val length = input.readDouble().coerceIn(0.5, 100.0)
            val width = input.readDouble().coerceIn(0.5, 100.0)
            val yaw = input.readDouble()
            MC.addScheduledTask {
                Shockwave.squareSlamFracture(x, y, z, length, width, yaw)
            }
        },

        PacketType.CircleShockwave.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive CircleShockwave")
            val x = input.readDouble()
            val y = input.readDouble()
            val z = input.readDouble()
            val r = input.readDouble().coerceIn(0.5, 100.0)
            MC.addScheduledTask {
                Shockwave.circleSlamFracture(x, y, z, r)
            }
        },

        PacketType.SectorShockwave.header to PacketProcessor { input ->
            OrryxMod.logger.info("Packet Receive SectorShockwave")
            val x = input.readDouble()
            val y = input.readDouble()
            val z = input.readDouble()
            val r = input.readDouble().coerceIn(0.5, 100.0)
            val yaw = input.readDouble()
            val angle = input.readDouble().coerceIn(0.0, 360.0)
            MC.addScheduledTask {
                Shockwave.sectorSlamFracture(x, y, z, r, angle, yaw)
            }
        }
    )

    // 安全解析 UUID
    private fun parseUUID(str: String): UUID? {
        return runCatching { UUID.fromString(str) }.getOrNull().also {
            if (it == null) OrryxMod.logger.warn("Invalid UUID: $str")
        }
    }

    @SubscribeEvent
    fun onPacket(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        try {
            val packet = event.packet
            if (packet.channel() != "orryxmod:main") return

            val buffer = packet.payload()
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)
            val input = ByteStreams.newDataInput(bytes)

            val header = input.readInt()
            processors[header]?.process(input)
                ?: OrryxMod.logger.warn("Unknown packet type: $header")

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
                    "${OrryxMod.MOD_ID}:main"
                )
            )
        } catch (ex: Exception) {
            OrryxMod.logger.error("发送数据包失败: ${ex.message}")
        }
    }
}

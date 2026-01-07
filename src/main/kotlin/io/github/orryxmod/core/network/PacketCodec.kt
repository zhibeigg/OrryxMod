package io.github.orryxmod.core.network

import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import io.github.orryxmod.OrryxMod
import java.util.UUID

/**
 * 协议编解码器
 */
object PacketCodec {

    /**
     * 解码：字节流 -> OrryxPacket
     */
    fun decode(input: ByteArrayDataInput): OrryxPacket? {
        return try {
            when (val id = input.readInt()) {
                1 -> OrryxPacket.AimRequest(
                    skill = input.readUTF(),
                    module = input.readUTF(),
                    scale = input.readDouble(),
                    maxDistance = input.readDouble()
                )
                2 -> OrryxPacket.AimConfirm(
                    confirmed = input.readBoolean()
                )
                3 -> OrryxPacket.GhostEffect(
                    uuid = input.readUUID(),
                    timeout = input.readLong().coerceIn(0, 60_000),
                    density = input.readInt().coerceIn(1, 50),
                    gap = input.readInt().coerceIn(0, 20)
                )
                5 -> OrryxPacket.FlickerEffect(
                    uuid = input.readUUID(),
                    timeout = input.readLong().coerceIn(0, 60_000),
                    alpha = input.readFloat().coerceIn(0f, 1f),
                    duration = input.readLong().coerceIn(-1, 60_000),
                    scale = input.readFloat().coerceIn(0.1f, 10f)
                )
                7 -> OrryxPacket.MouseControl(
                    show = input.readBoolean()
                )
                8 -> OrryxPacket.EntityShowAdd(
                    uuid = input.readUUID(),
                    group = input.readUTF(),
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    timeout = input.readLong().coerceIn(0, 300_000),
                    rotateX = input.readFloat(),
                    rotateY = input.readFloat(),
                    rotateZ = input.readFloat(),
                    scale = input.readFloat().coerceIn(0.01f, 10f),
                    alpha = input.readFloat().coerceIn(0f, 1f),
                    fadeOut = input.readBoolean()
                )
                9 -> OrryxPacket.EntityShowRemove(
                    uuid = input.readUUID(),
                    group = input.readUTF()
                )
                10 -> OrryxPacket.NavigationStart(
                    x = input.readInt(),
                    y = input.readInt(),
                    z = input.readInt(),
                    range = input.readInt().coerceIn(0, 100)
                )
                11 -> OrryxPacket.NavigationStop
                12 -> OrryxPacket.SquareShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    length = input.readDouble().coerceIn(0.5, 100.0),
                    width = input.readDouble().coerceIn(0.5, 100.0),
                    yaw = input.readDouble()
                )
                13 -> OrryxPacket.CircleShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    radius = input.readDouble().coerceIn(0.5, 100.0)
                )
                14 -> OrryxPacket.SectorShockwave(
                    x = input.readDouble(),
                    y = input.readDouble(),
                    z = input.readDouble(),
                    radius = input.readDouble().coerceIn(0.5, 100.0),
                    angle = input.readDouble().coerceIn(0.0, 360.0),
                    yaw = input.readDouble()
                )
                15 -> {
                    val count = input.readInt()
                    val configs = mutableMapOf<String, io.github.orryxmod.feature.bloom.BloomConfig>()
                    repeat(count) {
                        val id = input.readUTF()
                        val config = readBloomConfig(input)
                        configs[id] = config
                    }
                    OrryxPacket.BloomConfigSync(configs)
                }
                16 -> OrryxPacket.BloomConfigUpdate(
                    id = input.readUTF(),
                    config = readBloomConfig(input)
                )
                17 -> OrryxPacket.BloomConfigRemove(
                    id = input.readUTF()
                )
                else -> {
                    OrryxMod.logger.warn("Unknown packet ID: $id")
                    null
                }
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error decoding packet", ex)
            null
        }
    }

    /**
     * 编码：OrryxPacket -> 字节流
     */
    fun encode(packet: OrryxPacket, output: ByteArrayDataOutput) {
        output.writeInt(packet.packetId)
        when (packet) {
            is OrryxPacket.AimResponse -> {
                output.writeUTF(packet.skill)
                output.writeDouble(packet.x)
                output.writeDouble(packet.y)
                output.writeDouble(packet.z)
                output.writeFloat(packet.yaw)
                output.writeFloat(packet.pitch)
            }
            else -> {
                // 目前只有 AimResponse 需要客户端发送
            }
        }
    }

    /**
     * 从输入流读取 UUID
     */
    private fun ByteArrayDataInput.readUUID(): UUID {
        val str = readUTF()
        return runCatching { UUID.fromString(str) }.getOrElse {
            throw IllegalArgumentException("Invalid UUID format: $str")
        }
    }

    /**
     * 从输入流读取 BloomConfig
     */
    private fun readBloomConfig(input: ByteArrayDataInput): io.github.orryxmod.feature.bloom.BloomConfig {
        return io.github.orryxmod.feature.bloom.BloomConfig(
            name = input.readUTF(),
            color = intArrayOf(
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255)
            ),
            strength = input.readFloat().coerceIn(0f, 10f),
            radius = input.readFloat().coerceIn(1f, 128f),
            priority = input.readInt()
        )
    }
}

package io.github.orryxmod.core.network

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.MC
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

/**
 * 网络处理入口（Forge 事件监听）
 */
object NetworkHandler {

    /** 单个网络包最大字节数，防止恶意超大包导致 OOM */
    private const val MAX_PACKET_SIZE = 65536

    @SubscribeEvent
    fun onPacketReceived(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        try {
            val fmlPacket = event.packet
            if (fmlPacket.channel() != "orryxmod:main") return

            val buffer = fmlPacket.payload()
            if (buffer.readableBytes() > MAX_PACKET_SIZE) {
                OrryxMod.logger.warn("[Network] Packet too large: ${buffer.readableBytes()} bytes (max: $MAX_PACKET_SIZE)")
                return
            }

            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)

            val packet = PacketCodec.decode(bytes)
            if (packet == null) {
                OrryxMod.logger.warn("[Network] Failed to decode packet")
                return
            }
            OrryxMod.logger.info("[Network] Received packet: ${packet::class.simpleName}")

            // 在主线程处理
            MC.addScheduledTask {
                PacketDispatcher.dispatch(packet)
            }
        } catch (ex: Exception) {
            OrryxMod.logger.error("Error processing packet", ex)
        }
    }
}

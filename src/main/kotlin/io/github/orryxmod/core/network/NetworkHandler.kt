package io.github.orryxmod.core.network

import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.MC
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

/**
 * 网络处理入口（Forge 事件监听）
 */
object NetworkHandler {

    @SubscribeEvent
    fun onPacketReceived(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        try {
            val fmlPacket = event.packet
            if (fmlPacket.channel() != "orryxmod:main") return

            val buffer = fmlPacket.payload()
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)

            val input = ByteStreams.newDataInput(bytes)
            val packet = PacketCodec.decode(input)
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

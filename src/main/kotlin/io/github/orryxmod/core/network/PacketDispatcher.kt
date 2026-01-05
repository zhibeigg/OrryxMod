package io.github.orryxmod.core.network

import com.google.common.io.ByteStreams
import io.github.orryxmod.OrryxMod
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import io.netty.buffer.Unpooled
import net.minecraft.network.PacketBuffer
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import kotlin.reflect.KClass

/**
 * 包处理器包装类
 */
private class PacketHandler<T : OrryxPacket>(
    private val handler: (T) -> Unit
) {
    fun handle(packet: T) = handler(packet)
}

/**
 * 协议分发器
 */
object PacketDispatcher {

    private val handlers = mutableMapOf<KClass<out OrryxPacket>, MutableList<PacketHandler<*>>>()

    /**
     * 注册处理器（泛型版本）
     */
    inline fun <reified T : OrryxPacket> register(noinline handler: (T) -> Unit) {
        register(T::class, handler)
    }

    /**
     * 注册处理器
     */
    fun <T : OrryxPacket> register(type: KClass<T>, handler: (T) -> Unit) {
        handlers.getOrPut(type) { mutableListOf() }
            .add(PacketHandler(handler))
    }

    /**
     * 分发协议包
     */
    fun dispatch(packet: OrryxPacket) {
        // 先发布事件，允许拦截
        val event = EventBus.publish(Events.PacketReceived(packet))
        if (event.cancelled) return

        // 分发到注册的处理器
        val packetHandlers = handlers[packet::class] ?: return

        for (handler in packetHandlers) {
            try {
                @Suppress("UNCHECKED_CAST")
                (handler as PacketHandler<OrryxPacket>).handle(packet)
            } catch (ex: Exception) {
                OrryxMod.logger.error("Error handling packet ${packet::class.simpleName}", ex)
            }
        }
    }

    /**
     * 发送协议包到服务器
     */
    fun send(packet: OrryxPacket) {
        try {
            val output = ByteStreams.newDataOutput()
            PacketCodec.encode(packet, output)
            OrryxMod.network.sendToServer(
                FMLProxyPacket(
                    PacketBuffer(Unpooled.wrappedBuffer(output.toByteArray())),
                    "${OrryxMod.MOD_ID}:main"
                )
            )
        } catch (ex: Exception) {
            OrryxMod.logger.error("Failed to send packet: ${ex.message}")
        }
    }

    /**
     * 清除所有处理器
     */
    fun clear() {
        handlers.clear()
    }
}

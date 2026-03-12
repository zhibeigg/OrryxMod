package io.github.orryxmod.core.network

import io.github.orryxmod.TestHelper
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.event.Events
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PacketDispatcherTest {

    @BeforeEach
    fun setup() {
        TestHelper.mockLogger()
        PacketDispatcher.clear()
        EventBus.clear()
    }

    @AfterEach
    fun teardown() {
        PacketDispatcher.clear()
        EventBus.clear()
        TestHelper.cleanup()
    }

    @Test
    fun `register and dispatch basic flow`() {
        var received: OrryxPacket.AimConfirm? = null
        PacketDispatcher.register<OrryxPacket.AimConfirm> { received = it }

        PacketDispatcher.dispatch(OrryxPacket.AimConfirm(true))

        assertNotNull(received)
        assertTrue(received!!.confirmed)
    }

    @Test
    fun `multiple handlers all called`() {
        val results = mutableListOf<Int>()
        PacketDispatcher.register<OrryxPacket.AimConfirm> { results.add(1) }
        PacketDispatcher.register<OrryxPacket.AimConfirm> { results.add(2) }

        PacketDispatcher.dispatch(OrryxPacket.AimConfirm(true))

        assertEquals(listOf(1, 2), results)
    }

    @Test
    fun `EventBus cancellation prevents dispatch`() {
        var handlerCalled = false
        PacketDispatcher.register<OrryxPacket.AimConfirm> { handlerCalled = true }

        // 注册拦截器
        EventBus.subscribe<Events.PacketReceived>(priority = 100) {
            it.cancelled = true
        }

        PacketDispatcher.dispatch(OrryxPacket.AimConfirm(true))

        assertFalse(handlerCalled)
    }

    @Test
    fun `handler exception does not affect other handlers`() {
        val results = mutableListOf<Int>()
        PacketDispatcher.register<OrryxPacket.AimConfirm> { throw RuntimeException("boom") }
        PacketDispatcher.register<OrryxPacket.AimConfirm> { results.add(2) }

        PacketDispatcher.dispatch(OrryxPacket.AimConfirm(true))

        assertEquals(listOf(2), results)
    }

    @Test
    fun `clear removes all handlers`() {
        var called = false
        PacketDispatcher.register<OrryxPacket.AimConfirm> { called = true }

        PacketDispatcher.clear()
        PacketDispatcher.dispatch(OrryxPacket.AimConfirm(true))

        assertFalse(called)
    }

    @Test
    fun `dispatch with no handlers does not throw`() {
        assertDoesNotThrow {
            PacketDispatcher.dispatch(OrryxPacket.NavigationStop)
        }
    }
}

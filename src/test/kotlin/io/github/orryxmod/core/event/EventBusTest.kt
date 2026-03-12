package io.github.orryxmod.core.event

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EventBusTest {

    // 测试用事件
    data class TestEvent(val value: String) : Event
    data class AnotherEvent(val number: Int) : Event
    data class CancelEvent(val data: String, override var cancelled: Boolean = false) : CancellableEvent

    @BeforeEach
    fun setup() {
        EventBus.clear()
    }

    @AfterEach
    fun teardown() {
        EventBus.clear()
    }

    @Test
    fun `subscribe and publish basic flow`() {
        var received: String? = null
        EventBus.subscribe<TestEvent> { received = it.value }

        EventBus.publish(TestEvent("hello"))

        assertEquals("hello", received)
    }

    @Test
    fun `priority ordering - higher priority executes first`() {
        val order = mutableListOf<Int>()

        EventBus.subscribe<TestEvent>(priority = 1) { order.add(1) }
        EventBus.subscribe<TestEvent>(priority = 10) { order.add(10) }
        EventBus.subscribe<TestEvent>(priority = 5) { order.add(5) }

        EventBus.publish(TestEvent("test"))

        assertEquals(listOf(10, 5, 1), order)
    }

    @Test
    fun `cancellable event stops propagation`() {
        val order = mutableListOf<Int>()

        EventBus.subscribe<CancelEvent>(priority = 10) {
            order.add(10)
            it.cancelled = true
        }
        EventBus.subscribe<CancelEvent>(priority = 5) { order.add(5) }
        EventBus.subscribe<CancelEvent>(priority = 1) { order.add(1) }

        val event = EventBus.publish(CancelEvent("test"))

        assertEquals(listOf(10), order)
        assertTrue(event.cancelled)
    }

    @Test
    fun `unsubscribeAll removes handlers for specific type`() {
        var called = false
        EventBus.subscribe<TestEvent> { called = true }
        EventBus.unsubscribeAll(TestEvent::class)

        EventBus.publish(TestEvent("test"))

        assertFalse(called)
    }

    @Test
    fun `clear removes all handlers`() {
        var testCalled = false
        var anotherCalled = false
        EventBus.subscribe<TestEvent> { testCalled = true }
        EventBus.subscribe<AnotherEvent> { anotherCalled = true }

        EventBus.clear()

        EventBus.publish(TestEvent("test"))
        EventBus.publish(AnotherEvent(42))

        assertFalse(testCalled)
        assertFalse(anotherCalled)
    }

    @Test
    fun `concurrent subscribe safety`() {
        val latch = CountDownLatch(10)
        val errors = CopyOnWriteArrayList<Throwable>()

        // 10 个线程同时 subscribe
        repeat(10) { i ->
            Thread {
                try {
                    EventBus.subscribe<TestEvent>(priority = i) { }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(errors.isEmpty(), "Concurrent subscribe should not throw: $errors")
    }

    @Test
    fun `multiple event types do not interfere`() {
        var testReceived = false
        var anotherReceived = false

        EventBus.subscribe<TestEvent> { testReceived = true }
        EventBus.subscribe<AnotherEvent> { anotherReceived = true }

        EventBus.publish(TestEvent("test"))

        assertTrue(testReceived)
        assertFalse(anotherReceived)
    }

    @Test
    fun `publish returns the event`() {
        EventBus.subscribe<TestEvent> { }
        val event = TestEvent("result")
        val returned = EventBus.publish(event)
        assertSame(event, returned)
    }

    @Test
    fun `publish with no subscribers returns event unchanged`() {
        val event = TestEvent("no-sub")
        val returned = EventBus.publish(event)
        assertSame(event, returned)
    }
}

package io.github.orryxmod.core.registry

import io.github.orryxmod.TestHelper
import io.github.orryxmod.core.api.Feature
import io.github.orryxmod.core.api.FeatureBase
import io.github.orryxmod.core.api.OnDisable
import io.github.orryxmod.core.api.OnEnable
import io.github.orryxmod.core.event.EventBus
import io.github.orryxmod.core.network.PacketDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeatureRegistryTest {

    @Feature(id = "test-a", description = "Feature A")
    class FeatureA : FeatureBase() {
        var enableCalls = 0
        var disableCalls = 0
        var onEnableCalls = 0
        var onDisableCalls = 0

        override fun enable() {
            enableCalls++
            super.enable()
        }

        override fun disable() {
            disableCalls++
            super.disable()
        }

        @OnEnable
        fun onEnable() { onEnableCalls++ }

        @OnDisable
        fun onDisable() { onDisableCalls++ }
    }

    @Feature(id = "test-b", description = "Feature B")
    class FeatureB : FeatureBase()

    @BeforeEach
    fun setup() {
        TestHelper.mockLogger()
        FeatureRegistry.clear()
    }

    @AfterEach
    fun teardown() {
        FeatureRegistry.clear()
        TestHelper.cleanup()
    }

    @Test
    fun `register and get by id`() {
        val feature = FeatureA()
        FeatureRegistry.register(feature)
        assertSame(feature, FeatureRegistry.get("test-a"))
    }

    @Test
    fun `register and get by class`() {
        val feature = FeatureA()
        FeatureRegistry.register(feature)
        assertSame(feature, FeatureRegistry.get(FeatureA::class))
    }

    @Test
    fun `duplicate registration is skipped`() {
        val f1 = FeatureA()
        val f2 = FeatureA()
        FeatureRegistry.register(f1)
        FeatureRegistry.register(f2)
        assertSame(f1, FeatureRegistry.get("test-a"))
    }

    @Test
    fun `features start disabled`() {
        assertFalse(FeatureA().enabled)
    }

    @Test
    fun `enable and disable are idempotent across overloads`() {
        val feature = FeatureA()
        var enabledEvents = 0
        var disabledEvents = 0
        EventBus.subscribe<io.github.orryxmod.core.event.Events.FeatureEnabled> { enabledEvents++ }
        EventBus.subscribe<io.github.orryxmod.core.event.Events.FeatureDisabled> { disabledEvents++ }
        FeatureRegistry.register(feature)

        assertTrue(FeatureRegistry.enable("test-a"))
        assertFalse(FeatureRegistry.enable(feature))
        assertTrue(feature.enabled)
        assertEquals(1, feature.enableCalls)
        assertEquals(1, feature.onEnableCalls)
        assertEquals(1, enabledEvents)

        assertTrue(FeatureRegistry.disable(feature))
        assertFalse(FeatureRegistry.disable("test-a"))
        assertFalse(feature.enabled)
        assertEquals(1, feature.disableCalls)
        assertEquals(1, feature.onDisableCalls)
        assertEquals(1, disabledEvents)
    }

    @Test
    fun `unregistered feature cannot be enabled or disabled`() {
        val feature = FeatureA()
        assertFalse(FeatureRegistry.enable(feature))
        assertFalse(FeatureRegistry.disable(feature))
    }

    @Test
    fun `getAll returns all registered features`() {
        FeatureRegistry.register(FeatureA())
        FeatureRegistry.register(FeatureB())
        assertEquals(2, FeatureRegistry.getAll().size)
    }

    @Test
    fun `clear cleans up EventBus and PacketDispatcher`() {
        var eventHandlerCalled = false
        EventBus.subscribe<io.github.orryxmod.core.event.Events.FeatureEnabled> {
            eventHandlerCalled = true
        }

        FeatureRegistry.register(FeatureA())
        FeatureRegistry.clear()

        // EventBus 应该被清空
        EventBus.publish(io.github.orryxmod.core.event.Events.FeatureEnabled(FeatureA()))
        assertFalse(eventHandlerCalled)
    }

    @Test
    fun `enableAll calls lifecycle methods`() {
        val feature = FeatureA()
        FeatureRegistry.register(feature)
        FeatureRegistry.enableAll()

        assertTrue(feature.enabled)
        assertEquals(1, feature.onEnableCalls)
    }

    @Test
    fun `disableAll calls lifecycle methods`() {
        val feature = FeatureA()
        FeatureRegistry.register(feature)
        FeatureRegistry.enableAll()
        FeatureRegistry.disableAll()

        assertFalse(feature.enabled)
        assertEquals(1, feature.onDisableCalls)
    }

    @Test
    fun `get returns null for unregistered id`() {
        assertNull(FeatureRegistry.get("nonexistent"))
    }

    @Test
    fun `get returns null for unregistered class`() {
        assertNull(FeatureRegistry.get(FeatureA::class))
    }
}

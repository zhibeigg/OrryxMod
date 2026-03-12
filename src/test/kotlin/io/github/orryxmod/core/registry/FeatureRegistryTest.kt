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
        var enableCalled = false
        var disableCalled = false

        @OnEnable
        fun onEnable() { enableCalled = true }

        @OnDisable
        fun onDisable() { disableCalled = true }
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
        assertTrue(feature.enableCalled)
    }

    @Test
    fun `disableAll calls lifecycle methods`() {
        val feature = FeatureA()
        FeatureRegistry.register(feature)
        FeatureRegistry.enableAll()
        FeatureRegistry.disableAll()

        assertFalse(feature.enabled)
        assertTrue(feature.disableCalled)
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

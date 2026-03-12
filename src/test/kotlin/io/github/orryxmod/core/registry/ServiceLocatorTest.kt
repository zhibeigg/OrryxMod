package io.github.orryxmod.core.registry

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ServiceLocatorTest {

    interface TestService
    class TestServiceImpl : TestService

    class DisposableService : Disposable {
        var disposed = false
        override fun dispose() { disposed = true }
    }

    @BeforeEach
    fun setup() {
        ServiceLocator.clear()
    }

    @AfterEach
    fun teardown() {
        ServiceLocator.clear()
    }

    @Test
    fun `register and get basic flow`() {
        val service = TestServiceImpl()
        ServiceLocator.register<TestService>(service)

        val retrieved = ServiceLocator.get<TestService>()
        assertSame(service, retrieved)
    }

    @Test
    fun `get returns null for unregistered service`() {
        assertNull(ServiceLocator.get<TestService>())
    }

    @Test
    fun `require returns service when exists`() {
        val service = TestServiceImpl()
        ServiceLocator.register<TestService>(service)

        val retrieved = ServiceLocator.require<TestService>()
        assertSame(service, retrieved)
    }

    @Test
    fun `require throws when service not found`() {
        assertThrows<IllegalStateException> {
            ServiceLocator.require<TestService>()
        }
    }

    @Test
    fun `has returns true for registered service`() {
        ServiceLocator.register<TestService>(TestServiceImpl())
        assertTrue(ServiceLocator.has<TestService>())
    }

    @Test
    fun `has returns false for unregistered service`() {
        assertFalse(ServiceLocator.has<TestService>())
    }

    @Test
    fun `remove disposes Disposable service`() {
        val service = DisposableService()
        ServiceLocator.register(DisposableService::class, service)

        val removed = ServiceLocator.remove(DisposableService::class)

        assertSame(service, removed)
        assertTrue(service.disposed)
        assertFalse(ServiceLocator.has(DisposableService::class))
    }

    @Test
    fun `register overwrites old service and disposes it`() {
        val old = DisposableService()
        val new = DisposableService()

        ServiceLocator.register(DisposableService::class, old)
        ServiceLocator.register(DisposableService::class, new)

        assertTrue(old.disposed)
        assertFalse(new.disposed)
        assertSame(new, ServiceLocator.get(DisposableService::class))
    }

    @Test
    fun `clear disposes all Disposable services`() {
        val s1 = DisposableService()
        val s2 = DisposableService()

        ServiceLocator.register(DisposableService::class, s1)
        // 注册不同类型
        ServiceLocator.register<TestService>(TestServiceImpl())

        // 重新注册 s2 到另一个 key
        ServiceLocator.register(Disposable::class, s2)

        ServiceLocator.clear()

        assertTrue(s1.disposed)
        assertTrue(s2.disposed)
        assertFalse(ServiceLocator.has(DisposableService::class))
        assertFalse(ServiceLocator.has<TestService>())
    }

    @Test
    fun `remove returns null for unregistered service`() {
        assertNull(ServiceLocator.remove(TestService::class))
    }

    @Test
    fun `generic register and get with KClass`() {
        val service = TestServiceImpl()
        ServiceLocator.register(TestService::class, service)

        val retrieved = ServiceLocator.get(TestService::class)
        assertSame(service, retrieved)
    }
}

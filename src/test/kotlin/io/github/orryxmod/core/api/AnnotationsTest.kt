package io.github.orryxmod.core.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnotationsTest {

    @Test
    fun `Feature annotation has RUNTIME retention`() {
        val retention = Feature::class.annotations
            .filterIsInstance<Retention>()
            .firstOrNull()
        assertNotNull(retention)
        assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
    }

    @Test
    fun `Feature annotation targets CLASS`() {
        val target = Feature::class.annotations
            .filterIsInstance<Target>()
            .firstOrNull()
        assertNotNull(target)
        assertTrue(AnnotationTarget.CLASS in target!!.allowedTargets)
    }

    @Test
    fun `OnEnable annotation has RUNTIME retention`() {
        val retention = OnEnable::class.annotations
            .filterIsInstance<Retention>()
            .firstOrNull()
        assertNotNull(retention)
        assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
    }

    @Test
    fun `OnEnable annotation targets FUNCTION`() {
        val target = OnEnable::class.annotations
            .filterIsInstance<Target>()
            .firstOrNull()
        assertNotNull(target)
        assertTrue(AnnotationTarget.FUNCTION in target!!.allowedTargets)
    }

    @Test
    fun `OnDisable annotation has RUNTIME retention and targets FUNCTION`() {
        val retention = OnDisable::class.annotations.filterIsInstance<Retention>().firstOrNull()
        val target = OnDisable::class.annotations.filterIsInstance<Target>().firstOrNull()
        assertEquals(AnnotationRetention.RUNTIME, retention?.value)
        assertTrue(AnnotationTarget.FUNCTION in target!!.allowedTargets)
    }

    @Test
    fun `OnDisconnect annotation has RUNTIME retention and targets FUNCTION`() {
        val retention = OnDisconnect::class.annotations.filterIsInstance<Retention>().firstOrNull()
        val target = OnDisconnect::class.annotations.filterIsInstance<Target>().firstOrNull()
        assertEquals(AnnotationRetention.RUNTIME, retention?.value)
        assertTrue(AnnotationTarget.FUNCTION in target!!.allowedTargets)
    }

    @Test
    fun `OnPacket annotation has RUNTIME retention and targets FUNCTION`() {
        val retention = OnPacket::class.annotations.filterIsInstance<Retention>().firstOrNull()
        val target = OnPacket::class.annotations.filterIsInstance<Target>().firstOrNull()
        assertEquals(AnnotationRetention.RUNTIME, retention?.value)
        assertTrue(AnnotationTarget.FUNCTION in target!!.allowedTargets)
    }

    @Test
    fun `Subscribe annotation has RUNTIME retention and targets FUNCTION`() {
        val retention = Subscribe::class.annotations.filterIsInstance<Retention>().firstOrNull()
        val target = Subscribe::class.annotations.filterIsInstance<Target>().firstOrNull()
        assertEquals(AnnotationRetention.RUNTIME, retention?.value)
        assertTrue(AnnotationTarget.FUNCTION in target!!.allowedTargets)
    }

    @Test
    fun `DependsOn annotation has RUNTIME retention and targets CLASS`() {
        val retention = DependsOn::class.annotations.filterIsInstance<Retention>().firstOrNull()
        val target = DependsOn::class.annotations.filterIsInstance<Target>().firstOrNull()
        assertEquals(AnnotationRetention.RUNTIME, retention?.value)
        assertTrue(AnnotationTarget.CLASS in target!!.allowedTargets)
    }
}

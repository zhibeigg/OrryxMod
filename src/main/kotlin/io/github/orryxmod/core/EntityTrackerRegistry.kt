package io.github.orryxmod.core

import net.minecraft.entity.player.EntityPlayer
import java.lang.ref.WeakReference
import java.util.AbstractList

object EntityTrackerRegistry {

    const val DEFAULT_MAX_ENTRIES = 64
    const val DEFAULT_MAX_SAMPLES_PER_ENTITY = 64
    private const val HARD_MAX_ENTRIES = 256
    private const val HARD_MAX_SAMPLES_PER_ENTITY = 256

    data class Limits(
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        val maxSamplesPerEntity: Int = DEFAULT_MAX_SAMPLES_PER_ENTITY
    ) {
        internal fun normalized(): Limits = Limits(
            maxEntries = maxEntries.coerceIn(1, HARD_MAX_ENTRIES),
            maxSamplesPerEntity = maxSamplesPerEntity.coerceIn(1, HARD_MAX_SAMPLES_PER_ENTITY)
        )
    }

    private val trackerEntries = mutableListOf<Entry>()
    private var limits = Limits()

    val currentLimits: Limits
        get() = limits

    internal val entryCount: Int
        get() = trackerEntries.size

    /**
     * 调整 Registry 的全局保守上限。调用方应在 Minecraft 客户端主线程执行。
     */
    fun configureLimits(newLimits: Limits) {
        limits = newLimits.normalized()
        trackerEntries.forEach { it.applyGlobalSampleLimit() }
        trimEntriesToLimit()
    }

    fun tick() {
        val iterator = trackerEntries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.update()) {
                entry.clearSnapshots()
                iterator.remove()
            }
        }
    }

    fun getOrCreateEntry(living: EntityPlayer, maxTrack: Int): Entry {
        pruneInvalidEntries()

        val existingIndex = trackerEntries.indexOfFirst { it.isTracking(living) }
        if (existingIndex >= 0) {
            val existing = trackerEntries.removeAt(existingIndex)
            existing.resize(maxTrack)
            trackerEntries.add(existing)
            return existing
        }

        while (trackerEntries.size >= limits.maxEntries) {
            trackerEntries.removeAt(0).clearSnapshots()
        }

        return Entry(living, maxTrack).also { trackerEntries.add(it) }
    }

    fun remove(entry: Entry?) {
        if (entry == null) return
        if (trackerEntries.remove(entry)) {
            entry.clearSnapshots()
        }
    }

    fun clear() {
        trackerEntries.forEach { it.clearSnapshots() }
        trackerEntries.clear()
    }

    private fun pruneInvalidEntries() {
        val iterator = trackerEntries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.hasTrackedEntity()) {
                entry.clearSnapshots()
                iterator.remove()
            }
        }
    }

    private fun trimEntriesToLimit() {
        while (trackerEntries.size > limits.maxEntries) {
            trackerEntries.removeAt(0).clearSnapshots()
        }
    }

    class Entry(entityToTrack: EntityPlayer, maxTrack: Int = 20) {
        private val trackedEntity = WeakReference(entityToTrack)
        private var requestedCapacity = maxTrack.coerceAtLeast(1)
        private var capacity = effectiveCapacity()
        private var snapshots = arrayOfNulls<EntityInfo>(capacity)
        private var firstSnapshotIndex = 0
        private var snapshotCount = 0

        private val trackedInfoView = object : AbstractList<EntityInfo>() {
            override val size: Int
                get() = snapshotCount

            override fun get(index: Int): EntityInfo {
                if (index < 0 || index >= snapshotCount) {
                    throw IndexOutOfBoundsException("index=$index, size=$snapshotCount")
                }
                val physicalIndex = (firstSnapshotIndex + index) % snapshots.size
                return snapshots[physicalIndex]
                    ?: throw IllegalStateException("Missing tracker snapshot at index $index")
            }
        }

        val entityToTrack: EntityPlayer?
            get() = trackedEntity.get()

        /**
         * 按时间从旧到新排列的稳定只读视图；读取和迭代不会复制底层快照。
         */
        val trackedInfo: List<EntityInfo>
            get() = trackedInfoView

        internal val sampleCount: Int
            get() = snapshotCount

        internal fun isTracking(entity: EntityPlayer): Boolean = trackedEntity.get() === entity

        internal fun hasTrackedEntity(): Boolean {
            val entity = trackedEntity.get() ?: return false
            return isEntityPresent(entity)
        }

        internal fun resize(maxTrack: Int) {
            requestedCapacity = maxTrack.coerceAtLeast(1)
            applyGlobalSampleLimit()
        }

        internal fun applyGlobalSampleLimit() {
            val newCapacity = effectiveCapacity()
            if (newCapacity == capacity) return

            val retainedCount = snapshotCount.coerceAtMost(newCapacity)
            val retained = arrayOfNulls<EntityInfo>(newCapacity)
            val firstRetained = snapshotCount - retainedCount
            for (index in 0 until retainedCount) {
                retained[index] = trackedInfoView[firstRetained + index]
            }

            snapshots = retained
            capacity = newCapacity
            firstSnapshotIndex = 0
            snapshotCount = retainedCount
        }

        fun update(): Boolean {
            val entity = trackedEntity.get() ?: return false
            if (!isEntityPresent(entity)) return false

            addSnapshot(EntityInfo(entity))
            return true
        }

        internal fun clearSnapshots() {
            snapshots.fill(null)
            firstSnapshotIndex = 0
            snapshotCount = 0
        }

        private fun effectiveCapacity(): Int = requestedCapacity
            .coerceAtMost(EntityTrackerRegistry.limits.maxSamplesPerEntity)
            .coerceAtLeast(1)

        private fun addSnapshot(snapshot: EntityInfo) {
            if (snapshotCount < capacity) {
                val targetIndex = (firstSnapshotIndex + snapshotCount) % capacity
                snapshots[targetIndex] = snapshot
                snapshotCount++
                return
            }

            snapshots[firstSnapshotIndex] = snapshot
            firstSnapshotIndex = (firstSnapshotIndex + 1) % capacity
        }

        private fun isEntityPresent(entity: EntityPlayer): Boolean {
            if (entity.isDead) return false
            val world = entity.world ?: return false
            return world.getEntityByID(entity.entityId) === entity
        }
    }

    class EntityInfo(entity: EntityPlayer) {
        private val trackedRef = WeakReference(entity)

        /** 获取被追踪的实体（可能已被 GC 回收） */
        val tracked: EntityPlayer? get() = trackedRef.get()

        val posX: Double = entity.posX
        val posY: Double = entity.posY
        val posZ: Double = entity.posZ

        val lastTickPosX: Double = entity.lastTickPosX
        val lastTickPosY: Double = entity.lastTickPosY
        val lastTickPosZ: Double = entity.lastTickPosZ

        val renderYawOffset: Float = entity.renderYawOffset
        val rotationYawHead: Float = entity.rotationYawHead
        val rotationPitch: Float = if (entity.ticksElytraFlying > 4) {
            Math.toDegrees(-(Math.PI.toFloat() / 4f).toDouble()).toFloat()
        } else {
            entity.rotationPitch
        }

        val limbSwing: Float = entity.limbSwing
        val limbSwingAmount: Float = entity.limbSwingAmount

        val sneaking: Boolean = entity.isSneaking
        val sleeping: Boolean = entity.isPlayerSleeping
        val sprinting: Boolean = entity.isSprinting
        val invisible: Boolean = entity.isInvisible
        val elytraFlying: Boolean = entity.ticksElytraFlying > 4

        val height: Float = entity.height

        val lastTick: Int = entity.ticksExisted
    }
}

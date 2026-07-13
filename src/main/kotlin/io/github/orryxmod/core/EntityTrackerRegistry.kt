package io.github.orryxmod.core

import net.minecraft.entity.player.EntityPlayer
import java.lang.ref.WeakReference
import java.util.ArrayDeque

object EntityTrackerRegistry {

    private val trackerEntries = mutableListOf<Entry>()

    fun tick() {
        val iterator = trackerEntries.iterator()
        while (iterator.hasNext()) {
            if (!iterator.next().update()) {
                iterator.remove()
            }
        }
    }

    fun getOrCreateEntry(living: EntityPlayer, maxTrack: Int): Entry {
        val existing = trackerEntries.firstOrNull { it.isTracking(living) }
        if (existing != null) {
            existing.resize(maxTrack)
            return existing
        }

        return Entry(living, maxTrack).also { trackerEntries.add(it) }
    }

    fun clear() {
        trackerEntries.clear()
    }

    class Entry(entityToTrack: EntityPlayer, maxTrack: Int = 20) {
        private val trackedEntity = WeakReference(entityToTrack)
        private val snapshots = ArrayDeque<EntityInfo>()
        private var capacity = maxTrack.coerceAtLeast(1)

        val entityToTrack: EntityPlayer?
            get() = trackedEntity.get()

        /**
         * 保持旧版 Ghost 读取接口兼容，返回按时间从旧到新排列的快照。
         */
        val trackedInfo: List<EntityInfo>
            get() = snapshots.toList()

        internal fun isTracking(entity: EntityPlayer): Boolean = trackedEntity.get() === entity

        internal fun resize(maxTrack: Int) {
            capacity = maxTrack.coerceAtLeast(1)
            trimToCapacity()
        }

        fun update(): Boolean {
            val entity = trackedEntity.get() ?: return false
            if (entity.isDead) return false

            snapshots.addLast(EntityInfo(entity))
            trimToCapacity()
            return true
        }

        private fun trimToCapacity() {
            while (snapshots.size > capacity) {
                snapshots.removeFirst()
            }
        }
    }

    class EntityInfo(entity: EntityPlayer) {
        private val trackedRef = WeakReference(entity)

        /** 获取被追踪的实体（可能已被 GC 回收） */
        val tracked: EntityPlayer? get() = trackedRef.get()

        var posX: Double = entity.posX
        var posY: Double = entity.posY
        var posZ: Double = entity.posZ

        var lastTickPosX: Double = entity.lastTickPosX
        var lastTickPosY: Double = entity.lastTickPosY
        var lastTickPosZ: Double = entity.lastTickPosZ

        var renderYawOffset: Float = entity.renderYawOffset
        var rotationYawHead: Float = entity.rotationYawHead
        var rotationPitch: Float = if (entity.ticksElytraFlying > 4) {
            Math.toDegrees(-(Math.PI.toFloat() / 4f).toDouble()).toFloat()
        } else {
            entity.rotationPitch
        }

        var limbSwing: Float = entity.limbSwing
        var limbSwingAmount: Float = entity.limbSwingAmount

        var sneaking: Boolean = entity.isSneaking
        var sleeping: Boolean = entity.isPlayerSleeping
        var sprinting: Boolean = entity.isSprinting
        var invisible: Boolean = entity.isInvisible
        var elytraFlying: Boolean = entity.ticksElytraFlying > 4

        var height: Float = entity.height

        var lastTick: Int = entity.ticksExisted
    }
}

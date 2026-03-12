package io.github.orryxmod.core

import net.minecraft.entity.player.EntityPlayer
import java.lang.ref.WeakReference

object EntityTrackerRegistry {

    private val trackerEntries by lazy { mutableListOf<Entry>() }

    fun tick() {
        trackerEntries.removeIf {
            !it.update()
        }
    }

    fun getOrCreateEntry(living: EntityPlayer, maxTrack: Int): Entry {
        return trackerEntries.firstOrNull { it.entityToTrack === living } ?: Entry(living, maxTrack).also { trackerEntries.add(it) }
    }

    class Entry(val entityToTrack: EntityPlayer, private var maxTrack: Int = 20) {

        var trackedInfo = mutableListOf<EntityInfo>()

        fun update(): Boolean {
            if (entityToTrack.isDead) return false

            val info = EntityInfo(entityToTrack)

            trackedInfo.add(info)

            while (trackedInfo.size > maxTrack) {
                trackedInfo.removeFirst()
            }

            return true
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
        var rotationPitch: Float = if (entity.ticksElytraFlying > 4) Math.toDegrees(-(Math.PI.toFloat() / 4f).toDouble()).toFloat() else entity.rotationPitch

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
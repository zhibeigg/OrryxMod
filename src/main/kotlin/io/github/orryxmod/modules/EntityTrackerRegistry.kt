package io.github.orryxmod.modules

import net.minecraft.entity.player.EntityPlayer

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

    class EntityInfo(val tracked: EntityPlayer) {

        var posX: Double = tracked.posX
        var posY: Double = tracked.posY
        var posZ: Double = tracked.posZ

        var lastTickPosX: Double = tracked.lastTickPosX
        var lastTickPosY: Double = tracked.lastTickPosY
        var lastTickPosZ: Double = tracked.lastTickPosZ

        var renderYawOffset: Float = tracked.renderYawOffset
        var rotationYawHead: Float = tracked.rotationYawHead
        var rotationPitch: Float = if (tracked.ticksElytraFlying > 4) Math.toDegrees(-(Math.PI.toFloat() / 4f).toDouble()).toFloat() else tracked.rotationPitch

        var limbSwing: Float = tracked.limbSwing
        var limbSwingAmount: Float = tracked.limbSwingAmount

        var sneaking: Boolean = tracked.isSneaking
        var sleeping: Boolean = tracked.isPlayerSleeping
        var sprinting: Boolean = tracked.isSprinting
        var invisible: Boolean = tracked.isInvisible
        var elytraFlying: Boolean = tracked.ticksElytraFlying > 4

        var height: Float = tracked.height

        var lastTick: Int = tracked.ticksExisted
    }
}
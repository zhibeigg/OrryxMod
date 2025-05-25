package io.github.orryxmod.util

import baritone.api.BaritoneAPI
import io.github.orryxmod.OrryxMod.Companion.scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Color

object BaritoneUtils {
    var initialized = false

    private val provider
        get() = if (initialized) BaritoneAPI.getProvider() else null

    val settings
        get() = if (initialized) BaritoneAPI.getSettings() else null

    val primary
        get() = provider?.primaryBaritone

    val isPathing
        get() = primary?.pathingBehavior?.isPathing ?: false

    val isActive
        get() = primary?.customGoalProcess?.isActive ?: false || primary?.pathingControlManager?.mostRecentInControl()?.orElse(null)?.isActive ?: false

    fun cancelEverything() = primary?.pathingBehavior?.cancelEverything()

    fun initialize() {
        initialized = true
        println("initialized BaritoneUtils")

        scope.launch {
            settings?.apply {
                println("settings load BaritoneUtils")
                chatControl.value = true
                chatControlAnyway.value = true
                prefixControl.value = false
                renderPathAsLine.value = false
                renderGoalAnimated.value = true
                /* Basic */
                allowBreak.value = false
                allowSprint.value = true
                allowPlace.value = false
                allowInventory.value = false
                allowDownward.value = false
                allowParkour.value = false
                allowParkourPlace.value = false
                sprintAscends.value = true
                overshootTraverse.value = true
                doBedWaypoints.value = false
                doDeathWaypoints.value = false
                colorCurrentPath.value = Color(44, 255, 46)
                colorGoalBox.value = Color(0, 255, 146)
                colorNextPath.value = Color(0, 180, 255)
                /* Visual */
                freeLook.value = false
                renderGoal.value = true
                censorCoordinates.value = true
                censorRanCommands.value = true
                /* Fall */
                maxFallHeightNoWater.value = 3
                allowWaterBucketFall.value = false
                maxFallHeightBucket.value = 23
                /* Build */
                buildInLayers.value = true
                layerOrder.value = false
                layerHeight.value = 1
                startAtLayer.value = 1
                skipFailedLayers.value = true
                buildOnlySelection.value = true
                buildIgnoreExisting.value = true
                buildIgnoreDirection.value = true
                mapArtMode.value = false
                schematicOrientationX.value = false
                schematicOrientationY.value = false
                schematicOrientationZ.value = false
                okIfWater.value = true
                incorrectSize.value = 0
                /* Advanced */
                preferSilkTouch.value = false
                backfill.value = false
                chunkCaching.value = true
                blockReachDistance.value = 1.0f
                enterPortal.value = false
                blockPlacementPenalty.value = 20.toDouble()
                jumpPenalty.value = 0.toDouble()
                assumeWalkOnWater.value = false
                assumeStep.value = false
                assumeExternalAutoTool.value = false
                autoTool.value = false
                assumeSafeWalk.value = false
                allowJumpAt256.value = true
                allowDiagonalDescend.value = true
                allowDiagonalAscend.value = true
                failureTimeoutMS.value = 3 * 1000L
                avoidance.value = true
                mobAvoidanceRadius.value = 1
                mobAvoidanceCoefficient.value = 1.toDouble()
                mobSpawnerAvoidanceRadius.value = 0
                mobSpawnerAvoidanceCoefficient.value = 1.toDouble()
                // 到达地点后断开服务器连接
                disconnectOnArrival.value = false
            }
        }
    }
}
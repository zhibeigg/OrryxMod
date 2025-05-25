package io.github.orryxmod.modules

import baritone.api.pathing.goals.GoalNear
import io.github.orryxmod.util.BaritoneUtils
import net.minecraft.util.math.BlockPos

object PlayerNavigation {

    fun start(x: Int, y: Int, z: Int, range: Int) {
        BaritoneUtils.cancelEverything()
        BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalNear(BlockPos(x, y, z), range))
    }

    fun stop() {
        BaritoneUtils.cancelEverything()
    }
}
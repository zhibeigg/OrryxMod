package io.github.orryxmod.modules

import baritone.api.pathing.goals.GoalNear
import io.github.orryxmod.api.Module
import io.github.orryxmod.util.BaritoneUtils
import io.github.orryxmod.util.MC
import net.minecraft.util.math.BlockPos

object PlayerNavigation: Module("Navigation", "导航") {

    fun start(x: Int, y: Int, z: Int, range: Int) {
        BaritoneUtils.cancelEverything()
        BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalNear(BlockPos(x, y, z), range))
    }

    fun stop() {
        BaritoneUtils.cancelEverything()
    }

    override fun test() {
        start((MC.player.posX + 10).toInt(), MC.player.posY.toInt(), (MC.player.posZ + 10).toInt(), 1)
    }
}
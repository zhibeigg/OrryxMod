package io.github.orryxmod.modules

import io.github.orryxmod.api.Module
import io.github.orryxmod.util.MC

object MouseCursor : Module("MouseCursor", description = "鼠标呼出") {

    fun show() {
        MC.mouseHelper.ungrabMouseCursor()
    }

    fun hide() {
        MC.mouseHelper.grabMouseCursor()
    }

    override fun test() {
        show()
    }
}
package io.github.orryxmod.util

/**
 * MC stub - provides renderManager for RenderContext.create()
 */
object MC {
    val renderManager = RenderManagerStub()
}

class RenderManagerStub {
    var viewerPosX: Double = 0.0
    var viewerPosY: Double = 0.0
    var viewerPosZ: Double = 0.0
}

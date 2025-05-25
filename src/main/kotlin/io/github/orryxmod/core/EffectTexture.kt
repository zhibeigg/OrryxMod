package io.github.orryxmod.core

import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.resources.IResourceManager
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class EffectTexture : AbstractTexture {

    private var image: BufferedImage

    constructor(file: File) {
        this.image = TextureUtil.readBufferedImage(FileInputStream(file))
    }

    constructor(image: BufferedImage) {
        this.image = image
    }

    @Throws(IOException::class)
    override fun loadTexture(resourceManager: IResourceManager) {
        this.deleteGlTexture()
        TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), image, false, false)
    }
}
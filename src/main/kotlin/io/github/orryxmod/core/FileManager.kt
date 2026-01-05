package io.github.orryxmod.core

import io.github.orryxmod.OrryxMod.Companion.logger
import io.github.orryxmod.util.files
import io.github.orryxmod.util.newFile
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.ITextureObject
import net.minecraft.client.renderer.texture.TextureUtil
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException

object FileManager {

    val pictures = mutableMapOf<String, Int>()

    fun bindTexture(textureId: Int) {
        GlStateManager.bindTexture(textureId)
    }

    fun loadTextures() {
        files("Orryx", "select-default.png", "arrow-default.png", "flicker.png", "ghost.png") {
            try {
                pictures[it.nameWithoutExtension] = loadTexture(it)
                logger.info("picture ${it.name} loaded")
            } catch (e: Exception) {
                logger.warn("Failed to load texture: ${it.name}", e)
            }
        }
    }

    @Throws(IOException::class)
    fun loadTexture(file: File): Int {
        val texture: ITextureObject = EffectTexture(file)
        texture.loadTexture(Minecraft.getMinecraft().resourceManager)
        return texture.glTextureId
    }

    @Throws(IOException::class)
    fun loadTexture(image: BufferedImage): Int {
        val texture: ITextureObject = EffectTexture(image)
        texture.loadTexture(Minecraft.getMinecraft().resourceManager)
        return texture.glTextureId
    }

    fun deleteTexture(textureId: Int) {
        TextureUtil.deleteTexture(textureId)
    }

    fun releaseResourceFile(source: String, target: String = source, replace: Boolean): File {
        val file = File("resourcepacks/Orryx", target)
        if (file.exists() && !replace) {
            return file
        }
        val bytes = javaClass.classLoader.getResourceAsStream(source)?.use { it.readBytes() }
            ?: error("resource not found: $source")
        newFile(file).writeBytes(bytes)
        return file
    }
}
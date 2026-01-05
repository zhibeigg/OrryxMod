package io.github.orryxmod.shared.texture

import io.github.orryxmod.OrryxMod
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

/**
 * 纹理管理器 - 管理 mod 使用的纹理资源
 */
object TextureManager {

    private val loadedTextures = mutableMapOf<String, ResourceLocation>()

    /**
     * 获取纹理资源位置
     * @param path 相对于 assets/orryxmod/textures 的路径
     */
    fun getTexture(path: String): ResourceLocation {
        return loadedTextures.getOrPut(path) {
            ResourceLocation(OrryxMod.MOD_ID, "textures/$path")
        }
    }

    /**
     * 加载并注册动态纹理
     * @param name 纹理名称
     * @param image 图像数据
     */
    fun registerDynamicTexture(name: String, image: BufferedImage): ResourceLocation {
        val location = ResourceLocation(OrryxMod.MOD_ID, "dynamic/$name")
        val texture = DynamicImageTexture(image)

        net.minecraft.client.Minecraft.getMinecraft().textureManager
            .loadTexture(location, texture)

        loadedTextures[name] = location
        return location
    }

    /**
     * 预加载纹理
     */
    fun preload(vararg paths: String) {
        paths.forEach { path ->
            getTexture(path)
        }
    }

    /**
     * 清除缓存
     */
    fun clear() {
        loadedTextures.clear()
    }

    /**
     * 动态图像纹理
     */
    private class DynamicImageTexture(private val image: BufferedImage) : AbstractTexture() {

        override fun loadTexture(resourceManager: IResourceManager) {
            deleteGlTexture()

            try {
                TextureUtil.uploadTextureImageAllocate(
                    getGlTextureId(),
                    image,
                    false,
                    false
                )
            } catch (ex: IOException) {
                OrryxMod.logger.error("Failed to load dynamic texture", ex)
            }
        }
    }
}

/**
 * 常用纹理路径常量
 */
object Textures {
    const val FRACTURE_BLOCK = "blocks/fracture.png"
    const val AIM_INDICATOR = "gui/aim_indicator.png"
    const val PARTICLE_DUST = "particles/dust.png"
}

package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.util.MC
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.joml.Math.clamp
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Quaternion
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class RenderFractureBlock: TileEntitySpecialRenderer<FractureBlockTileEntity>() {

    override fun render(
        blockEntity: FractureBlockTileEntity,
        x: Double,
        y: Double,
        z: Double,
        partialTicks: Float,
        destroyStage: Int,
        alpha: Float,
    ) {
        val turnBackTime = 5.0f
        val lerpAmount = clamp(
            1 - (blockEntity.maxLifeTime.toFloat() - blockEntity.lifeTime - partialTicks) / turnBackTime,
            0.0f,
            1.0f
        )

        val translate: Vector3f =
            if (blockEntity.lifeTime + turnBackTime < blockEntity.maxLifeTime) {
                blockEntity.translate
            } else {
                blockEntity.translate.lerp(Vector3f(), lerpAmount, Vector3f())
            }

        val rotate: Quaternionf =
            if (blockEntity.lifeTime + turnBackTime < blockEntity.maxLifeTime) {
                blockEntity.rotation
            } else {
                blockEntity.rotation.nlerp(Quaternionf(), lerpAmount, Quaternionf())
            }

        val bounceMaxHeight: Double = blockEntity.bouncing
        val time = max(bounceMaxHeight * 8.0, 8.0)
        val extender = 1 / (time * 0.5).pow(2.0)
        val moveGraph = sqrt(bounceMaxHeight / extender)
        val bouncingAnimation = max(-extender * (blockEntity.lifeTime + partialTicks - moveGraph).pow(2.0) + bounceMaxHeight, 0.0)

        // 开始渲染方块
        val blockrendererdispatcher = Minecraft.getMinecraft().blockRendererDispatcher

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        buffer.setTranslation(-blockEntity.pos.x.toDouble(), -blockEntity.pos.y.toDouble(), -blockEntity.pos.z.toDouble())

		MC.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK)

        GlStateManager.pushMatrix()

		GlStateManager.enableCull()
		GlStateManager.enableColorMaterial()
		GL11.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT_AND_DIFFUSE)

        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha)

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5) // 移动到方块中心
        GlStateManager.rotate(Quaternion(rotate.x, rotate.y, rotate.z, rotate.w))
        GlStateManager.translate(translate.x.toDouble(), translate.y + bouncingAnimation, translate.z.toDouble())
        GlStateManager.translate(-0.5, -0.5, -0.5)

        val state = blockEntity.originalBlockState

        blockrendererdispatcher.blockModelRenderer.renderModelSmooth(
            world,
            blockrendererdispatcher.getModelForState(state),
            state,
            blockEntity.pos,
            buffer,
            false,
            blockEntity.seed
        )
        tessellator.draw()

        buffer.setTranslation(0.0, 0.0, 0.0)
        GlStateManager.popMatrix()
    }
}
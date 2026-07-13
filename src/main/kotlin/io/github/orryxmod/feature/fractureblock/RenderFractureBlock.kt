package io.github.orryxmod.feature.fractureblock

import io.github.orryxmod.core.render.RenderUtils
import io.github.orryxmod.util.MC
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import org.joml.Math.clamp
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Quaternion
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class RenderFractureBlock : TileEntitySpecialRenderer<FractureBlockTileEntity>() {

    companion object {
        private const val FULL_BRIGHT = (15 shl 20) or (15 shl 4)
    }

    override fun render(
        blockEntity: FractureBlockTileEntity,
        x: Double,
        y: Double,
        z: Double,
        partialTicks: Float,
        destroyStage: Int,
        alpha: Float,
    ) {
        val node = blockEntity.fractureNode ?: return
        val state = node.state

        val turnBackTime = 5.0f
        val lerpAmount = clamp(
            1 - (node.maxLifeTime.toFloat() - node.lifeTime - partialTicks) / turnBackTime,
            0.0f,
            1.0f
        )

        val translate: Vector3f = if (node.lifeTime + turnBackTime < node.maxLifeTime) {
            node.translate
        } else {
            node.translate.lerp(Vector3f(), lerpAmount, Vector3f())
        }

        val rotate: Quaternionf = if (node.lifeTime + turnBackTime < node.maxLifeTime) {
            node.rotation
        } else {
            node.rotation.nlerp(Quaternionf(), lerpAmount, Quaternionf())
        }

        val bounceMaxHeight = node.bouncing
        val time = max(bounceMaxHeight * 8.0, 8.0)
        val extender = 1 / (time * 0.5).pow(2.0)
        val moveGraph = sqrt(bounceMaxHeight / extender)
        val bouncingAnimation = max(
            -extender * (node.lifeTime + partialTicks - moveGraph).pow(2.0) + bounceMaxHeight,
            0.0
        )

        val combinedLight = if (lerpAmount > 0.5f) {
            FULL_BRIGHT
        } else {
            val actualY = (blockEntity.pos.y + translate.y + bouncingAnimation).toInt().coerceIn(0, 255)
            val lightPos = BlockPos(blockEntity.pos.x, actualY, blockEntity.pos.z)
            world.getCombinedLight(lightPos, 0)
        }

        val dispatcher = Minecraft.getMinecraft().blockRendererDispatcher
        val model = dispatcher.getModelForState(state)
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        RenderUtils.withGlState(lighting = false) {
            MC.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.disableCull()
            GlStateManager.color(1.0f, 1.0f, 1.0f, alpha)
            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)
            GlStateManager.rotate(Quaternion(rotate.x, rotate.y, rotate.z, rotate.w))
            GlStateManager.translate(translate.x.toDouble(), translate.y + bouncingAnimation, translate.z.toDouble())
            GlStateManager.translate(-0.5, -0.5, -0.5)

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK)
            try {
                for (facing in EnumFacing.VALUES) {
                    for (quad in model.getQuads(state, facing, blockEntity.seed)) {
                        renderQuadWithLight(buffer, quad.vertexData, combinedLight)
                    }
                }
                for (quad in model.getQuads(state, null, blockEntity.seed)) {
                    renderQuadWithLight(buffer, quad.vertexData, combinedLight)
                }
            } finally {
                tessellator.draw()
            }
        }
    }

    private fun renderQuadWithLight(buffer: BufferBuilder, vertexData: IntArray, light: Int) {
        buffer.addVertexData(vertexData)
        buffer.putBrightness4(light, light, light, light)
    }
}

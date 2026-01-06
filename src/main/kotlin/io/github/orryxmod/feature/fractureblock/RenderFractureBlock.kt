package io.github.orryxmod.feature.fractureblock

import io.github.orryxmod.util.MC
import net.minecraft.client.Minecraft
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

class RenderFractureBlock: TileEntitySpecialRenderer<FractureBlockTileEntity>() {

    companion object {
        // 最大光照值 (天空光 15 + 方块光 15)
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

        // 计算光照值
        val combinedLight: Int = if (lerpAmount > 0.5f) {
            // 动画结束阶段：使用全亮光照，避免恢复时闪黑
            FULL_BRIGHT
        } else {
            // 正常阶段：计算方块实际渲染位置的光照
            val actualY = (blockEntity.pos.y + translate.y + bouncingAnimation).toInt().coerceIn(0, 255)
            val lightPos = BlockPos(blockEntity.pos.x, actualY, blockEntity.pos.z)
            world.getCombinedLight(lightPos, 0)
        }

        // 开始渲染方块
        val blockrendererdispatcher = Minecraft.getMinecraft().blockRendererDispatcher

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        MC.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)

        GlStateManager.pushMatrix()

        // 禁用 OpenGL 光照，使用光照贴图
        GlStateManager.disableLighting()
        // 禁用背面剔除，确保旋转后所有面可见
        GlStateManager.disableCull()

        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha)

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5) // 移动到方块中心
        GlStateManager.rotate(Quaternion(rotate.x, rotate.y, rotate.z, rotate.w))
        GlStateManager.translate(translate.x.toDouble(), translate.y + bouncingAnimation, translate.z.toDouble())
        GlStateManager.translate(-0.5, -0.5, -0.5)

        val state = blockEntity.originalBlockState
        val model = blockrendererdispatcher.getModelForState(state)

        // 自定义渲染：为所有面使用统一光照
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK)

        // 渲染所有方向的面
        for (facing in EnumFacing.VALUES) {
            val quads = model.getQuads(state, facing, blockEntity.seed)
            for (quad in quads) {
                renderQuadWithLight(buffer, quad.vertexData, combinedLight)
            }
        }

        // 渲染无方向的面 (null facing)
        val generalQuads = model.getQuads(state, null, blockEntity.seed)
        for (quad in generalQuads) {
            renderQuadWithLight(buffer, quad.vertexData, combinedLight)
        }

        tessellator.draw()

        GlStateManager.enableCull()
        GlStateManager.enableLighting()
        GlStateManager.popMatrix()
    }

    /**
     * 渲染 quad 并设置统一光照值
     * BLOCK 格式每顶点: position(3f) + color(4b) + uv(2f) + lightmap(2s) = 7 ints
     */
    private fun renderQuadWithLight(buffer: net.minecraft.client.renderer.BufferBuilder, vertexData: IntArray, light: Int) {
        val intsPerVertex = 7  // DefaultVertexFormats.BLOCK = 28 bytes = 7 ints
        val lightmapOffset = 6  // lightmap 在每个顶点的第 7 个 int (index 6)

        // 复制顶点数据并修改光照值
        val modifiedData = vertexData.copyOf()
        for (v in 0 until 4) {
            modifiedData[v * intsPerVertex + lightmapOffset] = light
        }

        buffer.addVertexData(modifiedData)
    }
}
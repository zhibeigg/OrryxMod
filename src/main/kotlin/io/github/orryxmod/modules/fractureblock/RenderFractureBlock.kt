package io.github.orryxmod.modules.fractureblock

import io.github.orryxmod.OrryxMod
import io.github.orryxmod.util.MC
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.math.BlockPos
import org.joml.Math.clamp
import org.joml.Quaternionf
import org.joml.Vector3f
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
        super.render(blockEntity, x, y, z, partialTicks, destroyStage, alpha)
        OrryxMod.logger.info("Render Fracture block tile entity")
        val turnBackTime = 5.0f
        // 插值计算
        val lerpAmount: Float = clamp(
            partialTicks * (1.0f / turnBackTime) + (turnBackTime - (blockEntity.maxLifeTime - blockEntity.lifeTime)) * (1.0f / turnBackTime),
            0.0f,
            1.0f
        )

        val translate: Vector3f =
            if (blockEntity.maxLifeTime > blockEntity.lifeTime + turnBackTime) blockEntity.translate else blockEntity.translate.lerp(Vector3f(), lerpAmount)

        val rotate: Quaternionf =
            if (blockEntity.maxLifeTime > blockEntity.lifeTime + turnBackTime) blockEntity.rotation else blockEntity.rotation.nlerp(Quaternionf(), lerpAmount)

        val bounceMaxHeight: Double = blockEntity.bouncing
        val time = max(bounceMaxHeight * 8.0, 8.0)
        val extender = 1 / (time * 0.5).pow(2.0)
        val moveGraph = sqrt(bounceMaxHeight / extender)
        val bouncingAnimation = max(-extender * (blockEntity.lifeTime + partialTicks - moveGraph).pow(2.0) + bounceMaxHeight, 0.0)

        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5, 0.5, 0.5)
        GlStateManager.rotate(Quaternion(rotate.x, rotate.y, rotate.z, rotate.w))
        GlStateManager.translate(translate.x.toDouble(), translate.y + bouncingAnimation, translate.z.toDouble())
        GlStateManager.translate(-0.5, -0.5, -0.5)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        // 开始渲染方块
        buffer.begin(7, DefaultVertexFormats.BLOCK)
        val blockrendererdispatcher = Minecraft.getMinecraft().blockRendererDispatcher
        blockrendererdispatcher.blockModelRenderer.renderModel(
            MC.world,
            blockrendererdispatcher.getModelForState(blockEntity.originalBlockState),
            blockEntity.originalBlockState,
            BlockPos(x, y, z),
            buffer,
            false
        )
        tessellator.draw()

        GlStateManager.popMatrix()
    }
}
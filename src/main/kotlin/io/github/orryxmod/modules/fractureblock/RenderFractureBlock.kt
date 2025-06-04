package io.github.orryxmod.modules.fractureblock

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
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
        val turnBackTime = 5.0f
        val lerpAmount = clamp(
            (blockEntity.lifeTime + partialTicks) / blockEntity.maxLifeTime.toFloat(),
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

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5) // 移动到方块中心
        GlStateManager.rotate(Quaternion(rotate.x, rotate.y, rotate.z, rotate.w))
        GlStateManager.translate(translate.x.toDouble(), translate.y + bouncingAnimation, translate.z.toDouble())
        GlStateManager.translate(-0.5,  -0.5, -0.5)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        // 开始渲染方块
        buffer.begin(7, DefaultVertexFormats.BLOCK)
        val blockrendererdispatcher = Minecraft.getMinecraft().blockRendererDispatcher

        val state = blockEntity.originalBlockState
        val rand = MathHelper.getPositionRandom(BlockPos(x, y, z))

        blockrendererdispatcher.blockModelRenderer.renderModelSmooth(
            world,
            blockrendererdispatcher.getModelForState(state),
            state,
            BlockPos.ORIGIN,
            buffer,
            false,
            rand
        )
        tessellator.draw()

        GlStateManager.popMatrix()
    }
}
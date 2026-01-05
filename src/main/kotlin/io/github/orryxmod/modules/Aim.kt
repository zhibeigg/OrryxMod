package io.github.orryxmod.modules

import io.github.orryxmod.api.Module
import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.PacketHandler
import io.github.orryxmod.core.PacketHandler.sendDataPacket
import io.github.orryxmod.util.MC
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockModelRenderer
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.crash.CrashReport
import net.minecraft.crash.CrashReportCategory
import net.minecraft.util.ReportedException
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import org.lwjgl.opengl.GL11

object Aim : Module("Aim", description = "技能辅助瞄准") {

    internal var max = 10.0
    internal var scale = 2.0
    internal var skill: String? = null
    internal var enable = false
    internal var module = "default"

    private val selectTextureId: Int?
        get() = FileManager.pictures["select-$module"]

    private val arrowTextureId: Int?
        get() = FileManager.pictures["arrow-$module"]

    data class Location(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {
        override fun toString() = "$x, $y, $z, yaw: $yaw, pitch: $pitch"
    }

    override fun test() {
        enable = !enable
    }

    @SubscribeEvent
    fun clientLogoutEvent(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        if (!event.isCanceled) {
            reset()
        }
    }

    /**
     * 获取玩家瞄准的位置
     * 优先使用光线追踪，如果没有命中则使用逐步搜索算法
     */
    private fun getLocation(tick: Float): Location {
        val player = MC.player ?: return Location(0.0, 0.0, 0.0, 0f, 0f)
        val world = MC.world ?: return Location(0.0, 0.0, 0.0, 0f, 0f)

        val playerEyes = player.getPositionEyes(tick)
        val lookVec = player.lookVec
        val playerLook = playerEyes.add(lookVec.scale(max))

        // 优先使用光线追踪
        world.rayTraceBlocks(playerEyes, playerLook, false, true, false)?.hitVec?.let {
            return Location(it.x, it.y, it.z, player.pitchYaw.y, player.pitchYaw.x)
        }

        // 逐步搜索地面位置
        return findGroundLocation(player, lookVec)
    }

    /**
     * 沿着视线方向逐步搜索地面位置
     */
    private fun findGroundLocation(player: net.minecraft.entity.player.EntityPlayer, lookVec: Vec3d): Location {
        val eyePos = Vec3d(player.posX, player.posY + player.eyeHeight, player.posZ)
        var targetVec = lookVec
        var distance = 0.0
        var effectiveMax = max

        // 向前搜索碰撞点
        while (distance < max) {
            distance++
            targetVec = lookVec.scale(distance).add(eyePos)

            if (!player.world.isAirBlock(BlockPos(targetVec))) {
                // 找到碰撞，精细化搜索
                val refinedResult = refineCollisionPoint(player, lookVec, eyePos, distance)
                if (refinedResult != null) {
                    return refinedResult
                }
                effectiveMax = player.positionVector.subtract(targetVec.x, player.posY, targetVec.z).length() - 0.1
                distance = effectiveMax
                break
            }
        }

        // 检查是否在有效范围内找到了位置
        return if (distance < effectiveMax) {
            val blockPos = BlockPos(targetVec).up()
            Location(targetVec.x, blockPos.y.toDouble(), targetVec.z, player.pitchYaw.y, player.pitchYaw.x)
        } else {
            // 向下搜索地面
            findGroundBelow(player, lookVec, eyePos, distance)
        }
    }

    /**
     * 精细化搜索碰撞点
     */
    private fun refineCollisionPoint(
        player: net.minecraft.entity.player.EntityPlayer,
        lookVec: Vec3d,
        eyePos: Vec3d,
        initialDistance: Double
    ): Location? {
        var distance = initialDistance
        var refinement = 0.0

        while (refinement < 1.0) {
            refinement += 0.1
            distance -= 0.1
            val testVec = lookVec.scale(distance).add(eyePos)

            if (player.world.isAirBlock(BlockPos(testVec))) {
                val nextVec = lookVec.scale(distance + 0.1).add(eyePos)
                if (!player.world.isAirBlock(BlockPos(nextVec).up())) {
                    return null // 继续主搜索
                }
                break
            }
        }
        return null
    }

    /**
     * 向下搜索地面位置
     */
    private fun findGroundBelow(
        player: net.minecraft.entity.player.EntityPlayer,
        lookVec: Vec3d,
        eyePos: Vec3d,
        forwardDistance: Double
    ): Location {
        var verticalOffset = 0.0
        var targetVec = lookVec.scale(forwardDistance).add(eyePos)
        val halfMax = max / 2

        while (verticalOffset > -halfMax) {
            verticalOffset--
            targetVec = lookVec.scale(forwardDistance).add(
                player.posX,
                player.posY + player.eyeHeight + verticalOffset,
                player.posZ
            )

            if (!player.world.isAirBlock(BlockPos(targetVec))) {
                // 找到地面，精细化向上搜索
                var refinement = 0.0
                while (refinement < 1.0) {
                    refinement += 0.1
                    verticalOffset += 0.1
                    targetVec = lookVec.scale(forwardDistance).add(
                        player.posX,
                        player.posY + player.eyeHeight + verticalOffset,
                        player.posZ
                    )
                    if (player.world.isAirBlock(BlockPos(targetVec))) {
                        targetVec = lookVec.scale(forwardDistance).add(
                            player.posX,
                            player.posY + player.eyeHeight + verticalOffset - 0.1,
                            player.posZ
                        )
                        break
                    }
                }
                break
            }
        }

        val blockPos = BlockPos(targetVec).up()
        return Location(targetVec.x, blockPos.y.toDouble(), targetVec.z, player.pitchYaw.y, player.pitchYaw.x)
    }

    internal fun confirm() {
        val currentSkill = skill ?: return

        val location = getLocation(MC.renderPartialTicks)
        sendDataPacket(PacketHandler.PacketType.AimResponse) {
            writeUTF(currentSkill)
            writeDouble(location.x)
            writeDouble(location.y)
            writeDouble(location.z)
            writeFloat(location.yaw)
            writeFloat(location.pitch)
        }
        reset()
    }

    internal fun cancel() {
        if (skill != null) {
            reset()
        }
    }

    private fun reset() {
        enable = false
        module = "default"
        max = 10.0
        scale = 2.0
        skill = null
    }

    // 动画状态
    private var animationOffset = 0.0
    private var animationDirection = true

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (!enable) return

        val selectId = selectTextureId ?: return
        val arrowId = arrowTextureId ?: return
        val player = MC.player ?: return

        val loc = getLocation(event.partialTicks)
        val targetVec = Vec3d(loc.x, loc.y, loc.z)
        val relativeVec = targetVec.subtract(player.positionVector.add(0.0, player.eyeHeight.toDouble(), 0.0))

        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)

        // 渲染底部选择圈
        FileManager.bindTexture(selectId)
        GlStateManager.translate(relativeVec.x, relativeVec.y, relativeVec.z)
        GlStateManager.rotate(-player.rotationYaw, 0.0f, 1.0f, 0.0f)
        drawTexturedRect(0.0, 0.0, 0.0, scale, scale)

        // 渲染反向底部
        FileManager.bindTexture(selectId)
        GlStateManager.rotate(180f, 1.0f, 0.0f, 0.0f)
        drawTexturedRect(0.0, -3.4, 0.0, scale, scale)

        // 更新动画偏移
        updateAnimation()

        // 渲染浮动箭头
        FileManager.bindTexture(arrowId)
        GlStateManager.rotate(-180f, 1.0f, 0.0f, 0.0f)
        drawFloatingArrow(0.0, animationOffset / 2000.0, 0.0, scale / 4, scale / 4)

        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun updateAnimation() {
        if (animationDirection) {
            if (animationOffset < 500) animationOffset++ else {
                animationDirection = false
                animationOffset--
            }
        } else {
            if (animationOffset > 0) animationOffset-- else {
                animationDirection = true
                animationOffset--
            }
        }
    }

    private fun drawTexturedRect(x: Double, y: Double, z: Double, width: Double, height: Double) {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        val halfWidth = width / 2
        val halfHeight = height / 2
        val yOffset = y + 1.7

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - halfWidth, yOffset, z + halfHeight).tex(0.0, 1.0).endVertex()
        buffer.pos(x + halfWidth, yOffset, z + halfHeight).tex(1.0, 1.0).endVertex()
        buffer.pos(x + halfWidth, yOffset, z - halfHeight).tex(1.0, 0.0).endVertex()
        buffer.pos(x - halfWidth, yOffset, z - halfHeight).tex(0.0, 0.0).endVertex()
        tessellator.draw()
    }

    private fun drawFloatingArrow(x: Double, y: Double, z: Double, width: Double, height: Double) {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        val halfWidth = width / 2
        val yBase = y + 3.5

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(x - halfWidth, yBase + height, z).tex(0.0, 0.0).endVertex()
        buffer.pos(x + halfWidth, yBase + height, z).tex(1.0, 0.0).endVertex()
        buffer.pos(x + halfWidth, yBase, z).tex(1.0, 1.0).endVertex()
        buffer.pos(x - halfWidth, yBase, z).tex(0.0, 1.0).endVertex()
        tessellator.draw()
    }

    data class AimPacket(
        val skill: String,
        val picture: String = "default",
        val enable: Boolean,
        val scale: Double,
        val max: Double,
    )

    fun renderBlock(event: RenderWorldLastEvent) {
        val player = MC.player ?: return
        val world = player.world

        val groundPos = BlockPos(player.posX, player.posY - 0.2, player.posZ)
        val groundState = world.getBlockState(groundPos)

        if (groundState.block.isAir(groundState, world, groundPos)) return

        val partialTicks = event.partialTicks
        val x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + 2.5
        val z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks

        renderFloatingBlock(groundState, x - player.posX, y - player.posY, z - player.posZ, 1f)
    }

    private fun renderFloatingBlock(state: IBlockState, x: Double, y: Double, z: Double, scale: Float) {
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableLighting()

        GlStateManager.translate(x, y, z)
        GlStateManager.rotate(((System.currentTimeMillis() / 20) % 360).toFloat(), 0f, 1f, 0f)
        GlStateManager.translate(-0.5, -0.5, -0.5)
        GlStateManager.scale(scale, scale, scale)
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.7f)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        val blockDispatcher = Minecraft.getMinecraft().blockRendererDispatcher

        buffer.begin(7, DefaultVertexFormats.BLOCK)

        val model = blockDispatcher.getModelForState(state)
        val pos = BlockPos(x, y, z)
        val rand = MathHelper.getPositionRandom(pos)
        val useAO = Minecraft.isAmbientOcclusionEnabled() &&
                state.getLightValue(MC.world, pos) == 0 &&
                model.isAmbientOcclusion(state)

        blockDispatcher.blockModelRenderer.renderBlock(
            MC.world, model, state, BlockPos.ORIGIN, buffer, false, rand, useAO
        )
        tessellator.draw()

        GlStateManager.enableLighting()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun BlockModelRenderer.renderBlock(
        world: IBlockAccess,
        model: IBakedModel,
        state: IBlockState,
        pos: BlockPos,
        buffer: BufferBuilder,
        checkSides: Boolean,
        rand: Long,
        useAmbientOcclusion: Boolean
    ): Boolean {
        try {
            return if (useAmbientOcclusion) {
                renderModelSmooth(world, model, state, pos, buffer, checkSides, rand)
            } else {
                renderModelFlat(world, model, state, pos, buffer, checkSides, rand)
            }
        } catch (throwable: Throwable) {
            val crashReport = CrashReport.makeCrashReport(throwable, "Tesselating block model")
            val category = crashReport.makeCategory("Block model being tesselated")
            CrashReportCategory.addBlockInfo(category, pos, state)
            category.addCrashSection("Using AO", useAmbientOcclusion)
            throw ReportedException(crashReport)
        }
    }
}

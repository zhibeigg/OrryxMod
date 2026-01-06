package io.github.orryxmod.feature.aim

import io.github.orryxmod.core.FileManager
import io.github.orryxmod.core.api.RenderableEffect
import io.github.orryxmod.core.render.RenderContext
import io.github.orryxmod.util.MC
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.lwjgl.opengl.GL11

/**
 * Aim 渲染器 - 技能辅助瞄准渲染
 * 从老模块 modules/Aim.kt 迁移
 */
object AimRenderer : RenderableEffect {

    override val id: String = "aim_renderer"
    override val renderPriority: Int = 100

    // 动画状态
    private var animationOffset = 0.0
    private var animationDirection = true

    // 始终保持活跃，渲染时检查 isAiming
    override val isActive: Boolean
        get() = true

    override fun update() {
        if (!AimState.isAiming) return
        updateAnimation()
    }

    override fun render(context: RenderContext) {
        if (!AimState.isAiming) return

        val config = AimState.currentConfig
        val module = AimState.currentModule.name.lowercase()

        val selectId = FileManager.pictures["select-$module"]
            ?: FileManager.pictures["select-default"]
            ?: return
        val arrowId = FileManager.pictures["arrow-$module"]
            ?: FileManager.pictures["arrow-default"]
            ?: return
        val player = MC.player ?: return

        val loc = getLocation(context.partialTicks, config)
        val targetVec = Vec3d(loc.x, loc.y, loc.z)
        val relativeVec = targetVec.subtract(
            player.positionVector.add(0.0, player.eyeHeight.toDouble(), 0.0)
        )

        val scale = config.scale

        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )

        // 渲染底部选择圈
        FileManager.bindTexture(selectId)
        GlStateManager.translate(relativeVec.x, relativeVec.y, relativeVec.z)
        GlStateManager.rotate(-player.rotationYaw, 0.0f, 1.0f, 0.0f)
        drawTexturedRect(0.0, 0.0, 0.0, scale, scale)

        // 渲染反向底部
        FileManager.bindTexture(selectId)
        GlStateManager.rotate(180f, 1.0f, 0.0f, 0.0f)
        drawTexturedRect(0.0, -3.4, 0.0, scale, scale)

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

    /**
     * 获取玩家瞄准的位置
     * 优先使用光线追踪，如果没有命中则使用逐步搜索算法
     */
    private fun getLocation(tick: Float, config: AimConfig): Location {
        val player = MC.player ?: return Location(0.0, 0.0, 0.0, 0f, 0f)
        val world = MC.world ?: return Location(0.0, 0.0, 0.0, 0f, 0f)
        val max = config.maxDistance

        val playerEyes = player.getPositionEyes(tick)
        val lookVec = player.lookVec
        val playerLook = playerEyes.add(lookVec.scale(max))

        // 优先使用光线追踪
        world.rayTraceBlocks(playerEyes, playerLook, false, true, false)?.hitVec?.let {
            return Location(it.x, it.y, it.z, player.pitchYaw.y, player.pitchYaw.x)
        }

        // 逐步搜索地面位置
        return findGroundLocation(player, lookVec, max)
    }

    /**
     * 沿着视线方向逐步搜索地面位置
     */
    private fun findGroundLocation(
        player: net.minecraft.entity.player.EntityPlayer,
        lookVec: Vec3d,
        max: Double
    ): Location {
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
            findGroundBelow(player, lookVec, eyePos, distance, max)
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
        forwardDistance: Double,
        max: Double
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

    data class Location(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)
}

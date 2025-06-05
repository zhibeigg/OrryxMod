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
import org.joml.Math.clamp
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Quaternion
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt


object Aim : Module("Aim", description = "技能辅助瞄准") {

    internal var max = 10.0
    internal var scale = 2.0
    internal var skill: String? = null
    internal var enable = false
    internal var module = "default"

    private val lowID: Int?
        get() = FileManager.pictures["select-${module}"]

    private val highID: Int?
        get() = FileManager.pictures["arrow-${module}"]

    class Location(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float) {

        override fun toString(): String {
            return "$x, $y, $z, yaw: $yaw, pitch: $pitch"
        }
    }

    override fun test() {
        enable = !enable
    }

    @SubscribeEvent
    fun clientLogoutEvent(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        if (!event.isCanceled) {
            enable = false
        }
    }

    private fun getLocation(tick: Float): Location {
        val playerEyes = MC.player.getPositionEyes(tick)
        val playerLook = playerEyes.add(MC.player.lookVec.scale(max)) // 光线投射的距离，可以根据需要调整
        val rayTraceResult = MC.world.rayTraceBlocks(playerEyes, playerLook, false, true, false)
        rayTraceResult?.hitVec?.let {
            return Location(it.x, it.y, it.z, MC.player.pitchYaw.y, MC.player.pitchYaw.x)
        }

        var vec3d = MC.player.lookVec
        var var0 = 0.0
        var newMax = max
        while(var0 < max) {
            var0++
            vec3d = MC.player.lookVec.scale(var0).add(MC.player.posX, MC.player.posY + MC.player.eyeHeight, MC.player.posZ)
            if (!MC.player.world.isAirBlock(BlockPos(vec3d))) {
                var var1 = 0.0
                while (var1 < 1) {
                    var1 += 0.1
                    var0 -= 0.1
                    vec3d = MC.player.lookVec.scale(var0).add(MC.player.posX, MC.player.posY + MC.player.eyeHeight, MC.player.posZ)
                    if (MC.player.world.isAirBlock(BlockPos(vec3d))) {
                        vec3d = MC.player.lookVec.scale(var0 + 0.1).add(MC.player.posX, MC.player.posY + MC.player.eyeHeight, MC.player.posZ)
                        if (!MC.player.world.isAirBlock(BlockPos(vec3d).up())) {
                            newMax = MC.player.positionVector.subtract(vec3d.x, MC.player.posY, vec3d.z).length()-0.1
                            var0 = newMax
                        }
                        break
                    }
                }
                break
            }
        }
        return if (var0 < newMax) {
            val block = BlockPos(vec3d).up()
            Location(vec3d.x, block.y.toDouble(), vec3d.z, MC.player.pitchYaw.y, MC.player.pitchYaw.x)
        } else {
            var var1 = 0.0
            while (var1 > -max/2) {
                var1 --
                vec3d = MC.player.lookVec.scale(var0).add(MC.player.posX, MC.player.posY+MC.player.eyeHeight+var1, MC.player.posZ)
                if (!MC.player.world.isAirBlock(BlockPos(vec3d))) {
                    var var2 = 0.0
                    while (var2 < 1) {
                        var2 += 0.1
                        var1 += 0.1
                        vec3d = MC.player.lookVec.scale(var0).add(MC.player.posX, MC.player.posY+MC.player.eyeHeight+var1, MC.player.posZ)
                        if (MC.player.world.isAirBlock(BlockPos(vec3d))) {
                            vec3d = MC.player.lookVec.scale(var0).add(MC.player.posX, MC.player.posY+MC.player.eyeHeight+var1-0.1, MC.player.posZ)
                            break
                        }
                    }
                    break
                }
            }
            val block = BlockPos(vec3d).up()
            Location(vec3d.x, block.y.toDouble(), vec3d.z, MC.player.pitchYaw.y, MC.player.pitchYaw.x)
        }
    }

    internal fun confirm() {
        if (skill != null) {
            val location = getLocation(MC.renderPartialTicks)
            sendDataPacket(PacketHandler.PacketType.AimResponse) {
                writeUTF(skill!!)
                writeDouble(location.x)
                writeDouble(location.y)
                writeDouble(location.z)
                writeFloat(location.yaw)
                writeFloat(location.pitch)
            }
            enable = false
            module = "default"
            max = 10.0
            scale = 2.0
            skill = null
        }
    }

    internal fun cancel() {
        if (skill != null) {
            enable = false
            module = "default"
            max = 10.0
            scale = 2.0
            skill = null
        }
    }

    private var offset = 0.0
    private var upOrDown = false

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        if (enable) {
            lowID ?: return
            highID ?: return

            val loc = getLocation(event.partialTicks)
            val vec3d = Vec3d(loc.x, loc.y, loc.z)
            val newVec = vec3d.subtract(MC.player.positionVector.add(0.0, MC.player.eyeHeight.toDouble(), 0.0))

            GlStateManager.pushMatrix()
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
            FileManager.bindTexture(lowID!!)
            //旋转平移
            GlStateManager.translate(newVec.x, newVec.y, newVec.z)
            GlStateManager.rotate(-MC.player.rotationYaw, 0.0f, 1.0f, 0.0f)
            // 绑定纹理并绘制底
            drawTexturedModalRect(0.0, 0.0, 0.0, scale, scale)
            FileManager.bindTexture(lowID!!)
            //旋转平移
            GlStateManager.rotate(180f, 1.0f, 0.0f, 0.0f)
            // 绑定纹理并绘制反向底
            drawTexturedModalRect(0.0, -3.4, 0.0, scale, scale)
            if (upOrDown) {
                if (offset < 500) {
                    offset++
                } else {
                    upOrDown = false
                    offset--
                }
            } else {
                if (offset > 0) {
                    offset--
                } else {
                    upOrDown = true
                    offset--
                }
            }
            FileManager.bindTexture(highID!!)
            GlStateManager.rotate(-180f, 1.0f, 0.0f, 0.0f)
            // 绑定纹理并绘制箭头
            drawFloatTexture(0.0, offset/2000.0, 0.0, scale/4, scale/4)

            GlStateManager.disableBlend()
            GlStateManager.popMatrix()
        }
    }

    // 这是一个帮助方法，用于绘制纹理矩形
    private fun drawTexturedModalRect(x: Double, y: Double, z: Double, width: Double, height: Double) {
        val tessellator: Tessellator = Tessellator.getInstance()
        val bufferBuilder: BufferBuilder = tessellator.buffer
        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        bufferBuilder.pos(x - width/2, y+1.7, z + height/2).tex(0.0, 1.0).endVertex()
        bufferBuilder.pos(x + width/2, y+1.7, z + height/2).tex(1.0, 1.0).endVertex()
        bufferBuilder.pos(x + width/2, y+1.7, z - height/2).tex(1.0, 0.0).endVertex()
        bufferBuilder.pos(x - width/2, y+1.7, z - height/2).tex(0.0, 0.0).endVertex()
        tessellator.draw()
    }

    private fun drawFloatTexture(x: Double, y: Double, z: Double, width: Double, height: Double) {
        val tessellator: Tessellator = Tessellator.getInstance()
        val bufferBuilder: BufferBuilder = tessellator.buffer
        bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        bufferBuilder.pos(x - width/2, y+3.5+height, z).tex(0.0, 0.0).endVertex()
        bufferBuilder.pos(x + width/2, y+3.5+height, z).tex(1.0, 0.0).endVertex()
        bufferBuilder.pos(x + width/2, y+3.5, z).tex(1.0, 1.0).endVertex()
        bufferBuilder.pos(x - width/2, y+3.5, z).tex(0.0, 1.0).endVertex()
        tessellator.draw()
    }

    class AimPacket(
        val skill: String,
        val picture: String = "default",
        val enable: Boolean,
        val scale: Double,
        val max: Double,
    ) {
        override fun toString(): String {
            return "AimPacket(skill=$skill, picture=$picture, enable=$enable, max=$max, scale=$scale)"
        }
    }

    fun renderBlock(event: RenderWorldLastEvent) {
        val player = MC.player

        // 获取玩家脚下方块
        val groundPos = BlockPos(player.posX, player.posY - 0.2, player.posZ)
        val world = player.world
        val groundState = world.getBlockState(groundPos)

        // 跳过空气方块
        if (groundState.block.isAir(groundState, world, groundPos)) return

        // 计算头顶位置（Y偏移2.5个单位）
        val x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks
        val y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks + 2.5
        val z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks

        // 渲染方块
        renderFloatingBlock(groundState, x - player.posX, y - player.posY, z - player.posZ, 1f)
    }

    private fun renderFloatingBlock(state: IBlockState, x: Double, y: Double, z: Double, scale: Float) {
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.disableLighting()

        GlStateManager.translate(x, y, z)
        GlStateManager.rotate(((System.currentTimeMillis() / 20) % 360).toFloat(), 0f, 1f, 0f) // Y轴旋转动画
        GlStateManager.translate(-0.5, -0.5, -0.5)
        GlStateManager.scale(scale, scale, scale)
        // 设置半透明效果
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.7f)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        // 开始渲染方块
        buffer.begin(7, DefaultVertexFormats.BLOCK)
        val blockrendererdispatcher = Minecraft.getMinecraft().blockRendererDispatcher

        val modelIn = blockrendererdispatcher.getModelForState(state)
        val pos = BlockPos(x, y, z)
        val rand = MathHelper.getPositionRandom(pos)
        val flag = Minecraft.isAmbientOcclusionEnabled() && state.getLightValue(MC.world, pos) == 0 && modelIn.isAmbientOcclusion(state)

        blockrendererdispatcher.blockModelRenderer.renderBlock(MC.world,
            blockrendererdispatcher.getModelForState(state),
            state,
            BlockPos.ORIGIN,
            buffer,
            false,
            rand,
            flag)
        tessellator.draw()

        // 恢复OpenGL状态
        GlStateManager.enableLighting()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    fun BlockModelRenderer.renderBlock(worldIn: IBlockAccess, modelIn: IBakedModel, stateIn: IBlockState, posIn: BlockPos, buffer: BufferBuilder, checkSides: Boolean, rand: Long, flag: Boolean): Boolean {
        try {
            return if (flag) {
                this.renderModelSmooth(
                    worldIn,
                    modelIn,
                    stateIn,
                    posIn,
                    buffer,
                    checkSides,
                    rand
                )
            } else {
                this.renderModelFlat(worldIn, modelIn, stateIn, posIn, buffer, checkSides, rand)
            }
        } catch (throwable: Throwable) {
            val crashreport = CrashReport.makeCrashReport(throwable, "Tesselating block model")
            val crashreportcategory = crashreport.makeCategory("Block model being tesselated")
            CrashReportCategory.addBlockInfo(crashreportcategory, posIn, stateIn)
            crashreportcategory.addCrashSection("Using AO", flag)
            throw ReportedException(crashreport)
        }
    }
}
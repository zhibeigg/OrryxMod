package io.github.orryxmod.feature.effect

import io.github.orryxmod.core.EntityTrackerRegistry
import io.github.orryxmod.util.MC
import net.minecraft.client.model.ModelPlayer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.RenderPlayer
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11
import java.util.UUID

/**
 * 烘焙玩家几何数据
 * 使用 OpenGL Display List 在创建时"录制"一次完整渲染，
 * 之后直接回放录制的 GL 调用，完全绕过任何动画模组的 hook
 *
 * 工作原理：
 * 1. 在 bake() 时调用 glNewList() 开始录制
 * 2. 执行一次完整的模型渲染（此时 Mo' Bends 等模组会修改动画）
 * 3. 调用 glEndList() 结束录制，GL 调用被"冻结"到 Display List 中
 * 4. 之后调用 render() 只需 glCallList()，无需再调用任何模型方法
 */
class BakedPlayerGeometry(
    private val entityUUID: UUID
) {
    /** Display List ID，-1 表示未初始化 */
    private var displayListId: Int = -1

    /** 烘焙时的快照信息 */
    private var bakedSnapshot: EntityTrackerRegistry.EntityInfo? = null

    /** 烘焙时的纹理 ID（用于渲染时重新绑定） */
    private var bakedTextureId: Int = -1

    /** 是否已成功烘焙 */
    val isBaked: Boolean
        get() = displayListId != -1

    /**
     * 烘焙玩家几何数据
     * 必须在渲染线程（GL 上下文）中调用
     *
     * @param textureId 闪影纹理 ID
     * @return 是否烘焙成功
     */
    fun bake(textureId: Int): Boolean {
        val player = MC.world?.getPlayerEntityByUUID(entityUUID) ?: return false

        // 保存快照
        bakedSnapshot = EntityTrackerRegistry.EntityInfo(player)
        bakedTextureId = textureId

        // 获取玩家渲染器
        val renderer = MC.renderManager.getEntityRenderObject<Entity>(player) as? RenderPlayer
            ?: return false

        val model = renderer.mainModel

        // 生成 Display List
        displayListId = GL11.glGenLists(1)
        if (displayListId == 0) {
            return false
        }

        // 开始录制 Display List
        GL11.glNewList(displayListId, GL11.GL_COMPILE)

        var success = false
        try {
            // 录制模型渲染调用
            // 此时 Mo' Bends 会修改动画，但这些修改会被录制到 Display List 中
            // 录制完成后，这些 GL 调用被"冻结"
            bakeModelRender(player, model, renderer)
            success = true
        } catch (e: Exception) {
            // 烘焙失败，标记 Display List 为无效
            success = false
        } finally {
            // 结束录制
            GL11.glEndList()

            // 如果烘焙失败，删除 Display List 避免泄漏
            if (!success) {
                GL11.glDeleteLists(displayListId, 1)
                displayListId = -1
            }
        }

        return success
    }

    /**
     * 执行实际的模型渲染（被录制到 Display List）
     */
    private fun bakeModelRender(player: EntityPlayer, model: ModelPlayer, renderer: RenderPlayer) {
        val snapshot = bakedSnapshot ?: return
        val scale = 0.0625f

        // 计算动画参数
        val limbSwing = snapshot.limbSwing - snapshot.limbSwingAmount
        val limbSwingAmount = snapshot.limbSwingAmount.coerceAtMost(1.0f)
        val ageInTicks = player.ticksExisted.toFloat() + MC.renderPartialTicks
        val headYaw = snapshot.rotationYawHead - snapshot.renderYawOffset
        val headPitch = snapshot.rotationPitch

        // 设置蹲伏状态
        model.isSneak = snapshot.sneaking

        // 调用 setRotationAngles 设置姿态
        // Mo' Bends 可能会 hook 这个方法，但没关系，我们正在录制
        model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale, player)

        // 渲染所有部件
        // 这些 GL 调用会被录制到 Display List
        model.bipedHead.render(scale)
        model.bipedHeadwear.render(scale)
        model.bipedBody.render(scale)
        model.bipedRightArm.render(scale)
        model.bipedLeftArm.render(scale)
        model.bipedRightLeg.render(scale)
        model.bipedLeftLeg.render(scale)

        // 渲染外层皮肤
        model.bipedBodyWear.render(scale)
        model.bipedRightArmwear.render(scale)
        model.bipedLeftArmwear.render(scale)
        model.bipedRightLegwear.render(scale)
        model.bipedLeftLegwear.render(scale)
    }

    /**
     * 渲染烘焙的几何数据
     *
     * @param alpha 透明度
     * @param scale 缩放
     */
    fun render(alpha: Float, scale: Float = 1.0f) {
        if (!isBaked) return
        val snapshot = bakedSnapshot ?: return
        val player = MC.world?.getPlayerEntityByUUID(entityUUID) ?: return

        // 检查可见性条件
        if (player.isInvisible) return
        if (player === MC.player && MC.gameSettings.thirdPersonView == 0) return

        // 绑定纹理（Display List 不存储纹理绑定状态）
        GlStateManager.bindTexture(bakedTextureId)

        // 设置渲染状态
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)
        GlStateManager.disableCull()
        GlStateManager.disableLighting()

        GlStateManager.pushMatrix()

        // 计算渲染位置（相对于摄像机）
        val renderManager = MC.renderManager
        val renderX = snapshot.posX - renderManager.viewerPosX
        val renderY = snapshot.posY - renderManager.viewerPosY
        val renderZ = snapshot.posZ - renderManager.viewerPosZ

        GlStateManager.translate(renderX, renderY, renderZ)

        // 应用快照时的旋转
        GlStateManager.rotate(180f - snapshot.renderYawOffset, 0f, 1f, 0f)

        // 模型缩放调整（标准 Minecraft 玩家渲染变换）
        GlStateManager.scale(-scale, -scale, scale)
        GlStateManager.translate(0.0f, -1.3f, 0.0f)

        // 设置透明度
        GlStateManager.color(1f, 1f, 1f, alpha)

        // 调用预编译的 Display List
        // Mo' Bends 无法干预，因为我们不再调用任何模型方法
        GL11.glCallList(displayListId)

        GlStateManager.popMatrix()

        // 恢复渲染状态
        GlStateManager.enableLighting()
        GlStateManager.enableCull()
        GlStateManager.shadeModel(GL11.GL_FLAT)
        GlStateManager.disableBlend()
    }

    /**
     * 获取烘焙的快照信息
     */
    fun getSnapshot(): EntityTrackerRegistry.EntityInfo? = bakedSnapshot

    /**
     * 释放 Display List 资源
     * 必须在渲染线程（GL 上下文）中调用
     */
    fun dispose() {
        if (displayListId != -1) {
            GL11.glDeleteLists(displayListId, 1)
            displayListId = -1
        }
        bakedSnapshot = null
    }
}

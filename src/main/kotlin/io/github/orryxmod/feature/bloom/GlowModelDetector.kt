package io.github.orryxmod.feature.bloom

import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Glow 模型检测器
 * 检测模型部件名称是否包含 "glow" 关键词
 */
@SideOnly(Side.CLIENT)
object GlowModelDetector {

    // 匹配模式（不区分大小写）
    private val glowPattern = Regex("(?i)glow")

    // 额外的发光关键词
    private val additionalPatterns = listOf(
        Regex("(?i)emissive"),
        Regex("(?i)light"),
        Regex("(?i)bloom")
    )

    /**
     * 检查模型部件名称是否应该发光
     */
    fun shouldGlow(partName: String?): Boolean {
        if (partName == null) return false
        return glowPattern.containsMatchIn(partName) ||
               additionalPatterns.any { it.containsMatchIn(partName) }
    }

    /**
     * 从模型中筛选出需要发光的部件
     */
    fun filterGlowParts(model: ModelBase): List<ModelRenderer> {
        val boxList = model.boxList ?: return emptyList()
        return boxList.filterIsInstance<ModelRenderer>().filter { renderer ->
            renderer.boxName?.let { shouldGlow(it) } ?: false
        }
    }

    /**
     * 递归获取所有子部件
     */
    fun getAllParts(renderer: ModelRenderer): List<ModelRenderer> {
        val result = mutableListOf(renderer)
        renderer.childModels?.forEach { child ->
            if (child is ModelRenderer) {
                result.addAll(getAllParts(child))
            }
        }
        return result
    }

    /**
     * 从模型中筛选出需要发光的部件（包括子部件）
     */
    fun filterGlowPartsRecursive(model: ModelBase): List<ModelRenderer> {
        val boxList = model.boxList ?: return emptyList()
        val result = mutableListOf<ModelRenderer>()

        boxList.filterIsInstance<ModelRenderer>().forEach { renderer ->
            val allParts = getAllParts(renderer)
            allParts.forEach { part ->
                if (part.boxName?.let { shouldGlow(it) } == true) {
                    result.add(part)
                }
            }
        }

        return result.distinct()
    }
}

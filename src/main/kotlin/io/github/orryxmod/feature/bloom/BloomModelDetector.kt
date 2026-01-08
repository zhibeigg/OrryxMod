package io.github.orryxmod.feature.bloom

import io.github.orryxmod.OrryxMod
import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Bloom 模型检测器
 * 检测模型部件名称是否包含 BloomConfig 的匹配名称
 */
@SideOnly(Side.CLIENT)
object BloomModelDetector {

    private var lastLogTime = 0L
    private const val LOG_INTERVAL = 5000L // 每5秒打印一次日志，避免刷屏

    /**
     * 为骨骼名称查找匹配的配置（与实体名称匹配逻辑相同）
     */
    fun findConfigForBone(boneName: String?): BloomConfig? {
        if (boneName == null) return null
        return BloomConfigManager.findConfig(boneName)
    }

    /**
     * 检查模型部件名称是否应该发光（基于配置匹配）
     */
    fun shouldBloom(partName: String?): Boolean {
        return findConfigForBone(partName) != null
    }

    /**
     * 递归获取所有子部件
     */
    private fun getAllParts(renderer: ModelRenderer): List<ModelRenderer> {
        val result = mutableListOf(renderer)
        renderer.childModels?.forEach { child ->
            if (child is ModelRenderer) {
                result.addAll(getAllParts(child))
            }
        }
        return result
    }

    /**
     * 从模型中筛选出需要发光的部件及其匹配的配置（包括子部件）
     * @return 部件与其匹配配置的列表
     */
    fun filterBloomPartsWithConfig(model: ModelBase): List<Pair<ModelRenderer, BloomConfig>> {
        val boxList = model.boxList ?: return emptyList()
        val result = mutableListOf<Pair<ModelRenderer, BloomConfig>>()

        // 调试日志：限制频率避免刷屏
        val currentTime = System.currentTimeMillis()
        val shouldLog = currentTime - lastLogTime > LOG_INTERVAL
        if (shouldLog) {
            lastLogTime = currentTime
            OrryxMod.logger.info("[BloomModelDetector] boxList size: ${boxList.size}, model class: ${model.javaClass.simpleName}")
        }

        val allBoneNames = mutableListOf<String>()
        val matchedBones = mutableListOf<String>()

        boxList.filterIsInstance<ModelRenderer>().forEach { renderer ->
            val allParts = getAllParts(renderer)
            allParts.forEach { part ->
                val boneName = part.boxName
                allBoneNames.add(boneName ?: "null")

                val config = findConfigForBone(boneName)
                if (config != null) {
                    result.add(part to config)
                    matchedBones.add("$boneName -> ${config.name}")
                }
            }
        }

        if (shouldLog && allBoneNames.isNotEmpty()) {
            OrryxMod.logger.info("[BloomModelDetector] All bone names: $allBoneNames")
            OrryxMod.logger.info("[BloomModelDetector] Matched bones: $matchedBones")
            OrryxMod.logger.info("[BloomModelDetector] Available configs: ${BloomConfigManager.hasConfigs()}")
        }

        return result.distinctBy { it.first }
    }

    /**
     * 从模型中筛选出需要发光的部件（包括子部件）
     * @deprecated 使用 filterBloomPartsWithConfig 以获取部件及其配置
     */
    fun filterBloomPartsRecursive(model: ModelBase): List<ModelRenderer> {
        return filterBloomPartsWithConfig(model).map { it.first }
    }
}

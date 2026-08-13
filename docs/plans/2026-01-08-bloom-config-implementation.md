# Bloom 配置化实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 bloom 泛光效果改为配置驱动，支持服务端发包同步多个配置项到客户端。

**Architecture:** 新增 BloomConfig 数据类和 BloomConfigManager 管理器，通过三种网络包（全量同步、增量更新、删除）实现配置同步。渲染时根据实体名称匹配配置，使用配置的颜色、强度、距离参数。

**Tech Stack:** Kotlin, Forge 1.12.2, GLSL 着色器

---

### Task 1: 创建 BloomConfig 数据类

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/feature/bloom/BloomConfig.kt`

**Step 1: 创建数据类文件**

```kotlin
package io.github.orryxmod.feature.bloom

/**
 * Bloom 配置项
 */
data class BloomConfig(
    val name: String,           // 匹配关键词
    val color: IntArray,        // RGBA [r, g, b, a]
    val strength: Float,        // 泛光强度
    val radius: Float,          // 渲染距离
    val priority: Int           // 优先级
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BloomConfig) return false
        return name == other.name && color.contentEquals(other.color) &&
               strength == other.strength && radius == other.radius && priority == other.priority
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + color.contentHashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + radius.hashCode()
        result = 31 * result + priority
        return result
    }
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/BloomConfig.kt
git commit -m "feat(bloom): 添加 BloomConfig 数据类"
```

---

### Task 2: 创建 BloomConfigManager 管理器

**Files:**
- Create: `src/main/kotlin/io/github/orryxmod/feature/bloom/BloomConfigManager.kt`

**Step 1: 创建管理器文件**

```kotlin
package io.github.orryxmod.feature.bloom

/**
 * Bloom 配置管理器
 */
object BloomConfigManager {
    private val configs = mutableMapOf<String, BloomConfig>()

    /**
     * 为实体查找匹配的配置（按优先级排序后取最高优先级）
     */
    fun findConfig(entityName: String): BloomConfig? {
        return configs.values
            .filter { entityName.contains(it.name, ignoreCase = true) }
            .maxByOrNull { it.priority }
    }

    /**
     * 全量同步配置
     */
    fun syncAll(newConfigs: Map<String, BloomConfig>) {
        configs.clear()
        configs.putAll(newConfigs)
    }

    /**
     * 更新单个配置
     */
    fun update(id: String, config: BloomConfig) {
        configs[id] = config
    }

    /**
     * 删除配置
     */
    fun remove(id: String) {
        configs.remove(id)
    }

    /**
     * 清空所有配置
     */
    fun clear() {
        configs.clear()
    }

    /**
     * 检查是否有配置
     */
    fun hasConfigs(): Boolean = configs.isNotEmpty()
}
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/BloomConfigManager.kt
git commit -m "feat(bloom): 添加 BloomConfigManager 配置管理器"
```

---

### Task 3: 添加网络包定义

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/core/network/OrryxPacket.kt:138` (在文件末尾 sealed class 内添加)

**Step 1: 在 OrryxPacket.kt 的 sealed class 内添加三个新包类型**

在 `SectorShockwave` 之后、`}` 之前添加：

```kotlin
    // ========== Bloom 配置 ==========

    data class BloomConfigSync(
        val configs: Map<String, io.github.orryxmod.feature.bloom.BloomConfig>
    ) : OrryxPacket() {
        override val packetId = 15
    }

    data class BloomConfigUpdate(
        val id: String,
        val config: io.github.orryxmod.feature.bloom.BloomConfig
    ) : OrryxPacket() {
        override val packetId = 16
    }

    data class BloomConfigRemove(
        val id: String
    ) : OrryxPacket() {
        override val packetId = 17
    }
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/OrryxPacket.kt
git commit -m "feat(bloom): 添加 Bloom 配置网络包定义"
```

---

### Task 4: 添加网络包编解码

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/core/network/PacketCodec.kt:88-92` (在 else 分支前添加)

**Step 1: 在 decode 函数的 when 表达式中添加解码逻辑**

在 `14 -> OrryxPacket.SectorShockwave(...)` 之后、`else ->` 之前添加：

```kotlin
                15 -> {
                    val count = input.readInt()
                    val configs = mutableMapOf<String, io.github.orryxmod.feature.bloom.BloomConfig>()
                    repeat(count) {
                        val id = input.readUTF()
                        val config = readBloomConfig(input)
                        configs[id] = config
                    }
                    OrryxPacket.BloomConfigSync(configs)
                }
                16 -> OrryxPacket.BloomConfigUpdate(
                    id = input.readUTF(),
                    config = readBloomConfig(input)
                )
                17 -> OrryxPacket.BloomConfigRemove(
                    id = input.readUTF()
                )
```

**Step 2: 在 PacketCodec 对象末尾添加辅助函数**

在 `readUUID()` 函数之后添加：

```kotlin
    /**
     * 从输入流读取 BloomConfig
     */
    private fun readBloomConfig(input: ByteArrayDataInput): io.github.orryxmod.feature.bloom.BloomConfig {
        return io.github.orryxmod.feature.bloom.BloomConfig(
            name = input.readUTF(),
            color = intArrayOf(
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255),
                input.readInt().coerceIn(0, 255)
            ),
            strength = input.readFloat().coerceIn(0f, 10f),
            radius = input.readFloat().coerceIn(1f, 128f),
            priority = input.readInt()
        )
    }
```

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/core/network/PacketCodec.kt
git commit -m "feat(bloom): 添加 Bloom 配置包编解码"
```

---

### Task 5: 注册网络包处理器

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt:54-62` (在 enable 函数中添加)

**Step 1: 在 BloomFeature.enable() 中注册包处理器**

在 `ShaderManager.init()` 之后添加：

```kotlin
        // 注册配置包处理器
        PacketDispatcher.register<OrryxPacket.BloomConfigSync> { packet ->
            BloomConfigManager.syncAll(packet.configs)
        }
        PacketDispatcher.register<OrryxPacket.BloomConfigUpdate> { packet ->
            BloomConfigManager.update(packet.id, packet.config)
        }
        PacketDispatcher.register<OrryxPacket.BloomConfigRemove> { packet ->
            BloomConfigManager.remove(packet.id)
        }
```

**Step 2: 添加 import**

在文件顶部添加：

```kotlin
import io.github.orryxmod.core.network.OrryxPacket
import io.github.orryxmod.core.network.PacketDispatcher
```

**Step 3: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt
git commit -m "feat(bloom): 注册 Bloom 配置包处理器"
```

---

### Task 6: 修改 ShaderManager 添加 setUniform4f

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/feature/bloom/ShaderManager.kt:130-132` (在 setUniform2f 之后添加)

**Step 1: 添加 setUniform4f 函数**

在 `setUniform2f` 函数之后添加：

```kotlin
    fun setUniform4f(program: Int, name: String, x: Float, y: Float, z: Float, w: Float) {
        GL20.glUniform4f(getUniformLocation(program, name), x, y, z, w)
    }
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/ShaderManager.kt
git commit -m "feat(bloom): 添加 setUniform4f 函数"
```

---

### Task 7: 修改着色器添加颜色 uniform

**Files:**
- Modify: `src/main/resources/assets/orryxmod/shaders/bloom_combine.frag`

**Step 1: 修改着色器文件**

将整个文件替换为：

```glsl
#version 120

varying vec2 textureCoords;

uniform sampler2D buffer_a;
uniform sampler2D buffer_b;
uniform float intensive;
uniform float base;
uniform float threshold_up;
uniform float threshold_down;
uniform vec4 bloom_color;

void main(void){
    vec3 bloom = texture2D(buffer_b, textureCoords).rgb * intensive;
    vec3 background = texture2D(buffer_a, textureCoords).rgb;

    // 应用光晕颜色
    vec3 tintedBloom = bloom * bloom_color.rgb * bloom_color.a;

    float max = max(background.b, max(background.r, background.g));
    float min = min(background.b, min(background.r, background.g));
    gl_FragColor = vec4(background + tintedBloom * ((1. - (max + min) / 2.) * (threshold_up - threshold_down) + threshold_down + base), 1.);
}
```

**Step 2: Commit**

```bash
git add src/main/resources/assets/orryxmod/shaders/bloom_combine.frag
git commit -m "feat(bloom): 着色器添加 bloom_color uniform"
```

---

### Task 8: 修改 BloomFeature 使用配置管理器

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt`

**Step 1: 修改 onRenderWorldLast 中的实体收集逻辑**

将 `onRenderWorldLast` 函数中的实体收集部分（约 153-180 行）替换为：

```kotlin
        // 收集需要泛光的实体（使用配置管理器）
        val bloomEntities = mutableListOf<Triple<net.minecraft.entity.EntityLivingBase, net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>, BloomConfig>>()

        if (BloomConfigManager.hasConfigs()) {
            var count = 0
            for (entity in world.loadedEntityList) {
                if (count >= Config.maxBloomEntities) break
                if (entity !is net.minecraft.entity.EntityLivingBase) continue

                // 获取实体名称
                val customName = entity.customNameTag
                val name = if (!customName.isNullOrEmpty()) customName else entity.name ?: ""

                // 查找匹配的配置
                val bloomConfig = BloomConfigManager.findConfig(name) ?: continue

                // 距离剔除（使用配置的 radius）
                val maxDistSq = (bloomConfig.radius * bloomConfig.radius).toDouble()
                val distSq = player.getDistanceSq(entity)
                if (distSq > maxDistSq) continue

                // 获取渲染器
                @Suppress("UNCHECKED_CAST")
                val renderer = rm.getEntityRenderObject<net.minecraft.entity.EntityLivingBase>(entity) as? net.minecraft.client.renderer.entity.RenderLivingBase<net.minecraft.entity.EntityLivingBase>
                    ?: continue

                bloomEntities.add(Triple(entity, renderer, bloomConfig))
                count++
            }
        }
```

**Step 2: 修改 hasGlow 检查**

将：
```kotlin
val hasGlow = persistentGlow || glowRenderCallbacks.isNotEmpty() || bloomEntities.isNotEmpty()
```

保持不变（已兼容）。

**Step 3: 修改实体渲染循环**

将渲染泛光实体的循环（约 208-217 行）替换为：

```kotlin
        // 渲染泛光实体
        for ((entity, renderer, _) in bloomEntities) {
            try {
                val rx = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - rm.viewerPosX
                val ry = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - rm.viewerPosY
                val rz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - rm.viewerPosZ
                renderer.doRender(entity, rx, ry, rz, entity.rotationYaw, partialTicks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
```

**Step 4: 修改 renderBloomToMain 调用，传入配置参数**

将 `renderBloomToMain` 函数签名和调用修改为支持配置参数。

在 `onRenderWorldLast` 中，将：
```kotlin
renderBloomToMain(mainFBO, blurredFBO)
```

替换为：
```kotlin
// 获取第一个实体的配置（如果有），否则使用默认值
val activeConfig = bloomEntities.firstOrNull()?.third
renderBloomToMain(mainFBO, blurredFBO, activeConfig)
```

**Step 5: 修改 renderBloomToMain 函数**

将 `renderBloomToMain` 函数（约 247-282 行）替换为：

```kotlin
    /**
     * 使用着色器将泛光叠加到主画面
     */
    private fun renderBloomToMain(mainFBO: Framebuffer, bloomFBO: Framebuffer, config: BloomConfig? = null) {
        // 确保临时 FBO 存在
        ensureTempFBO(mainFBO)
        val temp = tempFBO ?: return

        // 绑定主画面纹理到 TEXTURE0
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture2D()
        mainFBO.bindFramebufferTexture()

        // 绑定泛光纹理到 TEXTURE1
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.enableTexture2D()
        bloomFBO.bindFramebufferTexture()

        // 使用混合着色器渲染到临时 FBO
        ShaderManager.renderFullImageInFBO(temp, ShaderManager.PROGRAM_BLOOM_COMBINE) { program ->
            ShaderManager.setUniform1i(program, "buffer_a", 0)
            ShaderManager.setUniform1i(program, "buffer_b", 1)
            ShaderManager.setUniform1f(program, "intensive", config?.strength ?: Config.strength)
            ShaderManager.setUniform1f(program, "base", Config.baseBrightness)
            ShaderManager.setUniform1f(program, "threshold_up", Config.highBrightnessThreshold)
            ShaderManager.setUniform1f(program, "threshold_down", Config.lowBrightnessThreshold)
            // 设置光晕颜色
            if (config != null) {
                ShaderManager.setUniform4f(program, "bloom_color",
                    config.color[0] / 255f,
                    config.color[1] / 255f,
                    config.color[2] / 255f,
                    config.color[3] / 255f
                )
            } else {
                ShaderManager.setUniform4f(program, "bloom_color", 1f, 1f, 1f, 1f)
            }
        }

        // 清理纹理绑定
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        GlStateManager.bindTexture(0)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)

        // 将临时 FBO 复制回主 FBO
        temp.bindFramebufferTexture()
        ShaderManager.renderFullImageInFBO(mainFBO, ShaderManager.PROGRAM_IMAGE, null)

        GlStateManager.bindTexture(0)
    }
```

**Step 6: 删除 Config.entityBloom 相关代码**

删除 `Config` 对象中的 `entityBloom` 字段（第 28 行），因为现在由配置管理器控制。

**Step 7: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt
git commit -m "feat(bloom): 使用 BloomConfigManager 替代硬编码匹配"
```

---

### Task 9: 添加断开连接时清理配置

**Files:**
- Modify: `src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt:396-399` (在 onDisconnect 函数中添加)

**Step 1: 在 onDisconnect 中添加清理**

在 `onDisconnect` 函数中添加：

```kotlin
    @OnDisconnect
    fun onDisconnect() {
        glowRenderCallbacks.clear()
        persistentGlow = false
        BloomConfigManager.clear()  // 添加这行
    }
```

**Step 2: Commit**

```bash
git add src/main/kotlin/io/github/orryxmod/feature/bloom/BloomFeature.kt
git commit -m "feat(bloom): 断开连接时清理配置"
```

---

### Task 10: 验证构建

**Step 1: 运行 Gradle 构建**

```bash
cd D:/code/OrryxMod && ./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 2: 如果构建失败，修复错误**

根据错误信息修复问题。

**Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat(bloom): 完成 bloom 配置化功能"
```

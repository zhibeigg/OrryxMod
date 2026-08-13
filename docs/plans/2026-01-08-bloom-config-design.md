# Bloom 配置化设计方案

## 概述

将 bloom 泛光效果改为配置驱动，支持多个配置项，通过服务端发包同步到客户端。

## 配置格式

```yaml
bloom_fire:
  name: "fire"           # 匹配实体名称（包含匹配）
  color: [255, 100, 0, 255]  # RGBA 光晕颜色
  strength: 1.5          # 泛光强度
  radius: 30.0           # 渲染距离（方块）
  priority: 10           # 优先级（数值越大越优先）
```

## 数据结构

```kotlin
data class BloomConfig(
    val name: String,        // 匹配关键词
    val color: IntArray,     // RGBA [r, g, b, a]
    val strength: Float,     // 泛光强度
    val radius: Float,       // 渲染距离
    val priority: Int        // 优先级
)
```

## 网络包

### BloomConfigSync (packetId = 15)
全量同步，玩家登录时发送所有配置。

```kotlin
data class BloomConfigSync(
    val configs: Map<String, BloomConfig>
)
```

### BloomConfigUpdate (packetId = 16)
增量更新，添加或更新单个配置。

```kotlin
data class BloomConfigUpdate(
    val id: String,
    val config: BloomConfig
)
```

### BloomConfigRemove (packetId = 17)
删除配置。

```kotlin
data class BloomConfigRemove(
    val id: String
)
```

## 配置管理器

```kotlin
object BloomConfigManager {
    private val configs = mutableMapOf<String, BloomConfig>()

    fun findConfig(entityName: String): BloomConfig? {
        return configs.values
            .filter { entityName.contains(it.name, ignoreCase = true) }
            .maxByOrNull { it.priority }
    }

    fun syncAll(newConfigs: Map<String, BloomConfig>) {
        configs.clear()
        configs.putAll(newConfigs)
    }

    fun update(id: String, config: BloomConfig) {
        configs[id] = config
    }

    fun remove(id: String) {
        configs.remove(id)
    }

    fun clear() {
        configs.clear()
    }
}
```

## 着色器修改

bloom_combine.fsh 添加颜色 uniform：

```glsl
uniform vec4 bloom_color;

void main() {
    vec4 scene = texture2D(buffer_a, texCoord);
    vec4 bloom = texture2D(buffer_b, texCoord);

    vec3 tintedBloom = bloom.rgb * bloom_color.rgb * bloom_color.a;

    gl_FragColor = scene + vec4(tintedBloom * intensive, 1.0);
}
```

## 匹配规则

- 使用包含匹配（contains，忽略大小写）
- 当多个配置匹配同一实体时，取 priority 最高的配置
- color 只影响模糊后的光晕颜色，实体本身保持原色渲染

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `BloomConfig.kt` | 新增 | 配置数据类 |
| `BloomConfigManager.kt` | 新增 | 配置存储与匹配逻辑 |
| `OrryxPacket.kt` | 修改 | 添加三个新包类型 |
| `PacketCodec.kt` | 修改 | 添加新包的编解码 |
| `NetworkHandler.kt` | 修改 | 处理新包的接收逻辑 |
| `BloomFeature.kt` | 修改 | 使用 ConfigManager 替代硬编码匹配 |
| `bloom_combine.fsh` | 修改 | 添加 bloom_color uniform |
| `ShaderManager.kt` | 修改 | 传入颜色参数 |

## 清理

断开连接时调用 `BloomConfigManager.clear()` 清空配置。

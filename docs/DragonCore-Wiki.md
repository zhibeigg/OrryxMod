# DragonCore Mod Wiki 文档

## 概述

DragonCore 是一个 Minecraft 1.12.2 客户端模组，主要用于渲染 Bedrock Edition (基岩版) 格式的模型和粒子特效。该模组提供了强大的渲染能力，包括：

- Bedrock Edition 粒子系统
- 玩家模型加载和动画系统
- 泛光 (Bloom) 后处理效果
- Molang 表达式解析器

---

## 项目架构

```
DragonCore-1.0.0/
├── blockbuster/                    # 粒子系统核心
│   ├── render/                     # 渲染相关
│   │   ├── BloomEffect.java        # 泛光效果实现
│   │   └── BloomHelper.java        # 泛光辅助工具
│   ├── emitter/                    # 粒子发射器
│   │   ├── BedrockEmitter.java     # 粒子发射器
│   │   └── BedrockParticle.java    # 粒子实体
│   ├── components/                 # 粒子组件系统
│   │   ├── appearance/             # 外观组件
│   │   ├── expiration/             # 过期组件
│   │   ├── lifetime/               # 生命周期组件
│   │   ├── motion/                 # 运动组件
│   │   ├── rate/                   # 发射速率组件
│   │   └── shape/                  # 形状组件
│   ├── math/                       # 数学库
│   │   └── molang/                 # Molang 表达式解析
│   ├── BedrockScheme.java          # 粒子方案定义
│   └── BedrockMaterial.java        # 材质类型
├── eos/moe/dragoncore/
│   └── api/                        # 公开 API
│       ├── CoreAPI.java            # 核心 API
│       ├── PlayerModelLoader.java  # 玩家模型加载器
│       └── model/                  # 模型相关接口
└── assets/dragoncore/
    ├── shaders/                    # 着色器
    └── PlayerAnimations.json       # 动画定义示例
```

---

## 核心功能模块

### 1. 泛光效果 (Bloom Effect)

泛光效果是一种后处理技术，可以让发光物体产生柔和的光晕效果。

#### BloomHelper - 泛光辅助类

`blockbuster.render.BloomHelper` 提供了简单的泛光渲染开关。

**API 方法：**

| 方法 | 说明 |
|------|------|
| `start()` | 开始泛光渲染（默认启用） |
| `start(boolean state)` | 开始泛光渲染，可指定是否启用 |
| `end()` | 结束泛光渲染并应用效果 |

**字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `bloomMark` | `boolean` | 当前是否处于泛光渲染状态 |
| `shaderPackLoaded` | `Supplier<Boolean>` | 检测是否加载了光影包（加载时禁用泛光） |

**使用示例：**

```java
import blockbuster.render.BloomHelper;

// 在渲染代码中使用泛光效果
public void renderWithBloom() {
    // 开始泛光渲染
    BloomHelper.start();

    // 在这里渲染需要发光的内容
    renderGlowingObjects();

    // 结束泛光渲染，应用效果
    BloomHelper.end();
}

// 条件性启用泛光
public void renderConditionalBloom(boolean enableBloom) {
    BloomHelper.start(enableBloom);
    renderGlowingObjects();
    BloomHelper.end();
}
```

#### BloomEffect - 泛光效果核心

`blockbuster.render.BloomEffect` 是泛光效果的底层实现。

**API 方法：**

| 方法 | 说明 |
|------|------|
| `getInput()` | 获取输入 Framebuffer |
| `getOutput()` | 获取输出 Framebuffer |
| `renderBloom(width, height, background, input, output)` | 执行泛光渲染 |
| `hookDepthBuffer(fbo, depthBuffer)` | 挂接深度缓冲 |
| `blitShader(shaderInstance, dist)` | 使用着色器进行 blit 操作 |

**着色器：**

| 着色器 | 说明 |
|--------|------|
| `SEPARABLE_BLUR` | 可分离高斯模糊着色器 |
| `UNREAL_COMPOSITE` | Unreal 风格合成着色器 |
| `BLIT_SHADER` | 快速 blit 着色器 |

**泛光渲染流程：**

1. 将发光内容渲染到输入 Framebuffer
2. 对输入进行多级降采样和高斯模糊（2x, 4x, 8x）
3. 使用 Unreal 风格合成着色器混合原始图像和模糊结果
4. 输出最终结果

---

### 2. 粒子系统 (Bedrock Particle System)

DragonCore 实现了完整的 Bedrock Edition 粒子系统。

#### BedrockScheme - 粒子方案

`blockbuster.BedrockScheme` 定义了粒子效果的完整配置。

**主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `identifier` | `String` | 粒子效果标识符 |
| `material` | `BedrockMaterial` | 材质类型 |
| `texture` | `ResourceLocation` | 纹理资源 |
| `curves` | `Map<String, BedrockCurve>` | 曲线定义 |
| `components` | `List<BedrockComponentBase>` | 组件列表 |
| `parser` | `MolangParser` | Molang 解析器 |

**API 方法：**

```java
// 从 JSON 解析粒子方案
BedrockScheme scheme = BedrockScheme.parse(jsonString);

// 从 JsonElement 解析
BedrockScheme scheme = BedrockScheme.parse(jsonElement);

// 转换为 JSON
JsonElement json = BedrockScheme.toJson(scheme);

// 复制粒子方案
BedrockScheme copy = BedrockScheme.dupe(originalScheme);

// 获取组件
BedrockComponentAppearanceBillboard billboard = scheme.get(BedrockComponentAppearanceBillboard.class);

// 添加组件
scheme.add(BedrockComponentAppearanceBillboard.class);

// 获取或创建组件
BedrockComponentAppearanceBillboard billboard = scheme.getOrCreate(BedrockComponentAppearanceBillboard.class);
```

#### BedrockMaterial - 材质类型

`blockbuster.BedrockMaterial` 定义了粒子的混合模式。

| 材质类型 | 说明 |
|----------|------|
| `OPAQUE` | 不透明 |
| `ALPHA` | Alpha 测试 |
| `BLEND` | Alpha 混合 |
| `ADDITIVE` | 叠加混合 |
| `OPAQUE_DEPTH` | 不透明（禁用深度测试） |
| `ALPHA_DEPTH` | Alpha 测试（禁用深度测试） |
| `BLEND_DEPTH` | Alpha 混合（禁用深度测试） |
| `ADDITIVE_DEPTH` | 叠加混合（禁用深度测试） |

#### BedrockEmitter - 粒子发射器

`blockbuster.emitter.BedrockEmitter` 是粒子发射器的核心类。

**主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `particles` | `List<BedrockParticle>` | 活跃粒子列表 |
| `scheme` | `BedrockScheme` | 粒子方案 |
| `target` | `EntityLivingBase` | 目标实体 |
| `world` | `World` | 世界实例 |
| `bloom` | `boolean` | 是否启用泛光 |
| `running` | `boolean` | 是否运行中 |
| `age` | `int` | 发射器年龄（tick） |
| `lifetime` | `int` | 发射器生命周期 |

**API 方法：**

```java
// 创建发射器
BedrockEmitter emitter = new BedrockEmitter("effect_name");

// 设置粒子方案
emitter.setScheme(scheme);

// 设置带变量的粒子方案
Map<String, String> variables = new HashMap<>();
variables.put("variable.custom", "1.5");
emitter.setScheme(scheme, variables);

// 设置目标实体
emitter.setTarget(entity);

// 设置世界
emitter.setWorld(world);

// 启动/停止发射器
emitter.start();
emitter.stop();

// 更新发射器（每 tick 调用）
emitter.update();

// 渲染粒子
emitter.render(partialTicks);

// 生成粒子
emitter.spawnParticle();
```

**使用示例：**

```java
// 完整的粒子效果使用示例
public class ParticleExample {
    private BedrockEmitter emitter;

    public void init(String particleJson, EntityLivingBase target) {
        // 解析粒子方案
        BedrockScheme scheme = BedrockScheme.parse(particleJson);

        // 创建发射器
        emitter = new BedrockEmitter("my_effect");
        emitter.setScheme(scheme);
        emitter.setTarget(target);
        emitter.bloom = true;  // 启用泛光

        // 设置位置
        emitter.lastGlobal.set(target.posX, target.posY, target.posZ);
    }

    public void onTick() {
        if (emitter != null && emitter.running) {
            emitter.update();
        }
    }

    public void onRender(float partialTicks) {
        if (emitter != null) {
            emitter.render(partialTicks);
        }
    }
}
```

#### BedrockParticle - 粒子实体

`blockbuster.emitter.BedrockParticle` 表示单个粒子。

**主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `position` | `Vector3d` | 当前位置 |
| `prevPosition` | `Vector3d` | 上一帧位置 |
| `speed` | `Vector3f` | 速度 |
| `acceleration` | `Vector3f` | 加速度 |
| `rotation` | `float` | 旋转角度 |
| `age` | `int` | 粒子年龄 |
| `lifetime` | `int` | 生命周期 |
| `r, g, b, a` | `float` | 颜色和透明度 |
| `dead` | `boolean` | 是否已死亡 |

---

### 3. 粒子组件系统

粒子系统使用组件化架构，每个组件负责特定功能。

#### 组件接口

| 接口 | 说明 |
|------|------|
| `IComponentEmitterInitialize` | 发射器初始化 |
| `IComponentEmitterUpdate` | 发射器更新 |
| `IComponentParticleInitialize` | 粒子初始化 |
| `IComponentParticleUpdate` | 粒子更新 |
| `IComponentParticleRender` | 粒子渲染 |

#### 外观组件 (appearance/)

| 组件 | 说明 |
|------|------|
| `BedrockComponentAppearanceBillboard` | 公告板渲染（始终面向摄像机） |
| `BedrockComponentAppearanceLighting` | 光照设置 |
| `BedrockComponentAppearanceTinting` | 颜色着色 |

**CameraFacing 模式：**

| 模式 | 说明 |
|------|------|
| `LOOKAT_XYZ` | 完全面向摄像机 |
| `LOOKAT_Y` | 仅 Y 轴面向摄像机 |
| `ROTATE_XYZ` | 跟随摄像机旋转 |
| `ROTATE_Y` | 仅 Y 轴跟随旋转 |
| `DIRECTION_X/Y/Z` | 沿速度方向 |
| `EMITTER_XY/XZ/YZ` | 固定平面 |

#### 生命周期组件 (lifetime/)

| 组件 | 说明 |
|------|------|
| `BedrockComponentLifetimeOnce` | 单次发射 |
| `BedrockComponentLifetimeLooping` | 循环发射 |
| `BedrockComponentLifetimeExpression` | 表达式控制 |

#### 运动组件 (motion/)

| 组件 | 说明 |
|------|------|
| `BedrockComponentInitialSpeed` | 初始速度 |
| `BedrockComponentInitialSpin` | 初始旋转 |
| `BedrockComponentMotionDynamic` | 动态运动（重力、阻力） |
| `BedrockComponentMotionParametric` | 参数化运动 |
| `BedrockComponentMotionCollision` | 碰撞检测 |

#### 形状组件 (shape/)

| 组件 | 说明 |
|------|------|
| `BedrockComponentShapePoint` | 点发射 |
| `BedrockComponentShapeBox` | 盒子区域发射 |
| `BedrockComponentShapeSphere` | 球体区域发射 |
| `BedrockComponentShapeDisc` | 圆盘区域发射 |
| `BedrockComponentShapeEntityAABB` | 实体碰撞箱发射 |

#### 发射速率组件 (rate/)

| 组件 | 说明 |
|------|------|
| `BedrockComponentRateInstant` | 瞬间发射 |
| `BedrockComponentRateSteady` | 稳定发射 |

---

### 4. Molang 表达式系统

`blockbuster.math.molang.MolangParser` 实现了 Bedrock Edition 的 Molang 表达式语言。

#### 内置变量

**粒子变量：**

| 变量 | 说明 |
|------|------|
| `variable.particle_age` | 粒子年龄（秒） |
| `variable.particle_lifetime` | 粒子生命周期（秒） |
| `variable.particle_random_1~4` | 随机数 (0-1) |
| `variable.particle_speed.length` | 速度长度 |
| `variable.particle_speed.x/y/z` | 速度分量 |
| `variable.particle_bounces` | 弹跳次数 |

**发射器变量：**

| 变量 | 说明 |
|------|------|
| `variable.emitter_age` | 发射器年龄（秒） |
| `variable.emitter_lifetime` | 发射器生命周期（秒） |
| `variable.emitter_random_1~4` | 随机数 (0-1) |

#### 内置函数

**数学函数：**

| 函数 | 说明 |
|------|------|
| `math.abs(x)` | 绝对值 |
| `math.ceil(x)` | 向上取整 |
| `math.floor(x)` | 向下取整 |
| `math.round(x)` | 四舍五入 |
| `math.trunc(x)` | 截断 |
| `math.clamp(x, min, max)` | 限制范围 |
| `math.min(a, b)` | 最小值 |
| `math.max(a, b)` | 最大值 |
| `math.mod(a, b)` | 取模 |
| `math.pow(base, exp)` | 幂运算 |
| `math.sqrt(x)` | 平方根 |
| `math.exp(x)` | e 的 x 次方 |
| `math.ln(x)` | 自然对数 |

**三角函数（角度制）：**

| 函数 | 说明 |
|------|------|
| `math.sin(x)` | 正弦 |
| `math.cos(x)` | 余弦 |
| `math.asin(x)` | 反正弦 |
| `math.acos(x)` | 反余弦 |
| `math.atan(x)` | 反正切 |
| `math.atan2(y, x)` | 二参数反正切 |

**工具函数：**

| 函数 | 说明 |
|------|------|
| `math.lerp(a, b, t)` | 线性插值 |
| `math.lerprotate(a, b, t)` | 旋转插值 |
| `math.random(min, max)` | 随机浮点数 |
| `math.random_integer(min, max)` | 随机整数 |
| `math.die_roll(num, low, high)` | 骰子投掷 |
| `math.die_roll_integer(num, low, high)` | 整数骰子投掷 |
| `math.hermite_blend(t)` | Hermite 混合 |

**常量：**

| 常量 | 值 |
|------|-----|
| `math.pi` | 3.14159... |

**使用示例：**

```java
MolangParser parser = new MolangParser();

// 解析表达式
MolangExpression expr = parser.parseExpression("math.sin(variable.particle_age * 360) * 0.5");

// 设置变量值
parser.setValue("variable.particle_age", 0.5);

// 计算结果
double result = expr.get();
```

---

### 5. 玩家模型系统

#### PlayerModelLoader - 玩家模型加载器

`eos.moe.dragoncore.api.PlayerModelLoader` 用于加载和管理玩家模型。

**API 方法：**

```java
// 获取模型（异步加载）
IModel model = PlayerModelLoader.getModel(modelName, modelData);

// 绑定纹理
PlayerModelLoader.bindTexture(modelName, textureData);

// 绑定发光纹理
PlayerModelLoader.bindGlowTexture(modelName, glowTextureData);

// 检查是否有发光纹理
boolean hasGlow = PlayerModelLoader.hasGlowTexture(glowTextureData);

// 应用动画
PlayerModelLoader.applyAnimation(uuid, modelName, animationData, animationName, apply);

// 清理数据
PlayerModelLoader.clearData(modelName);

// 清理所有
PlayerModelLoader.clear();
```

#### CoreAPI - 核心 API

`eos.moe.dragoncore.api.CoreAPI` 提供核心功能访问。

```java
// 获取动画管理器
AnimationManager manager = CoreAPI.getAnimationManager(playerUUID);

// 检查实体是否使用了模型替换
boolean isReplaced = CoreAPI.isModelReplace(entity);
```

#### 模型接口

**IModel：**

```java
public interface IModel {
    void render(float scale);
    void clearData();
    List<IModelPiece> getModelPieces();
}
```

**IModelPiece：**

```java
public interface IModelPiece {
    void render(float scale, boolean selected);
    String getName();
}
```

**AnimationEntityModel：**

```java
public interface AnimationEntityModel {
    AnimationModel getBaseModel();
    Entity getEntity();
    void setEntity(Entity entity);
}
```

**AnimationManager：**

```java
public interface AnimationManager {
    void applyAnimation(AnimationEntityModel model, float partialTicks);
    void applyAnimation(AnimationEntityModel model);
    boolean isOnPlayAnimation();
    boolean needPlaySwordTrail();
}
```

---

## 动画格式

DragonCore 使用 Bedrock Edition 的动画格式。

**示例动画 JSON：**

```json
{
    "format_version": "1.8.0",
    "animations": {
        "animation.walk": {
            "loop": true,
            "animation_length": 2.08,
            "bones": {
                "head": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.52": [2.5, 0, 0],
                        "1.04": [0, 0, 0]
                    },
                    "position": {
                        "0.0": [0, 0, 0],
                        "1.04": [0, 0, 0]
                    }
                },
                "RightArm": {
                    "rotation": {
                        "0.0": [0, 0, 0],
                        "0.52": [-20, 0, 5],
                        "1.04": [0, 0, 0]
                    }
                }
            }
        },
        "animation.attack": {
            "loop": "hold_on_last_frame",
            "animation_length": 0.68,
            "bones": {
                "RightArm": {
                    "rotation": {
                        "0.0": [17.5, 0, 15],
                        "0.6": [30, 0, 15],
                        "0.68": [-90, -40, 40]
                    }
                }
            }
        }
    }
}
```

**循环模式：**

| 值 | 说明 |
|----|------|
| `true` | 循环播放 |
| `false` | 播放一次 |
| `"hold_on_last_frame"` | 播放一次并保持最后一帧 |

---

## 着色器

DragonCore 包含多个后处理着色器。

### 泛光相关着色器

**separable_blur.fsh** - 可分离高斯模糊

```glsl
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform vec2 BlurDir;
uniform int Radius;
```

**unreal_composite.fsh** - Unreal 风格合成

```glsl
uniform sampler2D DiffuseSampler;  // 背景
uniform sampler2D HighLight;        // 高光
uniform sampler2D BlurTexture1;     // 2x 模糊
uniform sampler2D BlurTexture2;     // 4x 模糊
uniform sampler2D BlurTexture3;     // 8x 模糊
uniform float BloomRadius;
uniform float BloomIntensive;
```

### 其他后处理效果

| 着色器 | 说明 |
|--------|------|
| `blur` | 模糊 |
| `fxaa` | 快速近似抗锯齿 |
| `sobel` | 边缘检测 |
| `deconverge` | 色差效果 |
| `phosphor` | 磷光效果 |

---

## 配置说明

### 粒子效果 JSON 格式

```json
{
    "format_version": "1.10.0",
    "particle_effect": {
        "description": {
            "identifier": "namespace:effect_name",
            "basic_render_parameters": {
                "material": "particles_blend",
                "texture": "textures/particle/particles.png"
            }
        },
        "components": {
            "minecraft:emitter_rate_instant": {
                "num_particles": 10
            },
            "minecraft:emitter_lifetime_once": {
                "active_time": 1.0
            },
            "minecraft:emitter_shape_point": {},
            "minecraft:particle_lifetime_expression": {
                "max_lifetime": "1.0"
            },
            "minecraft:particle_initial_speed": 5.0,
            "minecraft:particle_motion_dynamic": {
                "linear_acceleration": [0, -9.8, 0]
            },
            "minecraft:particle_appearance_billboard": {
                "size": [0.1, 0.1],
                "facing_camera_mode": "lookat_xyz",
                "uv": {
                    "texture_width": 128,
                    "texture_height": 128,
                    "uv": [0, 0],
                    "uv_size": [8, 8]
                }
            }
        }
    }
}
```

---

## 最佳实践

### 1. 泛光效果使用

```java
// 推荐：使用 try-finally 确保正确关闭
BloomHelper.start();
try {
    renderGlowingContent();
} finally {
    BloomHelper.end();
}

// 检查光影包兼容性
if (!BloomHelper.shaderPackLoaded.get()) {
    BloomHelper.start();
    // ...
    BloomHelper.end();
}
```

### 2. 粒子系统性能优化

- 限制同时活跃的粒子数量
- 使用适当的材质类型（ADDITIVE 比 BLEND 更快）
- 避免过于复杂的 Molang 表达式
- 及时清理不再使用的发射器

### 3. 模型加载

- 模型加载是异步的，首次调用会返回占位模型
- 缓存已加载的模型引用
- 在不需要时调用 `clearData()` 释放资源

---

## 注意事项

1. **混淆代码**：`eos.moe.dragoncore` 包下的大部分类名被混淆（如 `fx.java`, `au.java`），建议只使用 `api` 包下的公开接口。

2. **版本兼容**：此文档基于 DragonCore 1.0.0，适用于 Minecraft 1.12.2。

3. **光影兼容**：当检测到光影包加载时，泛光效果会自动禁用以避免冲突。

4. **线程安全**：模型加载使用线程池异步执行，注意在主线程访问模型数据。

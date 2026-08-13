<p align="center">
  <img src="src/main/resources/Orryx/orryx.png" alt="Orryx Client" width="128" />
</p>

<h1 align="center">Orryx Client</h1>

<p align="center">
  面向 Minecraft 1.12.2 Forge 的客户端模组，为服务端提供可控的视觉效果、瞄准、导航和调试能力。
</p>

> 当前项目版本：**1.6.11**

## 功能概览

- **Aim**：点、方向和区域瞄准，以及 Texture、Circle、Model 指示器。
- **Bloom**：实体泛光、强度/半径/优先级控制与 OptiFine 光影冲突保护。
- **Effect**：残影、闪烁和实体投影效果。
- **Navigation**：通过 Baritone API 执行服务端指定的路径规划。
- **Shockwave**：圆形、矩形和扇形地面冲击波及方块碎裂动画。
- **Collider**：Sphere、AABB、OBB、Capsule、Ray 和 Composite 调试线框。
- **Mouse**：服务端控制客户端鼠标指针状态。

完整网络协议见 [`docs/Plugin-Integration.md`](docs/Plugin-Integration.md)，性能边界见 [`docs/Performance-Limits.md`](docs/Performance-Limits.md)。

## 平台与工具链

| 项目 | 版本/要求 |
| --- | --- |
| Minecraft | 1.12.2 |
| Minecraft Forge | 14.23.5.2864 |
| 构建 JDK | JDK 17 |
| Gradle Wrapper | 7.6.1 |
| 产物字节码 | Java 8（JVM target 1.8） |
| 主要语言 | Kotlin 1.9.0 |

JDK 17 用于运行 Gradle 和 ForgeGradle；构建脚本将 Java/Kotlin 编译目标设为 Java 8。运行游戏时仍应使用兼容 Minecraft 1.12.2/Forge 的 Java 8 环境。

## 构建

Windows：

```powershell
.\gradlew.bat clean shadowJar
```

Linux/macOS：

```bash
./gradlew clean shadowJar
```

可发布模组 JAR 输出到 `builds/`。不要分发带 `-plain`、`-api` 或 `-api-source` 后缀的开发产物。

## 测试

主构建验证：

```bash
./gradlew unitTest shadowJar
```

不加载 Forge/Minecraft 的快速单元测试使用独立脚本；该脚本需要本机可用的 Gradle 8.9：

```bash
gradle --no-build-cache -b build-test.gradle test
```

## 启动开发客户端

首次运行会下载 Forge、Minecraft 映射和开发依赖：

```bash
./gradlew runClient
```

开发实例文件位于本地 `run/` 目录，该目录不会提交。测试仅应连接到你有权限使用的服务器。

## 安装

1. 安装 Minecraft 1.12.2 和 Forge **14.23.5.2864**。
2. 使用适用于该游戏版本的 Java 8 启动环境。
3. 从 GitHub Releases 下载版本 `v1.6.11` 的唯一发行 JAR，并按需校验同一 Release 中的 `SHA256SUMS`。
4. 将 JAR 放入 Minecraft 实例的 `mods/` 目录。
5. 启动游戏。客户端与服务端插件应使用兼容的协议版本。

## 发布物与供应链信息

`v*` 标签触发的 Release workflow 会验证标签与项目版本一致，构建并确认只有一个发行 JAR，同时生成：

- `SHA256SUMS`：发行 JAR 的 SHA-256 摘要；
- `dependencies.txt`：Gradle 依赖报告；
- `sbom.spdx.json`：SPDX JSON 软件物料清单（SBOM）。

Pull Request 和 `master` 分支工作流只执行测试与构建验证，不读取发布凭据，也不会发布制品。

## 贡献

提交问题或代码前请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md) 和 [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)。建议先搜索现有 Issue，并为行为变化补充测试和文档。Pull Request 必须通过 CI，合并由维护者审核决定。

## 安全

请不要通过公开 Issue 报告可利用的安全漏洞。请按照 [`SECURITY.md`](SECURITY.md) 中的私密报告流程提交，并说明受影响版本、复现步骤和影响范围。

## 许可证

- OrryxMod 的**原创源代码**采用 [`MIT License`](LICENSE)。
- Orryx 名称/PNG 素材由项目维护者持有或已取得合法授权，并按 [`docs/ASSET-LICENSES.md`](docs/ASSET-LICENSES.md) 中的条件提供；它们不因代码采用 MIT 而自动变为 MIT。
- Bloom Shader 包含经合法授权使用的第三方/派生内容，继续受其原许可约束。
- 打包依赖、开发依赖、Minecraft、Forge、Baritone、Mixin 等第三方组件均采用各自许可证，不由本项目的 MIT 许可证重新授权。

详细归属与许可证路径见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和 [`licenses/`](licenses/)。Minecraft、Minecraft Forge 和其他第三方名称及商标归各自权利人所有；本项目不是 Mojang Studios、Microsoft 或 Forge 的官方产品。

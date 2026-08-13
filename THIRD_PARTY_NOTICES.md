# Third-Party Notices

OrryxMod 1.6.11 的原创代码采用 MIT License。第三方代码、库、游戏内容、素材和商标不因包含在本仓库或发行包中而改用 MIT；它们继续受各自许可证和权利声明约束。

本文件是方便查阅的归属清单，不替代上游许可证，也不是法律意见。发行工作流生成的 `dependencies.txt` 与 `sbom.spdx.json` 是每个发行版本实际解析依赖的补充记录。

## 随发行 JAR 打包或包含的组件

| 组件 | 使用版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| Kotlin 标准库 / Kotlin Reflect | 1.9.0 | Apache-2.0 | 由 Shadow JAR 打包；许可证全文：[`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。 |
| JOML | 1.10.7 | MIT | 由 Shadow JAR 打包；上游许可证及版权声明：[`licenses/JOML.txt`](licenses/JOML.txt)。 |
| Baritone API | Maven 坐标 `cabaletta:baritone-api:1.2` | LGPL-3.0 | 由发行 JAR 打包；许可证全文：[`licenses/BARITONE-LGPL-3.0.txt`](licenses/BARITONE-LGPL-3.0.txt)。许可证已对照上游 tag `v1.2.19`（commit `d9cb2d91a06501c5bcba2181509d0df80361f413`）核验；每次发行的实际解析构件以依赖报告、SBOM 和摘要为准。 |
| Lumenized Bloom Shader portions | 本仓库所含修改版本 | LGPL-3.0 | `src/main/resources/assets/orryxmod/shaders/` 中的 Bloom 相关 shader 包含或改编自经授权的 Lumenized shader。OrryxMod 对集成和修改部分保留修改说明，但不重新许可上游内容。许可证全文：[`licenses/LGPL-3.0-only.txt`](licenses/LGPL-3.0-only.txt)。具体文件见 [`docs/ASSET-LICENSES.md`](docs/ASSET-LICENSES.md)。 |

Baritone API 和 Lumenized 相关内容以 LGPL-3.0 条款提供。Lumenized shader 的可修改形式是本仓库 `docs/ASSET-LICENSES.md` 所列 shader 源文件。Baritone API 的对应源代码不复制到本仓库；可从 Baritone 上游仓库的 tag `v1.2.19`（commit `d9cb2d91a06501c5bcba2181509d0df80361f413`）获取。重新分发者应保留本通知、LGPL-3.0 与 GPL-3.0 文本（[`licenses/GPL-3.0-only.txt`](licenses/GPL-3.0-only.txt)），提供适用的对应源码获取信息，并确保其分发与安装方式不妨碍 LGPL 所允许的修改、替换、调试或重新链接。

## 编译、开发或测试依赖

以下组件通常不由 OrryxMod 的发行 JAR 重新许可；某些组件由 Minecraft/Forge 运行环境提供。版本以构建脚本和发行 SBOM 为准。

| 组件 | 当前声明版本 | 许可证 |
| --- | --- | --- |
| Minecraft | 1.12.2 | Mojang/Microsoft EULA 及适用条款 |
| Minecraft Forge | 14.23.5.2864 | LGPL-2.1 |
| SpongePowered Mixin | 0.8.5 | MIT（[`licenses/MIXIN.txt`](licenses/MIXIN.txt)） |
| ForgeGradle | 5.1.77 | LGPL-2.1 |
| MixinGradle | 0.7.38 | MIT |
| Gradle Shadow Plugin | 6.1.0 | Apache-2.0 |
| Kotlin Gradle Plugin | 1.9.0 | Apache-2.0 |
| JetBrains annotations | 23.0.0 | Apache-2.0 |
| JUnit Jupiter | 5.10.2 | EPL-2.0 |
| MockK | 1.13.10 | Apache-2.0 |
| Guava（测试） | 31.1-jre | Apache-2.0 |
| Log4j 2（独立测试） | 2.17.2 | Apache-2.0 |
| Netty（独立测试） | 4.1.9.Final | Apache-2.0 |

仓库提供常用许可证全文：

- Apache-2.0：[`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)
- EPL-2.0：[`licenses/EPL-2.0.txt`](licenses/EPL-2.0.txt)
- LGPL-2.1：[`licenses/LGPL-2.1-only.txt`](licenses/LGPL-2.1-only.txt)
- LGPL-3.0：[`licenses/LGPL-3.0-only.txt`](licenses/LGPL-3.0-only.txt)
- GPL-3.0：[`licenses/GPL-3.0-only.txt`](licenses/GPL-3.0-only.txt)
- MIT：[`licenses/MIT.txt`](licenses/MIT.txt)

## 素材和品牌

Orryx PNG 与 Bloom Shader 的使用授权已由项目维护者确认。PNG、shader 和品牌的具体范围及再分发条件见 [`docs/ASSET-LICENSES.md`](docs/ASSET-LICENSES.md)。除该文件明确授予的权利外，不授予 Orryx 名称、标识或其他商标权。

## 无背书声明

Minecraft、Mojang Studios、Microsoft、Forge、Kotlin、Baritone、SpongePowered 及其他名称可能是其各自所有者的商标。本项目与这些权利人不存在官方隶属或背书关系。

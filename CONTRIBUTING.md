# 为 OrryxMod 贡献

感谢你帮助改进 OrryxMod。参与即表示你同意遵守 [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md)。

## 开始之前

- 搜索现有 Issue 和 Pull Request，避免重复工作。
- Bug 与功能请求分别使用仓库模板；安全漏洞使用 [`SECURITY.md`](SECURITY.md) 的私密渠道。
- 较大功能、协议或公开 API 变更应先提交讨论 Issue，说明兼容性和迁移方案。
- 仅提交你有权按本项目适用许可证贡献的代码和素材。引入第三方内容时必须记录来源、版本、许可证和修改情况。

## 开发环境

- JDK 17：运行 Gradle/ForgeGradle。
- Java 8：工具链和 Minecraft 1.12.2 开发客户端运行环境。
- 使用仓库内 Gradle Wrapper 7.6.1。

```bash
./gradlew runClient
./gradlew unitTest shadowJar
```

快速单元测试可使用本机 Gradle 8.9：

```bash
gradle --no-build-cache -b build-test.gradle test
```

## 编码与变更要求

- 优先使用 Kotlin 空安全，避免不必要的 `!!`。
- 不使用阻塞线程的实现；Minecraft/Forge 客户端状态和渲染操作必须留在正确的游戏线程。
- 保持 Minecraft 1.12.2、Forge 14.23.5.2864 和 Java 8 字节码兼容。
- 网络协议输入必须有长度、数量、递归、数值范围和生命周期校验。
- 修复应包含回归测试；新增行为应包含测试、默认配置、用户文档、示例、语言文件和外部 API 文档（如适用）。
- 不提交 `run/`、日志、构建结果、第三方 JAR、凭据或本地 IDE 状态。
- 不修改或删除与你的变更无关的现有工作。

## 版本和 Changelog

版本格式为 `A.B.C`：

- A：公开 API 的不兼容变更；
- B：新增功能；
- C：Bug 或兼容性修复。

面向用户的变化应更新 [`CHANGELOG.md`](CHANGELOG.md)。发布版本必须与 `gradle.properties`、构建脚本元数据和 Git 标签 `vA.B.C` 一致；版本更新由维护者在发布分支统一完成。

## Commit 与 Pull Request

建议使用简洁的 Conventional Commit 风格，例如：

- `feat: add ...`
- `fix: prevent ...`
- `perf: reduce ...`
- `docs: clarify ...`

Pull Request 应：

1. 聚焦单一目的，说明问题、方案和兼容性影响；
2. 列出实际执行的测试，未执行项需说明原因；
3. 包含相关 Issue（例如 `Closes #123`）；
4. 对视觉变化提供截图或录屏；
5. 对依赖/素材变化更新 `THIRD_PARTY_NOTICES.md`、`docs/ASSET-LICENSES.md`、`licenses/` 和 SBOM 相关配置；
6. 通过 CI，并响应 CODEOWNERS 的审核意见。

提交贡献即表示你有权提交该内容，并同意你的原创代码按根目录 MIT License 提供；第三方内容仍按其各自许可证提供。

## 许可证合规清单

新增依赖或素材前：

- 记录准确名称、来源、固定版本和许可证标识；
- 确认许可证与打包、修改和再分发方式兼容；
- 保留上游版权、NOTICE 和许可证文本；
- 在 `THIRD_PARTY_NOTICES.md` 或 `docs/ASSET-LICENSES.md` 标明是否修改；
- 不提交许可证不明、仅供个人使用或禁止再分发的内容。

许可证或权利状态不确定时，不要合并该内容。

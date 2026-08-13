# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的结构，版本号采用 `A.B.C`：API 不兼容变更递增 A，功能更新递增 B，修复递增 C。

## [Unreleased]

### Added

- 尚无。

## [1.6.11] - 2026-08-13

### Added

- 完整开源发布文档：构建、测试、开发客户端、安装、贡献、安全与许可证说明。
- 第三方通知、素材许可证记录和常用第三方许可证全文。
- Issue/PR 模板、行为准则、安全策略、贡献指南、CODEOWNERS 与 Dependabot 配置。
- 标签发布流水线：版本一致性校验、唯一发行 JAR 校验、SHA-256、依赖报告和 SPDX SBOM。

### Changed

- PR 与 `master` CI 仅执行测试和构建验证，不再调用发布任务或读取发布凭据。
- GitHub Actions 使用最小权限、并发控制、超时和固定提交 SHA。
- `.gitignore` 覆盖 Gradle/Minecraft 输出、测试报告、SBOM、校验和、本地二进制与敏感文件。

### Security

- 建立私密漏洞报告和协调披露流程。

[Unreleased]: https://github.com/zhibeigg/OrryxMod/compare/v1.6.11...HEAD
[1.6.11]: https://github.com/zhibeigg/OrryxMod/releases/tag/v1.6.11

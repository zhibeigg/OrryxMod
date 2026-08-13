# 素材与 Shader 许可证

本文记录 OrryxMod 1.6.11 仓库中非普通原创程序代码资源的权利范围。项目维护者已确认 Orryx PNG 与 Bloom Shader 拥有合法使用和发布授权。

## Orryx PNG 素材

适用文件：

- `src/main/resources/Orryx/orryx.png`
- `src/main/resources/Orryx/arrow-default.png`
- `src/main/resources/Orryx/select-default.png`
- `src/main/resources/Orryx/flicker.png`
- `src/main/resources/Orryx/ghost.png`

项目维护者已确认拥有将这些文件随 OrryxMod 源码和二进制发行物复制、修改与再分发所需的权利。出于权利人隐私和授权文件可能包含的非公开信息，本仓库不公开逐文件作者、授权日期或原始凭证；相关记录由维护者留存，可在发生权利争议时通过本文末尾的私密渠道核验。本声明不应被理解为第三方对修改版、分叉或其他项目的背书。

**限制：** PNG 素材不是 MIT 许可代码。除随 OrryxMod 使用、修改和再分发所必需的权利外，不授予独立销售素材、冒充官方版本、使用 Orryx 名称/标识进行背书，或取得任何商标权的许可。若分发修改版，应清楚标明其为非官方修改版，并保留本文件与 `THIRD_PARTY_NOTICES.md`。

## Bloom Shader

适用目录：

- `src/main/resources/assets/orryxmod/shaders/`

其中以下 Bloom 管线文件包含、改编或集成了经合法授权使用的 Lumenized shader 内容：

- `blur.frag`
- `image.frag`
- `image.vert`
- `bloom_combine.frag`

这些文件及其派生修改继续按 **GNU Lesser General Public License v3.0 only（LGPL-3.0-only）** 提供，而不是按 OrryxMod 的 MIT 许可证重新授权。许可证全文见：

- [`../licenses/LGPL-3.0-only.txt`](../licenses/LGPL-3.0-only.txt)
- [`../licenses/GPL-3.0-only.txt`](../licenses/GPL-3.0-only.txt)

本仓库中的文件即为 OrryxMod 所分发 shader 的首选可修改形式。OrryxMod 对文件名、Forge/Minecraft 渲染集成、采样/合成逻辑以及为本项目所作的修改负责；原第三方部分的版权仍归其各自权利人。重新分发这些文件或包含它们的 JAR 时，必须保留 LGPL/GPL 许可证文本、本说明和 `THIRD_PARTY_NOTICES.md`，并遵守 LGPL-3.0 的其他适用要求。

目录中未被上表列为 Lumenized 派生内容的 shader，如确属 OrryxMod 原创程序代码，则按根目录 `LICENSE` 的 MIT 条款提供；但第三方来源或文件内声明优先。

## 报告权利问题

如果你认为某项素材的归属或授权记录不准确，请不要在公开 Issue 中发布敏感证明材料；请按 [`../SECURITY.md`](../SECURITY.md) 的私密联系渠道报告，并附上文件路径和权利依据。

# OrryxMod 插件对接文档

本文档描述服务端插件如何通过网络包与 OrryxMod 客户端通信。

## 通道名称

```
orryxmod:main
```

## 数据包格式

所有数据包以 `packetId` (Int) 开头，后跟具体字段。单包不得超过 65536 字节；UTF 字段解码后最长 1024 个字符。

---

## 瞄准系统

### AimRequest (ID: 1) - 发起瞄准

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| skill | UTF | 最长 1024 字符 | 技能标识 |
| module | UTF | `point` / `direction` / `area` | 瞄准模式 |
| scale | Double | 0.01-100 | 纹理指示器缩放 |
| maxDistance | Double | 0.1-512 | 最大瞄准距离 |
| indicatorType | UTF | `texture` / `model` / `circle` | 可选；旧客户端字段结束处可省略 |
| indicatorColor | Int | `0xRRGGBB` | 可选；指示器颜色 |
| indicatorAlpha | Float | 0-1 | 可选；透明度 |
| indicatorRadius | Double | 0.1-50 | 可选；圆环或区域半径 |
| modelScale | Float | 0.1-10 | 可选；模型指示器缩放 |

新增字段位于旧格式尾部。旧格式必须在 `maxDistance` 后直接结束；发送新格式时必须按表中顺序完整写入。非有限值、损坏字段、超限集合或未知类型会使整个包被拒绝；表中有界的普通数值会被客户端夹取到安全范围。

### AimConfirm (ID: 2)

| 字段 | 类型 | 说明 |
|------|------|------|
| confirmed | Boolean | `true` 确认当前目标，`false` 取消 |

### AimResponse (ID: 4) - 客户端回传

| 字段 | 类型 | 说明 |
|------|------|------|
| skill | UTF | 技能标识 |
| x, y, z | Double | Point/Area 为画面指示器位置；Direction 为归一化方向向量 |
| yaw | Float | 玩家水平朝向，发送前环绕到 `[-180, 180)` |
| pitch | Float | 玩家垂直朝向，发送前夹取到 `[-90, 90]` |

---

## 实体效果

### FlickerEffect (ID: 5) - 闪影效果

在玩家当前位置渲染一个渐隐的残影。

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| uuid | UTF | - | 目标玩家 UUID |
| timeout | Long | 0-60000 | 效果持续时间(ms) |
| alpha | Float | 0-1 | 初始透明度 |
| duration | Long | -1 到 60000 | 淡化时间(ms)，-1 表示在整个 timeout 期间线性衰减 |
| scale | Float | 0.1-10 | 模型缩放 |

**Java 示例：**
```java
public void applyFlickerEffect(Player player, long timeout, float alpha, long duration, float scale) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeInt(5);  // packetId
    out.writeUTF(player.getUniqueId().toString());
    out.writeLong(timeout);
    out.writeFloat(alpha);
    out.writeLong(duration);
    out.writeFloat(scale);
    player.sendPluginMessage(plugin, "orryxmod:main", out.toByteArray());
}
```

### GhostEffect (ID: 3) - 残影效果

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| uuid | UTF | - | 目标玩家 UUID |
| timeout | Long | 0-60000 | 效果持续时间(ms) |
| density | Int | 1-50 | 残影密度 |
| gap | Int | 0-20 | 残影间隔 |

### EntityShowAdd (ID: 8) - 添加实体影子

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| uuid | UTF | - | 目标玩家 UUID |
| group | UTF | - | 分组名称 |
| x, y, z | Double | - | 世界坐标 |
| timeout | Long | 0-300000 | 持续时间(ms) |
| rotateX, rotateY, rotateZ | Float | - | 旋转角度 |
| scale | Float | 0.01-10 | 缩放 |
| alpha | Float | 0-1 | 透明度 |
| fadeOut | Boolean | - | 是否渐隐消失 |

### EntityShowRemove (ID: 9) - 移除实体影子

| 字段 | 类型 | 说明 |
|------|------|------|
| uuid | UTF | 目标玩家 UUID |
| group | UTF | 分组名称 |

---

## 冲击波系统

### SquareShockwave (ID: 12) - 矩形冲击波

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| x, y, z | Double | - | 中心坐标 |
| length | Double | 0.5-100 | 长度 |
| width | Double | 0.5-100 | 宽度 |
| yaw | Double | 有限值，解码后环绕到 `[-180, 180)` | 朝向角度 |

### CircleShockwave (ID: 13) - 圆形冲击波

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| x, y, z | Double | - | 中心坐标 |
| radius | Double | 0.5-100 | 半径 |

### SectorShockwave (ID: 14) - 扇形冲击波

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| x, y, z | Double | - | 中心坐标 |
| radius | Double | 0.5-100 | 半径 |
| angle | Double | 0-360 | 扇形角度 |
| yaw | Double | 有限值，解码后环绕到 `[-180, 180)` | 朝向角度 |

---

## Bloom 配置

### BloomConfigSync (ID: 15) - 全量同步

| 字段 | 类型 | 说明 |
|------|------|------|
| count | Int | 配置数量，范围 0-1000 |
| configs | BloomConfig[] | 配置列表 |

**BloomConfig 结构：**
| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| id | UTF | - | 配置 ID |
| name | UTF | - | 匹配实体名称(包含匹配) |
| r, g, b, a | Int | 0-255 | 泛光颜色 |
| strength | Float | 0-10 | 泛光强度 |
| radius | Float | 1-128 | 模糊半径 |
| priority | Int | - | 优先级(高优先) |

### BloomConfigUpdate (ID: 16) - 增量更新

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UTF | 配置 ID |
| config | BloomConfig | 配置内容 |

### BloomConfigRemove (ID: 17) - 移除配置

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UTF | 配置 ID |

---

## Collider 碰撞体线框

Collider 类型使用稳定的显式 wire ID：

| 类型 | wire ID |
|------|--------:|
| Sphere | 0 |
| AABB | 1 |
| OBB | 2 |
| Capsule | 3 |
| Ray | 4 |
| Composite | 5 |

### ColliderShow (ID: 18)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UTF | 碰撞体唯一标识 |
| type | Int | 上表中的 wire ID |
| r, g, b, a | Int | 0-255 线框颜色 |
| shape | 变长 | 对应类型的几何数据 |

### ColliderUpdate (ID: 19)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UTF | 已存在碰撞体 ID |
| type | Int | 上表中的 wire ID |
| shape | 变长 | 新几何数据，颜色保持不变 |

### ColliderRemove (ID: 20)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UTF | 要移除的碰撞体 ID |

### Shape 数据

- Sphere：`cx, cy, cz, radius`，均为 Double。
- AABB：`cx, cy, cz, hx, hy, hz`，均为 Double。
- OBB：AABB 字段后追加 `qx, qy, qz, qw` 四个 Float；客户端会归一化四元数。
- Capsule：`cx, cy, cz, radius, halfHeight`，均为 Double。
- Ray：`ox, oy, oz, dx, dy, dz, length`，均为 Double；方向必须非零，客户端会归一化。
- Composite：先写 `count`，随后每个子项依次写 `childId、childType、r、g、b、a、childShape`。

Composite 限制：每层最多 50 个直接子项、最大递归深度 3、单棵树最多 200 个节点。所有坐标和尺寸必须是有限值。

---

## 导航系统

### NavigationStart (ID: 10) - 开始导航

| 字段 | 类型 | 范围 | 说明 |
|------|------|------|------|
| x, y, z | Int | - | 目标坐标 |
| range | Int | 0-100 | 到达范围 |

### NavigationStop (ID: 11) - 停止导航

无额外字段。

---

## 鼠标控制

### MouseControl (ID: 7)

| 字段 | 类型 | 说明 |
|------|------|------|
| show | Boolean | 是否显示鼠标 |

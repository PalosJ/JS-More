# JS More

JS More 是 Minecraft 1.21.1 NeoForge 的 Jurassic Saga 附属模组。它围绕“麻醉—捕获—运输—照料—释放”建立一套服务端权威的生存玩法，并补充恐龙观察、年龄画像、真实麻醉运动和可选物理结构兼容。

当前源码对应 `1.1.0` 候选。自动化通过不等同于已完成真实整合包验收；正式发布前仍需验证游戏内视觉、移动、保存重载和可选模组组合。

## 支持与安装

| 项目 | 要求 |
| --- | --- |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.232` 或更高的兼容 1.21.1 构建 |
| Jurassic Saga | `0.2.1` 或更高版本 |
| Travelers Lib | `0.7.1` 或更高版本；主开发基线为 `0.7.2` |
| Java | `21` |
| Mod ID / 制品 | `jsmore` / `jsmore-1.1.0.jar` |

最低安装链只包含 JS More、Jurassic Saga 及 Jurassic Saga 声明的前置。Curios、Jade、TerraBlender、Sable、Create 和 Aeronautics 实现均为可选集成；缺少它们时不得阻止游戏或独立服务器启动。

## 核心生存流程

1. 在酿造台中用虞美人与粗制药水制作不可饮用的麻醉药水。
2. 用麻醉药水和 Jurassic Saga 的空注射器制作麻醉针，再将麻醉针与羽毛合成麻醉镖。
3. 用六发麻醉弩射击可麻醉的 Jurassic Saga 动物。剂量会延迟生效并叠加，服务端负责睡眠、AI、飞行和水中运动状态。
4. 目标进入有效麻醉期后，用恐龙捕获箱保存其原 UUID、成长状态和必要 NBT。
5. 运输期间维持箱体耐久以及麻醉、水、肉食和草食余量；恐龙博士眼镜可查看动物与箱体状态。
6. 在安全地点释放恐龙。普通搜索失败时会逐级扩大安全搜索，最终强制释放用于避免永久困箱。

破损捕获箱不可修复、不可回收，也不能再次保存恐龙；它代表运输失败的资源损耗。

## 玩家功能

### 麻醉与运动

- 麻醉剂量、待生效区间、剩余时长和相对时间会保存并在服务端同步。
- 陆生、水生和飞行动物使用真实实体状态与碰撞感知移动，不以纯客户端动画代替游戏规则。
- 水面漂浮、姿态和粒子按环境与体型处理；未知未来动物优先使用能力与稳定基类回退，而不是固定物种白名单。
- 麻醉镖只对可麻醉目标保留短期体内显示；命中方块后不会像原版箭一样长期残留。

### 捕获、运输与恢复

- 捕获箱是 `2×4×2` 多方块结构，最大耐久为 `500`，并保留麻醉、水源和两类食物余量。
- 捕获数据区分 EMPTY、VALID 与 UNREADABLE。无法安全解析的 raw 数据和未知字段会原样保全，不会静默当作空箱覆盖。
- 捕获、放置、破坏、掉落、容器、保存重载、释放和受支持的物理化搬运都维持单一恐龙权威，避免复制或无提示丢失。
- 物理化事务无法恢复规范箱体时，可生成经过严格校验的恢复载体。有效恢复掉落物会抵抗普通消失与环境损坏，直到数据成功恢复为唯一规范权威；伪造或畸形标记不会获得永久保护。
- 标准耐久组件与捕获载荷同步，供原版耐久条和第三方耐久显示读取；占用箱不能通过原版维修路径消费载荷。

### 恐龙博士眼镜

- 查看生命、年龄、成长阶段、性别、饱食、口渴、心情、麻醉、基因和自然下蛋进度。
- 观察捕获箱时显示四项运输余量、捕获时长和箱体耐久。
- 支持原版头部栏；安装 Curios 时可识别兼容头部槽。缺少 Curios 不影响基础功能。
- 所有客户端缓存均有容量与生命周期边界；服务端会重新验证目标、距离、佩戴状态和请求频率。

### 画像、配置与世界生成

- 年龄、体型、成长阶段和刷怪蛋规则基于 Jurassic Saga 稳定类型与安全回退。已知物种可使用精确数据，未知动物仍能获得通用功能。
- `registerAdultAge` 忽略 null、非有限数、零和负数；已有覆盖优先，其次使用已知成年日，未知物种才使用体型回退。
- COMMON 配置可关闭 Jurassic Saga 自定义群系生成；该项需要完整重启。它不移除动物、化石、物品或无关结构。

### 生存引导

- 五个工作台配方通过隐藏 advancement 自然解锁。
- 获得麻醉药水会开启可见玩法入口；同时持有麻醉弩、麻醉镖和捕获箱会完成“运输准备”里程碑。
- 不需要 JEI、Guidebook 扩展或自定义网络 criterion。

## 可选兼容

- **Curios：** 提供眼镜槽位识别。
- **Jade：** 仅在安装时启用对应客户端信息集成。
- **TerraBlender：** 可选群系生成钩子；缺失时安全禁用，结构漂移时提供明确诊断。
- **Travelers Lib：** `0.7.1` 的已知不安全服务端渲染安装会被精确修补；可证明安全的新版结构不修改，模糊漂移会 fail-fast，避免静默破坏公共初始化。
- **Sable / Create / Aeronautics：** 仅对通过精确结构门禁的组合启用捕获箱物理化；未知实现保持不可搬运。内嵌的 Sable Companion 是缺少实现时安全的兼容门面，不会把航空学实现变成必需依赖。

完整边界见 [兼容性文档](docs/COMPATIBILITY.md)，系统职责与数据权威见 [架构文档](docs/ARCHITECTURE.md)。

## 开发与验证

使用仓库中的 Gradle Wrapper 和 Java 21：

```powershell
.\gradlew.bat test runGameTestServer assemble --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=minimal --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=aeronautics --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=pack-interop --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat auditReleaseArtifact --warning-mode all --console=plain
.\gradlew.bat publish --dry-run --warning-mode all --console=plain
```

`publish --dry-run` 只检查任务图，不执行实际发布。完整开发流程、Travelers 二进制 smoke、独立服务器验收和制品审计见 [开发文档](docs/DEVELOPMENT.md)。网络协议身份为 `7`；更改 payload、附件或持久化格式前必须重新评估协议和迁移边界。

## 1.1.0 破坏性身份迁移

1.1.0 将旧公开名称 **JS-revise**、Mod ID `jsrevise` 和 Java 包 `com.palos.jsrevise` 全面迁移为 **JS More**、`jsmore` 与 `com.palos.jsmore`。旧世界中的注册表对象、配置、附件和网络身份不兼容；本项目不提供 MissingMappings、DataFixer、双 namespace 或旧 Mod ID alias。升级前请备份世界，并把 1.1.0 视为全新的附属模组安装。

后续实质版本从 `1.1.1` 开始递增。详细变化见 [CHANGELOG](CHANGELOG.md)。

## 许可证、来源与隐私

- 代码、测试、构建与 CI、普通数据、语言、配方、标签和文档使用 [MIT](LICENSES/MIT.txt)。
- 仅在来源台账中标记为 ARR-VERIFIED 的项目原创视觉资产适用 [JS More Visual Assets License](LICENSES/LicenseRef-JSMore-Visual-Assets.txt)。
- 上游衍生、参考或第三方内容不属于 Palos 原创视觉资产授权范围。
- 完整范围见 [LICENSE](LICENSE)、[第三方通知](THIRD_PARTY_NOTICES.md) 与 [素材来源台账](ASSET_PROVENANCE.md)。
- 本模组不包含遥测或隐私收集功能。提交公开日志前仍应移除本机路径、账户标识与凭据。

项目主页与问题跟踪：[PalosJ/JS-More](https://github.com/PalosJ/JS-More)

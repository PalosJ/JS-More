# JS More 1.0.0 正式版发布材料

用于人工发布。本文件与介绍文案不执行任何线上修改；完成最终验收后再使用下面的发布字段。

## 发布字段

| 字段 | 内容 |
| --- | --- |
| 项目 | Jurassic Saga More / `jurassic-saga-more` |
| 项目 ID | `4o4yBaUe` |
| 版本名称 | JS More 1.0.0 |
| 版本号 | `1.0.0` |
| 发布类型 | Release |
| Minecraft / 加载器 | 1.21.1 / NeoForge |
| 客户端 / 服务端 | 均需要安装 |
| 文件 | `jsmore-1.0.0.jar`，以最终制品哈希核对 |
| 问题反馈 | https://github.com/PalosJ/JS-More/issues |
| 源码 | https://github.com/PalosJ/JS-More |
| 许可证展示 | 自定义多许可证范围；代码、普通数据与文档为 MIT，已验证原创视觉资产另有项目授权范围 |
| 许可证链接 | https://github.com/PalosJ/JS-More/blob/main/LICENSE |

使用 [MODRINTH_DESCRIPTION.md](MODRINTH_DESCRIPTION.md) 中的一种语言填写简介和正文。不要继续把整个项目仅标为 MIT；视觉资产及上游材料的范围以 LICENSE、来源台账和第三方通知为准。

### 必需依赖

正式版自动安装默认使用当前组合：

| 依赖 | 项目 ID | NeoForge 1.21.1 文件 ID | 类型 |
| --- | --- | --- | --- |
| Jurassic Saga 0.2.3 | `i4bpeN7J` | `EzDlTUHi` | required |
| Travelers Lib 0.8.2.2 | `3qvUCqn7` | `Aadzy3jP` | required |

旧版支持组合为 Jurassic Saga `YdbXbcFo`（0.2.1）和 Travelers `vKc8KD7H`（0.7.2）；它用于手动维持旧整合包，不与当前主模组混搭。Curios、Jade、TerraBlender 和航空学实现不是必需依赖。

**不要复用 Alpha 的 `Va3G53TV` 依赖：它是 Forge 1.20.1 文件。** 即使版本号相同，Minecraft 和加载器也必须匹配。创建草稿或发布记录后，使用干净的 1.21.1 NeoForge 实例检查自动安装实际取得的两个依赖文件。

## 中文更新说明

这是 JS More 的 **1.0.0 正式版**，以下变化相对于已经发布的 1.0.0 Alpha。

- 新增集蛋器：收取半径 16 格内、落地约五秒的基础蛋，提供 18 格库存并支持漏斗，装不下的蛋留在地上。
- 加入玩家喂食繁殖。普通动物需要双方符合条件并分别喂食；鸵鸟、鳄鱼、芦苇蛙和蛇怪的成年雌性可在喂食后，于下次产蛋周期产下带母系基因的实体蛋。
- 默认防止主模组陆生、水生动物因距离或闲置自然消失。繁殖与防自然消失都有独立服务器开关，默认开启；关闭繁殖时会保留已有待产数据。
- 修正成年后的年龄显示，睡觉和时间指令不再造成异常增长。旧成年个体只校正一次，已迁移个体不会重复重置。
- 默认保留主模组群系生成。与其他群系模组冲突时，可按需开启配置中的禁用选项，作为简单直接的备用方案。升级时旧设置重置并备份一次，之后保留玩家选择。修改后重启，已有区块不变。
- 支持 Jurassic Saga 0.2.3 / Travelers 0.8.2.2，同时保留旧版组合；补充新版觅食排序修复与自定义动物分类兼容。
- 支持 Aeronautics 1.3.2 / Sable 2.0.5，保留原组合；补充 Jade 15.10.6 验证，并减少满载集蛋器、大量运输箱和频繁查看信息时的额外开销。
- 修复已验证新版组合中的 Jade 重复选项注册，并补齐捕获箱信息选项的中英文名称。

麻醉弩、捕获和运输原本已在 Alpha 中提供，本次继续保留。运输失败仍会强制就近释放；危险地形可能造成恐龙受伤或死亡，破损箱不能修复后复用。安装前请核对所下载依赖均为 NeoForge 1.21.1。

## English release notes

This is the **1.0.0 stable release** of JS More. Changes below are relative to the published 1.0.0 Alpha.

- Added the Egg Collector: gathers base egg items within 16 blocks after about five seconds, with 18 slots and hopper support. Eggs that do not fit stay on the ground.
- Added player-fed breeding. Ordinary eligible parents must both be fed. Eligible female ostriches, alligators, reed frogs, and basilisks can produce one maternal-gene entity egg on their next laying cycle after feeding.
- Added default protection from distance- and inactivity-based despawning for the host's land and water creatures. Breeding and despawn protection have independent server switches, both enabled by default. Disabling breeding keeps pending egg data.
- Corrected adult age display so sleep and daylight commands no longer cause large jumps. Legacy adult animals are corrected once; previously migrated animals are not reset again.
- Jurassic Saga biome generation now stays enabled by default. An optional disable setting offers a simple fallback for biome conflicts. Old settings are backed up and reset once during the upgrade, then future choices are preserved. Restart after editing; existing chunks are unaffected.
- Supports Jurassic Saga 0.2.3 / Travelers 0.8.2.2 alongside the legacy pair, including the newer food-sort fix and custom animal spawn categories.
- Adds Aeronautics 1.3.2 / Sable 2.0.5 support alongside the older set and Jade 15.10.6 coverage. Full collectors, large numbers of transport boxes, and frequent information checks now do less unnecessary work.
- Fixes duplicate Jade option registration in the verified newer combination and adds the capture-box option's English and Chinese labels.

The anesthetic crossbow, capture, and transport tools already existed in Alpha and are retained. Transport failure still forces a nearby release; hazardous terrain can injure or kill the dinosaur, and broken boxes cannot be repaired and reused. Check that every dependency download targets NeoForge 1.21.1.

## 制品与验收记录

- 最终 JAR SHA-256：`9afc630bf2797dff2bb540c0cd1d3a37dcf190688fce30a1be22b5cc8c2f173f`
- 最终 JAR SHA-512：`1c5448b0ec0fb42327194a99445f77325faca1579b062f9148ba2c78c8616b1a1f8e6c7196dccbdeef22b38d91f79d560c7224b579440303dee17f6f2e91fb35`
- 保留原 Alpha `jrAX7Rcv` 及其制品，不替换旧文件。
- 发布前核对：新旧依赖矩阵、独服正常启动退出、配置迁移及服务器同步、实际整合包视觉和长期圈养繁殖、许可证与来源、GitHub CI、自动安装依赖。
- 需要人工发布后的自动安装验证才能确认 Modrinth 实际解析结果；本地依赖和哈希核对不能替代这一项。

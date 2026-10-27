# JS More 1.1.0 正式版发布材料

用于人工发布。本地实现与下列验证已完成；本文件与介绍文案不执行任何线上修改。尚未覆盖的实机场景单独列在验收记录中。

## 发布字段

| 字段 | 内容 |
| --- | --- |
| 项目 | Jurassic Saga More / `jurassic-saga-more` |
| 项目 ID | `4o4yBaUe` |
| 版本名称 | JS More 1.1.0 |
| 版本号 | `1.1.0` |
| 发布类型 | Release |
| Minecraft / 加载器 | 1.21.1 / NeoForge |
| 客户端 / 服务端 | 均需要安装 |
| 文件 | `jsmore-1.1.0.jar`，以最终制品哈希核对 |
| 问题反馈 | https://github.com/PalosJ/JS-More/issues |
| 源码 | https://github.com/PalosJ/JS-More |
| 许可证展示 | 自定义多许可证范围；代码、普通数据与文档为 MIT，已验证原创视觉资产另有项目授权范围 |
| 许可证链接 | https://github.com/PalosJ/JS-More/blob/main/LICENSE |

使用 [MODRINTH_DESCRIPTION.md](MODRINTH_DESCRIPTION.md) 中的一种语言填写简介和正文。不要继续把整个项目仅标为 MIT；视觉资产及上游材料的范围以 LICENSE、来源台账和第三方通知为准。

### 必需依赖

正式版自动安装默认使用当前组合：

| 依赖 | 项目 ID | NeoForge 1.21.1 文件 ID | 类型 |
| --- | --- | --- | --- |
| Jurassic Saga 0.3 | `i4bpeN7J` | `nti1NZVa` | required |
| Travelers Lib 1.0 | `3qvUCqn7` | `I9ua8Njp` | required |

公开 1.0.0 保留旧版组合：Jurassic Saga `EzDlTUHi`（0.2.3）/ Travelers `Aadzy3jP`（0.8.2.2），以及 `YdbXbcFo`（0.2.1）/ `vKc8KD7H`（0.7.2）。它用于维持旧整合包，不与当前主模组混搭。Curios、Jade、TerraBlender 和航空学实现不是必需依赖。生成的 Maven POM 同样锁定上表的平台文件 ID。

**不要复用 Alpha 的 `Va3G53TV` 依赖：它是 Forge 1.20.1 文件。** 即使版本号相同，Minecraft 和加载器也必须匹配。创建草稿或发布记录后，使用干净的 1.21.1 NeoForge 实例检查自动安装实际取得的两个依赖文件。

## 中文更新说明

1.1.0 适配 Jurassic Saga 0.3 / Travelers 1.0，完整恢复主模组繁殖。防自然清理改为关闭、仅保护已捕获个体（默认）、全部三档；旧开关不迁移，群系与调试选项保留。

修复捕获期间成长和补给时间结算、饥渴伤害区间、普通占用箱掉落物计时清理及弱小动物被麻醉镖击杀的问题。眼镜改用服务器快照，只对 Jade 实际显示的信息去重，并可选择完整独立显示。保留最多四针待生效自动补给、普通蛋收集、物理搬运和强制就近释放的风险。

实机追加修复箱体观察请求丢弃后界面冻结，以及 Travelers 1.0 寻路线程阻止独服正常退出的问题；未改变寻路算法。

旧合法待产蛋只兑现一次，无法解析的数据继续保全；旧箱可证明捕获历史，升级前已经释放的动物无法追溯。需要保护旧围栏时选择新 ALL 模式，或成功重捕一次。协议更新为 8，客户端和服务器一起升级。旧依赖请继续使用 1.0.0，未来上游版本仍需实际检查。

## English release notes

1.1.0 targets Jurassic Saga 0.3 / Travelers 1.0 and restores upstream breeding in full. Despawn protection is now OFF, CAPTURED_ONLY (default), or ALL. Old booleans are not migrated; valid biome/debug choices remain.

Fixes captured growth and chronological supply settlement, starvation duration, occupied normal item-box expiry and lethal dart hits on weak animals. Goggles use server snapshots and omit only matching information actually visible in Jade, with an optional full display. Retains four future pending automatic injections, ordinary egg collection, physics transport and the risks of forced-nearby release.

Also fixes frozen box observations after a dropped request and Travelers 1.0 path workers preventing a saved server from exiting, without changing pathfinding.

Valid legacy pending eggs complete once; unreadable data is retained. Old occupied boxes prove capture history; animals released before upgrading do not. Select the new ALL mode or recapture old enclosure animals if needed. Protocol 8 requires both sides to update. Keep public 1.0.0 for older dependency pairs; future upstream versions remain subject to verification.

## 制品与验收记录

- 本地制品与以下验收记录已就绪。GitHub 源码同步不代表已创建 GitHub / Modrinth Release，在线制品发布和自动安装验收仍需另行完成。
- 最终 JAR：`jsmore-1.1.0.jar`；SHA-256：`d9efb350fe9e3f26d5a5d6222de6b779061b990a8337896389fb2ebbbafaa763`。
- 2026-10-27，Java 21 / 当前 Wrapper：七组新基线矩阵通过，包括最小、普通、两组航空学、两组共存环境和 NeoForge 21.1.250 最小环境，其余使用 21.1.232。每组发现 484 项 JUnit 测试，失败/错误为 0；非共存环境各有 1 项按环境条件跳过，共存环境无跳过。每组 162 项 GameTest 通过；构建、POM 和制品审计通过。
- 同一最终 JAR 在七组真实独服中完成启动、生成动物并触发寻路、保存及正常退出；公开 1.0.0 的两个旧组合另行启动退出回归通过。旧布尔值 true/false、COMMON 值保留及新 ALL/OFF 两次重启，共六次配置实机运行通过。
- 最终候选 JFR 连续记录 65 分 44 秒，服务器正常退出。平均 tick 2.76 ms，99 分位 27.47 ms；有五次短时落后警告，不能描述为无卡顿。首次约 27 分钟无客户端并与其他验证并行，后续客户端实际完成运输与显示验收。小型隔离世界的结果不代表大型整合包容量。
- 实机确认捕获箱掉落物保存重载、容器储存与区块卸载结算、重新放置和释放；原动物 UUID、基因数据、年龄身份和捕获历史保留，最终只有一个动物实体且没有遗留动物载体。确认 Jade 显示/隐藏、完整眼镜模式、箱体信息持续刷新、水生浮水及苏醒游动、飞行动物麻醉落地。低血量重复中镖在同轮前候选实机确认，最终候选仍有对应 GameTest。
- 尚未逐物种进行完整繁殖周期的客户端观察，也未完成人工操纵大型航空学载具、多人长时间压力或其他操作系统的视觉验收；这些不能由本轮服务器回归代替。飞行动物醒后飞行状态已由服务器确认，未单独录下恢复飞行动画。
- 保留既有 Alpha、1.0.0 Release 及其制品，不替换旧文件。
- 人工发布后仍需验证 Modrinth 自动安装取得正确平台的依赖；本地校验不替代这一项。

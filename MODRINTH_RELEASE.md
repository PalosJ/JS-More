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

复审进一步修复高延迟下动物眼镜信息持续空白、迟到麻醉重新延长、乱序待生效剂量结算、损坏新繁殖记录遮蔽合法旧记录，以及手持箱释放未扣结算补给的问题。

追加修复捕获临时加载使未长满的成年动物提前长满，以及 Jade 去重移除基因图标后眼镜整面板消失的问题。

旧合法待产蛋只兑现一次，无法解析的数据继续保全；旧箱可证明捕获历史，升级前已经释放的动物无法追溯。需要保护旧围栏时选择新 ALL 模式，或成功重捕一次。协议更新为 8，客户端和服务器一起升级。旧依赖请继续使用 1.0.0，未来上游版本仍需实际检查。

## English release notes

1.1.0 targets Jurassic Saga 0.3 / Travelers 1.0 and restores upstream breeding in full. Despawn protection is now OFF, CAPTURED_ONLY (default), or ALL. Old booleans are not migrated; valid biome/debug choices remain.

Fixes captured growth and chronological supply settlement, starvation duration, occupied normal item-box expiry and lethal dart hits on weak animals. Goggles use server snapshots and omit only matching information actually visible in Jade, with an optional full display. Retains four future pending automatic injections, ordinary egg collection, physics transport and the risks of forced-nearby release.

Also fixes frozen box observations after a dropped request and Travelers 1.0 path workers preventing a saved server from exiting, without changing pathfinding.

The follow-up review also fixes blank goggles under high latency, overdue doses restarting, out-of-order restored dose settlement, invalid new breeding data hiding valid legacy data, and manual box release retaining consumed reserves.

Also fixes partial adult growth being completed by temporary capture loads, and the remaining goggles panel disappearing after Jade deduplication removes gene icons.

Valid legacy pending eggs complete once; unreadable data is retained. Old occupied boxes prove capture history; animals released before upgrading do not. Select the new ALL mode or recapture old enclosure animals if needed. Protocol 8 requires both sides to update. Keep public 1.0.0 for older dependency pairs; future upstream versions remain subject to verification.

## 制品与验收记录

- 本地制品与以下验收记录已就绪。GitHub 源码同步不代表已创建 GitHub / Modrinth Release，在线制品发布和自动安装验收仍需另行完成。
- 最终 JAR：`jsmore-1.1.0.jar`；SHA-256：`addf6d424a969ffff05b6c228f4a12474646b7214f2c84491d5f736d4681d1ac`。
- 2026-10-28，Java 21 / 当前 Wrapper：七组新基线矩阵通过，包括最小、普通、两组航空学、两组共存环境和 NeoForge 21.1.250 最小环境，其余使用 21.1.232。每组发现 490 项 JUnit 测试，失败/错误为 0；非共存环境各有 1 项按环境条件跳过，共存环境无跳过。每组 167 项必需 GameTest 通过；构建、POM 和制品审计通过。
- 同一最终 JAR 在七组真实独服中完成启动、生成动物并触发寻路、保存及正常退出；未修改的公开 1.0.0 在两个旧组合上的启动退出回归通过。前轮六次配置实机记录保留为基线，本轮三档策略仍由最终 GameTest 覆盖。
- 修复前候选（`bf8d7cb`）JFR 连续记录 60 分 48 秒，服务器正常退出。平均 tick 样本 8.97 ms，99 分位 76.45 ms；完整进程日志有 16 次落后警告，不能描述为无卡顿。GC collection duration 合计约 54.67 秒，不等于停顿时间；复核实际停顿为 16.85 秒，其中 6 次堆检查暂停合计 7.93 秒，与默认 JFR 的 ObjectCount 分块统计有关；服务端 RSS 采样峰值约 574 MiB。首次约 17 分钟无客户端并与其他验证并行，之后有客户端重连；这些全模组汇总数据不代表 JS More 单独开销或大型整合包容量。
- 前轮候选实机完成生存装填/射击消耗、0.4 HP 重复中镖、清醒捕获失败、真实补给、占用掉落物超过五分钟、拾取及下界释放。原动物 UUID、两项基因、年龄标记和捕获历史保留，释放后只有一个动物且空箱只保留剩余补给。实际区块卸载后已过期的待生效药剂未重新启动。
- Jade 显示/隐藏回退及经过核实的 1.25 秒服务端至客户端延迟下眼镜刷新通过。观察水生浮水、苏醒下潜、飞行动物麻醉落地；醒后飞行由服务端状态确认，未单独录下恢复飞行动画。本轮另外完成 Curios 槽实际佩戴、完整眼镜选项、Jade 显示/隐藏和默认去重的视觉复测；已确认并修复去重后整个面板消失的问题。
- 鸵鸟在未强制求偶/孵化计时的情况下产生两个新 UUID 后代，普通蛋收集至 20 个并通过实际界面取出。未拍到破壳瞬间，不能代表每个物种的完整繁殖周期。
- 最终 JAR 在 NeoForge 21.1.250 最小客户端完成头部眼镜、射击、捕获、拆箱、拾取和释放；旧物理组合完成 90° 旋转，当前物理完整共存组合完成 45° 旋转，均实际补给、拆箱、拾取并释放，保留唯一 UUID、基因和捕获历史。平台组装/位移/旋转由命令准备，玩家交互真实执行；不是人工驾驶。
- 音频通道耗尽已定位：官方 Jurassic Saga 0.3 文件 nti1NZVa 的鸵鸟 idle02/03/04 解码失败，原版异步加载失败后未回收预分配通道。原场景 247 个滞留静态通道均为鸵鸟叫声，无 JS More 的对照实例也复现耗尽。aggressive03 另有截断；未修改上游 JAR 或把它列为附属模组已修复项。详见兼容说明。
- 两组物理实机均发现上游 Jade 围栏检测混用世界/子空间坐标，触发 Sable 超大 AABB 拒绝并持续打印堆栈。无 JS More、只含普通方块的当前组合对照也复现；固定视角关闭 Jade 显示后停止，开启后恢复。临时关闭 Jade 显示可规避此触发，眼镜仍可使用；1.1.0 未修补这个上游问题，也不将共存实机标为完全无错误。
- 本轮实际观察命令准备的实体蛋破壳，集蛋器只收普通蛋；对孵出的幼体设置接近成年进度后，实际麻醉、捕获、补给、拆箱、拾取及释放。连续保存的成长值与上游每刻增长量一致，未提前跳到最大值。这不是默认倍率下从出生等待至成年的完整周期。
- 最终 JAR 共存环境额外录制 3 分钟低干扰 JFR（关闭 Jade 显示及堆对象计数）：177 个 tick 统计样本均值 4.03 ms、p99 22.94 ms、最大 118.11 ms；3 次 G1 暂停合计 0.661 秒、最长 0.470 秒，无 Full GC。它不能替代一小时压力测试，也不能据此声称无短时卡顿。
- 尚未完成逐物种的自然繁殖至成长全周期、完整人工驾驶与物理组装器拆解、多人并发/长时间压力、旧物理完整共存客户端和其他操作系统视觉验收。当前控制方式未完成组装器按住拖动的拆解；自动化回归不代替这些项目。最终 JAR 未重新跑满一小时，前轮的一小时证据保留原制品归属。
- 保留既有 Alpha、1.0.0 Release 及其制品，不替换旧文件。
- 人工发布后仍需验证 Modrinth 自动安装取得正确平台的依赖；本地校验不替代这一项。

# JS More

**简体中文** | [English](README.en.md)

JS More 是 **Minecraft 1.21.1 NeoForge** 的 Jurassic Saga 附属模组，为恐龙饲养补上麻醉、运输、观察和普通蛋自动收集。

源码版本：**1.1.0**。下载请认准 [Modrinth](https://modrinth.com/mod/jurassic-saga-more)。本地源码版本不代表已在线发布，实际发布状态以该页面为准。

## 安装

客户端和服务器均需要 Java 21、NeoForge，以及以下三个模组的 **NeoForge 1.21.1 文件**：

| 组合 | JS More | Jurassic Saga | Travelers Lib |
| --- | --- | --- | --- |
| 新版基线 | 1.1.0 | [0.3](https://modrinth.com/mod/jurassic-saga/version/nti1NZVa) | [1.0](https://modrinth.com/mod/travelers-lib/version/I9ua8Njp) |
| 旧版正式制品 | 1.0.0 | [0.2.3](https://modrinth.com/mod/jurassic-saga/version/EzDlTUHi) | [0.8.2.2](https://modrinth.com/mod/travelers-lib/version/Aadzy3jP) |
| 旧版兼容 | 1.0.0 | [0.2.1](https://modrinth.com/mod/jurassic-saga/version/YdbXbcFo) | [0.7.2](https://modrinth.com/mod/travelers-lib/version/vKc8KD7H) |

NeoForge 构建基线为 `21.1.232`，验收矩阵另包含 `21.1.250`。Travelers `0.7.1` 保留旧版独服回归，不要与 Jurassic Saga `0.2.3` 混用。不同平台可能共用版本号，下载时还要核对 Minecraft 和加载器。

Curios、Jade、TerraBlender 与航空学组件均为可选项，没有它们也能使用核心功能。

## 从麻醉到运输

1. 用虞美人与粗制药水酿造麻醉药水，再结合主模组的空注射器和羽毛制作麻醉镖。
2. 长按右键为麻醉弩装填，最多六发；左键射击。剂量会延迟生效，可以叠加。
3. 恐龙被充分麻醉后，用捕获箱收容，再将它运到新围栏。
4. 运输过程中维持麻醉、水和对应食物的补给，留意箱体耐久。恐龙博士眼镜可以查看动物与箱体状态。
5. 尽量在宽敞、合适的地形释放。运输失败时会强制就近释放，危险地形仍可能让恐龙受伤或死亡。

捕获箱占地 2×4 格、高 2 格，最大耐久 500。破损箱不可修复或重新收容恐龙。若物品提示数据无法读取，请保留它，勿当成空箱使用。

## 圈养与繁殖

默认仅保护**成功被恐龙捕获箱捕获过**的动物，释放、重捕和重载后仍保留捕获历史。失败的捕获不标记原动物，后代不会继承此标记。所有模式都保留主模组自己的持久化规则，也不会免除伤害。

繁殖完整沿用 Jurassic Saga：恢复上游自然求偶、繁殖条件、遗传与出生，不再增加玩家喂食门槛或新的单母系受精蛋。旧存档已有的合法待产蛋在成功生成后兑现一次；无法解析或生成失败的数据继续保留。

防自然清理不是数量上限。被保护的动物、上游原生持久动物及正常繁殖仍可能增加围栏数量，需要持续管理食物、空间和数量。

## 集蛋器与观察

集蛋器会收取半径 16 格内、落地约 5–5.5 秒的基础蛋，包括鸡蛋及主模组的鳄鱼、鸵鸟、青蛙、蛇怪、鱼和蜘蛛蛋。它不收取正在孵化的实体蛋，也不主动加载区块。右键打开 18 格库存，可接漏斗；库存不足时，装不下的蛋会留在地上。

恐龙博士眼镜通过限频的服务器目标快照查看生命、性别、饱食、口渴、心情、成长、估算年龄、麻醉和产蛋进度，观察捕获箱时还会显示补给与耐久。可佩戴在原版头部栏，安装 Curios 后支持相应槽位。默认只省略 Jade 同一帧确实显示的同类信息；可启用完整独立显示。

成年前，显示年龄随成长进度变化；成年后每经过一个游戏日的服务器运行时间增加一天。睡觉或 `/time` 跳过白天不会让年龄突然增加。尚未迁移的旧成年恐龙只校正一次，已迁移个体不会再次重置。

## 配置与升级

| 文件与配置项 | 默认值 | 用途 |
| --- | --- | --- |
| COMMON `disable_jurassicsaga_biome_generation` | `false` | 按需关闭主模组群系生成，重启生效，已有区块不变 |
| COMMON `debug_logging` | `false` | 调试日志 |
| SERVER `animal_despawn_protection` | `CAPTURED_ONLY` | `OFF` 遵循上游；`CAPTURED_ONLY` 仅保护已捕获个体；`ALL` 另保护陆生、水生类别 |
| CLIENT `show_full_goggles_information` | `false` | 开启后眼镜独立显示全部信息 |

配置文件分别为 `jsmore-common.toml`、`jsmore-server.toml`、`jsmore-client.toml`。SERVER 设置由服务器决定并同步，世界的 `serverconfig/jsmore-server.toml` 可覆盖全局设置；修改防清理模式后重启世界或服务器。

升级不转换旧防清理布尔值，删除旧玩家喂食繁殖开关与群系重置流程。仍有效的群系、调试选项保留；缺少新的防清理项时使用 `CAPTURED_ONLY`。

旧捕获箱中的动物可据此补记捕获历史。旧版**已经释放**的动物没有可靠历史，不猜测补标。要保护旧围栏，可先选新 `ALL` 模式，或保留旧捕获箱至升级后释放；旧范围之外的个体应成功重捕一次。

防自然清理不限制繁殖或动物总数，仍需管理围栏内的数量；`CAPTURED_ONLY` 不会让所有野生动物都永久保留。

未加载期间，捕获箱按服务器经过的游戏时间结算药效、补给、饥渴与耐久，不主动加载区块，也不计停服时间。自动补针保留最多四针待生效规则。重新加载动物不会重新开始已过期的药效；手持箱释放也会扣除时间结算消耗的补给。装有动物的普通捕获箱掉落物不会被物品寿命计时清理，但仍消耗补给和耐久，环境伤害规则不变。麻醉镖单次命中最多造成 1 点伤害且不直接杀死目标；其他伤害仍可能致死。

1.1.0 使用网络协议 `8`，客户端和服务端须一起升级。1.0.0 继续用于旧依赖组合；新版不能装在 Jurassic Saga 0.2.x 上。未来主模组版本仍须检查实际接口与行为。

## 可选兼容与反馈

- Curios `9.5.1`：眼镜槽位；Jade `15.10.6`：信息显示；TerraBlender `4.1.0.8`：群系集成。
- 捕获箱物理搬运支持 Create `6.0.10`，搭配 Aeronautics `1.3.0` / Sable `2.0.3`，或 Aeronautics `1.3.2` / Sable `2.0.5`。请整套安装对应版本。未知或不完整组合会禁用搬运集成。
- 未列出的新版本不代表已验证兼容。问题请提交到 [GitHub Issues](https://github.com/PalosJ/JS-More/issues)，附上版本组合、复现步骤和相关日志。

## 开发

使用 Java 21 和仓库 Wrapper。macOS/Linux：

```sh
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=minimal
```

Windows PowerShell：

```powershell
.\gradlew.bat test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=minimal
```

完整矩阵、独服检查和制品审计见 [开发说明](docs/DEVELOPMENT.md)，设计边界见 [架构](docs/ARCHITECTURE.md) 与 [兼容性](docs/COMPATIBILITY.md)。更新记录见 [CHANGELOG](CHANGELOG.md)，发布材料见 [Modrinth 介绍](MODRINTH_DESCRIPTION.md) 与 [发布清单](MODRINTH_RELEASE.md)。

## 许可证

代码、普通数据、语言及文档采用 [MIT](LICENSES/MIT.txt)。台账明确列出的原创视觉资产适用 [JS More Visual Assets License](LICENSES/LicenseRef-JSMore-Visual-Assets.txt)，第三方及上游参考内容保留各自条款。完整范围见 [LICENSE](LICENSE)、[第三方通知](THIRD_PARTY_NOTICES.md) 和 [素材来源](ASSET_PROVENANCE.md)。

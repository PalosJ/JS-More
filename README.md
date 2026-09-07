# JS More

JS More 是 **Minecraft 1.21.1 NeoForge** 的 Jurassic Saga 附属模组，为恐龙饲养补上麻醉、运输、观察、可控繁殖和自动集蛋。

源码版本：**1.0.0 正式版**。下载请认准 [Modrinth](https://modrinth.com/mod/jurassic-saga-more)。公开的 `1.0.0 Alpha` 与本次正式版分开记录，实际发布状态以该页面为准。

## 安装

客户端和服务器均需要 Java 21、NeoForge，以及以下三个模组的 **NeoForge 1.21.1 文件**：

| 组合 | JS More | Jurassic Saga | Travelers Lib |
| --- | --- | --- | --- |
| 当前版本 | 1.0.0 | [0.2.3](https://modrinth.com/mod/jurassic-saga/version/EzDlTUHi) | [0.8.2.2](https://modrinth.com/mod/travelers-lib/version/Aadzy3jP) |
| 旧版兼容 | 1.0.0 | [0.2.1](https://modrinth.com/mod/jurassic-saga/version/YdbXbcFo) | [0.7.2](https://modrinth.com/mod/travelers-lib/version/vKc8KD7H) |

NeoForge 最低验证基线为 `21.1.232`，另覆盖 `21.1.250`。Travelers `0.7.1` 保留旧版独服回归，不要与 Jurassic Saga `0.2.3` 混用。不同平台可能共用版本号，下载时还要核对 Minecraft 和加载器。

Curios、Jade、TerraBlender 与航空学组件均为可选项，没有它们也能使用核心功能。

## 从麻醉到运输

1. 用虞美人与粗制药水酿造麻醉药水，再结合主模组的空注射器和羽毛制作麻醉镖。
2. 长按右键为麻醉弩装填，最多六发；左键射击。剂量会延迟生效，可以叠加。
3. 恐龙被充分麻醉后，用捕获箱收容，再将它运到新围栏。
4. 运输过程中维持麻醉、水和对应食物的补给，留意箱体耐久。恐龙博士眼镜可以查看动物与箱体状态。
5. 尽量在宽敞、合适的地形释放。运输失败时会强制就近释放，危险地形仍可能让恐龙受伤或死亡。

捕获箱占地 2×4 格、高 2 格，最大耐久 500。破损箱不可修复或重新收容恐龙。若物品提示数据无法读取，请保留它，勿当成空箱使用。

## 圈养与繁殖

默认情况下，主模组陆生、水生动物不会因为玩家走远或长时间没有互动而自然消失。它们仍然会受到伤害；本功能保留主模组已有的持久化规则。

普通繁殖需要同种、异性、成年、可育且冷却结束的双方，分别被玩家喂食各自接受的食物。求偶、遗传与出生过程沿用 Jurassic Saga。

鸵鸟、鳄鱼、芦苇蛙和蛇怪按自己的周期产蛋。喂食符合条件的成年雌性后，下一次产蛋会变为带有母系基因的实体蛋。鸵鸟吃种子，鳄鱼吃鱼，芦苇蛙和蛇怪吃主模组的蚊子。成功产下后，需要再次喂食才能让下个周期继续产受精蛋。待产状态可随存档、区块重载和捕获箱运输保留。

两项规则都能由服务器单独关闭，详见配置。

## 集蛋器与观察

集蛋器会收取半径 16 格内、落地约 5–5.5 秒的基础蛋，包括鸡蛋及主模组的鳄鱼、鸵鸟、青蛙、蛇怪、鱼和蜘蛛蛋。它不收取正在孵化的实体蛋，也不主动加载区块。右键打开 18 格库存，可接漏斗；库存不足时，装不下的蛋会留在地上。

恐龙博士眼镜可查看生命、性别、饱食、口渴、心情、成长、年龄、麻醉和产蛋进度，观察捕获箱时还会显示补给与耐久。可佩戴在原版头部栏，安装 Curios 后支持相应槽位。

成年前，显示年龄随成长进度变化；成年后每经过一个游戏日的服务器运行时间增加一天。睡觉或 `/time` 跳过白天不会让年龄突然增加。尚未迁移的旧成年恐龙只校正一次，已迁移个体不会再次重置。

## 配置

| 文件与配置项 | 默认值 | 用途 |
| --- | --- | --- |
| `config/jsmore-common.toml` → `disable_jurassicsaga_biome_generation` | `false` | 按需关闭主模组群系生成 |
| `jsmore-server.toml` → `prevent_jurassicsaga_animal_despawn` | `true` | 防止陆生、水生动物因距离或闲置自然消失 |
| `jsmore-server.toml` → `enable_player_fed_breeding` | `true` | 玩家喂食繁殖与周期受精蛋规则 |

**默认保留 Jurassic Saga 的群系生成。** 如果它与整合包中的其他群系模组发生冲突，可以把群系禁用选项改成 `true`。这是一个简单直接的备用解决办法，需要重启游戏或服务器；已有区块不会因此重新生成。

升级正式版时，旧格式的群系禁用设置会重置为 `false` 一次。原配置备份在同目录的 `.pre-schema-1.bak` 文件中，格式标记 `config_schema_version` 随后变为 `1`。此后自行开启的 `true` 会保留，请勿手动删除格式标记。

SERVER 配置通常位于 `config/`，世界的 `serverconfig/jsmore-server.toml` 可以覆盖它。两个玩法开关由服务器决定并同步到客户端，修改后重启世界或服务器。关闭繁殖开关会恢复主模组规则，已有待产数据保留，重新开启后校验使用。

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

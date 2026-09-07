# Modrinth 发布文案

以下中英文各提供短简介和正文。正式版更新说明、依赖和发布字段见 [发布清单](MODRINTH_RELEASE.md)。

## 中文短简介

为 Jurassic Saga 添加麻醉运输、恐龙观察、可控繁殖和自动集蛋，让捕获、迁移与日常饲养更方便。

## 中文正文

# JS More

发现了想带回家的恐龙，却不知道怎么把它运回围栏？JS More 为 Jurassic Saga 补充了麻醉和运输工具，也加入了观察眼镜、可控繁殖与集蛋器，方便照顾已经养起来的动物。

### 先麻醉，再搬家

酿造麻醉药水，制作麻醉镖，为最多容纳六发的麻醉弩装填。长按右键装填、左键射击。药效会逐渐生效，足够的剂量能让恐龙进入麻醉状态，再用捕获箱将它收容。

运送期间要维持麻醉、水和食物补给，也要留意箱体耐久。带到新地点后，找一块适合它活动的空地释放。

**运输失败有代价。** 箱体破损或运输状态无法继续时，恐龙会被强制就近释放。岩浆、悬崖和狭窄空间仍可能让它受伤或死亡，破损箱也不能修复后再次使用。

### 看清状态，安排繁殖

恐龙博士眼镜可以显示生命、年龄、成长、性别、饱食、口渴、心情、麻醉和产蛋进度。看向捕获箱时，还能检查补给与耐久。眼镜可以放在原版头部栏，安装 Curios 后支持相应槽位。

默认关闭随机求偶。繁殖普通动物时，需要给同种、异性、成年、可育且冷却结束的双方分别喂食合适的食物。之后的配对、遗传和出生仍由 Jurassic Saga 处理。

鸵鸟、鳄鱼、芦苇蛙和蛇怪按自己的周期产蛋。给符合条件的成年雌性喂食后，下一次产蛋会变为带有母系基因的实体蛋：鸵鸟吃种子，鳄鱼吃鱼，芦苇蛙和蛇怪吃主模组的蚊子。每次成功产下后都需要重新喂食。有效待产状态会在保存、重载和捕获箱运输后保留。

主模组的陆生、水生动物默认也不会因为玩家离开或长时间没有互动而自然消失。繁殖和防自然消失可以由服务器分别关闭。

### 让集蛋器收拾围栏

集蛋器会收取半径 16 格内、落地约五秒的基础蛋，包括鸡蛋及主模组的鳄鱼、鸵鸟、青蛙、蛇怪、鱼和蜘蛛蛋。正在孵化的实体蛋会留在原处。

右键打开 18 格库存，也可以连接漏斗。装不下时，剩下的蛋会留在地上。

### 配置与升级

**默认保留 Jurassic Saga 的群系生成。** 如果它与整合包中的其他群系模组冲突，可以在 `config/jsmore-common.toml` 中把 `disable_jurassicsaga_biome_generation` 改为 `true`。这是额外提供的简单直接的备用方案，默认不开启。修改后需要重启游戏或服务器，已有区块不会重新生成。

升级正式版时，旧的群系禁用设置会重置一次，并在同目录留下原配置备份。此后可以按需开启，重启会保留你的选择。请保留自动生成的 `config_schema_version`。

服务器还可以在 `jsmore-server.toml` 中调整两个默认开启的选项：

- `prevent_jurassicsaga_animal_despawn`：防止陆生、水生动物自然消失。
- `enable_player_fed_breeding`：玩家喂食繁殖规则。关闭后恢复主模组行为，已有待产数据保留到重新开启后使用。

这两个选项由服务器同步给客户端，修改后重启世界或服务器。文件通常位于 `config/`，单个世界可以在自己的 `serverconfig/` 中覆盖。

成年后的显示年龄也已修正：睡觉和时间指令不会让恐龙突然长大很多岁。尚未迁移的旧成年个体只校正一次，已经迁移的不会再次重置。

### 安装与兼容

需要 **Minecraft 1.21.1、NeoForge 和 Java 21**。客户端和服务器都要安装 JS More、Jurassic Saga 与 Travelers Lib。

- 当前组合：Jurassic Saga **0.2.3** + Travelers Lib **0.8.2.2**。
- 旧版组合：Jurassic Saga **0.2.1** + Travelers Lib **0.7.2**。
- Curios、Jade、TerraBlender 为可选项。
- 捕获箱物理搬运支持 Create **6.0.10**，搭配 Aeronautics **1.3.0** / Sable **2.0.3**，或 Aeronautics **1.3.2** / Sable **2.0.5**。未知组合会禁用该集成。

下载时请核对 Minecraft 和加载器，不要只看模组版本号。遇到问题可在 [GitHub Issues](https://github.com/PalosJ/JS-More/issues) 反馈。

代码、普通数据与文档采用 MIT；项目原创视觉资产有独立授权范围，上游及第三方内容保留各自条款，详见 [许可证说明](https://github.com/PalosJ/JS-More/blob/main/LICENSE)。

---

## English short description

Anesthesia, dinosaur transport, observation goggles, controlled breeding, and automatic egg collection for Jurassic Saga.

## English description

# JS More

Found a dinosaur you want to bring home? JS More adds tools for getting it into an enclosure and looking after it once it arrives: anesthetic darts, transport boxes, observation goggles, controlled breeding, and an Egg Collector.

### Sedate it, then move it

Brew anesthetic supplies, craft darts, and load up to six shots into the anesthetic crossbow. Hold right-click to load and left-click to fire. The dose takes time to work and can build up over several shots. Once the dinosaur is sufficiently sedated, use a capture box to contain it.

Keep the box supplied with anesthetic, water, and the right food, and watch its durability. At your destination, choose an open area suited to the animal before releasing it.

**Transport failure has a cost.** If the box breaks or can no longer keep the animal contained, the dinosaur is forcibly released nearby. Lava, cliffs, and cramped spaces can still injure or kill it. Broken boxes cannot be repaired and reused.

### Check on your animals and plan their breeding

Dino Doctor goggles show health, age, growth, sex, hunger, thirst, mood, anesthesia, and egg-laying progress. Look at a capture box to check supplies and durability. The goggles work in the normal head slot, with optional Curios slot support.

Random mate searching is disabled by default. For ordinary breeding, feed both eligible parents: they must be the same species, opposite sexes, adult, fertile, and off cooldown. Jurassic Saga still handles pairing, genetics, and birth.

Ostriches, alligators, reed frogs, and basilisks keep their own egg-laying cycles. Feed an eligible adult female to make her next lay produce a hatchable entity egg carrying her genes. Ostriches take seeds, alligators take fish, and reed frogs and basilisks take Jurassic Saga mosquitoes. Feed her again after each successful fertile lay. Saving, reloading, and capture-box transport preserve a valid pending egg.

Jurassic Saga land and water creatures are also protected from distance- and inactivity-based despawning by default. Servers can turn breeding changes and despawn protection off separately.

### Collect eggs around the enclosure

The Egg Collector gathers base egg items within 16 blocks after they have been on the ground for about five seconds. It collects chicken eggs and Jurassic Saga alligator, ostrich, frog, basilisk, fish, and spider eggs. Hatchable entity eggs stay where they are.

Right-click to open its 18 storage slots, or connect a hopper. Eggs that do not fit remain on the ground.

### Configuration and upgrading

**Jurassic Saga biome generation stays enabled by default.** If it conflicts with other biome mods in your pack, set `disable_jurassicsaga_biome_generation` to `true` in `config/jsmore-common.toml`. This is an optional, simple fallback for biome conflicts. Restart the game or server after changing it; existing chunks are not regenerated.

When upgrading to this stable release, the old biome-disable setting resets once, with a backup kept beside the original configuration. You can then enable it if needed, and later restarts will keep your choice. Leave the generated `config_schema_version` marker in place.

Two options in `jsmore-server.toml` are enabled by default:

- `prevent_jurassicsaga_animal_despawn`: protects land and water creatures from natural despawning.
- `enable_player_fed_breeding`: enables the feeding-based breeding rules. Turning it off restores upstream behavior and keeps pending egg data for when it is enabled again.

Server rules are synchronized to clients. Restart the world or server to apply changes. The file normally lives in `config/`; a world-specific file in `serverconfig/` can override it.

Adult age display has also been corrected, so sleeping or changing daylight time does not suddenly add years. Old adult dinosaurs that have not received the correction are adjusted once; previously migrated animals are not reset again.

### Requirements and compatibility

Use **Minecraft 1.21.1, NeoForge, and Java 21**. Install JS More, Jurassic Saga, and Travelers Lib on both the client and server.

- Current pair: Jurassic Saga **0.2.3** + Travelers Lib **0.8.2.2**.
- Legacy pair: Jurassic Saga **0.2.1** + Travelers Lib **0.7.2**.
- Curios, Jade, and TerraBlender are optional.
- Moving capture boxes with physics requires Create **6.0.10**, with either Aeronautics **1.3.0** / Sable **2.0.3**, or Aeronautics **1.3.2** / Sable **2.0.5**. Unknown combinations disable this integration.

Check the Minecraft version and loader on each download, as different files may share a mod version number. Report problems through [GitHub Issues](https://github.com/PalosJ/JS-More/issues).

Code, ordinary data, and documentation use MIT. Original visual assets have a separate license scope, and upstream and third-party material retain their own terms. See the [license notice](https://github.com/PalosJ/JS-More/blob/main/LICENSE).

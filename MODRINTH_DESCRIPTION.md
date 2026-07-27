# JS More — Modrinth Copy

## 中文

### 短简介

为 Jurassic Saga 补全麻醉、运输、可控繁殖与自动集蛋玩法的 NeoForge 生存附属模组。

### 完整介绍

**JS More 让“活体恐龙运输”成为一套自然融入 Jurassic Saga 的生存流程。**

你不再需要用临时手段搬运大型动物：先酿造麻醉药水，制作麻醉针和麻醉镖，用六发麻醉弩控制目标；等待麻醉稳定后，将恐龙连同原 UUID、成长状态和必要数据装入运输箱。运输途中补充麻醉、水和食物，抵达目的地后再选择安全位置释放。

JS More 也让圈养与繁殖变得可控：Jurassic Saga 的 `CREATURE` 与 `WATER_CREATURE` 不会因距离或空闲被清理，但其上游已有的 `isPersistenceRequired` 标记保持不变。随机自动求偶被阻断；非周期动物必须分别喂食同种异性、成年、可育且零冷却的双亲，后续配偶、基因和出生仍由 Jurassic Saga 处理。

四种周期物种改用一次性雌性武装：鸵鸟吃种子、鳄鱼吃鱼类、芦苇蛙与 Basilisk（蛇怪）吃蚊子。喂食一只合格雌性后，下一个真实周期恰好生成一个母系基因实体蛋；成功后恢复普通物品蛋，失败则掉落原物品蛋并保留待产状态。待产状态会随存档、卸载重载和捕获箱往返保留。

养殖区还可以放置集蛋器：`16×8×16` 下仓承托横向 `19×19` 视觉外溢的草巢，总高为 `13.25`，物品栏 GUI 缩放为 `0.59`。选中时使用完整方块选择框，实际碰撞则精细限制在单格内。它只收集标签内的蛋掉落物（`ItemEntity`），等待约五秒后收入可供玩家和漏斗使用的两排 18 格库存。

**本地候选升级提示：** 若你使用过更早的 `1.0.1` 本地候选，请先在旧候选中手动清空集蛋器第 19–27 格。本次不迁移旧第三排，这些高位槽物品不会进入新的 18 格库存。

#### 主要特色

- **完整生存闭环**：麻醉药水、麻醉针、麻醉镖、六发麻醉弩、恐龙捕获箱和破损箱共同组成可合成、可消耗、会失败的运输玩法。
- **真实麻醉表现**：麻醉会影响服务端 AI、睡眠、飞行和水中运动；大型陆生、水生与飞行动物使用适合其体型和环境的通用处理。
- **可靠的数据运输**：捕获箱保留恐龙原 UUID 和关键 NBT，并在物品、方块、掉落物、存盘重载与物理化搬运之间维护唯一权威，避免复制或静默丢失。
- **运输管理**：箱体有耐久以及麻醉、水、肉食和草食补给。长期运输需要准备，箱体损毁会产生明确代价。
- **可控圈养与繁殖**：阻断距离/空闲清理和随机求偶，由玩家喂食决定普通双亲配对或四种周期物种的下一枚母系基因蛋。
- **恐龙博士 HUD**：佩戴眼镜即可查看生命、年龄、成长、性别、饱食、口渴、心情、麻醉、基因、下蛋进度以及捕获箱状态。
- **自然融入养殖区的集蛋器**：`16×8×16` 下仓承托 `19×19` 视觉外溢草巢，以两排 18 格库存只收集指定蛋掉落物，不会吸走周围的普通物品。
- **兼容但不强绑**：可选支持 Curios、Jade、TerraBlender 和经过验证的航空学/Sable 物理结构；不安装这些模组时，核心麻醉与运输玩法仍可使用。

JS More 不替代 Jurassic Saga，也不新增另一套恐龙生态。它专注补上原有生存体验中缺少的“控制—捕获—运输—释放”环节。

### 依赖

**必需：**

- Minecraft 1.21.1
- NeoForge
- Jurassic Saga
- Travelers Lib（Jurassic Saga 的前置链之一）

**可选兼容：**

- Curios
- Jade
- TerraBlender
- 经过项目兼容门禁验证的 Sable / Create: Aeronautics 组合

具体支持边界以项目的兼容性文档和对应版本说明为准。

---

## English

### Short summary

A NeoForge survival add-on for Jurassic Saga with anesthesia, transport, controlled breeding, and egg collection.

### Full description

**JS More turns live dinosaur transport into a survival system that fits naturally into Jurassic Saga.**

Brew anesthetic supplies, craft syringes and darts, and use a six-shot anesthetic crossbow to control a target. Once sedation is stable, capture the dinosaur while preserving its original UUID, growth state, and essential data. Keep anesthesia, water, and food stocked during the journey, then release the animal at a safe destination.

JS More also makes husbandry deliberate. Jurassic Saga entities in the `CREATURE` and `WATER_CREATURE` categories no longer undergo distance or idle cleanup, while their existing `isPersistenceRequired` flags remain unchanged. Random mate search is suppressed: non-periodic animals require both same-species, opposite-sex, adult, fertile, zero-cooldown parents to be fed separately, while Jurassic Saga still owns mate checks, genetics, and birth.

Periodic egg species use one-shot female arming: ostriches accept seeds, alligators fish, and reed frogs and basilisks mosquitoes. The next real cycle creates exactly one maternal-gene `EggEntity`; success returns later unarmed cycles to item eggs, while failure drops the original item egg and keeps `PENDING`. Pending state survives entity saves across unload/reload and capture-box round trips.

The Egg Collector uses a `16x8x16` lower bin beneath a visually overhanging `19x19` grass nest, for a total height of `13.25`. Its item GUI scale is `0.59`; selection uses a full-block outline, while detailed collision stays within the single placed cell. It collects only tagged egg `ItemEntity` drops after roughly five seconds and provides a two-row, 18-slot inventory for players and hoppers.

**Local candidate upgrade notice:** If you used an earlier local `1.0.1` candidate, manually empty Egg Collector slots 19–27 in that candidate before upgrading. The old third row is intentionally not migrated, so items in those high slots will not enter the new 18-slot inventory.

#### Highlights

- **A complete survival loop:** anesthetic potion, syringe, dart, six-shot crossbow, capture box, and broken box form one craftable, consumable system with real failure costs.
- **Real anesthesia behavior:** sedation affects server-side AI, sleep, flight, and water movement. Large terrestrial, aquatic, and flying animals use capability-based handling instead of a species whitelist.
- **Data-safe transport:** original UUID and important NBT survive item/block transfers, drops, save/reload, and supported moving structures while maintaining one canonical authority.
- **Transport management:** capture boxes have durability and separate anesthesia, water, carnivore-food, and herbivore-food supplies.
- **Controlled husbandry and breeding:** distance/idle cleanup and random mate search are blocked, while player feeding authorizes ordinary parent pairs or the next maternal-gene egg for four periodic species.
- **Dino Doctor HUD:** goggles reveal health, age, growth, sex, hunger, thirst, mood, anesthesia, genes, egg progress, and capture-box status.
- **Breeding-area egg collection:** a `16x8x16` lower bin supports a visually overhanging `19x19` grass nest, while its two-row, 18-slot inventory gathers only configured egg drops without vacuuming unrelated items.
- **Optional integrations, not hard requirements:** Curios, Jade, TerraBlender, and verified Sable/Create: Aeronautics setups add convenience without becoming necessary for the core loop.

JS More does not replace Jurassic Saga or build a separate dinosaur ecosystem. It focuses on the missing survival steps between controlling an animal and relocating it safely.

### Dependencies

**Required:**

- Minecraft 1.21.1
- NeoForge
- Jurassic Saga
- Travelers Lib (part of Jurassic Saga's prerequisite chain)

**Optional integrations:**

- Curios
- Jade
- TerraBlender
- Sable / Create: Aeronautics combinations covered by the project's compatibility gates

See the compatibility document and each release's notes for exact support boundaries.

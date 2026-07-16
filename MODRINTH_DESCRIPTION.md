# JS More — Modrinth Copy

## 中文

### 短简介

为 Jurassic Saga 补全麻醉、装箱、运输与照料闭环的 NeoForge 生存玩法附属模组。

### 完整介绍

**JS More 让“活体恐龙运输”成为一套自然融入 Jurassic Saga 的生存流程。**

你不再需要用临时手段搬运大型动物：先酿造麻醉药水，制作麻醉针和麻醉镖，用六发麻醉弩控制目标；等待麻醉稳定后，将恐龙连同原 UUID、成长状态和必要数据装入运输箱。运输途中补充麻醉、水和食物，抵达目的地后再选择安全位置释放。

#### 主要特色

- **完整生存闭环**：麻醉药水、麻醉针、麻醉镖、六发麻醉弩、恐龙捕获箱和破损箱共同组成可合成、可消耗、会失败的运输玩法。
- **真实麻醉表现**：麻醉会影响服务端 AI、睡眠、飞行和水中运动；大型陆生、水生与飞行动物使用适合其体型和环境的通用处理。
- **可靠的数据运输**：捕获箱保留恐龙原 UUID 和关键 NBT，并在物品、方块、掉落物、存盘重载与物理化搬运之间维护唯一权威，避免复制或静默丢失。
- **运输管理**：箱体有耐久以及麻醉、水、肉食和草食补给。长期运输需要准备，箱体损毁会产生明确代价。
- **恐龙博士 HUD**：佩戴眼镜即可查看生命、年龄、成长、性别、饱食、口渴、心情、麻醉、基因、下蛋进度以及捕获箱状态。
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

A NeoForge survival add-on that completes Jurassic Saga's anesthesia, capture, transport, and care loop.

### Full description

**JS More turns live dinosaur transport into a survival system that fits naturally into Jurassic Saga.**

Brew anesthetic supplies, craft syringes and darts, and use a six-shot anesthetic crossbow to control a target. Once sedation is stable, capture the dinosaur while preserving its original UUID, growth state, and essential data. Keep anesthesia, water, and food stocked during the journey, then release the animal at a safe destination.

#### Highlights

- **A complete survival loop:** anesthetic potion, syringe, dart, six-shot crossbow, capture box, and broken box form one craftable, consumable system with real failure costs.
- **Real anesthesia behavior:** sedation affects server-side AI, sleep, flight, and water movement. Large terrestrial, aquatic, and flying animals use capability-based handling instead of a species whitelist.
- **Data-safe transport:** original UUID and important NBT survive item/block transfers, drops, save/reload, and supported moving structures while maintaining one canonical authority.
- **Transport management:** capture boxes have durability and separate anesthesia, water, carnivore-food, and herbivore-food supplies.
- **Dino Doctor HUD:** goggles reveal health, age, growth, sex, hunger, thirst, mood, anesthesia, genes, egg progress, and capture-box status.
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

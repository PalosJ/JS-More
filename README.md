# JS-revise

JS-revise 是面向 Minecraft 1.21.1 NeoForge 的 Jurassic Saga 附属模组。项目以服务端权威的数据、实体状态和真实运动为基础，提供麻醉、睡眠与漂浮、年龄画像、恐龙博士眼镜 HUD、群系生成控制和恐龙捕获箱等功能。

本文档描述 `1.0.123` 候选源码。版本是否可发布仍以完整自动化、限时独立服务器和实际整合包验收记录为准；本文档不把候选版本等同于已发布或已完成实机验收。

## 1. 支持矩阵

| 项目 | 当前边界 | 说明 |
| --- | --- | --- |
| Mod ID | `jsrevise` | Java 包根为 `com.palos.jsrevise` |
| 作者 | `Palos` | 显示名称为 `JS-revise` |
| 候选版本 | `1.0.123` | `gradle.properties` 是版本单一来源，构建时展开到 metadata |
| Minecraft | `1.21.1` | metadata 固定为 `[1.21.1]` |
| NeoForge | `21.1.232` 及以上 | metadata 为 `[21.1.232,)` |
| Java | `21` | Mixin compatibility level 与编译 toolchain 均为 Java 21 |
| Jurassic Saga | 当前基线 `0.2.1` | 必需依赖范围 `[0.2.1,0.3.0)` |
| Travelers Lib | 当前二进制基线 `0.7.1` | 必需依赖范围 `[0.7.1,0.8.0)`；其他 `0.7.x` 需要取得对应二进制逐版验证 |
| Curios | 构建基线 `9.5.1` | 可选依赖，metadata 为 `[9.5.1,)`，服务端和客户端均可缺失 |
| Jade | `15.0.0` 及以上 | 可选客户端依赖，只用于 tooltip 去重 |
| 网络协议 | `6` | 当前 3 个 C2S 与 4 个 S2C payload；本版本不改变协议号、payload、codec 或附件 ID |

Jurassic Saga `0.2.1` 不依赖外部 AzureLib 模组。Travelers Lib 自身包含名称带 `azure`、`Az` 或 `AzureLib` 的内部动画实现；这些包名不能作为删除依赖或 Mixin 的依据。当前 Gradle 依赖和模组 metadata 都没有声明外部 AzureLib。

Travelers Lib 的 metadata 范围仍为 `[0.7.1,0.8.0)`，但服务端字节码兼容补丁只对真实 `0.7.1` 类结构建立了精确指纹和测试。未验证的 `0.7.x` 不应仅因落在版本范围内就宣称兼容。

## 2. 玩家功能

### 麻醉药水、麻醉针、麻醉镖与麻醉弩

- `jsrevise:anesthetic_potion` 是不可饮用的普通物品，不属于原版药水体系。它只能在酿造台中由虞美人加粗制的药水制成，不能继续用红石粉、萤石粉、火药或龙息加工成长效、强效、喷溅或滞留版本。
- 工作台中把麻醉药水放在九宫格中心，并在上、下、左、右各放一个 `jurassicsaga:empty_syringe`，会输出 4 个 `jsrevise:anesthetic_syringe`。麻醉针用于已放置捕获箱的注射和合成麻醉镖；旧 `anesthetic_syringe_projectile` 实体 ID 仅为已有世界中的历史实体保留兼容。
- `jsrevise:anesthetic_dart` 由麻醉针与其右上方相邻的羽毛合成；两者可随合成网格整体平移，但不能镜像、交换或加入额外物品。该方向配方以标准 shaped 元数据公开，因此 JEI 等配方查看器可通过麻醉镖查看配方、通过麻醉针查看用途。麻醉镖物品侧视明确显示后法兰、窄尾杆和阶梯羽片；实体使用两张正交双面 cutout 羽片，不含黑色矩形外框或正体积交叠，并以模型定义的 `58.5%` 统一比例渲染。飞行渲染以针尖为前方，不作为普通箭使用。
- `jsrevise:anesthetic_crossbow` 耐久 465，只接受麻醉镖，弹仓最多 6 发。独立的待机、三阶段拉弦和六档装填贴图会显示拉弦、装填比例及剩余镖数；麻醉针不再是弩弹药。
- 左键射击仍通过 `fire_anesthetic_crossbow` C2S 请求触发；服务端重新检查手、物品、装填状态和冷却后才生成投射物。每发间隔 10 tick，权威装填数量保存在 `CUSTOM_DATA.LoadedDarts`；只有显式存在旧 `LoadedSyringes` 的历史弩会迁移，伪造的 charged syringe 不会被当作有效弹药。
- 麻醉镖命中方块后沿用原版箭的落地、1200 tick 消失、存档和拾取语义；生存玩家可拾回，创造玩家射出的镖按原版采用仅创造可拾取规则。命中任何实体后都会立即消失，不会留在生物体表；只有成功命中可麻醉 `JSAnimalBase` 时才额外造成 1 点伤害并施加麻醉。
- 麻醉延迟和持续时间由运行时体型、食性、蛋型及陆生/水生/飞行类别推导。重复注射会缩短后续等待并叠加持续时间；pending 队列最多 64 项，时间运算有边界和饱和保护。

### 睡眠、AI 与真实漂浮

- 麻醉 active/ready、原生睡眠和短期稳定窗口由服务端统一协调。麻醉期间暂停 Travelers 的任务、战斗、移动和导航控制器，抑制主动 `travel` 输入，并保持可保存、可恢复和可同步的睡眠状态。
- 漂浮修改真实服务端实体位置、速度和碰撞状态，不是客户端渲染假象。碰撞感知移动根据实际位移更新阶段和同步状态；服务端与客户端都以 `FluidTags.WATER` 判断水体。
- 陆生、水生和飞行动物走各自的通用运动策略。水生动物保留 Jurassic Saga 的水生旅行链，飞行动物在受保护睡眠窗口中不能重新起飞；未来 `JSAnimalBase` 子类会先走运行时能力与安全回退，不以固定物种表作为功能开关。
- 水面定位固定采样五列并检查流体、碰撞和搜索上界。大型生物按碰撞箱与模型几何获得更深水线；上浮、沉浮和释放均遵守真实碰撞。
- 服务端在接近水面、首次破水和沉浮换向等事件发送有上限和冷却的水面粒子。客户端通用模型修正只允许向下校正非水生睡眠根骨骼，不能把模型抬离真实碰撞箱水线。
- 客户端按实际动画资源能力处理麻醉睡眠姿态：同时具有 `animation.sleep_in` 与 `animation.sleep_loop` 的资源不受干预；只有旧 `animation.sleep` 的资源在 guard 内本地循环该动画；没有任何睡眠动画的海王龙及同类资源则停止 base controller 中残留的游泳/待机循环。该分类不使用物种白名单，F3+T、退出或世界切换时会清理缓存。
- 当前没有海王龙专用 `TylosaurusAnimatorMixin`；动画回退只修正客户端残留循环，不改变服务端权威浮力、碰撞、水流推动或真实实体运动。本轮海王龙、大/小型水生动物、昆虫和青蛙类仍需游戏内逐项验证，本文档不把自动化资源分类测试等同于实机动画验收。

### 年龄、成长与生物画像

- 年龄附件保存可信出生时间，并能从 Jurassic Saga 的成长阶段、体型和运行时字段推算当前年龄、成年时间、成长百分比及生命周期阶段。
- 普通使用 Jurassic Saga 刷怪蛋生成成年个体；玩家按住 Shift 使用时生成幼体。生成后同步成长阶段、尺寸和年龄基线。
- `DinosaurSizeProfile` 集中提供物种 ID、尺寸级别、蛋型、成长比例和水面露出比例。已知物种覆盖只提高精度；未知或未来 `JSAnimalBase` 仍必须通过基类能力、碰撞箱和安全默认值获得通用功能。
- 对外保留 profile 覆盖入口，但注册值会统一验证 ID、枚举、尺寸、成长和露出比例；非法覆盖回退到自动画像。

### 恐龙博士眼镜 HUD

- `jsrevise:dino_doctor_goggles` 可装备在原版头部栏；安装 Curios 时也可从 Curios 头部槽识别。缺少 Curios 时不影响基础功能。
- 佩戴眼镜并观察 8 格内的 Jurassic Saga 动物时，HUD 显示生命值、年龄、成长阶段、性别、饥饿、口渴、心情、麻醉状态、基因和自然下蛋进度；观察已放置捕获箱时还显示箱体耐久与被捕获时长。
- 自然下蛋进度读取动物自身 `eggTime`，不把妊娠字段或已生成蛋的孵化进度混入该指标。客户端低频请求，服务端重新验证眼镜、目标、距离、字段和数值。
- 基因列表最多 64 项，基因 ID 最长 128 字符，显示名最长 256 字符；`ItemId` 可缺省。单个反射字段失败时只隐藏对应 HUD 项，不让整个面板失效。
- 客户端缓存按世界会话和 game time 回退自动清理，请求冷却使用饱和加法；资源重载只清模型几何缓存。Jade 存在时仅过滤与眼镜重复的 Jurassic Saga 性别/基因行，不删除 Jade 集成。

### 恐龙捕获箱

- 新 ID 为 `jsrevise:dinosaur_capture_box`；旧 `jsrevise:dinosaur_capture_cage` 作为物品、方块和方块实体别名保留。捕获箱最大堆叠 1，只能捕获 active 麻醉中的 `JSAnimalBase`。
- 捕获后形成 2×4×2、共 16 个 part 的逻辑单体。服务端先验证完整空间和可持久化数据，成功放置 controller 与全部 part 后才解除 leash、骑乘和乘客关系并移除原实体；任何失败都保持原实体关系不变。
- 被拴住的动物只在捕获提交成功后解除拴绳。生存玩家返还一根拴绳，背包满时在玩家处掉落；创造玩家不返还。
- 捕获数据保存实体类型、原 UUID、清洗后的实体 NBT、相对麻醉状态、捕获/结算时间、箱体耐久、生命体征和余数。`Passengers`、leash、载具、位置、运动、落地及摔落瞬态字段不会跨箱体恢复。
- 箱体耐久最大 100。处于麻醉保护区间时不消耗；其余时间每 20 tick 扣 1。手持、背包、打开容器、掉落物和已放置方块均参与结算，未加载容器和区块不会被全局扫描。
- 手持有恐龙的箱子按住 Shift 对方块使用，会在目标附近按确定性规则寻找安全释放点。手动或耐久归零自动释放没有安全候选时都会失败并保留 `VALID` 载荷；自动路径会推进结算时间，并在不超过 20 tick 的下一次结算重试，安全点出现后再正常释放并转为破损箱。
- 释放候选必须位于已加载范围和世界/建筑高度边界内、无碰撞且 AABB 不含岩浆。陆生候选按有支撑、少水、距离、`x/y/z` 排序；水生候选按水覆盖、距离、`x/y/z` 排序。
- 释放恢复原 UUID。防复制检查覆盖服务器当前所有已加载维度；项目没有世界级 UUID lease，因此未加载维度不在防重保证内。
- 已放置且有恐龙的箱子可接受麻醉针、符合 Jurassic Saga diet 的食物和水桶；操作前使用精确结算，成功后才写入新快照。
- 耐久归零并释放成功后，载体转为 `jsrevise:broken_dinosaur_capture_box`。破损箱仍是 2×4×2 的 16-part 装饰结构，不保存恐龙、没有 HUD 交互且无碰撞。
- 完整箱和破损箱的活塞反应均为 `BLOCK`。破坏任一 part 会按实际 part 位置执行 harvest 判定并清理整个结构；客户端把破坏进度镜像到全部 part。
- 捕获箱设置为不可原版维修。铁砧会拒绝右槽携带任意 raw 捕获 key，或左槽已有捕获 key 时用另一捕获箱合并；右槽为空的改名和附魔书仍可用，并保留 `CUSTOM_DATA`、名称、附魔及 Damage mirror。
- 数据无法解析时 tooltip 显示中英文恢复警告，捕获、放置、释放、结算和耐久镜像均停止，避免把原始数据覆盖掉。

### 配方与群系控制

| 产物 | 工作台配方 |
| --- | --- |
| 麻醉针 ×4 | 中心 1 个麻醉药水；上、下、左、右各 1 个 Jurassic Saga 空注射器 |
| 麻醉镖 | 1 个麻醉针；其右上方相邻格 1 根羽毛；整体可平移但不可镜像或交换 |
| 麻醉弩 | 上排 3 个铁锭；中排为线、绊线钩、线；下排中间 1 个铁锭 |
| 恐龙博士眼镜 | 上排左右各 1 个铁粒；中排为玻璃板、Jurassic Saga 指南书、玻璃板 |
| 恐龙捕获箱 | 7 个铁块包围中心黄色染料，中排右侧使用 1 个铁门 |

麻醉药水不走工作台：只接受酿造台中的粗制的药水 + 虞美人，且没有任何后续药水变体。该酿造输入、原料和输出通过 NeoForge 标准 brewing recipe 暴露，JEI 可显示麻醉药水的酿造配方。

`disable_jurassicsaga_biome_generation` 默认启用：它从 TerraBlender 注册入口阻止 Jurassic Saga 自定义主世界群系，并在噪声群系层保留已知原版回退；不删除生物、化石或结构。修改后需要重启游戏。

## 3. 数据架构

### 权威边界

服务端是麻醉、睡眠、AI、实体运动、年龄、捕获事务、世界数据和网络响应的唯一权威写入方。客户端只做输入请求、受限缓存、HUD/粒子/模型呈现和状态镜像；客户端预测不能替代服务端碰撞、流体接触或实体移动。

通用功能以 `JSAnimalBase`、`JSAquaticBase`、`JSAvianBase`、Travelers 稳定基类和运行时能力为入口。固定物种表只用于已证实的精度覆盖或上游视觉 workaround，不能决定某个新物种是否获得通用功能。

### Data Attachment

| ID | 数据 | 持久化 | 同步 | 用途 |
| --- | --- | --- | --- | --- |
| `jsrevise:dinosaur_age` | `DinosaurAgeData` | 是 | 是 | 出生时间、年龄推算与成长阶段 |
| `jsrevise:anesthetic` | `AnestheticData` | 是 | 是 | active/pending 剂量、相对保存与恢复 |
| `jsrevise:anesthetic_float` | `AnestheticFloatData` | 否 | 是 | 当前漂浮阶段和视觉同步；可从权威状态重建 |

附件 codec 对数量、持续时间和非法值做清洗。改变附件 ID、持久化语义或网络形状属于兼容性变更，不能作为普通重构处理。

### 网络协议 6

| 方向 | Payload ID | Java 类型 | 权威用途 |
| --- | --- | --- | --- |
| C2S | `jsrevise:fire_anesthetic_crossbow` | `FireAnestheticCrossbowPayload` | 请求当前手中麻醉弩射击；服务端重验物品、装填和冷却 |
| C2S | `jsrevise:egg_laying_progress_request` | `EggLayingProgressRequestPayload` | 请求目标动物自然下蛋进度；服务端重验佩戴、实体和距离 |
| C2S | `jsrevise:capture_cage_observation_request` | `CaptureCageObservationRequestPayload` | 请求已放置捕获箱精简快照；服务端重验佩戴、方块、controller 和距离 |
| S2C | `jsrevise:surface_effect` | `SurfaceEffectPayload` | 播放有界水面粒子事件 |
| S2C | `jsrevise:sleep_animation_guard` | `SleepAnimationGuardPayload` | 建立短期客户端睡眠动画保护 |
| S2C | `jsrevise:egg_laying_progress` | `EggLayingProgressPayload` | 返回可用或不可用的下蛋进度快照 |
| S2C | `jsrevise:capture_cage_observation` | `CaptureCageObservationPayload` | 返回有界捕获箱 HUD 快照 |

C2S 请求有频率、距离、玩家状态和目标类型校验；服务端不信任客户端给出的物品或世界状态。S2C 基因、字符串、枚举、数值和可选字段均按上限编码或清洗。`NETWORK_VERSION` 保持 `6`，本候选不迁移现有 payload 或 Data Attachment。

### 捕获数据与事务

捕获物品的权威载荷位于 `CUSTOM_DATA.DinosaurCapture`。`DinosaurCaptureItemData.inspect(ItemStack)` 将其分为三态：

- `EMPTY`：只有 capture key 完全不存在时成立。
- `VALID`：载荷成功解析为 `CapturedDinosaurData`；旧 `get(ItemStack)` 继续只返回这一态的 `Optional` 兼容视图。
- `UNREADABLE`：key 存在但值无法解析。任意 NBT `Tag` 都以 `copy()` 保存；普通 set、clear、mirror、捕获、放置、释放和结算不得覆盖，只有显式 raw/recovery API 可以替换。

方块实体同样能无损保存 `VALID` 或任意 `UNREADABLE` raw Tag。向客户端的 block entity update tag 只同步“是否含载荷”，不发送完整实体 NBT 或 raw 恢复数据。

`CapturedDinosaurData` 对实体 NBT 设定 1 MiB 上限和必填字段边界，并保持原实体类型与 UUID。物化临时实体失败时，结算返回原对象/raw，不推进时间、耐久、两个余数或 Damage mirror，也不因内容相同重复写回。

本版本增加两个向后兼容字段：

- `DurabilityRemainderTicks`：范围 `0..19`，保留不足 1 秒的箱体耐久时间。
- `CapturedRelativeTicks`：范围 `0..199`，保留不足一次饥饿/口渴漂移周期的捕获内时间。

两者都是 additive NBT；旧存档缺失时按 0 读取，不使用 DataFixer。`CapturedDinosaurData` 原 10 参数 public 构造器 descriptor 继续保留，新内部 capture/copy/with 路径显式携带新字段。

被动结算保留 20-tick 门槛；注射、喂食、补水、手动释放以及当前 tick 必须写新快照的路径使用精确结算。饥饿或口渴归零后的健康损失按 `elapsedTicks / 1200.0F` 计算。麻醉保护区间会合并 active 与最多 64 个 pending 区间，使用饱和时间运算，确保耐久投影单调不增。

权威耐久仍在捕获载荷中；`MAX_DAMAGE=100` 与 `DAMAGE=100-耐久` 只是原版耐久条和 Inventory HUD+ 的兼容镜像。正数被动流逝用纯数据投影，不每秒改写 `CUSTOM_DATA` 或 Damage mirror；真实结算、修复、归零和载荷清空才持久化变化。

### 观察与缓存

- `DinosaurObservationSystem` 以维度和实体 UUID 缓存服务端快照，有容量、过期、单实体失效和 server stopping 清理。
- HUD 反射按字段局部降级。基因 `ItemId` 是 optional；无物品图标时仍可显示文本，不再把整条基因丢弃。
- 客户端下蛋与捕获箱观察缓存有上限、过期、请求冷却、世界会话切换和 game time 回退清理；接近 `Long.MAX_VALUE` 时使用饱和加法。
- 模型几何缓存只缓存资源派生的边界，并由客户端 reload listener 在 F3+T 等资源重载时清除；不会顺带清理不相关的游戏状态缓存。

## 4. 兼容边界

### Travelers Lib 0.7.x 服务端补丁

Travelers Lib `0.7.1` 的 `collinvht.travelers.handler.v1211.Handler1211.onLoad(CoreServices)` 会在公共初始化前段无条件创建 NeoForge 渲染顶点 helper，从而让 dedicated server 提前解析客户端渲染类。JS-revise 当前不覆盖整段方法，而使用三个受限组件：

1. `server.TravelersHandler1211ServerMixin` 是 `@Pseudo`、字符串 target 的空 server-only marker。
2. `JSReviseMixinPlugin.preApply` 只在服务端、marker 和目标类都精确匹配时调用补丁。
3. `TravelersHandler1211BytecodePatch` 在真实 `0.7.1` 指纹上只删除一组连续的 `NEW`、`DUP`、无参构造和 `TravelersRenderVertex.setHandler`。

补丁有三种结果：

- `PATCHED`：只找到一组精确四元组，公共 event bus、network、item NBT、Services/Azure 初始化和分端 guard 不变量全部存在，删除后字节码再次通过栈与控制流验证。
- `NO_OP`：四元组及两个旧 owner 均已消失，方法仍完全匹配已知 server-safe 指纹，且没有新的 render/client bridge。
- fail-fast：partial、多组、descriptor/方法/栈变化、未知 helper、额外客户端桥或公共初始化缺失时立即拒绝启动，不猜测性修改未知字节码。

marker、plugin 和 helper 不 import Travelers render、Minecraft client 或外部 AzureLib 类。单元测试读取真实 `0.7.1` class bytes，并覆盖 patch、no-op、partial、multiple、descriptor 和结构漂移样本。其他 `0.7.x` 必须取得对应二进制后逐版验证；版本范围本身不是字节码兼容证据。未来 Travelers 在受支持版本中移除服务端无条件 render helper，且其公共初始化可独立验证安全后，才应删除或收窄这条补丁。

### 睡眠动画 workaround

Jurassic Saga `0.2/0.2.1` 已确认卢多翼龙和双脊龙的 `sleep_in` 首段会短暂出现站立、中性或飞行姿态，而 `sleep_loop` 才是稳定睡姿。JS-revise 只在已有 sleep guard、生物 ID 精确属于这两个上游案例、最终发送层正要发送精确 `sleep_in` 时改发 `sleep_loop`；它不改变自然睡眠条件、夜行性基因、麻醉计时、NBT 或网络协议。

若未来 Jurassic Saga 修复相关动画资源/transition，或能用精确版本及资源探测区分受影响包，应删除或进一步收窄该表。旧 public 别名 `shouldRedirectLudodactylusSleepInToLoop(...)` 暂时保留，不能在没有兼容性评估时直接移除。

与上述两个精确 `sleep_in` 重定向不同，客户端还会按当前 animation JSON 的实际能力选择麻醉姿态回退。标准 `sleep_in/sleep_loop` 资源保持上游行为；单一旧 `animation.sleep` 在本地 guard 中循环；无睡眠资源时每个 guard 停止一次 base controller 的旧循环。能力缓存按动画资源 ID 建立，并在资源重载、退出和世界卸载时清理；它不修改麻醉计时、服务端运动或网络协议。

### 可选集成与公开兼容桥

- Curios 通过存在性检查隔离；缺失时只使用原版头部栏。Jade 是可选客户端回调，缺失时不进入必需路径。
- worldgen Mixin 只控制 Jurassic Saga 自定义群系区域、地表规则和已知噪声群系回退，不删除生物、化石、结构或其他内容。
- `FloatingModelExposureCalculator.correction(...)` 的 public overload、旧卢多翼龙别名，以及 `DinosaurAnestheticSystem` 中标记为 `@Deprecated(forRemoval=false)` 的麻醉动画/AI/飞行/最终移动桥继续作为兼容面保留。
- 客户端类、可选依赖类和渲染类不得进入独立服务器的必需加载路径。

### 当前完整 Mixin 清单

`src/main/resources/jsrevise.mixins.json` 使用 `JSReviseMixinPlugin`，`defaultRequire=1`。只有明确允许随上游变化局部失效的注入点显式使用 `require=0`。

| 作用域 | Mixin | 目标与职责 |
| --- | --- | --- |
| common | `EntityTypeSpawnEggMixin` | Minecraft `EntityType`；刷怪蛋生成后设置成年/幼年阶段、尺寸和年龄 |
| common | `JSAnimalAnimationSystemsMixin` | Travelers `TravelersAnimal`；服务端准备原生睡眠 guard，客户端阻止普通动画覆盖 |
| common | `JSAnimalBaseSystemsMixin` | Jurassic Saga `JSAnimalBase`；在服务端 AI 前后准备/保持麻醉与自然睡眠，并抑制移动状态 |
| common | `JSAquaticBaseSystemsMixin` | Jurassic Saga `JSAquaticBase`；过滤麻醉主动旅行输入，并允许流体推动麻醉水生动物 |
| common | `JSAvianBaseSystemsMixin` | Jurassic Saga `JSAvianBase`；稳定睡眠、过滤主动输入、阻止重新起飞及恢复 stale 飞行状态 |
| common | `JSTerrablenderMixin` | 字符串目标 `JSTerrablender`；按配置取消 Jurassic Saga region 和 surface rules 初始化 |
| common | `JSEntityDataHolderSystemsMixin` | Jurassic Saga `JSEntityDataHolder`；桥接 raw sleeping 读写和读档 marker |
| common | `LivingEntityTravelMixin` | Minecraft `LivingEntity`；过滤 `JSAnimalBase` 主动输入并接管碰撞感知的被动漂浮 travel |
| common | `MultiNoiseBiomeSourceMixin` | Minecraft `MultiNoiseBiomeSource`；配置启用时把已知 Jurassic Saga 群系替换为 vanilla fallback |
| common | `TravelersAnimationDefinitionMixin` | Travelers 最终动画发送层；阻止睡眠 guard 中的普通动画，并处理两个精确 `sleep_in` workaround |
| common | `TravelersAnimalAnimationModuleAccessor` | Travelers `TravelersAnimalAnimationModule.animationMap`；为睡眠 transition 清理提供受限 accessor |
| common | `TravelersSmartAnimalBaseMixin` | Travelers `SmartAnimalBase`；麻醉期间暂停任务、战斗、移动和导航 controller |
| client | `AzEntityDispatchCommandPacketMixin` | Travelers 内置 Az 动画命令包；建立本地 sleep guard 并阻止普通/错误退出睡眠 stage |
| client | `CustomHeadLayerMixin` | Minecraft `CustomHeadLayer`；隐藏头部栏眼镜物品模型，HUD 功能不受影响 |
| client | `LevelRendererDestroyProgressMixin` | Minecraft `LevelRenderer`；把完整/破损捕获箱的破坏进度镜像到全部 part，并在世界切换时清理 |
| client | `TravelersClientAnimatorMixin` | Travelers 通用客户端 animator；睡眠 guard 中清 procedural bone cache 并跳过 `update/clientTick` |
| client | `TravelersAzureModelRendererMixin` | Travelers 内置 Azure renderer；按实际动画资源能力应用麻醉姿态回退、清睡眠 animator frame，并限制非水生漂浮根骨骼只向下校正 |
| server | `TravelersHandler1211ServerMixin` | 字符串目标 Travelers `Handler1211`；作为 plugin 精确字节码补丁的 server-only marker |

当前清单不包含 `TylosaurusAnimatorMixin`，也没有旧的 physics/canFace ThreadLocal 或 renderer 私有 root-lift resolver。更改 Mixin 时必须同步检查 JSON、plugin、目标 descriptor、分端加载、真实依赖 class bytes 和最终 JAR。

## 5. 配置与开发

### COMMON 配置

NeoForge COMMON 配置通过模组配置界面展示，并提供英文与简体中文名称和 tooltip。

| 键 | 默认值 | 重启要求 | 作用 |
| --- | --- | --- | --- |
| `debug_logging` | `false` | 否 | 输出画像推断、兼容审计及限频睡眠动画追踪 |
| `disable_jurassicsaga_biome_generation` | `true` | 游戏重启 | 取消 Jurassic Saga 自定义群系注册，并启用噪声群系 fallback；使用 `.gameRestart()` 标记 |

关闭群系生成只影响 Jurassic Saga 自定义群系。改变该配置后必须完整重启游戏；运行中热切换不构成受支持流程。

### 代码边界

- `neo/`：注册表、事件、网络、配置和客户端生命周期入口。
- `server/`：物品、方块、实体、Data Attachment 及服务端权威系统。
- `system/observation/`：客户端和服务端共享的有界观察 DTO、解析与快照。
- `network/`：协议 6 payload、codec、权限验证和请求限频。
- `compat/`：Curios、Jade、Travelers 等有明确依赖边界的集成。
- `mixin/`：只放必须进入上游调用链的最小注入；客户端和服务端分目录。
- `client/`：HUD、模型、粒子、物品属性和仅客户端缓存。
- `src/test/` 与 `gametest/`：纯逻辑、资源引用、真实依赖字节码和世界行为验证。

修改通用动物功能时，先证明基类或运行时能力边界，再决定是否需要物种覆盖。涉及移动、AI、碰撞、网络、NBT 或附件时必须保留服务端权威、非法值清洗、失败原子性和旧存档行为；避免无收益的大范围重构。

### 构建与验证

环境要求为 Java 21。Windows 下的完整自动化门禁为：

```powershell
.\gradlew.bat test runGameTestServer assemble --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat publish --dry-run --console=plain
```

`publish --dry-run` 只验证任务图，不授权实际 publish。常用局部命令包括：

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat assemble
.\gradlew.bat runServer
.\gradlew.bat runClient
```

自动化必须检查单元测试、GameTest、编译与 Gradle 警告、资源引用、Mixin/plugin、真实 Travelers class bytes、生成 metadata、JAR 内容及工作树范围。测试数量和产物绝对路径不写入长期文档，因为它们会随实现变化。

`runClient`、长期驻留服务器和实际整合包属于人工验收，不由普通构建自动启动。限时 dedicated server 验证应在五分钟内等待 `Done`，看到后正常输入 `stop`；超时或崩溃需保留日志并判定失败。

### 版本与兼容维护

- `gradle.properties` 的 `mod_version`、生成/processed metadata、最终 JAR metadata 和 README 候选版本必须一致。
- 网络协议、附件、捕获 NBT 或 public descriptor 变化前必须先决定是否需要协议升级、迁移或兼容桥。本版本新增余数字段但不需要 DataFixer。
- 资源变更要通过 recipe、model、texture、blockstate、loot、tag、lang 和动态 renderer allowlist 的引用完整性检查。
- 依赖升级后重新验证 Mixin 目标、Travelers 字节码指纹、Curios/Jade 降级和 dedicated server；历史结论不能自动外推到新二进制。
- 发布前确认源码仓库只包含批准的内层文件，不包含外层 `AGENTS.md`、`.codex/`、`.agents/`、`.planning/`、参考模组或运行产物。

## 6. 已验证基线

### 1.0.123 候选自动化边界

当前候选实现已建立并运行单元测试、资源引用测试、独立捕获/麻醉运动 GameTest 和 `assemble` 基线；Travelers 补丁测试使用真实 `0.7.1` class bytes，并构造 patch、no-op 和多种 fail-fast 漂移样本。最终交付仍必须在 README 与版本收口后复跑完整命令，并核对 JAR metadata、Mixin 清单、依赖内容、资源和工作树。

这些自动化结果不等于实际整合包验收。本文档不宣称 `1.0.123` 已通过游戏内视觉、完整捕获交互、可选依赖 A/B 或实际整合包验证，也不把未取得二进制的其他 Travelers `0.7.x` 标为已实测。限时 dedicated server 启动与实际整合包验收应分别留下明确记录。

### 1.0.115 历史实机基线

提交 `1c4a440`（`Document external validation pass for 1.0.115`）记录了以下 `1.0.115` 发布前实际整合包事实，仅用于防回归；该提交本身不代表远端 Actions 状态已核验：

- 已确认艾雷拉龙、腕龙、鸟鳄、卢多翼龙及其他陆生/飞行动物的模型按预期露出水面。
- 已确认成年和幼年生物的露出比例符合“体型越大，露出越少”。
- 已通过海王龙的沉浮振幅、水平姿态和尾部动画验证。
- 已确认接近水面、首次破水和沉浮换向时的粒子可见性。
- 已确认 DNA 图标、动态主题色、85% HUD 整体缩放、等效 90% 字体和透明度在不同 GUI 缩放下表现符合预期。
- 已通过独立服务器中的年龄、麻醉倒计时、漂浮阶段和粒子事件同步验证。

这些结论只对应 `1.0.115` 发布前的当时整合包环境，不能证明 `1.0.123`、未来依赖版本或其他整合包已通过相同验收。任何功能、资源、Mixin、依赖或整合包变化后，都应重新执行相应的自动化、独立服务器和游戏内验证。

# JS-revise

JS-revise 是由 Palos 开发的 Minecraft 1.21.1 NeoForge 模组，也是 Jurassic Saga 的附属模组。

项目目前主要为 Jurassic Saga 生物提供麻醉、落水漂浮、年龄推算、信息观察、刷怪蛋成长阶段控制和世界生成调整等功能。设计重点是让通用功能依赖 Jurassic Saga 的稳定生物基类和运行时能力，而不是依赖固定物种白名单，使主模组未来新增的常规生物能够自动获得基础支持。

本文档以 `1.0.59` 源码为准，面向后续维护、问题排查和功能扩展。

## 基本信息

| 项目 | 当前值 |
| --- | --- |
| 模组 ID | `jsrevise` |
| 显示名称 | `JS-revise` |
| 作者 | `Palos` |
| 当前版本 | `1.0.59` |
| Minecraft | `1.21.1` |
| NeoForge | `21.1.232` |
| Java | `21` |
| Gradle Wrapper | `9.2.1` |
| Parchment | `1.21.1-2024.11.17` |

### 依赖关系

必需依赖：

| 模组 | 开发版本 | 声明范围 |
| --- | --- | --- |
| Jurassic Saga | `0.1.11.1` | `[0.1.11.1,)` |
| TravelersLib | `0.5.6` | `[0.5.1,1.1.0)` |
| AzureLib | `3.1.3` | `[3.0.30,4.0)` |

可选兼容：

| 模组 | 用途 |
| --- | --- |
| Curios | 允许将恐龙博士眼镜佩戴在 Curios 头部槽位 |
| Jade | 过滤 Jurassic Saga 已由眼镜 HUD 展示的性别和基因信息 |

依赖声明位于 `gradle.properties`、`build.gradle` 和
`src/main/templates/META-INF/neoforge.mods.toml`。

## 设计原则

### 自动兼容 Jurassic Saga 新生物

通用系统以 `JSAnimalBase` 为功能入口：

- `JSAquaticBase` 使用水生漂浮策略。
- `JSAvianBase` 使用飞行动物落水捕获和漂浮策略。
- 其他 `JSAnimalBase` 使用陆生策略。
- 未知的新基类会先按普通 `JSAnimalBase` 使用安全回退逻辑。

`DinosaurProfileResolver` 会在运行时根据以下信息生成生物画像：

- 实体注册 ID。
- 当前碰撞箱宽度和高度。
- Jurassic Saga 生长模块提供的成长百分比和阶段。
- 是否继承水生或飞行动物基类。
- 根据体型推导的尺寸等级、露出水面比例和蛋型。

已知物种表只用于提供更精确的蛋型和年龄参数，不是功能是否生效的开关。未来新增的 `JSAnimalBase` 子类即使没有手工配置，也应获得年龄、麻醉、漂浮、刷怪蛋和 HUD 的通用支持。

服务器启动后会遍历 `JSAnimals.getAnimals()` 执行兼容性自检。开启
`debug_logging` 后，会记录发现、成功解析和自动推导的生物数量。

### 服务端权威

影响游戏状态的逻辑由服务端负责：

- 麻醉剂量、生效时间和剩余时间。
- 恐龙年龄数据。
- 漂浮状态机和最终实体位置。
- 麻醉弩是否允许发射。
- 水面粒子事件的触发时机。

客户端只负责收集输入、显示 HUD、修正特定渲染姿态和生成粒子。客户端发送的发射请求会在服务端重新检查手持物品、装填状态和冷却状态。

### 兼容边界

项目目标是兼容 Jurassic Saga 正常增加生物、动画和资源的更新，但无法保证兼容删除或重命名核心基类、方法和模块接口等二进制破坏性更新。

特殊视觉功能使用 `require = 0` 的可选 Mixin 注入。海王龙动画器还使用
`@Pseudo` 和字符串目标，目标类被删除或重命名时只停用该视觉修正。对
`JSAnimalBase` 等核心功能基类的依赖则属于模组运行所必需的兼容边界。

## 工程结构

```text
JS-revise/
├─ build.gradle
├─ gradle.properties
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/com/palos/jsrevise/
│  │  │  ├─ client/                 客户端输入、粒子和 HUD
│  │  │  ├─ compat/                 Curios、Jade 兼容
│  │  │  ├─ config/                 NeoForge 配置
│  │  │  ├─ mixin/                  Minecraft、Jurassic Saga 和渲染注入
│  │  │  ├─ neo/                    NeoForge 服务端和客户端入口
│  │  │  ├─ network/                自定义网络 Payload
│  │  │  ├─ server/entity/          麻醉针投射物
│  │  │  ├─ server/item/            麻醉针、麻醉弩、恐龙博士眼镜
│  │  │  ├─ server/registry/        物品、实体、附件、创造模式页注册
│  │  │  ├─ server/system/          年龄、麻醉、体型和世界生成系统
│  │  │  └─ system/observation/     HUD 观察快照和反射兼容
│  │  ├─ resources/
│  │  │  ├─ assets/jsrevise/        语言、物品模型和纹理
│  │  │  ├─ data/                   Curios 槽位数据
│  │  │  └─ jsrevise.mixins.json    Mixin 清单
│  │  └─ templates/
│  │     └─ META-INF/neoforge.mods.toml
│  └─ test/java/                    JUnit 自动化测试
└─ build/libs/                      构建后的 JAR
```

### 包职责

#### `com.palos.jsrevise`

`JSRevise` 保存模组常量、日志器和幂等初始化逻辑。初始化时向 TravelersLib 注册当前附属模组，并禁用 Gecko 路径，确保使用 Travelers/Azure 渲染体系。

#### `neo`

- `JSReviseNeo`：公共 NeoForge 入口，注册附件、物品、实体、创造模式页、网络和配置，并挂接服务端实体 tick。
- `JSReviseNeoClient`：客户端入口，注册投射物渲染器、HUD、输入事件、配置界面和缓存清理事件。

#### `server.registry`

- `JSReviseAttachments`：注册年龄、麻醉和漂浮附件。
- `JSReviseItems`：注册三个物品。
- `JSReviseEntityTypes`：注册麻醉针投射物。
- `JSReviseCreativeTabs`：注册模组创造模式物品页。

#### `server.system`

- `age`：年龄保存、成长阶段推算和现实年龄估算。
- `anesthetic`：麻醉剂量、状态推进、漂浮、水面检测和粒子事件。
- `profile`：自动生成 Jurassic Saga 生物画像。
- `size`：画像类型、尺寸等级、生命周期和对外门面。
- `worldgen`：按配置替换 Jurassic Saga 自定义群系。

#### `system.observation`

将 Jurassic Saga 的生命、性别、代谢和基因模块转换为稳定的
`DinosaurObservationSnapshot`。不稳定或可选模块通过缓存反射读取；单个字段读取失败时只隐藏该字段，不影响整个 HUD。

## 初始化与运行流程

### 公共初始化

1. NeoForge 创建 `JSReviseNeo`。
2. `JSRevise.init()` 向 TravelersLib 注册附属模组。
3. 注册 Data Attachment、物品、实体和创造模式页。
4. 注册网络协议和 COMMON 配置。
5. 在 NeoForge 事件总线挂接 `JSAnimalTickHandler`。
6. 服务器启动完成后执行 Jurassic Saga 生物画像自检。

`JSCommonMixin` 还会在 Jurassic Saga 的 `JSCommon` 初始化结束时调用
`JSRevise.init()`。初始化方法由 `AtomicBoolean` 保护，因此两条入口不会重复执行。

### 客户端初始化

1. 注册麻醉针投射物渲染器。
2. 在原版快捷栏上方注册恐龙博士眼镜 HUD。
3. 监听攻击键，为麻醉弩发送发射请求。
4. 注册 NeoForge 配置界面。
5. 在断线、客户端世界卸载和实体移除时清理 HUD、漂浮粒子与观察缓存。

### 每 tick 生物处理

`JSAnimalTickHandler` 只在服务端处理 `JSAnimalBase`：

1. `DinosaurAgeSystem.tick()` 更新年龄数据。
2. `DinosaurAnestheticSystem.tickServer()` 推进麻醉和漂浮状态。

麻醉系统使用游戏时间和 `lastProcessedTick` 防止同一生物在同一 tick 被重复推进。

## 物品与交互

### 麻醉针

注册名：`jsrevise:anesthetic_syringe`

麻醉针当前是麻醉弩的专用弹药，最大堆叠数量为 64。投射物命中
`JSAnimalBase` 后：

1. 造成 1 点投射物伤害。
2. 为目标排入一剂延迟生效的麻醉。
3. 命中实体或方块后销毁。

投射物重力为 `0.0075`，低于普通高抛投射物，以适配麻醉弩射程。

### 麻醉弩

注册名：`jsrevise:anesthetic_crossbow`

主要行为：

- 耐久度为 465。
- 只接受麻醉针作为弹药。
- 长按右键装填。
- 弹仓最多保存 6 支麻醉针。
- 一次装填会尽可能填满剩余弹仓。
- 左键发射一支，发射后有 10 tick 冷却。
- 弹仓数量始终限制在 `0..6`。
- 弹仓数量保存在物品 `CUSTOM_DATA` 的 `LoadedSyringes` 字段中。

客户端左键只发送发射请求并取消普通攻击挥手。服务端收到请求后重新确认玩家手中的物品确实是麻醉弩，再由物品逻辑检查弹药、已蓄力状态和冷却；成功发射不会调用玩家挥手动画，因此第一人称和第三人称继续保持原版持弩姿势。

麻醉弩保留原版弩从上方观察的正交俯视物品视角、待机状态和三段拉弦状态，但将材质改为克制的工业机械风格。弩身尾部继续缩短三个像素，并将新的单像素尾端提亮一个色阶，使轮廓保持清晰而不过长。弓臂保留中等厚度，左右外端的深色轮廓像素沿俯视对角轴严格对称，并使用分层蓝灰金属明暗、清晰但不过黑的单像素边缘、直线导轨和少量医疗蓝标识。前端移除两个白色像素，保留与弩身连续相连的紧凑对称结构。

客户端为该自定义物品注册原版式 `pulling`、`pull`、`charged` 谓词，以及按剩余弹量计算的 `jsrevise:loaded` 谓词。装填过程依次切换三段拉弦贴图；装填完成后使用 1 至 6 支弹药对应的六档模型。每发射一支麻醉针，弩弦就向待机位置回退一档，六档弦位均沿俯视对角轴保持几何对称。弦的两个固定端点使用弦线色阶而非弓臂深色，并在全部拉弦与剩余弹量状态中连续连接活动弦线。小型麻醉针在所有装填档位保持相同比例，金属针头朝向弩的前端，蓝色药液位于针管中后段。

### 恐龙博士眼镜

注册名：`jsrevise:dino_doctor_goggles`

眼镜可佩戴在原版头部装备栏；安装 Curios 后，也可放入 Curios 的
`head` 槽位。原版头部物品渲染层会隐藏该物品模型，避免与专用眼镜外观或玩家头部产生冲突。

物品贴图采用与工程师眼镜一致的正视构图，使用左右几何轮廓严格对称的黑色直镜腿、银色铰链、深海军蓝圆角矩形细框和较窄的灰白镜片。着色统一采用左上方光源，因此左右轮廓对称但颜色不会机械镜像；顶部和左侧较亮，底部和右侧较暗。圆角由四角各切去一个像素形成，保持博士护目镜的严肃比例，同时遵守
Minecraft 原版 16×16 像素物品的尺寸、硬边与有限调色板风格。

## 麻醉系统

### 剂量与生效

`AnestheticStateService` 根据生物画像计算：

- 注射后的生效延迟。
- 单剂麻醉持续时间。
- 体型、蛋型、食性以及水生/飞行分类带来的系数。

基础持续时间为 60 秒，最终时间会按生物类型和体型调整。体型越大，生效延迟通常越长。

多次注射具有以下规则：

- 后续注射会缩短已有待生效剂量的等待时间。
- 生效时间相同的剂量会合并。
- 已生效麻醉按现有结束时间继续叠加。
- 待生效剂量最多保存 64 条。
- 单个实体的总麻醉时间最多限制为 24 小时游戏 tick。
- 反序列化会丢弃非正数持续时间，并修复负时间和超长绝对时间。
- 网络读取只接受 `0..64` 条待生效剂量，越界数量会拒绝解码，避免污染后续数据。

### 睡眠和 AI 抑制

麻醉生效期间：

- 强制 `canSleep()` 和 `shouldSleep()` 返回可睡眠。
- `isMoving()` 返回 false。
- 清除攻击、逃跑、调查和观察目标。
- 正常停止当前正在运行的 Travelers 行为任务，执行其 `onStop()` 清理，释放任务内部缓存的攻击、抓取或追踪对象。
- 解除恐龙作为载具或乘客形成的实体关联，避免抓取任务结束后继续通过玩家位置驱动恐龙。
- 停止导航。
- 清除攻击、进食、恐慌、潜行和跳跃等运行状态。
- 在主模组服务端动画器执行前保持 Jurassic Saga 的睡眠状态。
- 到点的待生效剂量会在服务端动画器执行前先推进为 active，避免等待 `EntityTickEvent.Post` 时产生一帧普通动画。
- `isSleeping()` 在麻醉期间会桥接为 true；客户端若已收到“到点但尚未 promote 同步”的待生效剂量，也会只读桥接为睡眠，避免附件同步和动画包到达顺序不同造成一次性站立闪回。
- 麻醉期间通用 `JSAnimal` 动画入口会优先推进睡眠 transition，并阻止本 tick 的物种动画器继续发送站立、行走、游泳或飞行动画。因此固定飞行态或没有标准睡眠判断的现有/未来动物，也会按麻醉睡眠姿势收口。

这些操作统一从 `JSAnimalBase` 和 Travelers 的稳定生物基类生效，因此不需要逐个物种接入。麻醉期间
`TravelersSmartAnimalBaseMixin` 会暂停任务控制器、移动控制器和导航控制器的服务端更新，但不会跳过主模组的服务端动画器。解除麻醉后不保留永久禁用标记，主模组会继续按原流程运行。

## 漂浮系统

### 状态机

`AnestheticFloatData.Phase` 包含：

| 状态 | 含义 |
| --- | --- |
| `IDLE` | 未参与麻醉漂浮 |
| `FALLING` | 未捕获有效水面，按体型下落 |
| `RISING` | 已捕获水面，向目标漂浮高度上升 |
| `BOBBING` | 到达水面后进行周期性上下沉浮 |
| `RELEASING` | 麻醉结束时释放锁定和临时状态 |

`isFloating()` 只在 `RISING` 和 `BOBBING` 阶段返回 true。

### 生物分类

- **陆生生物**：接触有效流体后进入上浮流程。
- **水生生物**：在水中、接触流体或仍位于有效水面附近时保持漂浮。
- **飞行动物**：首次满足落水捕获条件后设置 `waterCaptured`，短暂水面检测失败不会立即丢失捕获状态。

飞行动物被麻醉后会立即关闭俯冲、滑翔和振翅状态，并停止地面和飞行导航。`JSAvianBaseSystemsMixin` 会拒绝普通翼龙重新进入飞行状态、过滤其重写 `travel` 方法收到的主动输入，并在麻醉期间取消基类 `handleFlying()` 对朝向速度的写入。蝴蝶、蚊子等由主模组将 `isFlying()` 固定为 true 的特殊类型不依赖该状态值，但同样无法继续获得飞行主动推进。释放麻醉时解除锁定。

### 水面定位

`FluidSurfaceLocator` 会检查实体中心和碰撞箱四角共五列：

- 从实体上方到下方的小范围内寻找流体。
- 找到流体后向上扫描流体柱顶面。
- 使用流体实际高度而不是简单的方块整数高度。
- 普通状态缓存 5 tick。
- 已捕获水体的飞行动物缓存最多保留 20 tick。
- 实体移动过远或缓存过期后重新扫描。
- 缓存水面不能单独维持漂浮：碰撞箱必须真实接触流体，或水面必须位于实体正下方允许的沉浮间隙内。
- 验证正下方水面时会检查流体与碰撞箱之间的方块碰撞；沙砾、石头等实体方块会阻断水面支撑。

这种设计减少每 tick 重复扫描，并允许大型实体跨越多个流体方块，同时避免被水流推上岸后继续围绕旧水面在陆地方块内沉浮。

### 目标高度

目标位置由以下参数共同决定：

- 实体碰撞箱宽度和高度。
- 尺寸等级。
- 成长阶段。
- 是否为水生或飞行动物。
- `surfaceExposureRatio`。

总体规则是体型越大，露出水面的模型比例越低。水生生物使用完整高度作为漂浮参考；陆生和飞行动物使用有上限的睡眠姿态参考高度，避免站立碰撞箱高度直接把睡眠模型压在水下。

非水生生物的目标高度还会按实体 Y 与碰撞箱底面的实际偏移进行校正，避免不同 Jurassic Saga 生物使用不同实体原点时出现整只悬空。最小浸水深度根据当前碰撞箱高度、宽度和尺寸等级连续增长；大型、巨型和超巨型生物会获得明显更深的真实物理水线，而不是共用固定的 `0.30` 格上限。`TITANIC` 级生物使用更高的高度占比和最多 `1.85` 格的浸水上限，因此腕龙、马门溪龙及未来同等级生物会比霸王龙级别露出更少。

### 上浮和沉浮

上浮阶段使用限制速度逐步接近目标高度。到达水面后：

- 记录 `bobbingStartedAt`。
- 使用 `BobbingWave` 生成严格 50% 下沉、50% 上浮的三角波。
- 最低点立即换向，不设置停顿平台。
- 上下方向使用相同位置修正上限。
- 沉浮周期和振幅按体型自动缩放。
- 水生生物允许更大的下沉振幅。
- 陆生、水生和飞行动物全部由服务端实体位置执行真实三角波沉浮，模型与碰撞箱始终一起移动。
- 陆生和飞行动物使用约 `220..260` tick 的缓慢周期；水生生物使用同体型普通周期的 `75%`，实际约为 `165..195` tick。海王龙的节奏因此明显慢于此前的 `56..100` tick，并接近其他恐龙，但不会重新进入曾导致实体近乎静止的统一慢周期。
- 水生生物按尺寸等级设置最低真实沉浮振幅，并保留至少 `0.008` 格/tick 的位置修正上限。周期目标仍由服务端逐 tick 写入真实实体 Y 坐标，模型与碰撞箱会共同完成完整下沉和上浮。
- 麻醉期间会将主动旅行输入过滤为零。非水生生物进入 `RISING` 或 `BOBBING` 后会隔离宿主 `LivingEntity.travel` 对水中竖直位置的再次计算，避免服务端状态机刚写入的真实上浮高度在同一运动链中被覆盖。
- 每次麻醉首次进入运动状态机时，会对所有 `JSAnimalBase` 清除一次麻醉前由飞行、奔跑、跳跃或游泳产生的主动速度。清除标记保存在非持久化漂浮附件中，同一次麻醉不会重复执行，因此后续玩家碰撞、水流及其他外力仍可推动真实实体。
- 飞行动物基类具有持续主动推进隔离：每 tick 的起飞请求、飞行朝向控制和飞行旅行输入都会在麻醉期间被拦截。该处理只阻止主模组主动生成新速度，不会按物种名称判断，也不会清除麻醉后由碰撞或流体写入的被动速度。
- `LivingEntity.aiStep` 在虚调用具体生物的 `travel` 前还会统一过滤一次旅行参数。因此幼年巨脉蜻蜓、两栖基类、蟹形基类以及未来自行重写 `travel` 的 `JSAnimalBase` 子类，不会因为绕过父类方法而重新获得主动移动输入。
- 隔离非水生宿主旅行链前，会读取玩家碰撞、水流和其他外力产生的 X/Z 速度，通过 `Entity.move(MoverType.SELF, ...)` 对模型与碰撞箱执行一次带碰撞检测的真实水平移动，再施加接近原版水体的阻力。这里没有回写水平位置锚点，也不是客户端渲染位移。
- Jurassic Saga 的水生基类通常会拒绝流体推动；本模组只在水生生物处于麻醉状态时临时恢复流体推动，解除麻醉后立即回到主模组原行为。
- 海王龙等 `JSAquaticBase` 继续使用 Jurassic Saga 水生旅行链和现有专用姿态路径；漂浮控制器仍只接管真实实体的 Y 坐标和竖直速度。水面目标、三角波周期、振幅和粒子触发逻辑不受影响。
- 稳定阶段只有位置或速度真正变化时才发送纠正，避免本地水体物理和每 tick 强制位置包叠加成抖动。
- 客户端只处理 Travelers/Azure 睡眠姿态产生的根骨骼偏移，不再生成独立于碰撞箱的视觉沉浮。
- 非水生麻醉漂浮期间，Travelers 单次动画更新会临时关闭物理链和朝向追踪，并在本帧更新后恢复原值，避免尾部、颈部或翼部动态求解产生抖动，也不会污染解除麻醉后的动画状态。

漂浮不再保存或回写水平位置锚点。水面定位缓存会随实体真实位置重新采样，因此生物被水流或玩家推离原位置后仍会基于当前位置继续寻找水面；离开真实接触或无阻挡支撑的有效水体时会立即清除旧水面缓存，并按原状态机进入下落流程。

### 粒子事件

服务端在以下时机发送 `SurfaceEffectPayload`：

- `APPROACH`：接近水面。
- `SURFACE_BREAK`：首次到达水面。
- `BOB_TURN`：沉浮方向发生变化。

客户端根据实体宽度和高度生成：

- 水中的 `BUBBLE` 和 `BUBBLE_POP`。
- 水面上方的 `SPLASH`。
- 所有恐龙的低频水面效果直接由客户端实体 tick 驱动，不依赖模型是否进入渲染回调。客户端会从当前模型 JSON 自动解析横向和纵向占地，按实体朝向在模型轮廓附近寻找真实流体表面；外圈采样失败时回退到碰撞箱范围。
- 粒子数量由当前碰撞箱宽高与模型占地共同计算。幼年和小型生物生成较少但清晰可见的粒子，大型生物生成更多粒子，数量不再被统一的高下限拉平。
- 首次破水、开始下沉和开始上浮时生成明显的粒子爆发；沉浮过程中按 `26..46` tick 的体型相关间隔生成中低强度环境粒子。
- 接近水面的上浮阶段使用更长冷却生成少量水泡，真正露出水面时再触发较明显的破水效果。
- 收到服务端破水或换向事件后会重置环境粒子计时，避免同一时刻叠加两套效果。

水生生物不再每 12 tick 强制发送一次粒子事件，只在破水和沉浮换向时发送明显事件。粒子数量仍有最大值且事件有冷却，避免海王龙或大型生物持续制造粒子洪流。

### 海王龙视觉修正

海王龙对应 Jurassic Saga 的 `Tylosaurus`。

`TylosaurusAnimatorMixin` 仅在海王龙处于麻醉漂浮状态时：

- 暂时关闭物理动画和朝向追踪。
- 清除根部和身体的异常倾斜。
- 清除尾部骨骼的横向物理摆动。
- 在本帧处理后恢复动画标志。

所有注入点使用 `require = 0`。主模组改变海王龙动画器时，这项视觉修正允许失效，而不应阻止游戏启动。

### 通用模型出水修正

TravelersLib 会在渲染时将睡眠动画的根骨骼 Y 偏移应用到整个模型，而服务端碰撞箱并不知道该偏移。这会导致碰撞箱已经到达水面，但陆生恐龙或翼龙模型仍完全位于水下。

`TravelersAzureModelRendererMixin` 在以下条件成立时校正根骨骼的额外垂直偏移：

- 当前骨骼名称为 `root`。
- 实体为 `JSAnimalBase`。
- 实体不是 `JSAquaticBase`。
- 实体正处于 `RISING` 或 `BOBBING`。
- Travelers 动画器和根骨骼状态可用。

该修正只处理睡眠姿态造成的模型根节点偏差，不负责沉浮运动。修正只允许向下压低模型，禁止把模型额外抬离真实碰撞箱水线。所有生物的上下沉浮均由服务端真实实体位置完成；海王龙继续保留自己的专用姿态稳定逻辑。

## 年龄与成长阶段

### 年龄记录

`DinosaurAgeData` 保存：

- 是否初始化。
- 是否为刷怪蛋强制生成的成年生物。
- 推算出生世界时间。
- 上次观察世界时间。
- 上次观察成长百分比。

年龄附件会持久化并同步到客户端。读取时会修复非法世界时间、负观察时间和非有限成长百分比。

### 年龄推算

`DinosaurAgeSystem` 同时提供：

- 游戏内年龄 tick。
- 成年所需游戏 tick。
- 估算现实年龄。
- 估算成年现实年龄。

已知生物使用精细年龄表。新生物根据尺寸等级和水生/飞行分类生成安全回退年龄。成年后继续根据出生时间增长，不会永远停留在成年阈值。

可通过以下接口为附属模组补充精确成年年龄：

```java
DinosaurAgeSystem.registerAdultAge(speciesId, adultAgeYears);
```

### 刷怪蛋规则

`EntityTypeSpawnEggMixin` 在刷怪蛋生成结束后检查结果：

- 只要生成实体是 `JSAnimalBase`，规则就会生效。
- 普通使用刷怪蛋生成成年生物。
- 玩家潜行使用刷怪蛋生成幼年生物。
- 设置成长阶段后刷新碰撞箱。
- 同步初始化对应年龄附件。

因此 Jurassic Saga 新增生物和刷怪蛋时，不需要把物种加入支持列表。

## 生物画像系统

### `DinosaurSizeProfile`

画像包含：

| 字段 | 含义 |
| --- | --- |
| `speciesId` | 实体注册 ID；无法解析时为 `jsrevise:unknown` |
| `eggType` | 推导或精细配置的蛋型 |
| `lifecycleStage` | 幼体、青年、亚成年或成年 |
| `sizeBucket` | `MICRO` 到 `TITANIC` 的六级体型 |
| `width` | 当前碰撞箱宽度 |
| `height` | 当前碰撞箱高度 |
| `majorDimension` | 宽高中的较大值 |
| `footprintArea` | 宽度乘高度的近似面积 |
| `growthPercentage` | 归一到 `0..100` 的成长百分比 |
| `surfaceExposureRatio` | 水面露出比例 |

### 回退规则

未知生物会根据能力和体型推导蛋型：

- 水生小型生物优先使用 `FISH`。
- 较大水生生物使用 `ALLIGATOR`。
- 飞行动物按体型使用 `CHICKEN`、`ALLIGATOR` 或 `OSTRICH`。
- 陆生生物按体型使用 `BASILISK`、`CHICKEN`、`ALLIGATOR` 或 `OSTRICH`。

画像失败不会抛出到游戏主循环。开启调试日志后，每个未精细配置的物种只记录一次自动推导日志。

### 扩展接口

其他附属模组可注册画像覆盖：

```java
DinosaurSizeSystem.registerProfileOverride(speciesId, profile -> {
    return new DinosaurSizeProfile(
            profile.speciesId(),
            profile.eggType(),
            profile.lifecycleStage(),
            profile.sizeBucket(),
            profile.width(),
            profile.height(),
            profile.majorDimension(),
            profile.footprintArea(),
            profile.growthPercentage(),
            customExposureRatio
    );
});
```

覆盖结果会集中验证 ID、枚举、尺寸、成长比例和露出比例。覆盖函数返回空值、
NaN、负尺寸、越界比例或抛出异常时，核心系统会忽略该覆盖、保留自动画像，
并只记录一次警告。

## 恐龙博士眼镜 HUD

### 目标选择

佩戴眼镜后，HUD 优先使用 Minecraft 当前命中的 `JSAnimalBase`。直接命中和
缓存目标同样必须位于玩家 8 格观察距离内；未直接命中实体时，会在玩家视线方向
最多 8 格内执行碰撞箱射线检测。

目标每 3 tick 刷新一次，观察快照每 5 tick 缓存一次，避免每帧重复扫描和反射。

### 显示信息

HUD 当前可以显示：

- 恐龙名称。
- 年龄。
- 当前与最大生命值。
- 性别。
- 饥饿度。
- 口渴度。
- 心情值。
- 麻醉生效倒计时。
- 麻醉剩余时间。
- 已排队追加的麻醉时间。
- 基因图标和基因名称。

缺少某个 Jurassic Saga 模块、方法或字段时，仅隐藏对应信息并显示未知值，不会使整个 HUD 失效。

### 动态视觉

- 面板整体按 `0.90` 比例渲染。
- 面板使用圆角渐变背景。
- 标题左侧优先显示当前生物的 `jurassicsaga:<species>_coin` DNA 图标。
- 缺少对应 DNA 物品时回退到恐龙博士眼镜图标。
- 主题色从 DNA 物品模型 `layer0` 指向的底图中计算。
- 颜色采样忽略透明、过暗和低饱和度像素。
- 资源缺失或解析失败时使用默认蓝灰色 `#4F718E`。
- DNA 主题色按物种缓存，在断线或世界卸载时清理。

### 观察缓存

`DinosaurObservationSystem` 使用维度 ID 和实体 UUID 作为缓存键：

- 快照有效期为 5 tick。
- 过期清理周期为 200 tick。
- 最大保存 2048 条。
- 超过上限时直接清空，防止长期增长。
- 实体离开客户端世界时按 UUID 失效。
- 断线和客户端世界卸载时全部清空。

### 反射兼容

Jurassic Saga 部分模块接口不稳定或没有公开统一 API，因此观察系统使用
`ReflectionAccessCache`：

- 按 `Class + 方法名` 缓存方法。
- 按 `Class + 字段名` 缓存字段。
- 支持从父类查找私有成员。
- 调用失败时返回空值。
- 百分比自动兼容 `0..1` 和 `0..100` 两种范围。

基因解析会依次尝试公开方法、字段、注册 ID 和类名推导。无法确定有效物品 ID 的条目会被跳过。

## Curios 与 Jade

### Curios

Curios 是可选依赖。未安装 Curios 时，相关类不会被主动调用，眼镜仍可通过原版头部栏使用。

数据文件：

- `data/curios/tags/item/head.json`
- `data/jsrevise/curios/entities/players.json`

### Jade

Jade 插件使用公开 `ITooltip` API。目标为 `JSAnimalBase` 时，会从 Jade
提示中移除 Jurassic Saga 的性别行和基因段，避免与恐龙博士眼镜 HUD 重复。

Jade 未安装时不影响核心模组运行。

## Jurassic Saga 群系控制

配置 `disable_jurassicsaga_biome_generation` 默认为 `true`。

启用后，`JSTerrablenderMixin` 会在 Jurassic Saga 注册 TerraBlender 主世界
区域和对应地表规则前取消这两条入口，从源头阻止它的自定义群系进入主世界气候
分布。`MultiNoiseBiomeSourceMixin` 仍作为兜底，在噪声群系选择完成后将已知
Jurassic Saga 群系替换为原版群系：

| Jurassic Saga 群系 | 原版回退群系 |
| --- | --- |
| `burnt_forest` | `forest` |
| `grassy_plains` | `plains` |
| `magma_cave` | `dripstone_caves` |
| `redwood` | `old_growth_pine_taiga` |
| `redwood_plains` | `meadow` |
| `sulphur_springs` | `windswept_hills` |
| `trench` | `deep_lukewarm_ocean` |

未列出的 Jurassic Saga 群系在兜底替换层保持原结果；正常情况下 TerraBlender
源头拦截会先阻止当前 Jurassic Saga 自定义群系进入主世界分布。关闭配置后不取消
TerraBlender 注册，也不执行返回值替换。原版回退群系 Holder 按当前服务器实例缓存，
停服时清理，世界生成热路径只执行映射查询。

## Data Attachment

### `jsrevise:dinosaur_age`

类型：`DinosaurAgeData`

- 持久化：是。
- 客户端同步：是。
- 用途：年龄推算和刷怪蛋成年年龄下限。

### `jsrevise:anesthetic`

类型：`AnestheticData`

- 持久化：是。
- 客户端同步：是。
- 用途：当前麻醉结束时间和待生效剂量。
- 空数据会在服务端清理。

### `jsrevise:anesthetic_float`

类型：`AnestheticFloatData`

- 持久化：否。
- 客户端同步：是。
- 用途：漂浮阶段、目标高度、水面高度和视觉状态。
- 水面扫描缓存和 tick 防重字段只保存在运行期。
- 实体卸载后由附件生命周期自动释放。

## 网络协议

网络版本：`3`

### C2S：`fire_anesthetic_crossbow`

类型：`FireAnestheticCrossbowPayload`

字段：

- `mainHand`：请求使用主手或副手。

当前客户端只发送主手请求。服务端会根据字段取得对应手的物品，并确认它是
`AnestheticCrossbowItem` 后才尝试发射。

### S2C：`surface_effect`

类型：`SurfaceEffectPayload`

字段：

- `entityId`
- `surfaceY`
- `effect`
- `width`
- `height`

客户端会修复非法水面高度，并将宽高限制在 `0.2..12.0`。非法事件序号回退为 `APPROACH`，实体不存在时忽略数据包。

Data Attachment 的年龄、麻醉和漂浮状态使用 NeoForge 附件同步机制，不额外定义重复的数据包。

## Mixin 清单

| Mixin | 目标 | 用途 |
| --- | --- | --- |
| `JSCommonMixin` | Jurassic Saga `JSCommon` | 补充附属模组初始化入口 |
| `JSAnimalAnimationSystemsMixin` | Jurassic Saga `JSAnimal` | 麻醉期间通用接管服务端睡眠动画，避免物种动画覆盖 |
| `JSAnimalBaseSystemsMixin` | `JSAnimalBase` | 在服务端 AI 步开始前准备麻醉睡眠状态并抑制移动 |
| `JSAvianBaseSystemsMixin` | `JSAvianBase` | 阻止麻醉翼龙重新起飞和生成主动飞行速度 |
| `JSEntityDataHolderSystemsMixin` | Jurassic Saga `JSEntityDataHolder` | 桥接麻醉期间的 `isSleeping()` 读取 |
| `LivingEntityTravelMixin` | Minecraft `LivingEntity` | 过滤麻醉生物主动旅行输入并保留被动碰撞移动 |
| `TravelersSmartAnimalBaseMixin` | Travelers `SmartAnimalBase` | 麻醉期间暂停任务、移动和导航控制器 |
| `EntityTypeSpawnEggMixin` | Minecraft `EntityType` | 控制刷怪蛋成年/幼年阶段 |
| `JSTerrablenderMixin` | Jurassic Saga `JSTerrablender` | 按配置取消 Jurassic Saga TerraBlender 群系区域和地表规则注册 |
| `MultiNoiseBiomeSourceMixin` | Minecraft `MultiNoiseBiomeSource` | 替换 Jurassic Saga 群系 |
| `CustomHeadLayerMixin` | Minecraft `CustomHeadLayer` | 隐藏头部栏中的眼镜物品模型 |
| `TylosaurusAnimatorMixin` | 海王龙动画器，可选字符串目标 | 稳定麻醉漂浮姿态和尾部动画 |
| `TravelersClientAnimatorMixin` | Travelers 通用动画器 | 临时关闭非水生麻醉漂浮的物理链与朝向追踪 |
| `TravelersAzureModelRendererMixin` | Travelers Azure 渲染器 | 抵消非水生睡眠模型根骨骼下沉 |

维护 Mixin 时应优先选择稳定基类和稳定方法返回点。不要为普通物种类新增强制 Mixin。物种专用和视觉专用注入必须优先使用 `require = 0`。

## 配置

配置类型：NeoForge `COMMON`

配置文件通常为 `config/jsrevise-common.toml`。

| 配置键 | 默认值 | 用途 |
| --- | --- | --- |
| `debug_logging` | `false` | 输出自动画像和兼容性自检详情 |
| `disable_jurassicsaga_biome_generation` | `true` | 使用原版群系替换指定 Jurassic Saga 群系 |

客户端通过 NeoForge `ConfigurationScreen` 提供独立配置界面。

修改配置系统时必须同步：

1. `JSReviseConfig` 的配置定义。
2. NeoForge 配置界面使用的翻译键。
3. `zh_cn.json`。
4. `en_us.json`。
5. 本文档中的配置表。

## 资源

主要资源位于 `src/main/resources`：

- `assets/jsrevise/lang`：中英文语言文件。
- `assets/jsrevise/models/item`：三个物品及麻醉弩三段拉弦、六档剩余弹量状态模型。
- `assets/jsrevise/textures/item`：16×16 物品纹理，包括眼镜正视图及麻醉弩待机、三段拉弦和六档剩余弹量状态。
- `assets/jsrevise/textures/models/armor`：与物品配色一致的眼镜备用装备纹理。
- `data/curios`：Curios 头部槽位标签。
- `data/jsrevise/curios`：玩家 Curios 槽位声明。
- `jsrevise.mixins.json`：Mixin 配置。

构建会包含 `src/generated/resources`，但排除 `.bbmodel` 和数据生成缓存。

## 构建与运行

### 环境要求

- JDK 21。
- Windows PowerShell 或其他可运行 Gradle Wrapper 的终端。
- 能访问项目配置的 Maven 仓库。

### 常用命令

Windows：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
.\gradlew.bat assemble
.\gradlew.bat test assemble
.\gradlew.bat runClient
.\gradlew.bat runServer
```

其他平台：

```bash
./gradlew test assemble
```

构建产物：

```text
build/libs/jsrevise-<version>.jar
```

当前已验证产物：

```text
build/libs/jsrevise-1.0.59.jar
```

修改模组代码或资源并重新发布构建时，需要同步更新
`gradle.properties` 中的 `mod_version`。仅修改维护文档时不要求提升模组版本。

## 自动化测试

当前共有 16 个测试类、65 项 JUnit 测试，以及 5 项 GameTest。

覆盖内容：

- 年龄附件非法数据修复。
- 麻醉绝对世界时间和剩余时间计算。
- 相同生效 tick 的剂量合并。
- 多剂量原子生效。
- 麻醉存档非法值修复和超长时间限制。
- 非正数存档剂量丢弃、64 条网络剂量边界和越界数据拒绝。
- 飞行动物水体捕获状态保持。
- 麻醉周期只执行一次行为任务清理。
- 待生效剂量到点后，所有已注册 `JSAnimalBase` 在主模组服务端动画器执行前都会先完成 active 推进并读取到睡眠状态；客户端只读桥接也会把到点 pending 视为睡眠窗口。
- 麻醉期间所有已注册 `JSAnimalBase` 会跳过 Travelers 任务/移动/导航控制器，但仍推进服务端睡眠动画 transition，避免物种动画器回发站立或飞行动画。
- 流体接触必须与碰撞箱真实重叠，水面支撑不能超过允许间隙。
- 只有 `RISING` 和 `BOBBING` 阶段抑制宿主水中移动。
- 漂浮附件非法坐标修复。
- 漂浮竖直控制不会清除玩家碰撞或水流产生的水平速度。
- 麻醉首次生效时会清除全部已注册 `JSAnimalBase` 的旧主动速度，可切换飞行状态的飞行动物同时退出飞行；固定飞行类型也必须停止原方向动量。同一麻醉周期只清除一次。
- 所有已注册飞行动物在麻醉期间拒绝主动 `travel` 输入，并验证完整 `aiStep` 不会重新生成朝向水平速度。
- 旅行参数在虚方法分派前统一过滤，当前重写 `travel` 的陆生、水生、两栖和飞行实体均有父类外层保护。
- 非水生漂浮阶段会通过实体碰撞移动实际消费被动力量，且不会让宿主旅行链改变受控 Y 坐标；该检查会遍历 Jurassic Saga 当前注册的全部生物。
- 麻醉水生生物会接受流体推动，解除麻醉后恢复 Jurassic Saga 默认行为。
- 陆生睡眠姿态参考高度。
- 体型越大露出比例越低。
- 巨型和超巨型生物获得更深的真实物理水线。
- 超巨型陆生生物的可见比例继续低于巨型生物。
- 水生生物下沉振幅。
- 幼年飞行动物不会整体悬空。
- 代表性陆生和飞行动物目标高度。
- 实体原点与碰撞箱底面不一致时的水线校正。
- 上下方向最终位置修正速度一致。
- 三角波上下时长相等。
- 最低点无停顿并立即换向。
- 水生周期保持为同体型普通周期的 `75%`，并验证整周期真实位置能够逐 tick 跟随目标波形。
- 大型水生生物具有可见的最低真实沉浮振幅和足够的逐 tick 位移。
- 粒子数量随当前体型增长。
- 换向粒子爆发强于过程环境粒子。
- 小型生物使用更低频的环境粒子。
- 小型生物的破水与过程粒子仍保持可见。
- 未知水生、飞行和陆生生物的蛋型推导。
- 非法 profile override 回退和合法 override 保留。
- HUD 八格观察边界、反射方法缓存、继承私有成员、非法成员降级和百分比归一化。
- 非法或异常基因字段的局部降级。
- 群系 Holder 缓存的单次初始化、不可变快照和按服务器所有者清理。
- 物品贴图尺寸、眼镜逐像素镜像和圆角切角、弩状态轮廓、与原版弩轮廓的差异以及麻醉针方向和关键配色。
- GameTest 会创建 Jurassic Saga 当前注册的所有生物并验证通用 profile 完整性。

标准验证命令：

```powershell
.\gradlew.bat test assemble
```

GameTest 验证命令：

```powershell
.\gradlew.bat runGameTestServer
```

自动化测试不能替代游戏内视觉验证。模型姿态、水面露出比例、粒子可见性、HUD 布局和独立服务器同步仍需要整合包实际测试。

## 维护检查清单

### 新增通用生物功能

- 优先以 `JSAnimalBase` 为入口。
- 使用 `JSAquaticBase`、`JSAvianBase` 或运行时能力分类。
- 不使用固定物种白名单决定功能是否生效。
- 为未知生物提供安全回退。
- 单个生物数据异常不得中断整个服务器 tick。

### 新增特殊物种行为

- 先确认通用画像和能力分类无法表达需求。
- 精细参数优先通过 profile override 提供。
- 只有动画或模型结构确实特殊时才增加物种专用客户端钩子。
- 专用视觉 Mixin 使用 `require = 0`。
- 不将特殊物种逻辑扩散到通用状态机。

### 修改附件

- 明确是否需要持久化。
- 明确是否需要客户端同步。
- 对 NBT 和网络数据进行范围限制和非法值修复。
- 检查旧存档缺少字段时的默认行为。
- 更新附件相关单元测试和本文档。

### 修改网络

- 更新 `NETWORK_VERSION` 或确认协议仍向后兼容。
- 服务端重新验证客户端请求，不信任客户端物品和状态。
- 限制数值范围、枚举序号和数据规模。
- 在正确的主线程执行世界和实体操作。
- 更新本文档中的协议说明。

### 修改配置

- 同步配置定义、NeoForge 配置界面翻译、中英文语言文件和 README。
- 检查服务端与客户端读取侧是否正确。
- 说明默认值变化对现有整合包的影响。

### 修改 HUD

- 避免每帧扫描大范围实体。
- 避免每帧执行未缓存反射。
- 缺少模块时按字段降级。
- 在断线、维度切换和实体移除时清理缓存。
- 使用实际 GUI 缩放和多语言文本验证布局。

### 发布前

1. 更新模组版本。
2. 执行 `.\gradlew.bat test runGameTestServer assemble`。
3. 检查编译警告和 Mixin 目标。
4. 确认最终 JAR 版本元数据。
5. 在独立客户端/服务器环境验证麻醉状态同步。
6. 使用陆生、水生和飞行生物分别验证漂浮。
7. 使用未知或新加入的 Jurassic Saga 生物验证自动画像回退。
8. 检查 Curios、Jade 和无可选模组环境。

## 当前状态

截至 `1.0.59`：

- 项目已完成模块化拆分。
- 麻醉和年龄数据已迁移到 NeoForge Data Attachment。
- 麻醉状态由服务端推进并同步。
- 漂浮使用独立状态机和三角波；陆生、水生和飞行动物均由服务端驱动模型与碰撞箱真实沉浮。
- 大型生物按碰撞箱高度、宽度和尺寸等级获得更深水线，`TITANIC` 级生物进一步降低露出比例。
- 海王龙使用约 `165..195` tick 的水生周期，速度接近其他恐龙；该周期由普通周期按 `75%` 自动推导，并保留防止实体静止的水生位置修正能力与专用姿态修正。
- 飞行动物具有持续水体捕获；麻醉生效时先清除旧飞行动量，随后持续阻止起飞、飞行朝向推进和主动旅行输入。非水生漂浮阶段仍以真实实体碰撞移动保留麻醉后产生的玩家碰撞、水流和其他被动水平移动。
- 麻醉睡眠状态会在 `JSAnimalBase.customServerAiStep()` 开始时提前写入；到点的待生效剂量会先推进为 active，并在 `isSleeping()` 读取处桥接。客户端只读桥接会覆盖“pending 已到点但 active 同步未到”的短窗口。
- `JSAnimal` 服务端动画入口会在麻醉期间统一推进睡眠 transition，并取消当前 tick 的普通物种动画发送。Travelers 的任务、移动和导航控制器仍会被暂停，但服务端动画器不再被整体取消，因此霸王龙、卢多翼龙以及固定飞行态/简化动画动物不会在入睡后短暂切回站立、行走或飞行姿势。
- 麻醉首次生效时会完整停止所有当前运行的 Travelers 任务并解除乘客关系；麻醉期间任务、移动和导航控制器暂停，因此抓取、俯冲、战斗跳跃及未来同类任务不能继续缓存目标或直接写入追踪速度。
- 漂浮状态必须由真实流体接触或实体正下方无方块阻挡的水面支撑；旧水面缓存不能再让已被推上沙砾等陆地方块的恐龙继续沉浮。
- 水面效果通过 S2C 事件和客户端实体 tick 补偿生成，粒子数量随当前体型缩放，并采用“破水/换向明显、周期过程适量”的阶段化节奏。
- 海王龙使用专用可选动画稳定钩子。
- 海王龙动画钩子使用 `@Pseudo` 字符串目标，目标类缺失时不会阻止启动。
- 非水生睡眠模型具有只允许向下修正的通用根骨骼校正，禁止客户端把模型抬离真实水线。
- 恐龙博士眼镜 HUD 使用 DNA 图标和动态主题色。
- 恐龙博士眼镜采用正视、几何对称的深蓝圆角矩形细框、灰白镜片、银色铰链和黑色直镜腿，并按统一左上光源进行非镜像着色。
- 麻醉弩采用原版正交俯视视角和完整拉弦动画结构；左键发射不再触发普通攻击挥手，六档剩余弹量模型会让弩弦随每支麻醉针发射逐步回到待机位置。前端白色像素已移除，尾部进一步缩短并提亮新尾点，左右弓臂外端轮廓已补齐为严格对称，弦的对称固定端点与各阶段活动弦线保持连续。
- 新增 `JSAnimalBase` 生物可以使用自动画像回退。
- profile override、基因反射和麻醉网络数据具有集中非法值降级。
- 恐龙博士眼镜的直接命中与缓存目标严格遵守 8 格观察距离。
- Jurassic Saga TerraBlender 主世界群系区域和地表规则注册会按配置被源头取消；
  `MultiNoiseBiomeSource` 层保留已知群系到原版群系的兜底替换，并按服务器缓存回退 Holder。
- 客户端 Mixin 和 Shift tooltip 已与通用服务端代码隔离。
- `test`、`runGameTestServer` 和 `assemble` 已成功执行。
- 65 项 JUnit 测试和 5 项 GameTest 全部通过。

仍需在实际整合包中重点验证：

- 艾雷拉龙、腕龙、鸟鳄、卢多翼龙及其他陆生/飞行动物的模型是否按预期露出水面。
- 成年和幼年生物的露出比例是否符合“体型越大，露出越少”。
- 海王龙的沉浮振幅、水平姿态和尾部动画。
- 接近水面、首次破水和沉浮换向时的粒子可见性。
- DNA 图标、动态主题色、85% HUD 整体缩放、等效 90% 字体和透明度在不同 GUI 缩放下的表现。
- 独立服务器中的年龄、麻醉倒计时、漂浮阶段和粒子事件同步。

如果游戏内测试结果与自动化测试不一致，应优先区分以下三层：

1. 服务端实体碰撞箱和目标高度。
2. Jurassic Saga 睡眠动画及 Travelers/Azure 根骨骼变换。
3. 客户端水面和粒子渲染。

不要在未确认问题层级前继续单纯调整服务端目标高度，否则可能造成碰撞箱、模型和粒子水面之间进一步失配。

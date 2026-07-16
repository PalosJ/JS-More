# JS-revise

JS-revise 是面向 Minecraft 1.21.1 NeoForge 的 Jurassic Saga 附属模组。项目以服务端权威的数据、实体状态和真实运动为基础，提供麻醉、睡眠与漂浮、年龄画像、恐龙博士眼镜 HUD、群系生成控制和恐龙捕获箱等功能。

本文档描述 `1.0.134` 候选源码。版本是否可发布仍以完整自动化、限时独立服务器和实际整合包验收记录为准；本文档不把候选版本等同于已发布或已完成实机验收。

## 1. 支持矩阵

| 项目 | 当前边界 | 说明 |
| --- | --- | --- |
| Mod ID | `jsrevise` | Java 包根为 `com.palos.jsrevise` |
| 作者 | `Palos` | 显示名称为 `JS-revise` |
| 候选版本 | `1.0.134` | `gradle.properties` 是版本单一来源，构建时展开到 metadata |
| Minecraft | `1.21.1` | metadata 固定为 `[1.21.1]` |
| NeoForge | `21.1.232` 及以上 | metadata 为 `[21.1.232,)` |
| Java | `21` | Mixin compatibility level 与编译 toolchain 均为 Java 21 |
| Jurassic Saga | 当前基线 `0.2.1` | 必需依赖范围 `[0.2.1,0.3.0)` |
| Travelers Lib | 当前二进制基线 `0.7.1` | 必需依赖范围 `[0.7.1,0.8.0)`；其他 `0.7.x` 需要取得对应二进制逐版验证 |
| Curios | 构建基线 `9.5.1` | 可选依赖，metadata 为 `[9.5.1,)`，服务端和客户端均可缺失 |
| Jade | `15.0.0` 及以上 | 可选客户端依赖，用于按眼镜佩戴状态去重动物信息和显示捕获箱余量 |
| Create: Aeronautics | 精确自动化基线 `1.3.0` | 仅支持 Simulated `1.3.0`、Sable `2.0.3`、Sable Companion `1.6.0` 与 Create `6.0.10-280` 的正式二进制组合；其他结构关闭移动兼容 |
| 网络协议 | `6` | 当前 3 个 C2S 与 4 个 S2C payload；本版本不改变协议号、payload、codec 或附件 ID |

Jurassic Saga `0.2.1` 不依赖外部 AzureLib 模组。Travelers Lib 自身包含名称带 `azure`、`Az` 或 `AzureLib` 的内部动画实现；这些包名不能作为删除依赖或 Mixin 的依据。当前 Gradle 依赖和模组 metadata 都没有声明外部 AzureLib。

Travelers Lib 的 metadata 范围仍为 `[0.7.1,0.8.0)`，但服务端字节码兼容补丁只对真实 `0.7.1` 类结构建立了精确指纹和测试。未验证的 `0.7.x` 不应仅因落在版本范围内就宣称兼容。

## 2. 玩家功能

### 麻醉药水、麻醉针、麻醉镖与麻醉弩

- `jsrevise:anesthetic_potion` 是不可饮用的普通物品，不属于原版药水体系。它只能在酿造台中由虞美人加粗制的药水制成，不能继续用红石粉、萤石粉、火药或龙息加工成长效、强效、喷溅或滞留版本。
- 工作台中把麻醉药水放在九宫格中心，并在上、下、左、右各放一个 `jurassicsaga:empty_syringe`，会输出 4 个 `jsrevise:anesthetic_syringe`。麻醉针用于装填捕获箱的麻醉余量和合成麻醉镖；旧 `anesthetic_syringe_projectile` 实体 ID 仅为已有世界中的历史实体保留兼容。
- `jsrevise:anesthetic_dart` 由麻醉针与其右上方相邻的羽毛合成；两者可随合成网格整体平移，但不能镜像、交换或加入额外物品。该方向配方以标准 shaped 元数据公开，因此 JEI 等配方查看器可通过麻醉镖查看配方、通过麻醉针查看用途。麻醉镖物品侧视明确显示后法兰、窄尾杆和阶梯羽片；实体使用两张正交双面 cutout 羽片，不含黑色矩形外框或正体积交叠，并以模型定义的 `58.5%` 统一比例渲染。飞行渲染以针尖为前方，不作为普通箭使用。
- `jsrevise:anesthetic_crossbow` 耐久 650，只接受麻醉镖，弹仓最多 6 发。独立的待机、三阶段拉弦和六档装填贴图会显示拉弦、装填比例及剩余镖数；麻醉针不再是弩弹药。
- 左键射击仍通过 `fire_anesthetic_crossbow` C2S 请求触发；服务端重新检查手、物品、装填状态和冷却后才生成投射物。每发间隔 10 tick，权威装填数量保存在 `CUSTOM_DATA.LoadedDarts`；只有显式存在旧 `LoadedSyringes` 的历史弩会迁移，伪造的 charged syringe 不会被当作有效弹药。
- 麻醉镖命中方块后沿用原版箭的落地、1200 tick 消失、存档和拾取语义；生存玩家可拾回，创造玩家射出的镖按原版采用仅创造可拾取规则。命中任何实体后都会立即消失，不会留在生物体表；只有成功命中可麻醉 `JSAnimalBase` 时才额外造成 1 点伤害并施加麻醉。
- 麻醉延迟和持续时间由运行时体型、食性、蛋型及陆生/水生/飞行类别推导。麻醉针、历史针投射物、麻醉镖和捕获箱自动注射共用同一个 resolver；新剂量以 120 秒为基准再应用这些倍率，已有存档中的 active/pending 时长不追溯翻倍。重复注射会缩短后续等待并叠加持续时间；pending 队列最多 64 项，时间运算有边界和饱和保护。

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
- 佩戴眼镜并观察 8 格内的 Jurassic Saga 动物时，HUD 显示生命值、年龄、成长阶段、性别、饱食度、口渴度、心情、麻醉状态、基因和自然下蛋进度；饱食度、口渴度和心情把一位小数百分比直接放在名称冒号后，并在下一行绘制进度。观察装有恐龙的捕获箱时，左侧面板只显示恐龙详情，右侧独立面板按麻醉、水源、肉食、草食顺序显示四条余量进度、捕获时长和箱体耐久；余量点数内联为“麻醉余量：30/40”等格式，耐久按剩余值向下取整显示百分比，例如 `375/500 → 75%`。空箱也会显示四条空进度与 `100%` 耐久。
- 三项生命体征、自然下蛋进度和捕获箱四项余量共享同一进度条样式：内部轨道为 66×7 px，空白部分使用 `0x2EFFFFFF` 半透明灰色轨道；外侧增加 1 px、与正文同色的 `#BFD1E6` 逻辑边框，总占位为 68×9 px。填充宽度按 `floor(66 × progress)` 计算，0%、50% 和 100% 分别为 0、33 和 66 px；每组保持 24 px 高。缺失、NaN 或无限生命体征显示“未知”并保留空轨道；捕获栏不再预留右侧计数列。下蛋进度使用取自 Jurassic Saga 蛋素材的奶油色渐变 `#EED1AF → #FFEDC6`。
- 两栏普通名称和值的有效字号统一为 `0.98`，标题为 `1.05`；同一主行的 label 与 value 共用按该字号计算的 Y 锚点，避免字号差异造成上下错位。灰色麻醉增加量仍为普通 value 字号的 `0.75`，标题、基因和独立灰色次行仍按各自高度定位；基因标签继续保持现有有效 `0.585`。恐龙详情左栏顶部 padding 保持 8 px，底部 padding 为 7 px；正文从标题内容起点向右偏移 20 px，除标准 6 px 右侧留白外另加 5 px 专用余量。内容起点、捕获栏测量与双栏布局不变。捕获栏除标题外的正文与进度条继续从标题文字起点绘制，即相对面板内容起点偏移 18 px，测量时包含同一偏移以避免裁切。
- 捕获箱面板使用以 `#A9A79E` 为基色的深暖灰同源渐变，顶部为 `0xDA282825`、底部为 `0xE61C1C1A`；圆角 3 和面板自身的 1 px 半透明渐变边框保持不变。水平 padding 为 6 px，上下 padding 各为 4 px，完整有效面板高 147 px；标题图标为 15 px，图标与标题文字按各自实际高度在 19 px 标题行内垂直居中，普通信息行高 12 px。
- 宽屏时左侧恐龙面板与屏幕中心保持 80 个虚拟像素净空，右侧捕获箱面板保持 96 个；单栏模式使用各自的对应净空。空间不足时依次尝试 100%、80% 和 75% 缩放，再分别按可用空间收紧但不低于 24 个虚拟像素；仍放不下时把右面板右对齐置于左面板下方，最低缩放为 75%。不可读箱只呈现能安全确认的余量和恢复提示，不伪造捕获时长或耐久。
- 捕获时长以及恐龙详情 HUD 中的麻醉生效延迟、麻醉剩余和灰色 `(+增加量)` 都按一位小数格式化：不足 60 秒显示秒，不足 60 分钟显示分钟，其余始终显示小时；正好 60 秒和 60 分钟分别切换为 `1.0` 分钟和 `1.0` 小时。麻醉字段只有正数才显示，顺序固定为麻醉剩余在前、麻醉生效在后；remaining 与 queued 同时存在时，灰色增加量从冒号后 value 的起点移到下一视觉行，宽度按主行与次行较宽者测量，麻醉生效排在两者之后。只有 queued 时仍保持同行显示，并且不伪造基础 `0.0 秒`。
- 自然下蛋进度读取动物自身 `eggTime`，不把妊娠字段或已生成蛋的孵化进度混入该指标。客户端低频请求，服务端重新验证眼镜、目标、距离、字段和数值。
- 基因列表最多 64 项，基因 ID 最长 128 字符，显示名最长 256 字符；`ItemId` 可缺省。单个反射字段失败时只隐藏对应 HUD 项，不让整个面板失效。
- 捕获箱观察缓存使用客户端单调 session clock，不跟随服务端 `gameTime` 校时：每个客户端逻辑 tick 递增 `logicalTick`，只有 Level 对象身份或维度变化、登出、客户端 Level 卸载及显式生命周期清理才更换 generation 并清缓存，同一 Level 的原始 `gameTime` 回退不会造成 HUD 刷新。普通隐藏 HUD 或摘下眼镜只清显示与观察状态，不重置 session clock。缓存身份还包含 sublevel UUID；现有协议仍只传 `BlockPos`，因此以单飞请求和 tombstone 阻止迟到回复附着到被复用的 plot 坐标。若回复丢失，该 plot 在当前客户端会话可能保持不可用，这是协议 6 下优先防止串箱的数据安全权衡。
- 捕获快照采用 stale-while-revalidate：成功快照超过 20 tick 后在后台刷新，持续观察本地仍占用且可读的箱体时始终显示 last-known-good；瞬时 unavailable、畸形响应和已经切换目标后的迟到回复不能清除或重新激活它。快照分别记录接收时间与最后观察时间；本地确认释放、不可读、方块消失、区块卸载、越界或 session 变化时立即失效。非当前快照从最后观察起第 200 tick 仍保留、第 201 tick 清除；缓存最多 512 项，按最后观察、接收、维度和位置确定性淘汰最旧非当前项，请求与清理使用 elapsed 比较以避免 `Long.MAX_VALUE` 下的逐帧风暴。
- 完整目标解析保持每 3 个逻辑 tick 一次。对同一仍在本地有效的捕获箱，从最后一次确认起保留 5 tick 的短暂 MISS 宽限，即典型 `t0` 确认、`t3` 首次 MISS 继续显示、`t6` 再次 MISS 清除；检测到不同动物或捕获箱时立即切换，不沿用旧目标宽限。
- Jade 存在且未佩戴眼镜时，完整保留 Jurassic Saga 的性别、基因等信息，并为捕获箱显示麻醉、水源、肉食、草食四项余量的文字与百分比；佩戴眼镜时才过滤重复动物行并省略四项捕获箱余量行，Jade 标准标题与模组名仍保留。

### 恐龙捕获箱

- 新 ID 为 `jsrevise:dinosaur_capture_box`；旧 `jsrevise:dinosaur_capture_cage` 作为物品、方块和方块实体别名保留。捕获箱最大堆叠 1，只能捕获 active 麻醉中的 `JSAnimalBase`。
- 捕获后形成 2×4×2、共 16 个 part 的逻辑单体。`CaptureBoxStructure` 与 `CaptureBoxAccess` 统一描述四种朝向、局部 placement、controller 和 canonical 校验；完整结构必须由同一 block 类型、facing、offset 和 controller 归属组成，且只有规范 controller 持有正确 BE。服务端在写入前一次性预检 16 个已加载位置、建筑高度及外部 BE，placement ledger 只回滚仍由本事务拥有的精确目标 state；任意 part、controller 内容提交或最终 canonical 验证失败都会完整回滚。成功放置 controller 与全部 part 后才解除 leash、骑乘和乘客关系并移除原实体；任何失败都保持原实体关系不变。
- 被拴住的动物只在捕获提交成功后解除拴绳。生存玩家返还一根拴绳，背包满时在玩家处掉落；创造玩家不返还。
- 捕获数据保存实体类型、原 UUID、清洗后的实体 NBT、相对麻醉状态、捕获/结算时间、箱体耐久、生命体征和余数。`Passengers`、leash、载具、位置、运动、落地及摔落瞬态字段不会跨箱体恢复。
- 箱体耐久最大 500。处于麻醉保护区间时不消耗；其余时间每 20 tick 扣 1。手持、背包、打开容器、掉落物和已放置方块均参与结算，未加载容器和区块不会被全局扫描。临时 20 容量数据按剩余比例迁移回 500，例如 `15/20 → 375/500`、`1/20 → 25/500`；旧 100 容量数据继续按 1.0.124 的既定规则保留已损耗点数映射到 500（`75 → 475`、`1 → 401`），直接升级与逐版本升级结果一致。任一旧容量的 0 都保持 0；首次服务端扫描会真正写回 `DurabilityCapacity=500`。HUD 和现有有效箱物品 tooltip 只把精确剩余值格式化为整数百分比；标准 `DAMAGE`/`MAX_DAMAGE`、Damage mirror、原版耐久条和第三方耐久模组的精确数据入口继续同步到 500。
- 每个捕获箱独立保存麻醉、水源、肉食和草食四类余量；麻醉上限 40，水源、肉食和草食上限各 20。余量在空箱、捕获、物品与方块互转、保存重载、任意 part 破坏掉落、手动释放和再次捕获之间继续保留；手动释放只清除恐龙载荷与 Damage mirror，耐久归零并成功转为破损箱时余量才随载体损毁。
- 手持麻醉针或食物右键已放置捕获箱时，每次只装入 1 点对应余量，不再立即注射或喂食。水桶在食性判断前识别，每桶增加 10 点水源，空间不足时补满且溢出部分不返还，例如 `17→20`；生存模式只在写回成功后消耗水桶并返空桶，背包满时在玩家处掉落，创造模式增加余量但保留水桶。满容量、歧义食物、不可读数据或写回失败不会扣物品，但已识别输入会拦截原版叶片放置或倒水；未识别物品保持普通交互。
- 食物白名单直接查询 Jurassic Saga 当前运行时 `Diets`：`HERBIVORE/SEEDS` 归草食，`CARNIVORE/PISCIVORE/OVIVORE/INSECTOVORE` 归肉食；同时命中两组的歧义物品会被拒绝。创造模式装填不消耗手持物，成功放置或围绕恐龙形成预装箱体后会清除玩家保留物品中的余量 key，避免复制。装填不会在同一次交互中触发自动照料。
- 完整 canonical 捕获箱的任意 16 个 part、任意侧面都通过 NeoForge 标准单槽 `ItemHandler` 暴露同一个 controller handler。漏斗每次最多插入 1 个麻醉针、纯草食物或纯肉食物并增加 1 点；满容量、恢复态、残缺结构、歧义/未知食物和水桶会被拒绝，模拟插入不写状态，接口不允许抽取。缓存 handler 在每次写入前重新验证 owner、controller 和 16-part 结构；水源仍只能由玩家右键水桶补充。
- 每次捕获箱结算先推进卸载期间的饥饿、口渴、健康、麻醉和耐久，历史期间不追溯消耗余量；加载后的合资格照料 pass 受完整 20-tick 冷却约束，最多各消费 1 点食物、水源和麻醉余量。饱食度严格低于 80% 时才按真实食性消耗食物并恢复 8,500 饱食值，杂食优先余量较多的一类、相同时优先草食；口渴值严格低于 80% 时每点水源恢复 `ceil(maxThirst / 10)` 并封顶。正好 80% 不消费，缺少或关闭 metabolism、禁食/禁水、无效上限及食性解析失败也不消费。
- 自动麻醉会先提升已到期 pending；只有 active 剩余严格低于 1,200 tick 且未来 pending 条目少于 4 时，才在一次合资格 pass 中最多追加并消耗 1 支麻醉针，active 本身不计入这 4 条。`LastAutoCareGameTime` 记录成功的合资格 pass，即使该次没有实际消费也建立 20-tick 冷却；game time 回退时跳过照料并重置基线。实体物化、快照或写回失败会保持恐龙数据、四项余量、时间戳、余数、耐久和 Damage mirror 全部不变。
- 静态放置箱、飞行箱、手持释放以及背包/容器/掉落物耐久归零统一使用三级释放 resolver，各入口只保留原有搜索范围差异。第一层严格候选要求已加载、建筑高度、世界边界、非岩浆和无碰撞，并按陆生支撑/少水或水生覆盖水量、距离与 `x/y/z` 确定性排序；第二层在相同范围内只把已加载和建筑高度作为硬条件，再按边界内、非岩浆、无碰撞、物种环境偏好、支撑、距离与坐标排序。
- 若相同搜索范围内仍没有候选，resolver 必须回退到入口附近的最终点：放置箱使用其 global AABB 顶面中心，手持使用点击面相邻基点，耐久释放沿用原 release origin；该层不再以碰撞、支撑、水、岩浆或边界阻止释放，接受附近卡墙、坠落或环境伤害风险，不把恐龙远传。最终只有实体 NBT 解码失败、任一已加载维度已存在相同 UUID 或 `addFreshEntity=false` 才保留载荷；实体成功加入世界后立即永久清除原 payload，后续载体替换失败不能恢复同 UUID 权威。
- 释放恢复原 UUID。防复制检查覆盖服务器当前所有已加载维度；项目没有世界级 UUID lease，因此未加载维度不在防重保证内。
- 物品 tooltip 会为余量可读的空箱、有效箱和恢复态箱显示麻醉、水源、肉食、草食百分比；有效箱继续显示麻醉、整数百分比耐久和按秒/分钟/小时切换的捕获时长。余量本身不可读时只显示恢复警告，不伪造数值。
- 耐久归零并释放成功后，载体转为 `jsrevise:broken_dinosaur_capture_box`。破损箱仍是 2×4×2 的 16-part 结构，不保存恐龙且没有 HUD 交互；碰撞使用 1 px 厚的箱体壳并按外观扣除前洞、左右两个主侧洞、右侧小洞和顶洞。两个主侧洞有 `25/16` 方块净高，可供 1.5 格高的蹲行实体通过，内部 `30/16` 方块净高可站立，前洞与右侧小洞只保证爬行通过；七块外围箱体碎片始终只是视觉装饰，不参与碰撞。每个 part 继续提供完整支撑面，选择与命中轮廓则限定为该 part 本地的 1×1 完整方块，不再由每个代理扩展到整个 2×4×2 结构；这避免斜向点击命中错误 part 或面，使底面附着更可靠，同时不改变细分碰撞、质量或 canonical 结构。方块保持 `noOcclusion`，使正式 Simulated `canAttach/canSurvive` 路径可放置物理组装器。支撑查询不依赖坐标，因为上游会传入组装器坐标；孤立 part 虽可附着方块，但实际组装仍必须通过 16-part canonical movement gate。
- 新鲜静态完整箱当场破损时，世界 BER 仍显示七块外围碎片；普通破损箱物品放置、挖掘掉落后重新放置、当前位于 sublevel 的完整箱破损、破损箱成功物理化及之后拆解、以及恢复物品重建时都会永久隐藏碎片。首次物理化失败并回滚时恢复原 source 的碎片状态。破损箱物品模型只保留主体，不烘焙七块世界碎片；静态世界 BER 仍使用现有 debris sheet 渲染允许显示的碎片，贴图与方块/物品 ID 均不改变。
- 完整箱和破损箱的活塞反应均为 `BLOCK`。破坏任一 part 会按实际 part 位置执行 harvest 判定，并且只级联清理通过 canonical 校验、属于同一 controller 的结构；客户端把破坏进度镜像到全部 part。
- 捕获箱设置为不可原版维修。铁砧会拒绝右槽携带任意 raw 捕获或余量 key，或左槽已有任一 key 时用另一捕获箱合并；右槽为空的改名和附魔书仍可用，并保留 `CUSTOM_DATA`、名称、附魔及 Damage mirror。
- 捕获载荷或余量数据无法解析时 tooltip 显示中英文恢复警告，整个箱体进入联合恢复保护：装填、捕获、放置、释放、自动结算和耐久镜像均停止，避免覆盖任一原始数据。

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

捕获物品的恐龙载荷与箱体余量分别位于同一 `CUSTOM_DATA` 下的 sibling key：`DinosaurCapture` 与 `DinosaurCaptureSupplies`。`DinosaurCaptureItemData.inspect(ItemStack)` 将恐龙载荷分为三态：

- `EMPTY`：只有 capture key 完全不存在时成立。
- `VALID`：载荷成功解析为 `CapturedDinosaurData`；旧 `get(ItemStack)` 继续只返回这一态的 `Optional` 兼容视图。
- `UNREADABLE`：key 存在但值无法解析。任意 NBT `Tag` 都以 `copy()` 保存；普通 set、clear、mirror、捕获、放置、释放和结算不得覆盖，只有显式 raw/recovery API 可以替换。

余量 key 有独立的 `EMPTY/VALID/UNREADABLE` 检查：缺失按四项全零读取，旧版缺少可选 `Water` 的三键数据合法迁移为零水源；麻醉钳制到 `0..40`，其余三项钳制到 `0..20`。错误类型、未知结构或任意其他 `Tag` 以 `copy()` 进入不可读态。任一 sibling key 不可读时，联合内容检查会阻止整个箱体的普通写入；手动释放清除合法恐龙载荷时不会清除合法余量 key。

方块实体同样能无损保存两类 `VALID` 数据或任意 `UNREADABLE` raw Tag，并在物品、放置、重载、任意多方块 part 破坏和掉落之间保持 `Tag.copy()` 往返。向客户端的 block entity update tag 只同步载荷/不可读状态和四个有界计数，不发送实体 NBT、raw、UUID 或观察快照。

破损箱方块实体另有 additive 顶层标记 `JSReviseDebrisDetached`：缺失或 Byte `0` 表示静态碎片可见，Byte `1` 表示永久隐藏；其他类型或 Byte 值作为 malformed raw 原样往返，并在视觉上安全隐藏。合法标记与未知 controller metadata 分离，避免保存时覆盖；客户端更新包只同步由该状态投影出的安全 Byte 可见性镜像，不发送 malformed raw 或其他未知 metadata。该字段不需要 DataFixer。

`DinosaurCapture` 顶层、`RelativeAnesthetic` 和 `Vitals` 都采用受控键集合：未知键或已知键类型错误会让整个载荷进入 `UNREADABLE`，而不是在下一次序列化时静默丢失。Relative 与 Vitals 各限制为 64 KiB，pending 列表及 fast-path 扫描最多 64 项；`EntityNbt` 继续允许 Jurassic Saga 的任意实体字段并保留 1 MiB 上限、实体类型和原 UUID。物化临时实体失败时，结算返回原对象/raw，不推进时间、耐久、两个余数或 Damage mirror，也不因内容相同重复写回。

捕获生命体征的内部 compound 还保存 `HungerPoints`、`MaxHungerPoints`、`HungerEnabled`、`ThirstPoints`、`MaxThirstPoints`、`ThirstEnabled`、`ReserveDiet` 与 `LastAutoCareGameTime`，用于纯数据阈值投影和完整 20-tick 自动照料冷却；旧数据在含水源且需要判断阈值时只懒物化一次，随后写回真实或稳定 disabled 的口渴投影。消费前仍会从临时实体重新解析真实食性，持久化食性只作 fast-path 调度提示；game time 回退时跳过该次照料并在成功快照中重置基线。

现有两个向后兼容余数字段继续保留：

- `DurabilityRemainderTicks`：范围 `0..19`，保留不足 1 秒的箱体耐久时间。
- `CapturedRelativeTicks`：范围 `0..199`，保留不足一次饥饿/口渴漂移周期的捕获内时间。

两者都是 additive NBT；旧存档缺失时按 0 读取，不使用 DataFixer。`CapturedDinosaurData` 仍有 11 个 record component，并保留原 10 参数 public 构造器与 canonical 11 参数构造器；内部 capture/copy/with 路径显式携带新增字段。

被动结算保留 20-tick 门槛；手动释放以及当前 tick 必须写新快照的路径使用精确结算。自动照料只在历史状态推进后处理加载时刻，并以 `LastAutoCareGameTime` 保证任意入口在完整 20 tick 内不重复消费；冷却期内仍可物化并提升已到期 pending，但保留原照料时间且不扣余量。饥饿或口渴归零后的健康损失按 `elapsedTicks / 1200.0F` 计算。麻醉保护区间会合并 active 与最多 64 个 pending 区间，使用饱和时间运算，确保耐久投影单调不增。

权威耐久仍在捕获载荷中；当前 `DurabilityCapacity=500`、`MAX_DAMAGE=500` 与 `DAMAGE=500-耐久` 共同维护原版耐久条和 Inventory HUD+ 的兼容镜像，旧 100 与临时 20 容量会按上述规则迁移并写回。正数被动流逝继续使用包含饥饿与麻醉投影的纯数据 fast path，不每秒物化实体或改写 `CUSTOM_DATA`/Damage mirror；旧数据懒迁移、阈值到达、pending 到期、精确结算和耐久归零才进入必要的持久化或实体路径。

### 观察与缓存

- `DinosaurObservationSystem` 以维度和实体 UUID 缓存服务端快照，有容量、过期、单实体失效和 server stopping 清理。
- HUD 反射按字段局部降级。基因 `ItemId` 是 optional；无物品图标时仍可显示文本，不再把整条基因丢弃。
- 客户端下蛋与捕获箱观察缓存都有上限和请求冷却。捕获箱缓存以独立的 generation、维度和单调逻辑 tick 标识客户端会话，原始 `gameTime` 校时回退不会清理；当前目标保留 last-known-good 并后台刷新，接收时间与最后观察时间分离，非当前项在 200/201 tick 边界过期并按确定性顺序限制为 512 项。请求和清理比较 elapsed 时间，在逻辑 tick 饱和时也不会形成逐帧请求。
- 模型几何缓存只缓存资源派生的边界，并由客户端 reload listener 在 F3+T 等资源重载时清除；不会顺带清理不相关的游戏状态缓存。

## 4. 兼容边界

### Create: Aeronautics / Sable 精确兼容

- 正式自动化基线固定为 Create: Aeronautics bundled `1.3.0`、Simulated `1.3.0`、Sable `2.0.3`、Sable Companion `1.6.0` 和 Create `6.0.10-280`；整合包互操作 profile 另外固定 Create Diesel Generators `1.3.14`、Create Enchantment Industry `2.5.0-preview-alpha1` 和 Create: Dragons Plus `1.11.2b`。构建只把 `sable-companion-common-1.21.1:1.6.0` 以 `[1.6.0,1.6.1)` JiJ 范围嵌入最终 JAR；其余六个正式文件只进入精确测试 profile，不得嵌套、发布或打包其类。
- 完整箱和破损箱始终属于 `#simulated:non_movable`。只有运行时正式类指纹、两个受限 ASM patch 的 post-apply 验证、AdditionalBlocks/AttachedCheck 注册及当前 16-part canonical 结构全部成立时，才由 Companion safe facade 开放移动。Sable `moveBlocks` 严格保留正式 pre-apply 421 条指令验证；post-apply 只接受标准 JS 439 条形态，或在正常 `finish()` 与唯一 `RETURN` 之间额外出现已指纹的 Diesel 五指令 TAIL。后者只从验证视图逻辑排除，实际 ClassNode 指令不删除，视图必须再次通过完整 439 指纹、异常表、邻接关系和 CFG 校验。依赖缺失、只存在一部分、额外尾代码、多个 TAIL、descriptor/owner/anchor/指令顺序漂移或未知版本均 fail-closed 并保持不可移动，不通过删除安全 tag 绕过。
- `CaptureBoxWorldContext` 对静态世界使用 identity transform；对 Sable sublevel 保存维度、sublevel UUID、local controller、八角投影后的 global AABB、global facing 和有限 point velocity。距离显式使用三维坐标，不依赖上游只覆盖单轴的便捷重载；飞行器上的捕获、释放、掉落、8 格观察和同 sublevel 漏斗都在明确的 local/global 边界中运行。现有两个 BER 保持 Sable 已提供的局部相机/PoseStack，不做二次投影。
- Sable 逐块搬运事务分别保存 `sourceOriginal` 与 `targetExpected`：provisional 目标必须先与 original 深度等价，破损目标再通过 relocation-only authority 投影为 detached expected 并二次验证；SOURCE 回滚始终恢复 original，TARGET commit/finish 与恢复物品始终使用 expected。位置等价比较仍只忽略 `x/y/z`，不会忽略碎片标记；快照保存完整 controller metadata、任意 raw tag、余量、耐久及 Damage mirror。目标完整时提交并中和源，目标不完整时回滚并恢复源；源无法恢复但目标完整时保留目标，二者都不能形成结构时才生成唯一的受保护恢复物品。该保证只覆盖正常世界写入语义下捕获箱域恰好一个权威，不宣称整艘航空器或 JVM 崩溃窗口具有 ACID 事务性。
- 反物理化目标的 16 个位置若都已加载且只含空气、普通非 BE 方块或流体，可按正式 Sable 语义覆盖；事务会先记录每格前态，并在后续搬运失败时仅恢复仍由本事务拥有的位置，避免覆盖意外出现的第三方状态。若目标含任意方块实体或 `EntityBlock`、另一捕获箱结构/权威、未加载位置或其他不安全状态，则本次移动会无崩溃地 fail-closed：精确匹配 Sable 的 setup/copy/notify/delete/update 五遍迭代，只让 setup 取得一个锚点，后四遍为空；仅在源 sublevel 质量追踪器失效时重建并重新聚合，保留源结构而不生成恢复物品。清除或修复冲突后可重试，但不支持覆盖方块实体，也不把该安全拒绝描述为整艘飞行器的原子回滚。
- 已释放恐龙的完整箱在耐久归零后执行原位 `COMPLETE → BROKEN` 转换：先替换 15 个非 controller part，最后替换 controller，全程不把 plot 清空，避免独立 sublevel 因瞬时空结构被移除。转换失败会回滚为空载的完整 canonical 结构，但不会恢复已经成功生成到世界中的恐龙 UUID 或捕获载荷。正式 exact 自动化会让独立 sublevel 跨过 `SubLevelContainer.processSubLevelRemovals()` 并确认 detached 破损结构仍存在；这仍不等同于用户真实整合包中的实机验收。
- 正式 Sable `2.0.3` 会让每个非空普通 part 的质量为 1；破损箱 64 种 facing/part 碰撞状态均非空，因此 16-part 总质量为 16，与完整箱一致，不增加 `physics_block_properties` JSON。`-PwithAeronautics=true` 验证基础精确二进制；`-PwithAeronauticsPackInterop=true` 自动包含前者，并以不传递的测试运行时依赖加载 Diesel、CEI 和 Dragons Plus。默认 profile 必须主动得到 `ABSENT`，后两者必须得到 `READY`；`DRIFT`、patch 未安装或仍不可移动会让 GameTest 直接失败。正式 profile 覆盖破损箱 `canAttach/canSurvive`、每 part/总质量、直接组装 helper、物理组装器 BE 入口、完整/余量/VALID/raw/破损箱组装与拆解回程，并校验 UUID、原始 NBT、余量、耐久、Damage mirror、碎片状态和未知 controller metadata 的单一权威。通过这些自动化仍不等于真实 Jurassic Tales 整合包、飞行保存重载、Jade/BER 或游戏内视觉已经人工验收。

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

- Curios 通过存在性检查隔离；缺失时只使用原版头部栏。Jade API 只出现在 `compat/jade` 和客户端注册路径；公共、方块实体和服务端代码不引用 Jade，缺失 Jade 时不进入必需加载路径。
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
| common | `aeronautics.SableSubLevelAssemblyHelperMixin` | 字符串目标 Sable `SubLevelAssemblyHelper`；作为 exact relocation 字节码事务的 `@Pseudo` marker，缺失或漂移时保持箱体不可移动 |
| common | `aeronautics.SimAssemblyContraptionMixin` | 字符串目标 Simulated `SimAssemblyContraption`；作为 AdditionalBlocks/attached-check 字节码补丁的 `@Pseudo` marker，只有精确正式结构才启用 |
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
- `compat/`：Curios、Jade、Travelers、Aeronautics/Sable 等有明确依赖边界的集成；可选实现只能经字符串、反射或 Companion safe facade 接入 common 启动路径。
- `mixin/`：只放必须进入上游调用链的最小注入；客户端和服务端分目录。
- `client/`：HUD、模型、粒子、物品属性和仅客户端缓存。
- `src/test/` 与 `gametest/`：纯逻辑、资源引用、真实依赖字节码和世界行为验证。

修改通用动物功能时，先证明基类或运行时能力边界，再决定是否需要物种覆盖。涉及移动、AI、碰撞、网络、NBT 或附件时必须保留服务端权威、非法值清洗、失败原子性和旧存档行为；避免无收益的大范围重构。

### 构建与验证

环境要求为 Java 21。Windows 下的完整自动化门禁为：

```powershell
.\gradlew.bat test runGameTestServer assemble --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat test runGameTestServer assemble -PwithAeronautics=true --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat test runGameTestServer assemble -PwithAeronauticsPackInterop=true --rerun-tasks --warning-mode all --console=plain
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

自动化必须检查单元测试、GameTest、编译与 Gradle 警告、资源引用、Mixin/plugin、真实 Travelers class bytes、精确 Aeronautics/Sable/Create 及 pack-interop addon archive/class 指纹、生成 metadata、JAR 内容及工作树范围。CI 同时执行默认、`withAeronautics` 和 `withAeronauticsPackInterop` 三套 test/GameTest/assemble 门禁；测试数量和产物绝对路径不写入长期文档，因为它们会随实现变化。

`runClient`、长期驻留服务器和实际整合包属于人工验收，不由普通构建自动启动。限时 dedicated server 验证应在五分钟内等待 `Done`，看到后正常输入 `stop`；超时或崩溃需保留日志并判定失败。

### 版本与兼容维护

- `gradle.properties` 的 `mod_version`、生成/processed metadata、最终 JAR metadata 和 README 候选版本必须一致。
- 网络协议、附件、捕获 NBT 或 public descriptor 变化前必须先决定是否需要协议升级、迁移或兼容桥。现有余数、余量、耐久容量与照料提示字段都是向后兼容的 additive NBT，不需要 DataFixer。
- 资源变更要通过 recipe、model、texture、blockstate、loot、tag、lang 和动态 renderer allowlist 的引用完整性检查。
- 依赖升级后重新验证 Mixin 目标、Travelers 字节码指纹、Curios/Jade 降级和 dedicated server；历史结论不能自动外推到新二进制。
- 发布前确认源码仓库只包含批准的内层文件，不包含外层 `AGENTS.md`、`.codex/`、`.agents/`、`.planning/`、参考模组或运行产物。

## 6. 已验证基线

### 1.0.134 候选自动化边界

当前候选沿用单元测试、资源引用测试、独立捕获/麻醉运动 GameTest 和 `assemble` 门禁，并覆盖 HUD 同字号主行对齐、queued 次行与左栏新增的 3 px 底部留白，旧 100/临时 20 耐久迁移、无空窗 `COMPLETE → BROKEN` 转换、破损箱 64 个碰撞状态与五个洞口、本地 part 命中轮廓及底面放置、碎片四态/raw 同步、`sourceOriginal/targetExpected` 搬运语义、普通目标前态回滚与危险目标五遍安全拒绝、正式 Sable 每 part 与总质量、canonical 结构、标准 ItemHandler、三级释放、sublevel 世界桥、单一权威及精确 bytecode 漂移样本；Travelers 补丁测试继续使用真实 `0.7.1` class bytes。本次交付的 focused JUnit，以及默认、exact Aeronautics、pack-interop 三套完整 `test`/GameTest/`assemble` 均已通过；最近一套 JUnit XML 为 `402/402`，三套 GameTest 均为 `120/120`。`publish --dry-run`、三套限时独立服务器的 `Done → stop → exit 0`、最终 JAR metadata、Mixin 清单、依赖内容、关键资源和工作树范围也已核对。

局部验证和历史自动化结果不等于当前候选的完整门禁或实际整合包验收。本文档不宣称 `1.0.134` 已通过用户真实 Jurassic Tales 整合包中的物理组装器 tick 入口、取消物理化回程、普通占位覆盖或危险目标重试，也不宣称独立物理化捕获箱破损、破损箱洞口/底面放置、飞行/旋转、保存重载、拆解、质量/碎片生命周期、Jade/BER、漏斗、持续观察捕获箱、双侧 HUD 多 GUI scale 或其他游戏内视觉已经验收；未知 Aeronautics/Sable/Create 组合和未取得二进制的其他 Travelers `0.7.x` 同样不能标为已实测。默认自动化、两套精确 profile、限时 dedicated server 与实际整合包验收应分别留下明确记录。

### 1.0.115 历史实机基线

提交 `1c4a440`（`Document external validation pass for 1.0.115`）记录了以下 `1.0.115` 发布前实际整合包事实，仅用于防回归；该提交本身不代表远端 Actions 状态已核验：

- 已确认艾雷拉龙、腕龙、鸟鳄、卢多翼龙及其他陆生/飞行动物的模型按预期露出水面。
- 已确认成年和幼年生物的露出比例符合“体型越大，露出越少”。
- 已通过海王龙的沉浮振幅、水平姿态和尾部动画验证。
- 已确认接近水面、首次破水和沉浮换向时的粒子可见性。
- 已确认 DNA 图标、动态主题色、85% HUD 整体缩放、等效 90% 字体和透明度在不同 GUI 缩放下表现符合预期。
- 已通过独立服务器中的年龄、麻醉倒计时、漂浮阶段和粒子事件同步验证。

这些结论只对应 `1.0.115` 发布前的当时整合包环境，不能证明 `1.0.134`、未来依赖版本或其他整合包已通过相同验收。任何功能、资源、Mixin、依赖或整合包变化后，都应重新执行相应的自动化、独立服务器和游戏内验证。

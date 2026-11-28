# Minecraft 26.2 工具使用教程

这份教程对应目前这套工具：**自动挖矿 autominer 1.15.1 + 多开器 multi + 信息工具 infotools 1.6.1 + 种子探测器 seedfinder 1.1.0 + 指令生成器**。

---

## 一、先看总览

| 工具 | 作用 | 装在哪里 |
| --- | --- | --- |
| autominer | 自动挖矿、自动回箱、宏、事件脚本 | `.minecraft/mods/autominer-1.15.1.jar` |
| multi | 一键多开、控制台、自动更新 | 桌面 `multi` 文件夹，双击 `启动多开.bat` |
| infotools | 行情、比价、商店地图、资产、收购榜等信息 | `.minecraft/mods/infotools-1.6.1.jar` |
| seedfinder | 查种子、攒反推种子的证据 | `.minecraft/mods/seedfinder-1.1.0.jar` |
| 指令生成器 | 浏览器里生成 fill / setblock / clone 指令 | 双击 `multi/指令生成器.html` |

公共环境：**Minecraft 26.2 / Fabric Loader ≥ 0.19.3 / Fabric API 0.161.0+26.2 / Java 25**。自动挖矿还需要 **Baritone API 版**：`baritone-api-fabric-1.19.0.jar`，不要放 standalone 精简版。

安装时只复制 jar，不需要管理员权限，不会弹 UAC。

---

## 二、自动挖矿 autominer

### 1. 第一次用

1. 把 `autominer-1.15.1.jar`、Fabric API、Baritone API 放进 `mods`。
2. 进游戏，按 **M** 打开「自动采矿助手」。
3. 第 1 页勾选要挖的矿石。
4. 第 2 页添加存矿箱：准星对着箱子/木桶/潜影盒，按 **C** 或点「添加箱子」。
5. 第 3 页设置定时回箱、攒够回箱、自动回收、进服自动开始。
6. 第 1 页点「开始自动采矿」。底部显示「男中音：已就绪」才说明 Baritone 正常。

默认按键：**M** 打开菜单，**C** 记录准星前的容器。都能在「选项 → 控制 → 按键绑定」里改。

### 2. 四个页面

- **矿石页**：勾矿种、开始/停止、看背包空位和回箱条件。
- **箱子页**：管理存矿箱，支持改名、删除、清空。
- **设置页**：定时回箱秒数、攒够某矿物多少个回箱、全部入库、自动回收（镐耐久/背包空位）、进服自动开始。
- **事件脚本页**：每个关键节点挂自己的指令/脚本，见下面第四节。

### 3. 常用命令

```text
/am start                 开始
/am stop                  停止
/am status                看当前状态
/am diag                  看男中音/环境诊断
/am chest add 名字        准星对着箱子添加
/am chest list            列箱子
/am interval 900          每 900 秒回箱，0 = 关
/am trigger diamond 64    攒够 64 个钻石回箱
/am trigger off           关掉攒够回箱
/am recycle on|off        自动回收开关
/am recycle pick 15       镐子剩 15% 回箱换镐
/am recycle slots 1        背包只剩 1 格回箱
/am depositall            全部入库开关
```

### 4. 宏：按键 + 鼠标点击，支持 Shift+F

适合商店、菜单、登录框、服务器菜单这类固定界面。1.15.0 起，键盘和鼠标都会录：

**有界面的宏**
1. 打开要录的界面。
2. 按 **F7** 开始录制，正常按键、点按钮，再按 **F7** 结束。
3. 按 **F8** 回放；回放中再按 F8 停止。

**全局宏：先按 Shift+F 打开服务器菜单**
1. 站在游戏里，不要打开任何界面。
2. 按 **F7** 开始录制，会提示「全局」。
3. 按 **Shift+F** 打开服务器菜单，再点菜单里的按钮。
4. 按 **F7** 结束；保存为 `宏/全局.txt`。
5. 以后在游戏里按 **F8**，会先发 Shift+F，菜单打开后继续点。

**组合键和 Shift+点击**
- `Shift+F`、`Ctrl+F`、`Alt+F` 这类组合键会连修饰键一起录。
- `Shift+左键` 快速移动物品也会保留 Shift，不会退化成普通左键。
- 宏里的 `F7/F8/F9` 本身不会被录进去，避免自己触发自己。

宏保存在 `游戏目录/宏/<界面标题>.txt`，是文本文件，可以直接改。按键回放会尽量发真实系统按键，所以能触发服务器或模组的全局快捷键；如果系统不允许模拟按键，会退回界面事件。

```text
/am macro                 看宏状态
/am macro list            列出宏
/am macro rec             开始/结束录制
/am macro play 名字       手动回放
/am macro del 名字        删除
/am macro auto on|off     自动回放开关
```

注意：只录「按下瞬间」，不录长按、拖拽、滚轮和连续打字；回放时游戏窗口要保持在前台。

### 5. 仓库索引和末影之眼

```text
/idx                      仓库索引概况
/idx scan                 扫附近容器
/idx find 钻石            查哪个箱子见过钻石
/idx auto on|off          走过新箱子自动记录

/eye                      末影之眼说明
/eye calc                 两次扔眼后算要塞坐标
/eye list                 看记录
/eye clear                清空
```

---

## 三、多开器 multi

### 1. 启动

把 `multi` 文件夹放桌面，双击 **启动多开.bat**：

1. 第一次会让你填服务器地址，写 `域名:端口`，例如 `ranmc.cc:25565`；填 `skip` 表示只开游戏不进服。
2. 填要开几个号、每个号的名字/矿种/基地/存矿箱。
3. 启动器会为每个号建独立目录 `bots/<名字>`，自动整理 mods、复制本模组、检查 Fabric API 和 Baritone。
4. 名字要求 3–16 位英文/数字/下划线；中文名会自动换成 `MinerN`。

### 2. 控制台

启动后会进中文控制台，常用：

```text
status            重画状态表
detail 名字       看某个号的画面、聊天、最近动作
watch off         关闭自动播报
watch on          恢复播报
quit              退出控制台（机器人继续跑）
```

状态表会显示：界面 / 维度 / 坐标 / 血饱 / 状态 / 登录 / 回箱 / 心跳。心跳超过 15 秒没更新，通常说明客户端卡住或断线。

### 3. 更新

双击 **一键更新.bat**，会从 GitHub Release 找最新版，自动替换 mods 里的 `autominer-*.jar`，旧版本自动挪走。

同 IP 同时在线数量受服务器限制，常见是 2 个；第 3 个号要换出口 IP，或者不要同时开。

---

## 四、事件脚本：所有关键节点都能挂指令

这是 1.14.0 新增的核心功能。按 **M** → 第 4 页「事件脚本」，每行一个事件，右边有开关和「编辑」。

| 事件 | 执行时机 |
| --- | --- |
| 开始挖矿 | 第一次真正开始挖矿前（走到开荒点之后） |
| 回箱前 | 任何回箱条件命中后、走向箱子前 |
| 背包满/爆仓 | 背包空位不足触发回箱时 |
| 镐子快坏 | 镐子耐久低于阈值触发回箱时 |
| 定时回箱 | 定时回箱触发时 |
| 攒够矿物 | 指定矿物攒到数量触发回箱时 |
| 回箱后 | 存完矿/关箱后、继续挖矿前 |
| 挖完/找不到矿 | 附近没有可挖矿石、准备暂停时 |
| 停止挖矿 | 手动或自动停止时 |

点「编辑」后可以配：

- **游戏指令**：多条用 `|` 分隔，按顺序发；不用写开头 `/`。例：`/home|/kit miner|/msg 队友 回箱`
- **脚本文件**：放 `config` 目录的文本文件，一行一条，也可用 `|` 分隔，`#` 开头是注释；空 = 不读
- **指令间隔**：每条之间等多少 tick，20 tick = 1 秒
- **执行完再等**：最后一条发完再等多少 tick 才继续下一步
- **开关**：每个事件独立开关；没配内容的事件不会占用时间

回箱顺序：**本次原因事件（爆仓/镐快坏/定时/攒够） → 回箱前 → 走向箱子 → 回箱后 → 继续挖矿**。

命令方式：

```text
/am hook                          列出所有事件
/am hook pre_deposit              看某个事件
/am hook pre_deposit on|off       开/关
/am hook pre_deposit add /home    追加一条
/am hook pre_deposit del 2        删除第 2 条
/am hook pre_deposit clear        清空指令
/am hook pre_deposit script xxx.txt
/am hook pre_deposit delay 10
/am hook pre_deposit settle 20
```

事件 key：`start`、`pre_deposit`、`full`、`low_pick`、`interval`、`threshold`、`post_deposit`、`mining_done`、`stop`。

注意：脚本是**游戏指令脚本**，只把每行发给服务器，不会运行 `.bat/.sh`，不需要 UAC。会传送/换维度的指令，建议把「执行完再等」调大。

---

## 五、信息工具 infotools

装 `infotools-1.6.1.jar`，进世界后聊天框敲 `/info`。它只读信息，不改背包、不动方块、不需要 OP。

### 1. 行情和比价

```text
/info price               看行情
/info price 钻石          看某物品
/info price list          列已记录物品
/info price cheap         看便宜店
/info price alert on|off  价格提醒
/info buy                 全服收购榜
/info buy 钻石            某物品收购榜
/info flip                搬砖套利（最低卖价 → 最高收价）
```

### 2. 商店和告示牌

```text
/info shop                看商店
/info shop 搜索词         搜商店
/info shop words          看商店关键词
/info shop add 词         加关键词
/info sign                告示牌商店
/info sign 钻石           查某物品的告示牌店
/info sign scan           扫描附近告示牌
/info sign near           看附近店
```

告示牌需要你**人工点开一次**才能读到内容；客户端读不到没打开过的告示牌。

### 3. 背包、资产、损耗

```text
/info worth               背包市值
/info assets              今日资产日报
/info assets snap         立刻记一笔
/info assets log          最近变动
/info assets reset        今日清零
/info death               上次死亡点
/info drops               附近掉落物倒计时
/info loss                死亡损失账单
/info income              今日挂机收益
```

### 4. 补货、开箱、历史

```text
/info stock               补货清单
/info stock set 钻石 64   目标库存
/info stock plan          采购路线
/info stock del 钻石
/info stock clear
/info chest 钻石          哪个箱子见过钻石
/info chest list          开箱索引
/info history 钻石        行情日线
```

开箱索引只记录你**打开过**的箱子；没打开过的箱子客户端读不到。

### 5. 提醒、订阅、配方

```text
/info alert on|off        关键词告警
/info alert add 词
/info alert list
/info watch add 店主名    关注店主
/info watch list
/info watch on|off
/info recipe 钻石         合成材料
/info use 钻石            用途
/info quote               报价快捷回复
/info quote copy          复制回复
```

### 6. 快捷卡和 HUD

- **V 键**：背包/箱子/商店里鼠标指着一格按 V；世界里准星对着展示框/掉落物按 V，屏幕右侧显示物品名、ID、数量、耐久、行情卖/收、附近最低卖店、参考价。
- **HUD**：`/info hud on|off` 开关；会显示收购榜、补货缺口、今日收益等。
- 数据文件在 `游戏目录/信息工具/<玩家名>/`，配置在 `游戏目录/信息工具配置.txt`。

---

## 六、种子探测器 seedfinder

装 `seedfinder-1.1.0.jar`，客户端模组，服务端不用装。**这个工具只自己用，不要上传公开仓库。**

```text
/sf                 查种子（单机直接读；服务器会试 /seed）
/sf copy            复制上次种子
/sf server          再问一次服务器
/sf auto on|off     进服自动试 /seed
/sf scan            扫描附近已加载区块的刷怪笼（地牢）
/sf biome           记录周围 11×11 区块生物群系
/sf mark 名字       记下坐标（村庄/要塞/神殿/矿洞口）
/sf list            看已攒证据
/sf export          显示并复制证据文件路径
/sf rec on|off      走路自动记录沿途生物群系
```

证据写在 `游戏目录/种子证据.txt`。最有用的是刷怪笼坐标，其次是生物群系；26.2 目前没有可靠的一键反推工具，先把证据攒着。

---

## 七、指令生成器

双击 `multi/指令生成器.html`，浏览器打开，不用装东西：

- `fill` 填充：replace / destroy / hollow / keep / outline / strict，可只替换某种方块
- `setblock` 放方块、`clone` 复制、`execute as/at/positioned`
- 坐标换算：站在原点，把绝对坐标换成 `~` 相对坐标
- 支持 `~` 和 `^` 写法，写错会标红；历史记录存在浏览器里

---

## 八、常见问题

**男中音不可用**：`mods` 里要放 `baritone-api-fabric-1.19.0.jar`，不是 standalone；Fabric Loader 要 ≥ 0.19.3。输入 `/am diag` 看具体原因。

**启动器说 Java 不对**：26.2 需要 Java 25；检查启动模板的 Java 路径。

**回箱脚本没执行**：看事件页对应事件是否开、指令/脚本是否为空、脚本文件是否在 `config` 目录；指令是发给服务器的，客户端 `/am` 命令不要写进事件脚本。

**会不会弹 UAC**：不会。安装只是复制 jar；事件脚本也只发游戏指令，不运行本机 `.bat/.sh`。

**多开第 3 个号进不去**：同 IP 在线限制，换出口 IP 或减少同时在线。

---

## 九、文件放在哪

- 模组：`.minecraft/mods/`
- autominer 配置：`.minecraft/config/autominer.json`
- 事件脚本：`.minecraft/config/` 下你填的文件名
- 宏：`.minecraft/宏/<界面标题>.txt`
- 多开：桌面 `multi/`，每个号 `bots/<名字>/`
- 信息工具：`.minecraft/信息工具/<玩家名>/`
- 种子证据：`.minecraft/种子证据.txt`


---

## 1.15.1 / 1.6.1 修复说明

- 多开：`启动多开.bat` 现在会在模组检查之前先建好 `bots\<名字>`，即使模组版本不对、脚本提前退出，控制台也能打开看到号，不会再说“找不到 bots”。
- 宏：录制的菜单标题带颜色符（§6 等）时，F8 也能对上，不再提示“这个宏是给另一个界面用的”。
- 宏：Shift+F 打开服务器菜单后，如果菜单加载慢，回放会最多等 3.5 秒再点，不会刚发完 Shift+F 就判定“菜单没打开”而停止。
- 物品识别：下界合金锭优先按完整名字识别，不再被“金锭”抢先；同时补了下界合金块、碎片、剑、斧、锹、锄、盔甲、升级模板等别名。
- 物品识别：服务器把原版物品改名成“下界合金锭”时，速查卡/仓库/背包估值会优先认显示名，不再只看注册表 id 把它当金锭。

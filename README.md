# TableBall - Minecraft 台球插件

TableBall 是一个基于 Minecraft 1.20+ 的台球游戏插件，为玩家提供完整的台球游戏体验，包括8球和标准模式。

## 🎱 游戏玩法

### 基本规则
- **游戏目标**：将指定颜色的球全部击入袋中，最后击入8号球获胜
- **游戏模式**：支持8球模式和标准模式
- **对局设置**：可选择3、5、7、9、11局的对局数

### 操作方式
1. **选择球杆**：使用快捷栏中的不同球杆（轻杆、中杆、重杆，可自行配置）来控制击球力度
2. **瞄准击球**：站在合适位置，右键点击母球进行击球
3. **策略游戏**：需要计算角度、力度和反弹路径

### 游戏流程
1. 玩家通过邀请系统开始游戏
2. 轮流击球，直到所有目标球入袋
3. 先完成目标球并击入8号球的玩家获胜

## 📦 安装事项

### 前置要求
- **Minecraft 服务器**：Paper/Spigot 1.20+
- **必需插件**：
  - [Multiverse-Core](https://dev.bukkit.org/projects/multiverse-core) (用于多世界管理)
- **Java 版本**：Java 17+

### 安装步骤
1. 下载最新的 TableBall-1.0.jar 文件以及适当版本的Multiverse-Core.jar 文件
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 插件会自动生成配置文件

### 配置文件位置
- `plugins/TableBall/config.yml` - 主要配置
- `plugins/TableBall/balls.yml` - 台球世界配置
- `plugins/TableBall/playerdata.db` - 玩家数据（SQLite）

## ⚙️ 配置文件格式

### config.yml
```yaml
# 大厅世界名称
lobby-world: "world"

# 编辑模式（允许放置和破坏方块）
# 如果在这里声明为true，则始终允许破坏、放置方块
# 建议声明为false
editmode: false

# 物品栏配置
Inventory:
  0:
    slot: 0
    material: "STICK" # 使用Material的枚举值更加准确，也可以直接使用命名空间如"minecraft:stick"
    display-name: "§c轻杆" # 物品显示的名字，暂不支持lore
    nbt: # 仅支持Enchantments和HideFlags，具体用法请自行查阅
      Enchantments:
        "unbreaking": 0
      HideFlags: 3
  # ... 更多物品配置
```

### balls.yml
```yaml
world1:
  friction: 0.6        # 摩擦力 (0-100)
  restitution: 0.8     # 弹性系数 (0-1)
  
  # 球的位置配置
  balls:
    0:
      loc:
        x: 0.5
        y: 65.0
        z: -19.5
      nbt: # 仅支持修改颜色和文本
        color: WHITE_TERRACOTTA
        text: "§a0"
  
  # 球洞位置
  holes:
    y: 62 # 统一的y轴高度
    hole1:
      x1: 16
      z1: 30
      x2: 13
      z2: 27
  
  # 游戏边界
  bounds:
    x1: -15
    z1: -29
    x2: 15
    z2: 29
```

## 🎮 玩家指令

### 基本命令
| 命令 | 别名 | 权限 | 描述 |
|------|------|------|------|
| `/inviteplayer <玩家> <世界> <模式> <对局数>` | `/ip` | `tableball.invite` | 邀请玩家开始游戏 |
| `/acceptinvite <玩家名>` | 无 | `tableball.invite` | 接受游戏邀请 |
| `/leave` | 无 | `tableball.leave` | 离开当前游戏返回大厅 |
| `/quickmenu` | `/qm` | 无 | 打开台球快捷菜单 |
| `/spectategame` | 无 | 无 | 观战其他玩家的游戏 |
| `/teleporttowhiteball` | `/tpwb` | `tableball.teleporttowhiteball` | 传送到母球旁 |

### 管理员命令
| 命令 | 权限 | 描述 |
|------|------|------|
| `/editmode <enable/disable/info>` | `tableball.editmode` | 切换编辑模式 |

### 权限节点
- `tableball.invite` - 允许使用邀请命令（默认true）
- `tableball.leave` - 允许使用离开命令（默认true）
- `tableball.teleporttowhiteball` - 允许传送到母球（默认true）
- `tableball.admin` - 所有TableBall权限（默认op）

## 🎯 快捷菜单系统

### 打开方式
- 在主城物品栏第一格放置钟表，右键点击
- 使用命令 `/quickmenu` 或 `/qm`

### 菜单功能
1. **玩家列表**：显示在线玩家，绿色名字可邀请，红色名字可观战
2. **世界选择**：循环选择配置的台球世界
3. **模式选择**：8balls 或 standard 模式
4. **对局数选择**：3、5、7、9、11局
5. **翻页控制**：支持多页显示玩家

## 🔧 技术特性

### 物理引擎
- 真实的碰撞检测和反弹计算
- 可配置的摩擦力和弹性系数
- 精确的球体运动模拟

### 数据存储
- SQLite 数据库存储玩家设置和数据
- 配置文件热重载支持
- 自动备份和恢复机制

### 用户体验
- 实时玩家状态显示
- 美化的邀请消息界面
- 音效反馈和视觉提示
- 防止误操作的安全机制

## 🐛 故障排除

### 常见问题
1. **插件无法加载**：检查 Multiverse-Core 是否安装
2. **世界无法使用**：确保在 balls.yml 中正确配置世界
3. **权限问题**：检查权限配置或使用 OP 权限

### 支持
- GitHub: https://github.com/itsdifficulttomakeaname/TableBall.Minecraftver.
- 问题反馈：在 GitHub 提交 Issue

## 📝 更新日志(其实作者一直忘记改版本号了xwx)

### v1.0.0
- 完整的台球游戏系统
- 快捷菜单界面
- 多世界支持
- SQLite 数据存储

---

**享受您的台球游戏体验！** 🎱✨

package org.tableBall.Game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.tableBall.Manager.RoundManager;
import org.tableBall.TableBall;
import org.tableBall.Utils.WorldUtils;
import org.tableBall.Utils.InventoryUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class Start {
    private final TableBall plugin;
    private final WorldUtils worldUtils;
    private final InGame inGame;

    public Start(TableBall plugin, WorldUtils worldUtils, InGame inGame) {
        this.plugin = plugin;
        this.worldUtils = worldUtils;
        this.inGame = inGame;
    }

    /**
     * 开始游戏（以balls.yml母球/0号球为传送点，生成球，初始化回合，通知玩家）
     * @param worldName 世界名称
     * @param players 玩家列表
     * @param gameType 游戏类型
     */
    public void startGame(String worldName, List<Player> players, String gameType) {
        // 获取游戏世界
        World gameWorld = plugin.getServer().getWorld(worldName);
        if (gameWorld == null) {
            gameWorld = worldUtils.createGameWorld(worldName);
        }

        // 读取balls.yml母球/0号球位置
        Map<String, InGame.BallData> balls = inGame.worldBalls.get(worldName);
        Location tpLoc = null;
        if (balls != null) {
            for (String key : balls.keySet()) {
                if (InGame.isMotherBallKey(key)) {
                    tpLoc = balls.get(key).location();
                    break;
                }
            }
        }
        if (tpLoc == null) {
            tpLoc = gameWorld.getSpawnLocation();
        }

        for(Player p: players){
            RoundManager.scores.put(p.getName(), 0);
        }

        // 传送玩家到球台
        for (Player player : players) {
            player.teleport(tpLoc);
            // 发放config.yml中的物品
            new InventoryUtils(plugin).loadAndSetInventoryFromConfig(player);
            // 创建计分板
            plugin.getScoreBoardManager().createScoreboard(player, gameType);
        }

        // 初始化回合
        plugin.getRoundManager().startGame(worldName, players, gameType);

        // 生成球
        inGame.spawnBalls(worldName);

        // 通知玩家
        for (Player player : players) {
            player.sendMessage("§a游戏开始！类型: " + gameType);
            if (gameType.equals("Standard")) {
                player.sendMessage("§e标准模式：每打进一个球得2分");
            } else {
                player.sendMessage("§e自定义模式：使用 /addscore 命令添加分数");
            }
        }
        plugin.getLogger().info("游戏初始化完成！");
    }

}

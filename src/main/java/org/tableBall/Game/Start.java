package org.tableBall.Game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.tableBall.Entity.DisplayBall;
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
    public static World currentGame=null;

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
     * @param rounds 对局数
     */
    public void startGame(String worldName, List<Player> players, String gameType, int rounds) {
        if(currentGame!=null) return;
        // 获取游戏世界
        World gameWorld = plugin.getServer().getWorld(worldName);
        if (gameWorld == null) {
            gameWorld = worldUtils.createGameWorld(worldName);
        }

        // 清理场上所有实体（除了玩家）
        gameWorld.getEntities().forEach(entity -> {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        });
        
        // 清理游戏数据
        plugin.getInGame().clearBalls(worldName);

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
            // 给对局玩家打上标签
            p.addScoreboardTag("tableball_ingame");
        }

        // 传送玩家到球台
        for (Player player : players) {
            player.teleport(tpLoc);
            player.setGameMode(GameMode.SURVIVAL);
            // 清空物品栏（包括清除下届之星等主城物品）
            player.getInventory().clear();
            // 发放config.yml中的物品
            new InventoryUtils(plugin).loadAndSetInventoryFromConfig(player);

            // 8balls模式特殊处理：给发出邀请的玩家（第一个玩家）第九格放置灰色陶瓦
            if (gameType.equals("8balls") && player.equals(players.get(0))) {
                player.getInventory().setItem(8, new org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_TERRACOTTA, 1));
            }

            // 创建计分板
            plugin.getScoreBoardManager().createScoreboard(player, gameType, rounds);
        }

        // 设置游戏数据
        inGame.setGameData(worldName, players, gameType);

        // 初始化回合
        plugin.getRoundManager().startGame(worldName, players, gameType, rounds);

        // 生成球
        inGame.spawnBalls(worldName);

        currentGame = gameWorld;

        // 通知玩家
        for (Player player : players) {
            player.sendMessage("§a游戏开始！类型: " + gameType + " (" + rounds + "局)");
            if (gameType.equals("Standard")) {
                player.sendMessage("§e标准模式：每打进一个球得2分");
            } else if (gameType.equals("8balls")) {
                player.sendMessage("§e8球模式：先打完自己的色球再打进黑8获胜");
                player.sendMessage("§e由 " + players.get(0).getName() + " 开球");
            }
        }
        plugin.getLogger().info("游戏初始化完成！");
    }

}

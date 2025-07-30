package org.tableBall.Commands;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Game.GameState;
import org.tableBall.Game.Start;
import org.tableBall.Manager.RoundManager;
import org.tableBall.TableBall;
import org.tableBall.Utils.InventoryUtils;

import java.util.HashSet;
import java.util.List;

public class LeaveCommand implements CommandExecutor {
    public static TableBall plugin;


    public LeaveCommand(TableBall pl) {
        plugin = pl;
    }

    public static void endGameForRealLikeDeepseekSTFU(World currentWorld){
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        String worldName = currentWorld.getName();

        // 清除所有实体（除了玩家）
        currentWorld.getEntities().forEach(entity -> {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        });

        // 获取当前世界的所有玩家并清理数据
        for (Player worldPlayer : currentWorld.getPlayers()) {
            // 清除标签
            worldPlayer.removeScoreboardTag("tableball_ingame");
            
            // 从游戏数据中移除玩家
            plugin.getInGame().removePlayer(worldPlayer);
            
            // 传送玩家到主城
            if (Bukkit.getWorld(lobbyWorld) != null) {
                // 清空物品栏
                worldPlayer.getInventory().clear();
                // 清除药水效果
                worldPlayer.getActivePotionEffects().forEach(effect -> worldPlayer.removePotionEffect(effect.getType()));
                // 重置玩家状态
                worldPlayer.setCollidable(true);
                worldPlayer.setAllowFlight(false);
                worldPlayer.setFlying(false);
                worldPlayer.setGameMode(GameMode.SURVIVAL);
                // 清除计分板数据
                plugin.getScoreBoardManager().clearPlayerData(worldPlayer);
                // 传送到主城
                worldPlayer.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation());
                // 设置主城物品栏
                new InventoryUtils(plugin).setLobbyInventory(worldPlayer);
            } else {
                worldPlayer.sendMessage("§c主城世界不存在！");
            }
        }

        // 清除游戏数据和任务
        plugin.getInGame().clearBalls(worldName);
        plugin.getRoundManager().endGame(worldName);
        
        // 清理全局数据
        Start.currentGame = null;
        RoundManager.scores.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        Player player = (Player) sender;
        World currentWorld = player.getWorld();

        // 检查玩家是否有对局标签
        if (player.getScoreboardTags().contains("tableball_ingame")) {
            // 玩家是对局中的玩家，执行弃权逻辑
            String worldName = currentWorld.getName();
            List<Player> playersInWorld = plugin.getInGame().getPlayersInWorld(worldName);

            // 找到对手
            Player opponent = null;
            for (Player p : playersInWorld) {
                if (!p.equals(player) && p.getScoreboardTags().contains("tableball_ingame")) {
                    opponent = p;
                    break;
                }
            }

            // 显示结算信息（弃权方式）
            String gameType = plugin.getInGame().getGameType(worldName);
            if (gameType == null) {
                gameType = "Standard"; // 默认模式
            }

            // 记录弃权结果到数据库
            if (opponent != null) {
                // 弃权者记录弃权失败，对方记录获胜
                plugin.getPlayerDataManager().recordGameResult(player, gameType, "forfeit");
                plugin.getPlayerDataManager().recordGameResult(opponent, gameType, "win");
            }

            for (Player worldPlayer : currentWorld.getPlayers()) {
                worldPlayer.sendMessage("§e结算信息:");

                if ("8balls".equals(gameType)) {
                    // 8balls模式显示局数
                    org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
                    if (gameState != null) {
                        for (Player p : playersInWorld) {
                            if (p.getScoreboardTags().contains("tableball_ingame")) {
                                int wins = gameState.getRoundWins(p);
                                worldPlayer.sendMessage("§b" + p.getName() + ": §a" + wins + "胜");
                            }
                        }
                    }
                } else {
                    // 标准模式显示分数
                    for (Player p : playersInWorld) {
                        if (p.getScoreboardTags().contains("tableball_ingame")) {
                            int s = RoundManager.scores.getOrDefault(p.getName(), 0);
                            worldPlayer.sendMessage("§b" + p.getName() + ": §a" + s);
                        }
                    }
                }

                // 判定对方获胜（弃权）
                if (opponent != null) {
                    worldPlayer.sendMessage("§6获胜者：" + opponent.getName() + " (对方弃权)");
                }
                worldPlayer.sendMessage("§a你已被传送回主城！");
            }

            endGameForRealLikeDeepseekSTFU(currentWorld);
            return true;
        }

        // 检查是否在观战
        if (Start.currentGame != null && currentWorld.equals(Start.currentGame)) {
            player.sendMessage("§a已离开游戏！");
            String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
            if (Bukkit.getWorld(lobbyWorld) != null) {
                player.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation());
            }
            return true;
        }

        // 玩家不在游戏中
        player.sendMessage("§c你不在游戏中！");
        return true;
    }
} 
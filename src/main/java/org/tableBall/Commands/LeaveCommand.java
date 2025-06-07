package org.tableBall.Commands;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Game.GameState;
import org.tableBall.Manager.RoundManager;
import org.tableBall.TableBall;

import java.util.HashSet;
import java.util.List;

public class LeaveCommand implements CommandExecutor {
    public static TableBall plugin;


    public LeaveCommand(TableBall pl) {
        plugin = pl;
    }

    public static void endGameForRealLikeDeepseekSTFU(World currentWorld){
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");

        // 清除所有球
        for (DisplayBall i: new HashSet<>(DisplayBall.displayBalls)) {
            i.destroy();
        }

        System.out.println(RoundManager.scores);

        // 获取当前世界的所有玩家
        for (Player worldPlayer : currentWorld.getPlayers()) {
            // 传送玩家到主城
            if (Bukkit.getWorld(lobbyWorld) != null) {
                // 清空物品栏
                worldPlayer.getInventory().clear();
                // 清除药水效果
                worldPlayer.getActivePotionEffects().forEach(effect -> worldPlayer.removePotionEffect(effect.getType()));
                // 关闭碰撞箱
                worldPlayer.setCollidable(false);
                // 重置allowFlight等属性
                worldPlayer.setAllowFlight(false);
                worldPlayer.setFlying(false);
                // 展示结算信息
                worldPlayer.sendMessage("§e本局结算：");
                for (Player p : currentWorld.getPlayers()) {
                    int s = RoundManager.scores.getOrDefault(p.getName(), -1);
                    if(s==-1) continue;
                    worldPlayer.sendMessage("§b" + p.getName() + "§f 得分: §a" + s);
                }
                // 判定胜负
                int maxScore = -1;
                List<Player> winners = new java.util.ArrayList<>();
                for (Player p : currentWorld.getPlayers()) {
                    int s = RoundManager.scores.getOrDefault(p.getName(), 0);
                    if (s > maxScore) {
                        maxScore = s;
                        winners.clear();
                        winners.add(p);
                    } else if (s == maxScore) {
                        winners.add(p);
                    }
                }
                if (winners.size() == 1) {
                    worldPlayer.sendMessage("§6获胜者: " + winners.get(0).getName());
                } else {
                    worldPlayer.sendMessage("§6平局！");
                }
                worldPlayer.sendMessage("§a你已被传送回主城！");
            } else {
                worldPlayer.sendMessage("§c主城世界不存在！");
            }
        }
        currentWorld.getPlayers().forEach(player->player.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation()));
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
        endGameForRealLikeDeepseekSTFU(currentWorld);
        return true;
    }
} 
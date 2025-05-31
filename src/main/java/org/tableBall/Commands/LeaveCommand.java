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
import org.tableBall.TableBall;

import java.util.HashSet;
import java.util.List;

public class LeaveCommand implements CommandExecutor {
    private final TableBall plugin;

    public LeaveCommand(TableBall plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        Player player = (Player) sender;
        World currentWorld = player.getWorld();
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");

        // 清除所有球
        for (DisplayBall i: new HashSet<>(DisplayBall.displayBalls)) {
            i.destroy();
        }

        // 获取当前世界的所有玩家
        for (Player worldPlayer : currentWorld.getPlayers()) {
            // 传送玩家到主城
            if (Bukkit.getWorld(lobbyWorld) != null) {
                worldPlayer.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation());
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
                for (Player p : plugin.getInGame().getPlayersInWorld(currentWorld.getName())) {
                    int s = plugin.getInGame().getScore(p);
                    worldPlayer.sendMessage("§b" + p.getName() + "§f 得分: §a" + s);
                }
                // 判定胜负
                int maxScore = -1;
                List<Player> winners = new java.util.ArrayList<>();
                for (Player p : plugin.getInGame().getPlayersInWorld(currentWorld.getName())) {
                    int s = plugin.getInGame().getScore(p);
                    if (s > maxScore) {
                        maxScore = s;
                        winners.clear();
                        winners.add(p);
                    } else if (s == maxScore) {
                        winners.add(p);
                    }
                }
                if (winners.size() == 1) {
                    worldPlayer.sendMessage("§6获胜者: " + winners.get(0).getName() + " §e得分: " + maxScore);
                } else {
                    worldPlayer.sendMessage("§6平局！§e得分: " + maxScore);
                }
                // 彻底移除玩家，释放世界占用
                plugin.getInGame().removePlayer(worldPlayer);
                worldPlayer.sendMessage("§a你已被传送回主城！");
            } else {
                worldPlayer.sendMessage("§c主城世界不存在！");
            }
        }
        return true;
    }
} 
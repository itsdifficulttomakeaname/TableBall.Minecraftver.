package org.tableBall.Game;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tableBall.Manager.ScoreBoardManager;

import java.util.ArrayList;
import java.util.List;

public class End {
    private final Plugin plugin;
    private final ScoreBoardManager scoreBoardManager;
    private final MultiverseCore mvCore;

    public End(Plugin plugin, ScoreBoardManager scoreBoardManager) {
        this.plugin = plugin;
        this.scoreBoardManager = scoreBoardManager;
        this.mvCore = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
    }

    /**
     * 结束游戏并处理结果
     * @param players 参与游戏的玩家列表
     */
    public void endGame(List<Player> players) {
        if (players.isEmpty()) return;

        // 获取最高分
        int maxScore = -1;
        List<Player> winners = new ArrayList<>();

        for (Player player : players) {
            int score = scoreBoardManager.getScore(player);
            if (score > maxScore) {
                maxScore = score;
                winners.clear();
                winners.add(player);
            } else if (score == maxScore) {
                winners.add(player);
            }
        }

        // 显示结果
        if (winners.size() == 1) {
            // 单个获胜者
            Player winner = winners.get(0);
            showResult(winner.getName());
        } else {
            // 平局
            showResult("平局");
        }

        // 3秒后传送回大厅
        new BukkitRunnable() {
            @Override
            public void run() {
                teleportToLobby(players);
            }
        }.runTaskLater(plugin, 60L); // 3秒 = 60 ticks
    }

    /**
     * 显示游戏结果
     * @param result 结果信息
     */
    private void showResult(String result) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                "§6获胜者",
                "§e" + result,
                10, 70, 20
            );
        }
    }

    /**
     * 传送玩家回大厅
     * @param players 要传送的玩家列表
     */
    private void teleportToLobby(List<Player> players) {
        if (mvCore == null) return;

        MVWorldManager worldManager = mvCore.getMVWorldManager();
        // 查找大厅世界
        String lobbyWorld = null;
        for (MultiverseWorld world : worldManager.getMVWorlds()) {
            // 假设大厅世界的名称包含"lobby"
            if (world.getName().toLowerCase().contains("lobby")) {
                lobbyWorld = world.getName();
                break;
            }
        }

        if (lobbyWorld == null) return;

        // 传送所有玩家
        for (Player player : players) {
            player.teleport(worldManager.getMVWorld(lobbyWorld).getSpawnLocation());
            // 清除计分板数据
            scoreBoardManager.clearPlayerData(player);
        }
    }
} 
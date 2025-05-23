package org.tableBall.Manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.*;
import org.tableBall.TableBall;

import java.util.HashMap;
import java.util.Map;

public class ScoreBoardManager {
    private final Plugin plugin;
    private final Map<String, Scoreboard> playerScoreboards;
    private final Map<String, Integer> playerScores;
    private final Map<String, String> gameTypes;
    private final Map<String, Long> gameStartTimes;
    private boolean enabled;

    public ScoreBoardManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new HashMap<>();
        this.playerScores = new HashMap<>();
        this.gameTypes = new HashMap<>();
        this.gameStartTimes = new HashMap<>();
        this.enabled = false;
    }

    /**
     * 启用或禁用计分板
     * @param enable 是否启用
     */
    public void setEnabled(boolean enable) {
        this.enabled = enable;
        if (!enable) {
            // 清除所有计分板
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
            playerScoreboards.clear();
            playerScores.clear();
            gameTypes.clear();
            gameStartTimes.clear();
        }
    }

    /**
     * 创建玩家的计分板
     * @param player 玩家
     * @param gameType 游戏类型
     */
    public void createScoreboard(Player player, String gameType) {
        if (!enabled) return;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("game", "dummy", Component.text("§6台球游戏"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 从配置文件读取计分板格式
        ConfigurationSection format = plugin.getConfig().getConfigurationSection("scoreboard");
        if (format == null) return;

        int score = 15; // 从下往上显示
        for (String line : format.getStringList("format")) {
            // 替换变量
            line = line.replace("{player}", player.getName())
                      .replace("{score}", String.valueOf(getScore(player)))
                      .replace("{type}", gameType)
                      .replace("{time}", getFormattedTime(player));

            objective.getScore(line).setScore(score--);
        }

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getName(), scoreboard);
        playerScores.put(player.getName(), 0);
        gameTypes.put(player.getName(), gameType);
        gameStartTimes.put(player.getName(), System.currentTimeMillis());

        // 启动定时更新任务
        startUpdateTask(player);
    }

    /**
     * 获取格式化的游戏时间
     * @param player 玩家
     * @return 格式化的时间字符串 (分:秒)
     */
    private String getFormattedTime(Player player) {
        Long startTime = gameStartTimes.get(player.getName());
        if (startTime == null) return "0:00";

        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000; // 转换为秒
        long minutes = elapsedTime / 60;
        long seconds = elapsedTime % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 启动定时更新任务
     * @param player 玩家
     */
    private void startUpdateTask(Player player) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && playerScoreboards.containsKey(player.getName())) {
                updateScoreboard(player);
            }
        }, 20L, 20L); // 每秒更新一次
    }

    /**
     * 更新玩家的分数
     * @param player 玩家
     * @param points 增加的分数
     */
    public void addScore(Player player, int points) {
        if (!enabled) return;

        String gameType = gameTypes.get(player.getName());
        if (gameType == null) return;

        // 根据游戏类型处理分数
        if ("Standard".equals(gameType)) {
            points = 2; // 标准模式固定加2分
        }

        int currentScore = getScore(player);
        playerScores.put(player.getName(), currentScore + points);
        updateScoreboard(player);
    }

    /**
     * 获取玩家的分数
     * @param player 玩家
     * @return 分数
     */
    public int getScore(Player player) {
        return playerScores.getOrDefault(player.getName(), 0);
    }

    /**
     * 更新玩家的计分板
     * @param player 玩家
     */
    private void updateScoreboard(Player player) {
        if (!enabled) return;

        Scoreboard scoreboard = playerScoreboards.get(player.getName());
        if (scoreboard == null) return;

        Objective objective = scoreboard.getObjective("game");
        if (objective == null) return;

        // 清除旧的分数
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // 重新设置分数
        ConfigurationSection format = plugin.getConfig().getConfigurationSection("scoreboard");
        if (format == null) return;

        int score = 15;
        for (String line : format.getStringList("format")) {
            line = line.replace("{player}", player.getName())
                      .replace("{score}", String.valueOf(getScore(player)))
                      .replace("{type}", gameTypes.get(player.getName()))
                      .replace("{time}", getFormattedTime(player));

            objective.getScore(line).setScore(score--);
        }
    }

    /**
     * 获取游戏类型
     * @param player 玩家
     * @return 游戏类型
     */
    public String getGameType(Player player) {
        return gameTypes.get(player.getName());
    }

    /**
     * 清除玩家的计分板数据
     * @param player 玩家
     */
    public void clearPlayerData(Player player) {
        playerScoreboards.remove(player.getName());
        playerScores.remove(player.getName());
        gameTypes.remove(player.getName());
        gameStartTimes.remove(player.getName());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
} 
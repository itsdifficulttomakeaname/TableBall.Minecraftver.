package org.tableBall.Manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.tableBall.TableBall;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ScoreBoardManager {
    private final Plugin plugin;
    private final Map<String, Scoreboard> playerScoreboards;
    private final Map<String, Integer> playerScores;
    private final Map<String, String> gameTypes;
    private final Map<String, Long> gameStartTimes;
    private final Map<String, Integer> totalRounds; // 总对局数
    private final Map<String, Integer> currentRounds; // 当前对局数
    private final Map<String, Integer> player1Wins; // 玩家1获胜局数
    private final Map<String, Integer> player2Wins; // 玩家2获胜局数
    private final Map<String, String> infractionStatus; // 犯规状态
    private final Map<String, String> infractionReason; // 犯规原因
    private boolean enabled;

    public ScoreBoardManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new HashMap<>();
        this.playerScores = new HashMap<>();
        this.gameTypes = new HashMap<>();
        this.gameStartTimes = new HashMap<>();
        this.totalRounds = new HashMap<>();
        this.currentRounds = new HashMap<>();
        this.player1Wins = new HashMap<>();
        this.player2Wins = new HashMap<>();
        this.infractionStatus = new HashMap<>();
        this.infractionReason = new HashMap<>();
        this.enabled = true; // 默认启用计分板
    }



    /**
     * 创建玩家的计分板
     * @param player 玩家
     * @param gameType 游戏类型
     * @param rounds 对局数
     */
    public void createScoreboard(Player player, String gameType, int rounds) {
        if (!enabled) {
            plugin.getLogger().warning("计分板未启用");
            return;
        }



        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("game", "dummy", Component.text("§6台球游戏"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getName(), scoreboard);
        playerScores.put(player.getName(), 0);
        gameTypes.put(player.getName(), gameType);
        gameStartTimes.put(player.getName(), System.currentTimeMillis());
        totalRounds.put(player.getName(), rounds);
        currentRounds.put(player.getName(), 1);
        player1Wins.put(player.getName(), 0);
        player2Wins.put(player.getName(), 0);
        infractionStatus.put(player.getName(), "未犯规");
        infractionReason.put(player.getName(), "");

        // 更新计分板显示
        updateScoreboard(player);

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
        // 8balls模式不使用分数系统

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
        if (!enabled) {
            plugin.getLogger().warning("计分板未启用，跳过更新");
            return;
        }

        Scoreboard scoreboard = playerScoreboards.get(player.getName());
        if (scoreboard == null) {
            plugin.getLogger().warning("玩家 " + player.getName() + " 的计分板不存在");
            return;
        }

        Objective objective = scoreboard.getObjective("game");
        if (objective == null) {
            plugin.getLogger().warning("玩家 " + player.getName() + " 的计分板目标不存在");
            return;
        }

        // 清除旧的分数
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }



        try {
            // 从scoreboard.yml读取配置
            File scoreboardFile = new File(plugin.getDataFolder(), "scoreboard.yml");
            if (!scoreboardFile.exists()) {
                plugin.getLogger().warning("scoreboard.yml不存在，使用默认显示");
                // 使用简化的默认显示
                showDefaultScoreboard(objective, player);
                return;
            }

            FileConfiguration scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardFile);
            ConfigurationSection scoreboardSection = scoreboardConfig.getConfigurationSection("scoreboard");
            ConfigurationSection functionsSection = scoreboardConfig.getConfigurationSection("functions");

            if (scoreboardSection == null) {
                plugin.getLogger().warning("scoreboard.yml格式错误，使用默认显示");
                showDefaultScoreboard(objective, player);
                return;
            }

            // 获取玩家信息
            String playerName = player.getName();
            String worldName = player.getWorld().getName();
            String gameType = gameTypes.getOrDefault(playerName, "Standard");

            // 获取对局中的两个玩家
            List<Player> playersInWorld = getPlayersInWorld(worldName);
            String player1Name = playersInWorld.size() > 0 ? playersInWorld.get(0).getName() : "玩家1";
            String player2Name = playersInWorld.size() > 1 ? playersInWorld.get(1).getName() : "玩家2";

            // 获取当前回合玩家
            Player currentPlayer = getCurrentPlayer(worldName);
            boolean isPlayer1Turn = currentPlayer != null && currentPlayer.getName().equals(player1Name);
            boolean isPlayer2Turn = currentPlayer != null && currentPlayer.getName().equals(player2Name);

            int score = 15; // 从下往上显示
            for (String key : scoreboardSection.getKeys(false)) {
                String line = scoreboardSection.getString(key, "");

                // 处理函数调用
                if (line.startsWith("&") && functionsSection != null) {
                    String functionName = line.substring(1);
                    ConfigurationSection functionSection = functionsSection.getConfigurationSection(functionName);
                    if (functionSection != null) {
                        line = processFunctionCall(functionSection, playerName, isPlayer1Turn, isPlayer2Turn, player1Name, player2Name, worldName);
                    }
                } else {
                    // 处理变量替换
                    line = processVariables(line, playerName, worldName, player1Name, player2Name, gameType, playersInWorld);
                }

                if (!line.isEmpty()) {
                    objective.getScore(line).setScore(score--);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("更新计分板时出错: " + e.getMessage());
            e.printStackTrace();
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
        totalRounds.remove(player.getName());
        currentRounds.remove(player.getName());
        player1Wins.remove(player.getName());
        player2Wins.remove(player.getName());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    /**
     * 处理函数调用
     */
    private String processFunctionCall(ConfigurationSection functionSection, String playerName,
                                     boolean isPlayer1Turn, boolean isPlayer2Turn,
                                     String player1Name, String player2Name, String worldName) {
        String functionName = functionSection.getName();

        // 处理displayPlayer1和displayPlayer2
        if ("displayPlayer1".equals(functionName) || "displayPlayer2".equals(functionName)) {
            if (functionSection.contains("isThisTurn == true")) {
                if ((playerName.equals(player1Name) && isPlayer1Turn) ||
                    (playerName.equals(player2Name) && isPlayer2Turn)) {
                    return functionSection.getString("isThisTurn == true.text", "")
                        .replace("{player1}", player1Name)
                        .replace("{player2}", player2Name);
                }
            }

            if (functionSection.contains("isThisTurn == false")) {
                if ((playerName.equals(player1Name) && !isPlayer1Turn) ||
                    (playerName.equals(player2Name) && !isPlayer2Turn)) {
                    return functionSection.getString("isThisTurn == false.text", "")
                        .replace("{player1}", player1Name)
                        .replace("{player2}", player2Name);
                }
            }
        }

        // 处理reason函数
        if ("reason".equals(functionName)) {
            String infractionStatus = this.infractionStatus.getOrDefault(playerName, "未犯规");
            String infractionReason = this.infractionReason.getOrDefault(playerName, "");

            if (functionSection.contains("isInfraction_犯规")) {
                if ("犯规".equals(infractionStatus)) {
                    return functionSection.getString("isInfraction_犯规.text", "")
                        .replace("{reason}", infractionReason);
                }
            }

            if (functionSection.contains("isInfraction_未犯规")) {
                if ("未犯规".equals(infractionStatus)) {
                    return functionSection.getString("isInfraction_未犯规.text", "");
                }
            }
        }

        return "";
    }

    /**
     * 处理变量替换
     */
    private String processVariables(String line, String playerName, String worldName,
                                  String player1Name, String player2Name, String gameType, List<Player> playersInWorld) {
        // 获取分数信息
        int score1 = 0, score2 = 0, SCORE1 = 0, SCORE2 = 0;

        if ("8balls".equals(gameType)) {
            // 8balls模式：score1/score2显示当前局分数，SCORE1/SCORE2显示大比分
            if (plugin instanceof TableBall) {
                TableBall tableBall = (TableBall) plugin;
                org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
                if (gameState != null && playersInWorld.size() >= 2) {
                    SCORE1 = gameState.getRoundWins(playersInWorld.get(0));
                    SCORE2 = gameState.getRoundWins(playersInWorld.get(1));
                }
            }
            // 8balls模式当前局分数暂时设为0（可以后续扩展为剩余球数等）
            score1 = 0;
            score2 = 0;
        } else {
            // 标准模式：score1/score2显示分数，SCORE1/SCORE2也显示分数
            if (playersInWorld.size() >= 2) {
                score1 = getScore(playersInWorld.get(0));
                score2 = getScore(playersInWorld.get(1));
                SCORE1 = score1;
                SCORE2 = score2;
            }
        }

        String infractionStatus = this.infractionStatus.getOrDefault(playerName, "未犯规");
        String infractionReason = this.infractionReason.getOrDefault(playerName, "");

        return line.replace("{player1}", player1Name)
                  .replace("{player2}", player2Name)
                  .replace("{score1}", String.valueOf(score1))
                  .replace("{score2}", String.valueOf(score2))
                  .replace("{SCORE1}", String.valueOf(SCORE1))
                  .replace("{SCORE2}", String.valueOf(SCORE2))
                  .replace("{lastTime}", getFormattedTime(playerName))
                  .replace("{isInfraction}", infractionStatus)
                  .replace("{reason}", infractionReason);
    }

    /**
     * 获取世界中的玩家列表
     */
    private List<Player> getPlayersInWorld(String worldName) {
        List<Player> players = new ArrayList<>();
        if (plugin instanceof TableBall) {
            TableBall tableBall = (TableBall) plugin;
            players = tableBall.getInGame().getPlayersInWorld(worldName);
        }

        // 如果没有获取到玩家，尝试从当前世界获取
        if (players.isEmpty()) {
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player p : world.getPlayers()) {
                    if (p.getScoreboardTags().contains("tableball_ingame")) {
                        players.add(p);
                    }
                }
            }
        }

        return players;
    }

    /**
     * 获取当前回合玩家
     */
    private Player getCurrentPlayer(String worldName) {
        if (plugin instanceof TableBall) {
            TableBall tableBall = (TableBall) plugin;
            return tableBall.getRoundManager().getCurrentPlayer(worldName);
        }
        return null;
    }

    /**
     * 获取格式化的游戏时间
     */
    private String getFormattedTime(String playerName) {
        Long startTime = gameStartTimes.get(playerName);
        if (startTime == null) return "00:00";

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long minutes = elapsed / 60;
        long seconds = elapsed % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 设置犯规状态
     * @param worldName 世界名称
     * @param reason 犯规原因
     */
    public void setInfraction(String worldName, String reason) {
        // 为该世界的所有玩家设置犯规状态
        List<Player> players = getPlayersInWorld(worldName);
        for (Player player : players) {
            infractionStatus.put(player.getName(), "犯规");
            infractionReason.put(player.getName(), reason);
            updateScoreboard(player);
        }
    }

    /**
     * 清除犯规状态
     * @param worldName 世界名称
     */
    public void clearInfraction(String worldName) {
        // 为该世界的所有玩家清除犯规状态
        List<Player> players = getPlayersInWorld(worldName);
        for (Player player : players) {
            infractionStatus.put(player.getName(), "未犯规");
            infractionReason.put(player.getName(), "");
            updateScoreboard(player);
        }
    }

    /**
     * 显示默认计分板
     */
    private void showDefaultScoreboard(Objective objective, Player player) {
        String playerName = player.getName();
        String worldName = player.getWorld().getName();
        String gameType = gameTypes.getOrDefault(playerName, "Standard");

        List<Player> playersInWorld = getPlayersInWorld(worldName);
        String player1Name = playersInWorld.size() > 0 ? playersInWorld.get(0).getName() : "玩家1";
        String player2Name = playersInWorld.size() > 1 ? playersInWorld.get(1).getName() : "玩家2";

        Player currentPlayer = getCurrentPlayer(worldName);

        int score = 15;
        objective.getScore("§b§l[台球厅]").setScore(score--);
        objective.getScore("").setScore(score--);

        // 显示玩家名称
        if (currentPlayer != null && currentPlayer.getName().equals(player1Name)) {
            objective.getScore("§a" + player1Name).setScore(score--);
        } else {
            objective.getScore("§7" + player1Name).setScore(score--);
        }

        if (currentPlayer != null && currentPlayer.getName().equals(player2Name)) {
            objective.getScore("§a" + player2Name).setScore(score--);
        } else {
            objective.getScore("§7" + player2Name).setScore(score--);
        }

        objective.getScore(" ").setScore(score--);

        // 显示分数
        if ("8balls".equals(gameType)) {
            if (plugin instanceof TableBall) {
                TableBall tableBall = (TableBall) plugin;
                org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
                if (gameState != null && playersInWorld.size() >= 2) {
                    int wins1 = gameState.getRoundWins(playersInWorld.get(0));
                    int wins2 = gameState.getRoundWins(playersInWorld.get(1));
                    objective.getScore("大比分: " + wins1 + ":" + wins2).setScore(score--);
                }
            }
        } else {
            if (playersInWorld.size() >= 2) {
                objective.getScore(player1Name + ": " + getScore(playersInWorld.get(0))).setScore(score--);
                objective.getScore(player2Name + ": " + getScore(playersInWorld.get(1))).setScore(score--);
            }
        }

        objective.getScore("  ").setScore(score--);
        objective.getScore("时间: " + getFormattedTime(playerName)).setScore(score--);

        // 显示犯规状态
        String infractionStatus = this.infractionStatus.getOrDefault(playerName, "未犯规");
        String infractionReason = this.infractionReason.getOrDefault(playerName, "");
        if ("犯规".equals(infractionStatus)) {
            objective.getScore("§c[犯规]: " + infractionReason).setScore(score--);
        }
    }
} 
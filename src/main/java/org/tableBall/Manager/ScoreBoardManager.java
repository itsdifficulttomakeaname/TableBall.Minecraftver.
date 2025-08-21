package org.tableBall.Manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.tableBall.TableBall;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class ScoreBoardManager {
    public static long startTime=0L;

    private final Plugin plugin;
    private final Map<String, Scoreboard> playerScoreboards;
    private final Map<String, Integer> playerScores;
    private final Map<String, String> gameTypes;
    private final Map<String, Integer> totalRounds; // 总对局数
    private final Map<String, Integer> currentRounds; // 当前对局数
    private final Map<String, Integer> player1Wins; // 玩家1获胜局数
    private final Map<String, Integer> player2Wins; // 玩家2获胜局数
    private final Map<String, String> infractionStatus; // 犯规状态
    private final Map<String, String> infractionReason; // 犯规原因
    private boolean enabled;
    public BukkitTask TitlePointerCycleTask;

    /**
     * 当前标题动画进度
     */
    private int TitlePointer;

    public ScoreBoardManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new HashMap<>();
        this.playerScores = new HashMap<>();
        this.gameTypes = new HashMap<>();
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
        TitlePointer = 0;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("game", "dummy", Component.text("§6台球游戏"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        player.setScoreboard(scoreboard);
        playerScoreboards.put(player.getName(), scoreboard);
        playerScores.put(player.getName(), 0);
        gameTypes.put(player.getName(), gameType);
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
     * 为旁观者创建计分板
     * @param spectator 旁观者
     * @param worldName 观战的世界名称
     */
    public void createSpectatorScoreboard(Player spectator, String worldName) {
        if (!enabled) {
            plugin.getLogger().warning("计分板未启用");
            return;
        }

        // 获取该世界的游戏信息
        List<Player> playersInWorld = getPlayersInWorld(worldName);
        if (playersInWorld.isEmpty()) {
            spectator.sendMessage("§c当前没有正在进行的游戏");
            return;
        }

        // 从游戏玩家中获取游戏类型和对局数
        String gameType = "Standard";
        int rounds = 1;
        for (Player gamePlayer : playersInWorld) {
            if (gameTypes.containsKey(gamePlayer.getName())) {
                gameType = gameTypes.get(gamePlayer.getName());
                rounds = totalRounds.getOrDefault(gamePlayer.getName(), 1);
                break;
            }
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("game", "dummy", Component.text("§6台球游戏"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        spectator.setScoreboard(scoreboard);
        playerScoreboards.put(spectator.getName(), scoreboard);
        gameTypes.put(spectator.getName(), gameType);
        totalRounds.put(spectator.getName(), rounds);
        currentRounds.put(spectator.getName(), 1);
        infractionStatus.put(spectator.getName(), "未犯规");
        infractionReason.put(spectator.getName(), "");
        // 启动动画帧更新任务
        stratTitlePointerCycle();

        // 更新计分板显示
        updateScoreboard(spectator);

        // 启动定时更新任务
        startUpdateTask(spectator);
    }

    /**
     * 动画帧定时更新
     */
    private void stratTitlePointerCycle(){
        // 更新标题动画帧
        TitlePointerCycleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if(TitlePointer < ScoreBoardTitle.length-1) TitlePointer++;
            else TitlePointer = 0;
        }, 20L, 20L);
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
            // 使用硬编码的计分板格式
            buildHardcodedScoreboard(objective, player);
        } catch (Exception e) {
            plugin.getLogger().warning("更新计分板时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 计分板标题常量
     * 共 11 个动画部分
     */
    private String[] ScoreBoardTitle = {
            "§b§l[台球厅]§r",
            "§6§l[§b§l台球厅]§r",
            "§6§l[台§b§l球厅]§r",
            "§6§l[台球§b§l厅]§r",
            "§6§l[台球厅§b§l]§r",
            "§6§l[台球厅]§r",
            "§b§l[§6§l台球厅]§r",
            "§b§l[台§6§l球厅]§r",
            "§b§l[台球§6§l厅]§r",
            "§b§l[台球厅§6§l]§r",
            "§b§l[台球厅]§r"
    };

    /**
     * 构建硬编码的计分板格式
     * @param objective 计分板目标
     * @param player 玩家
     */
    private void buildHardcodedScoreboard(Objective objective, Player player) {
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

        // 0: 标题
        String title = ScoreBoardTitle[TitlePointer];
        objective.getScore(title).setScore(score--);

        // 1: 空行
        objective.getScore("").setScore(score--);

        // 2: 玩家1显示
        String player1Display = getPlayerDisplay(player1Name, player1Name, isPlayer1Turn, gameType, worldName, playersInWorld);
        if (!player1Display.isEmpty()) {
            objective.getScore(player1Display).setScore(score--);
        }

        // 3: 玩家2显示
        String player2Display = getPlayerDisplay(player2Name, player2Name, isPlayer2Turn, gameType, worldName, playersInWorld);
        if (!player2Display.isEmpty()) {
            objective.getScore(player2Display).setScore(score--);
        }

        // 4: 空行
        objective.getScore(" ").setScore(score--);

        // 5-6: 分数显示
        if ("8balls".equals(gameType)) {
            // 8balls模式显示当前局剩余球数等信息
            String remainingBalls1 = getRemainingBallsDisplay(playersInWorld.size() > 0 ? playersInWorld.get(0) : null, worldName);
            String remainingBalls2 = getRemainingBallsDisplay(playersInWorld.size() > 1 ? playersInWorld.get(1) : null, worldName);
            
            if (!remainingBalls1.isEmpty()) {
                objective.getScore(remainingBalls1).setScore(score--);
            }
            if (!remainingBalls2.isEmpty()) {
                objective.getScore(remainingBalls2).setScore(score--);
            }
        } else {
            // 标准模式显示分数
            int score1 = playersInWorld.size() > 0 ? getScore(playersInWorld.get(0)) : 0;
            int score2 = playersInWorld.size() > 1 ? getScore(playersInWorld.get(1)) : 0;
            
            String player1Name_short = playersInWorld.size() > 0 ? playersInWorld.get(0).getName() : "玩家1";
            String player2Name_short = playersInWorld.size() > 1 ? playersInWorld.get(1).getName() : "玩家2";
            
            // 限制显示长度避免计分板过宽
            if (player1Name_short.length() > 8) {
                player1Name_short = player1Name_short.substring(0, 8) + "...";
            }
            if (player2Name_short.length() > 8) {
                player2Name_short = player2Name_short.substring(0, 8) + "...";
            }
            
            objective.getScore("§a" + player1Name_short + "§f: §e" + score1).setScore(score--);
            objective.getScore("§a" + player2Name_short + "§f: §e" + score2).setScore(score--);
        }

        // 7: 大比分
        String bigScoreDisplay = getBigScoreDisplay(gameType, worldName, playersInWorld);
        if (!bigScoreDisplay.isEmpty()) {
            objective.getScore(bigScoreDisplay).setScore(score--);
        }

        // 8: 当前局数显示（8balls模式）
        String currentRoundDisplay = getCurrentRoundDisplay(gameType, worldName);
        if (!currentRoundDisplay.isEmpty()) {
            objective.getScore(currentRoundDisplay).setScore(score--);
        }

        // 9: 游玩时间
        objective.getScore("§b游玩时间: §f" + getFormattedTime()).setScore(score--);

        // 10: 指令Tip
        objective.getScore("§a[Tip] §b使用 §b§l/tpwb §r§b传送到母球旁").setScore(score--);

        // 11: 犯规状态
        String infractionDisplay = getInfractionDisplay(playerName);
        if (!infractionDisplay.isEmpty()) {
            objective.getScore(infractionDisplay).setScore(score--);
        }
    }

    /**
     * 获取玩家显示文本（包含颜色信息）
     */
    private String getPlayerDisplay(String targetPlayerName, String currentPlayerName, boolean isThisTurn, 
                                   String gameType, String worldName, List<Player> playersInWorld) {
        String colorCode = isThisTurn ? "§a" : "§7";
        String colorDisplay = getPlayerColorDisplay(targetPlayerName, gameType, worldName, playersInWorld);
        
        return colorCode + targetPlayerName + " " + colorDisplay;
    }

    /**
     * 获取玩家颜色显示
     */
    private String getPlayerColorDisplay(String playerName, String gameType, String worldName, List<Player> playersInWorld) {
        if (!"8balls".equals(gameType) || !(plugin instanceof TableBall)) {
            return "";
        }

        TableBall tableBall = (TableBall) plugin;
        org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
        if (gameState == null) return "";

        // 找到对应的Player对象
        Player targetPlayer = null;
        for (Player p : playersInWorld) {
            if (p.getName().equals(playerName)) {
                targetPlayer = p;
                break;
            }
        }

        if (targetPlayer == null) return "";

        String playerColor = gameState.getPlayerColor(targetPlayer);
        return getColorDisplay(playerColor);
    }

    /**
     * 获取剩余球数显示（8balls模式）
     */
    private String getRemainingBallsDisplay(Player player, String worldName) {
        if (player == null || !(plugin instanceof TableBall)) {
            return "";
        }

        TableBall tableBall = (TableBall) plugin;
        org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
        if (gameState == null) return "";

        String playerColor = gameState.getPlayerColor(player);
        if ("none".equals(playerColor)) {
            return "§7" + player.getName() + ": §7[未分配]";
        }

        int remainingBalls = 0;
        if ("red".equals(playerColor)) {
            remainingBalls = countRemainingBalls(worldName, 1, 7);
        } else if ("blue".equals(playerColor)) {
            remainingBalls = countRemainingBalls(worldName, 9, 15);
        }

        String colorDisplay = getColorDisplay(playerColor);
        String ballCountColor = remainingBalls <= 2 ? "§e" : "§f"; // 剩余球数少时用黄色警告
        
        return "§f" + player.getName() + ": " + colorDisplay + " " + ballCountColor + "(" + remainingBalls + "球)";
    }

    /**
     * 计算剩余球数
     */
    private int countRemainingBalls(String worldName, int minNumber, int maxNumber) {
        if (!(plugin instanceof TableBall)) return 0;

        TableBall tableBall = (TableBall) plugin;
        int count = 0;
        
        // 遍历场上的球来计算剩余数量
        for (org.tableBall.Entity.DisplayBall ball : org.tableBall.Entity.DisplayBall.displayBalls) {
            if (ball.getWorld().equals(worldName) && !ball.isMotherBall) {
                int ballNumber = tableBall.getInGame().extractBallNumberFromDisplayBall(ball);
                if (ballNumber >= minNumber && ballNumber <= maxNumber) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 获取大比分显示
     */
    private String getBigScoreDisplay(String gameType, String worldName, List<Player> playersInWorld) {
        if ("8balls".equals(gameType) && plugin instanceof TableBall) {
            TableBall tableBall = (TableBall) plugin;
            org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
            if (gameState != null && playersInWorld.size() >= 2) {
                int wins1 = gameState.getRoundWins(playersInWorld.get(0));
                int wins2 = gameState.getRoundWins(playersInWorld.get(1));
                
                // 根据获胜局数添加颜色
                String score1Color = wins1 > wins2 ? "§a" : "§f";
                String score2Color = wins2 > wins1 ? "§a" : "§f";
                
                return "§6大比分: " + score1Color + wins1 + "§f:" + score2Color + wins2;
            }
        } else {
            // 标准模式显示总分
            if (playersInWorld.size() >= 2) {
                int score1 = getScore(playersInWorld.get(0));
                int score2 = getScore(playersInWorld.get(1));
                
                // 根据分数添加颜色
                String score1Color = score1 > score2 ? "§a" : "§f";
                String score2Color = score2 > score1 ? "§a" : "§f";
                
                return "§6总分: " + score1Color + score1 + "§f:" + score2Color + score2;
            }
        }
        return "";
    }

    /**
     * 获取当前局数显示（8balls模式）
     */
    private String getCurrentRoundDisplay(String gameType, String worldName) {
        if (!"8balls".equals(gameType) || !(plugin instanceof TableBall)) {
            return "";
        }

        TableBall tableBall = (TableBall) plugin;
        org.tableBall.Game.GameState gameState = tableBall.getRoundManager().getGameState(worldName);
        if (gameState == null) return "";

        int currentRound = gameState.getCurrentRound();
        int totalRounds = gameState.getTotalRounds();
        
        return "§e第 " + currentRound + "/" + totalRounds + " 局";
    }

    /**
     * 获取犯规显示
     */
    private String getInfractionDisplay(String playerName) {
        String infractionStatus = this.infractionStatus.getOrDefault(playerName, "未犯规");
        String infractionReason = this.infractionReason.getOrDefault(playerName, "");

        if ("犯规".equals(infractionStatus)) {
            return "§c[犯规]: " + infractionReason;
        }
        return "";
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
     * 获取颜色显示文本
     */
    private String getColorDisplay(String color) {
        return switch (color) {
            case "red" -> "§c■■■";
            case "blue" -> "§9■■■";
            case "none" -> "§7[未分配]";
            default -> "";
        };
    }

    /**
     * 获取格式化的游戏时间
     */
    private String getFormattedTime() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long minutes = elapsed / 60;
        long seconds = elapsed % 60;
        return String.format("%d:%02d", minutes, seconds);
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
        totalRounds.remove(player.getName());
        currentRounds.remove(player.getName());
        player1Wins.remove(player.getName());
        player2Wins.remove(player.getName());
        infractionStatus.remove(player.getName());
        infractionReason.remove(player.getName());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
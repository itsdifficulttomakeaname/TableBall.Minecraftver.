package org.tableBall.Game;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.tableBall.Commands.LeaveCommand;
import org.tableBall.Manager.RoundManager;
import org.tableBall.TableBall;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Utils.WorldUtils;

import java.io.File;
import java.util.*;

public class InGame {
    private final TableBall plugin;
    private final WorldUtils worldUtils;
    public final Map<String, Map<String, BallData>> worldBalls;
    private final Map<String, Map<String, HoleData>> worldHoles;
    public final Map<String, GameData> gameDataMap; // 世界 -> 游戏数据
    public FileConfiguration ballsConfig;
    private final Map<String, Set<Player>> playersInGame = new HashMap<>();
    private final Map<String, Set<DisplayBall>> balls = new HashMap<>();
    private final Map<String, DisplayBall> motherBalls = new HashMap<>();
    private final Map<String, BukkitTask> movementCheckTasks = new HashMap<>();
    private final Map<String, BukkitTask> ballInCheckTasks = new HashMap<>();

    public InGame(TableBall plugin, WorldUtils worldUtils) {
        this.plugin = plugin;
        this.worldUtils = worldUtils;
        this.worldBalls = new HashMap<>();
        this.worldHoles = new HashMap<>();
        this.gameDataMap = new HashMap<>();
        loadBallsConfig();
        loadBalls();
        loadHoles();
    }

    private void loadBallsConfig() {
        File ballsFile = new File(plugin.getDataFolder(), "balls.yml");
        if (!ballsFile.exists()) {
            plugin.saveResource("balls.yml", false);
        }
        ballsConfig = YamlConfiguration.loadConfiguration(ballsFile);
    }

    /**
     * 从配置文件加载球的数据（适配新格式：balls和holes为同级键）
     */
    private void loadBalls() {
        // 开始加载球的数据
        ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
        if (worlds == null) {
            plugin.getLogger().severe("balls.yml 中没有找到任何世界配置！");
            return;
        }
        for (String worldName : worlds.getKeys(false)) {
            // 正在加载世界的球数据
            ConfigurationSection worldSection = worlds.getConfigurationSection(worldName);
            if (worldSection == null) {
                plugin.getLogger().severe("世界 " + worldName + " 的配置部分无效！");
                continue;
            }
            ConfigurationSection ballsSection = worldSection.getConfigurationSection("balls");
            if (ballsSection == null) {
                plugin.getLogger().severe("世界 " + worldName + " 缺少balls部分！");
                continue;
            }
            Map<String, BallData> balls = new HashMap<>();
            for (String ballKey : ballsSection.getKeys(false)) {
                // 正在加载球 ID
                ConfigurationSection ballSection = ballsSection.getConfigurationSection(ballKey);
                if (ballSection == null) {
                    plugin.getLogger().severe("球 " + ballKey + " 的配置部分无效！");
                    continue;
                }
                ConfigurationSection locSection = ballSection.getConfigurationSection("loc");
                if (locSection == null) {
                    plugin.getLogger().severe("球 " + ballKey + " 缺少位置配置！");
                    continue;
                }
                Location loc = new Location(
                    plugin.getServer().getWorld(worldName),
                    locSection.getDouble("x"),
                    locSection.getDouble("y"),
                    locSection.getDouble("z"),
                    (float) locSection.getDouble("yaw"),
                    (float) locSection.getDouble("pitch")
                );
                ConfigurationSection nbtSection = ballSection.getConfigurationSection("nbt");
                if (nbtSection == null) {
                    plugin.getLogger().warning("球 " + ballKey + " 缺少NBT配置！");
                    continue;
                }
                String originalText = nbtSection.getString("text", "-");
                String processedText = processTextFor8balls(originalText, ballKey);
                balls.put(ballKey, new BallData(loc, Material.getMaterial(nbtSection.getString("color", "STONE")), processedText));
                //plugin.getLogger().info("成功加载球 ID: " + ballKey);
            }
            worldBalls.put(worldName, balls);
            //plugin.getLogger().info("世界 " + worldName + " 的球数据加载完成，共 " + balls.size() + " 个球");
        }
        // 所有球的数据加载完成
    }

    /**
     * 加载洞的位置（适配新格式：balls和holes为同级键）
     */
    private void loadHoles() {
        // 开始加载洞的数据
        ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
        if (worlds == null) {
            plugin.getLogger().severe("balls.yml 中没有找到任何世界配置！");
            return;
        }
        for (String worldName : worlds.getKeys(false)) {
            // 正在加载世界的洞数据
            ConfigurationSection worldSection = worlds.getConfigurationSection(worldName);
            if (worldSection == null) {
                plugin.getLogger().severe("世界 " + worldName + " 的配置部分无效！");
                continue;
            }
            ConfigurationSection holesSection = worldSection.getConfigurationSection("holes");
            if (holesSection == null) {
                plugin.getLogger().severe("世界 " + worldName + " 缺少holes部分！");
                continue;
            }
            Map<String, HoleData> holes = new HashMap<>();
            for (String holeId : holesSection.getKeys(false)) {
                if (holeId.equals("y")) continue;
                // 正在加载洞 ID
                ConfigurationSection holeSection = holesSection.getConfigurationSection(holeId);
                if (holeSection == null) {
                    plugin.getLogger().severe("洞 " + holeId + " 的配置部分无效！");
                    continue;
                }
                Location point1 = new Location(
                    plugin.getServer().getWorld(worldName),
                    holeSection.getDouble("x1"),
                    holeSection.getDouble("y1"),
                    holeSection.getDouble("z1")
                );
                Location point2 = new Location(
                    plugin.getServer().getWorld(worldName),
                    holeSection.getDouble("x2"),
                    holeSection.getDouble("y2"),
                    holeSection.getDouble("z2")
                );
                holes.put(holeId, new HoleData(point1, point2));
                //plugin.getLogger().info("成功加载洞 ID: " + holeId);
            }
            worldHoles.put(worldName, holes);
            //plugin.getLogger().info("世界 " + worldName + " 的洞数据加载完成，共 " + holes.size() + " 个洞");
        }
        // 所有洞的数据加载完成
    }

    /**
     * 生成指定世界的所有球
     * @param worldName 世界名称
     */
    public void spawnBalls(String worldName) {
        Map<String, BallData> balls = worldBalls.get(worldName);
        if (balls == null) return;

        for (Map.Entry<String, BallData> entry : balls.entrySet()) {
            spawnBall(worldName, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 生成单个球
     * @param worldName 世界名称
     * @param ballId 球ID
     * @param ballData 球数据
     */
    private void spawnBall(String worldName, String ballId, BallData ballData) {
        if (ballData == null) {
            plugin.getLogger().severe("球数据为null: " + ballId);
            return;
        }
        
        Location loc = ballData.location();
        if (loc == null) {
            plugin.getLogger().severe("球位置为null: " + ballId);
            return;
        }
        
        if (loc.getWorld() == null) {
            // 尝试重新获取世界
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().severe("世界不存在且无法加载: " + worldName);
                return;
            }
            
            // 使用相同坐标但正确的世界创建新位置
            loc = new Location(world, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }

        try {
            Material material = ballData.material();
            if (material == null) {
                material = Material.STONE;
                plugin.getLogger().warning("球 " + ballId + " 材质为null，使用默认石头材质");
            }
            
            String name = ballData.name;
            if (name == null || name.isEmpty()) {
                name = "球 " + ballId;
                plugin.getLogger().warning("球 " + ballId + " 名称为null或空，使用默认名称");
            }
            
            DisplayBall ball = new DisplayBall(loc, material, name, isMotherBallKey(ballId));
            addBall(worldName, ball);

            // 球生成完成
        } catch (Exception e) {
            plugin.getLogger().severe("生成球时发生错误！");
            plugin.getLogger().severe("错误信息: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查球是否在洞内
     * @param ball 球实体
     * @return 是否在洞内
     */
    public boolean isBallInHole(DisplayBall ball) {
        if (ball == null) return false;
        String worldName = ball.getWorld();
        if (worldName == null) return false;

        Map<String, HoleData> holes = worldHoles.get(worldName);
        if (holes == null) return false;

        Location ballLoc = ball.location;
        for (HoleData hole : holes.values()) {
            if (isLocationInHole(ballLoc, hole)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查位置是否在洞的范围内
     * @param loc 要检查的位置
     * @param hole 洞的数据
     * @return 是否在洞内
     */
    private boolean isLocationInHole(Location loc, HoleData hole) {
        Location point1 = hole.getPoint1();
        Location point2 = hole.getPoint2();

        // 检查是否在同一个世界
        if (!loc.getWorld().equals(point1.getWorld())) return false;

        // 检查是否在Y轴范围内
        double minY = Math.min(point1.getY(), point2.getY());
        double maxY = Math.max(point1.getY(), point2.getY());
        if (loc.getY() < minY || loc.getY() > maxY) return false;

        // 检查是否在XZ平面范围内
        double minX = Math.min(point1.getX(), point2.getX());
        double maxX = Math.max(point1.getX(), point2.getX());
        double minZ = Math.min(point1.getZ(), point2.getZ());
        double maxZ = Math.max(point1.getZ(), point2.getZ());

        return loc.getX() >= minX && loc.getX() <= maxX &&
               loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private void startMovementCheck(String worldName) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                checkAllBallsStatic(worldName);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        movementCheckTasks.put(worldName, task);
    }

    private void startBallInCheck(String worldName) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                checkBallsInHoles(worldName);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        ballInCheckTasks.put(worldName, task);
    }

    public /*void*/ boolean checkAllBallsStatic(String worldName) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls == null) return false;

        boolean allStatic = true;
        for (DisplayBall ball : worldBalls) {
            // 静止阈值（0.2）
            if (ball.velocity.clone().length() > 0.2) {
                allStatic = false;
                break;
            }
        }
        /*
        if(DisplayBall.getIsFalling()) {
            allStatic = false;
        }
        */

        return allStatic;
    }

    public void checkBallsInHoles(String worldName) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls == null) return;

        GameData gameData = gameDataMap.get(worldName);
        String gameType = gameData != null ? gameData.getGameType() : "Standard";

        for (DisplayBall ball : new HashSet<>(worldBalls)) {
            if (isBallInHole(ball)) {
                // 处理进球事件
                if (gameType.equals("8balls")) {
                    int ballNumber = extractBallNumberFromDisplayBall(ball);
                    plugin.getRoundManager().handle8ballsIn(worldName, ballNumber);
                    if(ballNumber != 8 && ballNumber != 0){
                        GameState.setIsOtherBallInHole(true);
                    }
                } else {
                    plugin.getRoundManager().handleBallIn(worldName, ball.isMotherBall);
                }

                removeBall(worldName, ball);
                ball.destroy();
            }
        }
    }

    /**
     * 从DisplayBall中提取球号
     */
    public int extractBallNumberFromDisplayBall(DisplayBall ball) {
        if (ball.isMotherBall) {
            // 球号提取: 母球
            return 0; // 母球
        }

        // 从球的名称中提取球号
        String name = ball.text;
        if (name != null) {
            int ballNumber = extractBallNumberFromText(name);
            // 球号提取完成
            return ballNumber;
        }
        plugin.getLogger().warning("球号提取失败: 球文本为null");
        return -1; // 无法识别
    }

    /**
     * 从文本中提取球号，支持多种格式
     * @param text 球的文本
     * @return 球号，如果无法提取则返回-1
     */
    private int extractBallNumberFromText(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }

        // 移除所有颜色代码
        String cleanText = text.replaceAll("§[0-9a-fA-F]", "");

        // 如果是母球的文本表示
        if (cleanText.equals("0") || cleanText.equals("母球")) {
            return 0;
        }

        // 提取数字
        try {
            String numberStr = cleanText.replaceAll("[^0-9]", "");
            if (!numberStr.isEmpty()) {
                int ballNumber = Integer.parseInt(numberStr);
                // 验证球号范围
                if (ballNumber >= 0 && ballNumber <= 15) {
                    return ballNumber;
                }
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("球号解析失败: " + text + " -> " + cleanText);
        }

        return -1; // 无法识别
    }

    /**
     * 结束游戏
     * @param worldName 世界名称
     */
    public void endGame(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        List<Player> playersInWorld = getPlayersInWorld(worldName);

        // 显示正常结算信息
        GameData gameData = gameDataMap.get(worldName);
        String gameType = gameData != null ? gameData.getGameType() : "Standard";

        // 记录游戏结果到数据库
        recordGameResults(worldName, playersInWorld, gameType, false);

        for (Player worldPlayer : world.getPlayers()) {
            worldPlayer.sendMessage("§e结算信息:");

            if (gameType.equals("8balls")) {
                // 8balls模式显示局数
                org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
                if (gameState != null) {
                    for (Player p : playersInWorld) {
                        if (p.getScoreboardTags().contains("tableball_ingame")) {
                            int wins = gameState.getRoundWins(p);
                            worldPlayer.sendMessage("§b" + p.getName() + ": §a" + wins + "胜");
                        }
                    }

                    // 判定获胜者
                    Player winner = gameState.getOverallWinner();
                    if (winner != null) {
                        worldPlayer.sendMessage("§6获胜者：" + winner.getName());
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

                // 判定获胜者
                Player winner = null;
                int maxScore = -1;
                boolean tie = false;

                for (Player p : playersInWorld) {
                    if (p.getScoreboardTags().contains("tableball_ingame")) {
                        int score = RoundManager.scores.getOrDefault(p.getName(), 0);
                        if (score > maxScore) {
                            maxScore = score;
                            winner = p;
                            tie = false;
                        } else if (score == maxScore) {
                            tie = true;
                        }
                    }
                }

                if (tie) {
                    worldPlayer.sendMessage("§6获胜者：平局");
                } else if (winner != null) {
                    worldPlayer.sendMessage("§6获胜者：" + winner.getName());
                }
            }

            worldPlayer.sendMessage("§a你已被传送回主城！");
        }

        LeaveCommand.endGameForRealLikeDeepseekSTFU(world);
    }

    /**
     * 添加分数
     * @param worldName 世界名称
     * @param player 玩家
     * @param score 分数
     */
    public void addScore(String worldName, Player player, int score) {
        GameData gameData = gameDataMap.get(worldName);
        if (gameData == null) return;

        String gameType = gameData.getGameType();
        if (gameType.equals("Standard")) {
            // 标准模式：每个球2分
            score = 2;
        }
        // 自定义模式：使用传入的分数

        gameData.addScore(player, score);
        player.sendMessage("§a得分 +" + score);
    }
    /**
     * 获取游戏类型
     * @param player 玩家
     * @return 游戏类型，如果玩家不在游戏中则返回null
     */
    public String getGameType(Player player) {
        for (GameData gameData : gameDataMap.values()) {
            if (gameData.getPlayers().contains(player)) {
                return gameData.getGameType();
            }
        }
        return null;
    }

    /**
     * 检查世界是否正在被使用
     * @param worldName 世界名称
     * @return 是否正在被使用
     */
    public boolean isWorldInUse(String worldName) {
        return gameDataMap.containsKey(worldName);
    }

    /**
     * 检查玩家是否在游戏中
     * @param player 玩家
     * @return 是否在游戏中
     */
    public boolean isPlayerInGame(Player player) {
        String worldName = player.getWorld().getName();
        Set<Player> players = playersInGame.get(worldName);
        return players != null && players.contains(player);
    }

    /**
     * 移除玩家
     * @param player 玩家
     */
    public void removePlayer(Player player) {
        String worldName = player.getWorld().getName();
        
        // 从playersInGame中移除
        Set<Player> players = playersInGame.get(worldName);
        if (players != null) {
            players.remove(player);
            if (players.isEmpty()) {
                playersInGame.remove(worldName);
            }
        }
        
        // 从gameDataMap中移除
        GameData gameData = gameDataMap.get(worldName);
        if (gameData != null) {
            gameData.getPlayers().remove(player);
            gameData.getScores().remove(player);
            gameData.getRounds().remove(player);
            if (gameData.getPlayers().isEmpty()) {
                gameDataMap.remove(worldName);
            }
        }
    }

    /**
     * 获取游戏类型
     * @param worldName 世界名称
     * @return 游戏类型
     */
    public String getGameType(String worldName) {
        if (gameDataMap.containsKey(worldName)) {
            return gameDataMap.get(worldName).getGameType();
        }
        return "Standard"; // 默认返回Standard
    }

    /**
     * 检查游戏配置是否有效
     * @param worldName 世界名称
     * @return 配置是否有效
     */
    public boolean checkGameConfig(String worldName) {
        if (!ballsConfig.contains(worldName)) {
            plugin.getLogger().severe("balls.yml 中没有对应的世界：" + worldName);
            return false;
        }
        ConfigurationSection worldSection = ballsConfig.getConfigurationSection(worldName);
        if (worldSection == null) {
            plugin.getLogger().severe("世界配置部分无效：" + worldName);
            return false;
        }
        ConfigurationSection ballsSection = worldSection.getConfigurationSection("balls");
        if (ballsSection == null) {
            plugin.getLogger().severe("世界 " + worldName + " 缺少balls部分！");
            return false;
        }
        boolean hasMotherBall = false;
        int normalBallCount = 0;
        for (String ballKey : ballsSection.getKeys(false)) {
            if (isMotherBallKey(ballKey)) {
                hasMotherBall = true;
                continue;
            }
            ConfigurationSection ballSection = ballsSection.getConfigurationSection(ballKey);
            if (ballSection == null) continue;
            if (!ballSection.contains("loc")) {
                plugin.getLogger().severe("球 " + ballKey + " 缺少位置配置！");
                continue;
            }
            normalBallCount++;
        }
        if (!hasMotherBall) {
            plugin.getLogger().severe("世界 " + worldName + " 缺少母球！");
            return false;
        }
        if (normalBallCount < 1) {
            plugin.getLogger().severe("世界 " + worldName + " 至少需要一个普通球！");
            return false;
        }
        if (!ballsConfig.contains(worldName + ".holes")) {
            plugin.getLogger().severe("世界 " + worldName + " 缺少holes的配置！");
            return false;
        }
        return true;
    }

    /**
     * 设置游戏数据
     * @param worldName 世界名称
     * @param players 玩家列表
     * @param gameType 游戏类型
     */
    public void setGameData(String worldName, List<Player> players, String gameType) {
        GameData gameData = new GameData(new ArrayList<>(players), gameType);
        for (Player player : players) {
            gameData.getScores().put(player, 0);
            gameData.getRounds().put(player, 1);
        }
        gameDataMap.put(worldName, gameData);
        // 设置游戏数据完成
    }

    /**
     * 将玩家添加到游戏中
     * @param player 玩家
     * @param worldName 世界名称
     */
    public void addPlayerToGame(Player player, String worldName) {
        if (!gameDataMap.containsKey(worldName)) {
            GameData gameData = new GameData(new ArrayList<>(), "Standard");
            gameDataMap.put(worldName, gameData);
        }
        GameData gameData = gameDataMap.get(worldName);
        if (!gameData.getPlayers().contains(player)) {
            gameData.getPlayers().add(player);
            gameData.getScores().put(player, 0);
            gameData.getRounds().put(player, 1);
        }
    }

    /**
     * 获取balls.yml中所有世界名
     * @return 世界名列表
     */
    public List<String> getAllWorldNamesFromBallsConfig() {
        List<String> worldNames = new ArrayList<>();
        try {
            if (ballsConfig == null) {
                plugin.getLogger().warning("balls.yml配置文件未加载");
                return worldNames;
            }

            ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
            if (worlds == null) {
                plugin.getLogger().warning("balls.yml配置文件为空或格式错误");
                return worldNames;
            }

            for (String worldName : worlds.getKeys(false)) {
                // 验证世界配置是否完整
                ConfigurationSection worldSection = worlds.getConfigurationSection(worldName);
                if (worldSection != null && worldSection.contains("balls")) {
                    worldNames.add(worldName);
                } else {
                    plugin.getLogger().warning("世界 " + worldName + " 的配置不完整，跳过");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("读取balls.yml配置时出错: " + e.getMessage());
        }
        return worldNames;
    }

    /**
     * 判断球的键名是否为母球
     */
    public static boolean isMotherBallKey(String key) {
        return key != null && (key.equals("母球") || key.equals("0"));
    }

    /**
     * 处理8balls模式的球文本显示
     * @param originalText 原始文本
     * @param ballKey 球的键名
     * @return 处理后的文本
     */
    private String processTextFor8balls(String originalText, String ballKey) {
        // 移除颜色代码
        String cleanText = originalText.replaceAll("§[0-9a-fA-F]", "");

        // 如果是母球，不添加颜色标识
        if (isMotherBallKey(ballKey)) {
            return originalText; // 保持原始文本
        }

        // 尝试从球键名或文本中提取球号
        int ballNumber = extractBallNumberFromText(originalText);
        if (ballNumber == -1) {
            // 如果从文本提取失败，尝试从键名提取
            ballNumber = extractBallNumberFromText(ballKey);
        }

        if (ballNumber >= 1 && ballNumber <= 7) {
            // 红色球 (1-7)
            return originalText + "§c■■■";
        } else if (ballNumber >= 9 && ballNumber <= 15) {
            // 蓝色球 (9-15)
            return originalText + "§9■■■";
        } else if (ballNumber == 8) {
            // 黑色球 (8)
            return originalText + "§0■■■";
        }

        // 默认返回原文本
        return originalText;
    }



    /**
     * 获取指定世界的母球
     * @param worldName 世界名称
     * @return 母球DisplayBall，如果没有找到则返回null
     */
    public DisplayBall getMotherBall(String worldName) {
        for (DisplayBall ball : DisplayBall.displayBalls) {
            if (ball.getWorld().equals(worldName) && ball.isMotherBall) {
                return ball;
            }
        }
        return null;
    }

    /**
     * 移除指定世界的母球
     * @param worldName 世界名称
     */
    public void removeMotherBall(String worldName) {
        DisplayBall motherBall = getMotherBall(worldName);
        if (motherBall != null) {
            motherBall.destroy();
            // 移除母球
        }
    }

    /**
     * 球数据内部类
     */
    public record BallData(Location location, Material material, String name) {
    }

    /**
     * 洞数据内部类
     */
    private static class HoleData {
        private final Location point1;
        private final Location point2;

        public HoleData(Location point1, Location point2) {
            this.point1 = point1;
            this.point2 = point2;
        }

        public Location getPoint1() {
            return point1;
        }

        public Location getPoint2() {
            return point2;
        }
    }

    /**
     * 游戏数据内部类
     */
    private static class GameData {
        private final List<Player> players;
        private final String gameType;
        private final Map<Player, Integer> scores;
        private final Map<Player, Integer> rounds;

        public GameData(List<Player> players, String gameType) {
            this.players = players;
            this.gameType = gameType;
            this.scores = new HashMap<>();
            this.rounds = new HashMap<>();
            // 初始化分数
            for (Player player : players) {
                scores.put(player, 0);
                rounds.put(player, 1);
            }
        }

        public List<Player> getPlayers() {
            return players;
        }

        public String getGameType() {
            return gameType;
        }

        public Map<Player, Integer> getScores() {
            return scores;
        }

        public void addScore(Player player, int points) {
            scores.put(player, scores.getOrDefault(player, 0) + points);
        }

        public int getScore(Player player) {
            return scores.getOrDefault(player, 0);
        }

        public void nextRound(Player player) {
            rounds.put(player, rounds.getOrDefault(player, 1) + 1);
        }

        public int getRound(Player player) {
            return rounds.getOrDefault(player, 1);
        }

        public Map<Player, Integer> getRounds() {
            return rounds;
        }
    }

    /**
     * 获取指定世界的所有游戏玩家
     * @param worldName 世界名
     * @return 玩家列表
     */
    public List<Player> getPlayersInWorld(String worldName) {
        if (gameDataMap.containsKey(worldName)) {
            return new ArrayList<>(gameDataMap.get(worldName).getPlayers());
        }
        return new ArrayList<>();
    }

    public void clearBalls(String worldName) {
        DisplayBall motherBall = motherBalls.remove(worldName);
        if (motherBall != null) {
            motherBall.destroy();
        }

        Set<DisplayBall> worldBalls = balls.remove(worldName);
        if (worldBalls != null) {
            for (DisplayBall ball : worldBalls) {
                ball.destroy();
            }
        }

        BukkitTask movementTask = movementCheckTasks.remove(worldName);
        if (movementTask != null) {
            movementTask.cancel();
        }

        BukkitTask ballInTask = ballInCheckTasks.remove(worldName);
        if (ballInTask != null) {
            ballInTask.cancel();
        }
    }

    public void setMotherBall(String worldName, DisplayBall ball) {
        motherBalls.put(worldName, ball);
    }

    /**
     * 为8balls模式的所有球设置发光效果
     * @param worldName 世界名称
     */
    public void setGlowingFor8ballsMode(String worldName) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls == null) return;

        for (DisplayBall ball : worldBalls) {
            // 从球的文本中提取球号
            int ballNumber = extractBallNumberFromDisplayBall(ball);
            setGlowingFor8balls(ball, ballNumber);
        }

        // 已为8balls模式设置球的发光效果
    }

    /**
     * 为8balls模式的球设置发光效果
     * @param ball 球对象
     * @param ballNumber 球号
     */
    private void setGlowingFor8balls(DisplayBall ball, int ballNumber) {
        if (ballNumber >= 1 && ballNumber <= 7) {
            // 1-7号球发红色光
            ball.setGlowing(ChatColor.RED);
        } else if (ballNumber >= 9 && ballNumber <= 15) {
            // 9-15号球发蓝色光
            ball.setGlowing(ChatColor.BLUE);
        } else if (ballNumber == 8) {
            // 8号球发白色光
            ball.setGlowing(ChatColor.WHITE);
        }
        // 0号球（母球）不设置发光效果
    }

    /**
     * 记录游戏结果到数据库
     * @param worldName 世界名称
     * @param players 参与游戏的玩家
     * @param gameType 游戏类型
     * @param isForfeit 是否是弃权结束
     */
    public void recordGameResults(String worldName, List<Player> players, String gameType, boolean isForfeit) {
        if (players.isEmpty()) return;

        if (gameType.equals("8balls")) {
            // 8balls模式：根据局数判定获胜者
            org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
            if (gameState != null) {
                Player winner = gameState.getOverallWinner();

                for (Player player : players) {
                    if (player.getScoreboardTags().contains("tableball_ingame")) {
                        if (isForfeit) {
                            // 弃权情况：弃权者记录为弃权失败，对方记录为获胜
                            if (player.equals(winner)) {
                                plugin.getPlayerDataManager().recordGameResult(player, gameType, "win");
                            } else {
                                plugin.getPlayerDataManager().recordGameResult(player, gameType, "forfeit");
                            }
                        } else {
                            // 正常结束：获胜者记录获胜，失败者记录失败
                            if (player.equals(winner)) {
                                plugin.getPlayerDataManager().recordGameResult(player, gameType, "win");
                            } else {
                                plugin.getPlayerDataManager().recordGameResult(player, gameType, "lose");
                            }
                        }
                    }
                }
            }
        } else {
            // 标准模式：根据分数判定获胜者
            Player winner = null;
            int maxScore = -1;
            boolean tie = false;

            for (Player p : players) {
                if (p.getScoreboardTags().contains("tableball_ingame")) {
                    int score = RoundManager.scores.getOrDefault(p.getName(), 0);
                    if (score > maxScore) {
                        maxScore = score;
                        winner = p;
                        tie = false;
                    } else if (score == maxScore) {
                        tie = true;
                    }
                }
            }

            for (Player player : players) {
                if (player.getScoreboardTags().contains("tableball_ingame")) {
                    if (tie) {
                        // 平局：所有人记录为失败
                        plugin.getPlayerDataManager().recordGameResult(player, gameType, "lose");
                    } else if (isForfeit) {
                        // 弃权情况
                        if (player.equals(winner)) {
                            plugin.getPlayerDataManager().recordGameResult(player, gameType, "win");
                        } else {
                            plugin.getPlayerDataManager().recordGameResult(player, gameType, "forfeit");
                        }
                    } else {
                        // 正常结束
                        if (player.equals(winner)) {
                            plugin.getPlayerDataManager().recordGameResult(player, gameType, "win");
                        } else {
                            plugin.getPlayerDataManager().recordGameResult(player, gameType, "lose");
                        }
                    }
                }
            }
        }
    }

//    public DisplayBall getMotherBall(String worldName) {
//        return motherBalls.get(worldName);
//    }

    public void addBall(String worldName, DisplayBall ball) {
        balls.computeIfAbsent(worldName, k -> new HashSet<>()).add(ball);
    }

    public void removeBall(String worldName, DisplayBall ball) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls != null) {
            worldBalls.remove(ball);
            if (worldBalls.isEmpty()) {
                balls.remove(worldName);
            }
        }
    }

    public Set<DisplayBall> getBalls(String worldName) {
        return balls.getOrDefault(worldName, new HashSet<>());
    }
} 
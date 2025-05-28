package org.tableBall.Game;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.tableBall.Listeners.EntityEventListener;
import org.tableBall.Manager.RoundManager;
import org.tableBall.TableBall;
import org.bukkit.NamespacedKey;
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
        plugin.getLogger().info("开始加载球的数据...");
        ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
        if (worlds == null) {
            plugin.getLogger().severe("balls.yml 中没有找到任何世界配置！");
            return;
        }
        for (String worldName : worlds.getKeys(false)) {
            plugin.getLogger().info("正在加载世界 " + worldName + " 的球数据...");
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
                plugin.getLogger().info("正在加载球 ID: " + ballKey);
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
                    plugin.getLogger().severe("球 " + ballKey + " 缺少NBT配置！");
                    continue;
                }
                balls.put(ballKey, new BallData(loc, Material.getMaterial(nbtSection.getString("color", "STONE"))));
                //plugin.getLogger().info("成功加载球 ID: " + ballKey);
            }
            worldBalls.put(worldName, balls);
            //plugin.getLogger().info("世界 " + worldName + " 的球数据加载完成，共 " + balls.size() + " 个球");
        }
        plugin.getLogger().info("所有球的数据加载完成！");
    }

    /**
     * 加载洞的位置（适配新格式：balls和holes为同级键）
     */
    private void loadHoles() {
        plugin.getLogger().info("开始加载洞的数据...");
        ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
        if (worlds == null) {
            plugin.getLogger().severe("balls.yml 中没有找到任何世界配置！");
            return;
        }
        for (String worldName : worlds.getKeys(false)) {
            plugin.getLogger().info("正在加载世界 " + worldName + " 的洞数据...");
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
                plugin.getLogger().info("正在加载洞 ID: " + holeId);
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
        plugin.getLogger().info("所有洞的数据加载完成！");
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
        Location loc = ballData.location();
        if (loc.getWorld() == null) {
            plugin.getLogger().severe("世界不存在：" + worldName);
            return;
        }

        try {
            DisplayBall ball = new DisplayBall(loc, ballData.material(), "球" + ballId, isMotherBallKey(ballId));
            addBall(worldName, ball);

            //plugin.getLogger().info("球 ID: " + ballId + " 生成完成！");
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

    public void checkAllBallsStatic(String worldName) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls == null) return;

        boolean allStatic = true;
        for (DisplayBall ball : worldBalls) {
            if (ball.velocity.length() > 0.5) {
                allStatic = false;
                break;
            }
        }

        if (allStatic) {
            plugin.getRoundManager().settleTurn(worldName);
        }
    }

    public void checkBallsInHoles(String worldName) {
        Set<DisplayBall> worldBalls = balls.get(worldName);
        if (worldBalls == null) return;

        for (DisplayBall ball : new HashSet<>(worldBalls)) {
            if (isBallInHole(ball)) {
                removeBall(worldName, ball);
                ball.destroy();
            }
        }
    }

    /**
     * 结束游戏
     * @param worldName 世界名称
     */
    public void endGame(String worldName) {
        GameData gameData = gameDataMap.get(worldName);
        if (gameData == null) return;

        // 获取最高分
        int maxScore = -1;
        List<Player> winners = new ArrayList<>();

        for (Map.Entry<Player, Integer> entry : gameData.getScores().entrySet()) {
            int score = entry.getValue();
            if (score > maxScore) {
                maxScore = score;
                winners.clear();
                winners.add(entry.getKey());
            } else if (score == maxScore) {
                winners.add(entry.getKey());
            }
        }

        // 显示结果
        if (winners.size() == 1) {
            Player winner = winners.get(0);
            for (Player player : gameData.getPlayers()) {
                player.sendMessage("§6获胜者: " + winner.getName() + " §e得分: " + maxScore);
            }
        } else {
            for (Player player : gameData.getPlayers()) {
                player.sendMessage("§6平局！§e得分: " + maxScore);
            }
        }

        // 清理游戏数据
        gameDataMap.remove(worldName);
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
     * 获取玩家分数
     * @param player 玩家
     * @return 分数，如果玩家不在游戏中则返回-1
     */
    public int getScore(Player player) {
        for (GameData gameData : gameDataMap.values()) {
            if (gameData.getPlayers().contains(player)) {
                return gameData.getScore(player);
            }
        }
        return -1;
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
        Set<Player> players = playersInGame.get(worldName);
        if (players != null) {
            players.remove(player);
            if (players.isEmpty()) {
                playersInGame.remove(worldName);
                clearBalls(worldName);
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
        return null;
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
        if (ballsConfig == null) return worldNames;
        ConfigurationSection worlds = ballsConfig.getConfigurationSection("");
        if (worlds == null) return worldNames;
        for (String worldName : worlds.getKeys(false)) {
            worldNames.add(worldName);
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
     * 球数据内部类
     */
    public record BallData(Location location, Material material) {
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

    public DisplayBall getMotherBall(String worldName) {
        return motherBalls.get(worldName);
    }

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
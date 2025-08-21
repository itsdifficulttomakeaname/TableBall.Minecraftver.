package org.tableBall.Manager;

import org.bukkit.entity.Player;
import org.tableBall.TableBall;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 玩家设置管理器
 * 负责存储和管理玩家的菜单选择设置
 */
public class PlayerSettingsManager {
    private final TableBall plugin;
    private Connection connection;
    private final String databasePath;
    private final Map<String, PlayerSettings> settingsCache = new HashMap<>();

    public PlayerSettingsManager(TableBall plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath() + File.separator + "playersettings.db";
        initializeDatabase();
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            // 确保数据文件夹存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // 连接到SQLite数据库
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

            // 创建玩家设置表
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_settings (
                    player_uuid TEXT PRIMARY KEY,
                    selected_world TEXT DEFAULT '',
                    selected_mode TEXT DEFAULT '8balls',
                    selected_rounds INTEGER DEFAULT 3
                )
            """;

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }

            plugin.getLogger().info("玩家设置数据库初始化完成！");

        } catch (SQLException e) {
            plugin.getLogger().severe("初始化玩家设置数据库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取玩家的设置
     */
    private PlayerSettings getPlayerSettings(Player player) {
        String uuid = player.getUniqueId().toString();
        
        // 先从缓存中获取
        if (settingsCache.containsKey(uuid)) {
            return settingsCache.get(uuid);
        }

        // 从数据库加载
        try {
            String selectSQL = "SELECT selected_world, selected_mode, selected_rounds FROM player_settings WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setString(1, uuid);
                ResultSet rs = pstmt.executeQuery();

                PlayerSettings settings;
                if (rs.next()) {
                    // 存在记录，加载设置
                    String world = rs.getString("selected_world");
                    String mode = rs.getString("selected_mode");
                    int rounds = rs.getInt("selected_rounds");
                    
                    // 如果世界为空，使用默认世界
                    if (world == null || world.isEmpty()) {
                        String[] availableWorlds = plugin.getWorldUtils().getBallsConfigWorlds();
                        world = (availableWorlds.length > 0) ? availableWorlds[0] : "";
                    }
                    
                    settings = new PlayerSettings(world, mode, rounds);
                } else {
                    // 不存在记录，创建默认设置
                    String[] availableWorlds = plugin.getWorldUtils().getBallsConfigWorlds();
                    String defaultWorld = (availableWorlds.length > 0) ? availableWorlds[0] : "";
                    settings = new PlayerSettings(defaultWorld, "8balls", 3);
                    
                    // 插入默认设置到数据库
                    savePlayerSettings(player, settings);
                }

                // 缓存设置
                settingsCache.put(uuid, settings);
                return settings;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家设置失败: " + e.getMessage());
            e.printStackTrace();
            
            // 返回默认设置
            String[] availableWorlds = plugin.getWorldUtils().getBallsConfigWorlds();
            String defaultWorld = (availableWorlds.length > 0) ? availableWorlds[0] : "";
            return new PlayerSettings(defaultWorld, "8balls", 3);
        }
    }

    /**
     * 保存玩家设置到数据库
     */
    private void savePlayerSettings(Player player, PlayerSettings settings) {
        String uuid = player.getUniqueId().toString();
        
        try {
            String upsertSQL = """
                INSERT OR REPLACE INTO player_settings (player_uuid, selected_world, selected_mode, selected_rounds)
                VALUES (?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(upsertSQL)) {
                pstmt.setString(1, uuid);
                pstmt.setString(2, settings.selectedWorld);
                pstmt.setString(3, settings.selectedMode);
                pstmt.setInt(4, settings.selectedRounds);
                pstmt.executeUpdate();
            }
            
            // 更新缓存
            settingsCache.put(uuid, settings);
            
        } catch (SQLException e) {
            plugin.getLogger().severe("保存玩家设置失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取玩家选择的世界
     */
    public String getSelectedWorld(Player player) {
        return getPlayerSettings(player).selectedWorld;
    }

    /**
     * 设置玩家选择的世界
     */
    public void setSelectedWorld(Player player, String world) {
        PlayerSettings settings = getPlayerSettings(player);
        settings.selectedWorld = world;
        savePlayerSettings(player, settings);
    }

    /**
     * 获取玩家选择的游戏模式
     */
    public String getSelectedMode(Player player) {
        return getPlayerSettings(player).selectedMode;
    }

    /**
     * 设置玩家选择的游戏模式
     */
    public void setSelectedMode(Player player, String mode) {
        PlayerSettings settings = getPlayerSettings(player);
        settings.selectedMode = mode;
        savePlayerSettings(player, settings);
    }

    /**
     * 获取玩家选择的对局数
     */
    public int getSelectedRounds(Player player) {
        return getPlayerSettings(player).selectedRounds;
    }

    /**
     * 设置玩家选择的对局数
     */
    public void setSelectedRounds(Player player, int rounds) {
        PlayerSettings settings = getPlayerSettings(player);
        settings.selectedRounds = rounds;
        savePlayerSettings(player, settings);
    }

    /**
     * 玩家退出时清理缓存
     */
    public void clearPlayerCache(Player player) {
        settingsCache.remove(player.getUniqueId().toString());
    }

    /**
     * 关闭数据库连接
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("玩家设置数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭玩家设置数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 玩家设置内部类
     */
    private static class PlayerSettings {
        public String selectedWorld;
        public String selectedMode;
        public int selectedRounds;

        public PlayerSettings(String selectedWorld, String selectedMode, int selectedRounds) {
            this.selectedWorld = selectedWorld;
            this.selectedMode = selectedMode;
            this.selectedRounds = selectedRounds;
        }
    }
}
package org.tableBall.Manager;

import org.bukkit.entity.Player;
import org.tableBall.TableBall;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据管理器
 * 负责存储和管理玩家的游戏统计数据
 */
public class PlayerDataManager {
    private final TableBall plugin;
    private Connection connection;
    private final String databasePath;

    public PlayerDataManager(TableBall plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath() + File.separator + "playerdata.db";
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
            
            // 创建玩家数据表
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    game_mode TEXT NOT NULL,
                    total_games INTEGER DEFAULT 0,
                    wins INTEGER DEFAULT 0,
                    forfeit_losses INTEGER DEFAULT 0,
                    opponent_wins INTEGER DEFAULT 0,
                    UNIQUE(player_uuid, game_mode)
                )
            """;
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }
            
            plugin.getLogger().info("玩家数据库初始化完成！");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("初始化玩家数据库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 记录游戏结果
     * @param player 玩家
     * @param gameMode 游戏模式
     * @param result 游戏结果 ("win", "forfeit", "lose")
     */
    public void recordGameResult(Player player, String gameMode, String result) {
        if (player == null || gameMode == null || result == null) return;
        
        String playerUUID = player.getUniqueId().toString();
        
        try {
            // 首先确保玩家记录存在
            ensurePlayerRecord(playerUUID, gameMode);
            
            // 更新统计数据
            String updateSQL = switch (result.toLowerCase()) {
                case "win" -> """
                    UPDATE player_stats 
                    SET total_games = total_games + 1, wins = wins + 1 
                    WHERE player_uuid = ? AND game_mode = ?
                """;
                case "forfeit" -> """
                    UPDATE player_stats 
                    SET total_games = total_games + 1, forfeit_losses = forfeit_losses + 1 
                    WHERE player_uuid = ? AND game_mode = ?
                """;
                case "lose" -> """
                    UPDATE player_stats 
                    SET total_games = total_games + 1, opponent_wins = opponent_wins + 1 
                    WHERE player_uuid = ? AND game_mode = ?
                """;
                default -> null;
            };
            
            if (updateSQL != null) {
                try (PreparedStatement pstmt = connection.prepareStatement(updateSQL)) {
                    pstmt.setString(1, playerUUID);
                    pstmt.setString(2, gameMode);
                    pstmt.executeUpdate();
                }
                
                plugin.getLogger().info("记录玩家 " + player.getName() + " 在模式 " + gameMode + " 的结果: " + result);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("记录游戏结果失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 确保玩家记录存在
     */
    private void ensurePlayerRecord(String playerUUID, String gameMode) throws SQLException {
        String checkSQL = "SELECT COUNT(*) FROM player_stats WHERE player_uuid = ? AND game_mode = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(checkSQL)) {
            pstmt.setString(1, playerUUID);
            pstmt.setString(2, gameMode);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) == 0) {
                // 记录不存在，创建新记录
                String insertSQL = """
                    INSERT INTO player_stats (player_uuid, game_mode, total_games, wins, forfeit_losses, opponent_wins) 
                    VALUES (?, ?, 0, 0, 0, 0)
                """;
                try (PreparedStatement insertStmt = connection.prepareStatement(insertSQL)) {
                    insertStmt.setString(1, playerUUID);
                    insertStmt.setString(2, gameMode);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    /**
     * 获取玩家的统计数据
     * @param player 玩家
     * @return 包含所有游戏模式统计数据的Map
     */
    public Map<String, PlayerStats> getPlayerStats(Player player) {
        Map<String, PlayerStats> statsMap = new HashMap<>();
        
        if (player == null) return statsMap;
        
        String playerUUID = player.getUniqueId().toString();
        String selectSQL = "SELECT game_mode, total_games, wins, forfeit_losses, opponent_wins FROM player_stats WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            pstmt.setString(1, playerUUID);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String gameMode = rs.getString("game_mode");
                int totalGames = rs.getInt("total_games");
                int wins = rs.getInt("wins");
                int forfeitLosses = rs.getInt("forfeit_losses");
                int opponentWins = rs.getInt("opponent_wins");
                
                PlayerStats stats = new PlayerStats(totalGames, wins, forfeitLosses, opponentWins);
                statsMap.put(gameMode, stats);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家统计数据失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return statsMap;
    }

    /**
     * 关闭数据库连接
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("玩家数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 玩家统计数据类
     */
    public static class PlayerStats {
        private final int totalGames;
        private final int wins;
        private final int forfeitLosses;
        private final int opponentWins;

        public PlayerStats(int totalGames, int wins, int forfeitLosses, int opponentWins) {
            this.totalGames = totalGames;
            this.wins = wins;
            this.forfeitLosses = forfeitLosses;
            this.opponentWins = opponentWins;
        }

        public int getTotalGames() { return totalGames; }
        public int getWins() { return wins; }
        public int getForfeitLosses() { return forfeitLosses; }
        public int getOpponentWins() { return opponentWins; }
        public int getTotalLosses() { return forfeitLosses + opponentWins; }
    }
}

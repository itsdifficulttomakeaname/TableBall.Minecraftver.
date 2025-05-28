package org.tableBall.Manager;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.tableBall.Game.GameState;
import org.tableBall.TableBall;

import java.util.*;

public class RoundManager {
    private final TableBall plugin;
    private final Map<String, GameState> gameStates; // 世界 -> 游戏状态
    private final Map<String, String> gameTypes = new HashMap<>();

    public RoundManager(TableBall plugin) {
        this.plugin = plugin;
        this.gameStates = new HashMap<>();
    }

    /**
     * 开始新游戏
     * @param worldName 世界名称
     * @param players 玩家列表
     * @param gameType 游戏类型
     */
    public void startGame(String worldName, List<Player> players, String gameType) {
        GameState gameState = new GameState(players, gameType);
        gameStates.put(worldName, gameState);
        gameTypes.put(worldName, gameType);
        startTurn(worldName);
    }

    /**
     * 开始一个玩家的回合
     * @param worldName 世界名称
     */
    public void startTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) return;

        // 设置当前玩家为生存模式
        currentPlayer.setGameMode(GameMode.SURVIVAL);
        currentPlayer.removePotionEffect(PotionEffectType.INVISIBILITY);
        currentPlayer.sendMessage("§a轮到你的回合了！");
        currentPlayer.sendMessage("§e你只能击打母球！");

        // 设置其他玩家为冒险模式
        for (Player player : gameState.getPlayers()) {
            player.setCollidable(false);
            player.setAllowFlight(true);
            player.setFlying(true);

            if (!player.equals(currentPlayer)) {
                setSpectatorMode(player);
            }
        }
    }

    /**
     * 设置玩家为旁观者模式
     * @param player 玩家
     */
    private void setSpectatorMode(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.setCollidable(false);
    }

    /**
     * 处理击球事件
     * @param worldName 世界名称
     * @param player 击球玩家
     */
    public void handleShot(String worldName, Player player) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null || !gameState.isCurrentPlayer(player)) return;

        // 设置击球玩家为旁观者模式
        setSpectatorMode(player);
        gameState.setWaitingForBallsToStop(true);
    }

    /**
     * 处理进球事件
     * @param worldName 世界名称
     * @param isWhiteBall 是否是母球
     */
    public void handleBallIn(String worldName, boolean isWhiteBall) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        plugin.getLogger().info("handleBallIn: "+worldName+" "+isWhiteBall);

        if (isWhiteBall) {
            gameState.setWhiteBallIn(true);
        } else {
            gameState.setHasScored(true);
            gameState.incrementBallsInHole();
        }
    }

    /**
     * 结算回合
     * @param worldName 世界名称
     */
    public void settleTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        gameState.setWaitingForBallsToStop(false);

        //plugin.getLogger().info("settleTurn: "+gameState.isWhiteBallIn()+" "+gameState.hasScored());

        if (gameState.isWhiteBallIn()) {
            // 母球进洞，切换回合
            endTurn(worldName);
            return;
        }

        if (gameState.hasScored()) {
            // 有效进球，加分并保持回合
            int points = gameState.getBallsInHole() * 2;
            gameState.addTempScore(points);
            Player currentPlayer = gameState.getCurrentPlayer();
            currentPlayer.sendMessage("§a你获得了 " + points + " 分！");
            startTurn(worldName);
        } else {
            // 未进球，切换回合
            endTurn(worldName);
        }
    }

    /**
     * 结束当前回合
     * @param worldName 世界名称
     */
    public void endTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();
        currentPlayer.sendMessage("§c回合结束！");

        // 切换到下一个玩家
        gameState.nextPlayer();
        startTurn(worldName);
    }

    /**
     * 检查是否是当前玩家的回合
     * @param worldName 世界名称
     * @param player 玩家
     * @return 是否是当前玩家的回合
     */
    public boolean isCurrentPlayer(String worldName, Player player) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return false;
        return gameState.isCurrentPlayer(player);
    }

    /**
     * 获取当前玩家
     * @param worldName 世界名称
     * @return 当前玩家
     */
    public Player getCurrentPlayer(String worldName) {
        GameState gameState = gameStates.get(worldName);
        return gameState != null ? gameState.getCurrentPlayer() : null;
    }

    /**
     * 获取游戏状态
     * @param worldName 世界名称
     * @return 游戏状态
     */
    public GameState getGameState(String worldName) {
        return gameStates.get(worldName);
    }

    /**
     * 结束游戏
     * @param worldName 世界名称
     */
    public void endGame(String worldName) {
        gameStates.remove(worldName);
    }

    /**
     * 添加临时分数
     * @param worldName 世界名称
     * @param points 分数
     */
    public void addTempScore(String worldName, int points) {
        GameState gameState = gameStates.get(worldName);
        if (gameState != null) {
            gameState.addTempScore(points);
        }
    }

    /**
     * 获取临时分数
     * @param worldName 世界名称
     * @return 临时分数
     */
    public int getTempScore(String worldName) {
        GameState gameState = gameStates.get(worldName);
        return gameState != null ? gameState.getTempScore() : 0;
    }

    /**
     * 重置临时分数
     * @param worldName 世界名称
     */
    public void resetTempScore(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState != null) {
            gameState.resetTempScore();
        }
    }

    public String getGameType(String worldName) {
        return gameTypes.getOrDefault(worldName, "standard");
    }
} 
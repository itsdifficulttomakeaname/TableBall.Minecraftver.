package org.tableBall.Game; 

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.tableBall.TableBall;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoundManager {
    private final TableBall plugin;
    private final Map<String, GameState> gameStates;

    public RoundManager(TableBall plugin) {
        this.plugin = plugin;
        this.gameStates = new HashMap<>();
    }

    public void startTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) return;

        // 设置当前玩家为生存模式
        currentPlayer.setGameMode(GameMode.SURVIVAL);
        currentPlayer.removePotionEffect(PotionEffectType.INVISIBILITY);
        currentPlayer.setAllowFlight(true);
        currentPlayer.setFlying(false);
        currentPlayer.setCollidable(true);
        currentPlayer.sendMessage("§a轮到你的回合了！");
        currentPlayer.sendMessage("§e你只能击打母球！");

        // 设置其他玩家为冒险模式，并清空背包
        for (Player player : gameState.getPlayers()) {
            if (!player.equals(currentPlayer)) {
                setSpectatorMode(player);
                player.getInventory().clear();
            }
        }
    }

    private void setSpectatorMode(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
    }

    public void nextPlayer(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) return;

        // 切换到下一个玩家
        List<Player> players = gameState.getPlayers();
        int currentIndex = players.indexOf(currentPlayer);
        int nextIndex = (currentIndex + 1) % players.size();
        Player nextPlayer = players.get(nextIndex);

        // 结束当前回合
        endTurn(worldName);

        // 开始下一个回合
        startTurn(worldName);
    }

    public void endTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) return;

        // 设置当前玩家为冒险模式
        setSpectatorMode(currentPlayer);
    }

    public boolean isCurrentPlayer(String worldName, Player player) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return false;
        return player.equals(gameState.getCurrentPlayer());
    }
} 
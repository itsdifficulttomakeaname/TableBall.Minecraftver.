package org.tableBall.Manager;

import cn.jason31416.planetlib.hook.NbtHook;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Game.GameState;
import org.tableBall.Game.InGame;
import org.tableBall.Listeners.EntityEventListener;
import org.tableBall.TableBall;

import java.util.*;

public class RoundManager {
    private final TableBall plugin;
    private final Map<String, GameState> gameStates; // 世界 -> 游戏状态
    private final Map<String, String> gameTypes = new HashMap<>();
    public static Map<String, Integer> scores = new HashMap<>();

    public RoundManager(TableBall plugin) {
        this.plugin = plugin;
        this.gameStates = new HashMap<>();
    }

    /**
     * 开始新游戏
     * @param worldName 世界名称
     * @param players 玩家列表
     * @param gameType 游戏类型
     * @param rounds 对局数
     */
    public void startGame(String worldName, List<Player> players, String gameType, int rounds) {
        GameState gameState = new GameState(players, gameType, rounds);
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

        EntityEventListener.hasStrike = false;

        // 设置当前玩家为生存模式
        currentPlayer.setGameMode(GameMode.SURVIVAL);
        currentPlayer.removePotionEffect(PotionEffectType.INVISIBILITY);
        currentPlayer.sendMessage("§a轮到你的回合了！");
        currentPlayer.sendMessage("§e你只能击打母球！");

        getGameState(worldName).setHasScored(false);
        getGameState(worldName).setWhiteBallIn(false);
        getGameState(worldName).resetTurnState(); // 重置回合状态

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

        if (isWhiteBall) {
            gameState.setWhiteBallIn(true);
        } else {
            gameState.setHasScored(true);
            gameState.incrementBallsInHole();
        }
    }

    /**
     * 处理8balls模式的进球事件（带球号信息）
     * @param worldName 世界名称
     * @param ballNumber 球号（1-15，0为母球）
     */
    public void handle8ballsIn(String worldName, int ballNumber) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null || !gameState.getGameType().equals("8balls")) {
            plugin.getLogger().warning("8balls进球处理失败: gameState=" + (gameState != null ? "存在" : "null") +
                                     ", gameType=" + (gameState != null ? gameState.getGameType() : "null"));
            return;
        }

        // 8balls进球处理

        if (ballNumber == 0) {
            // 母球进洞
            gameState.setWhiteBallIn(true);
            // 8balls: 母球进洞
            return;
        }

        Player currentPlayer = getCurrentPlayer(worldName);
        String playerColor = gameState.getPlayerColor(currentPlayer);



        // 特殊处理黑8进洞
        if (ballNumber == 8) {
            gameState.setBlack8InHole(true);
            
            // 不立即判断胜负，等到所有球停下来后在settleTurn中统一处理
            // 这样可以正确检测黑8与其他球同时进洞的犯规情况
            gameState.setHasScored(true);
            gameState.incrementBallsInHole();
            return;
        }

        // 如果还没有分配颜色，记录进球信息，等球停下来再分配
        if (!gameState.areColorsAssigned()) {
            if ((ballNumber >= 1 && ballNumber <= 7) || (ballNumber >= 9 && ballNumber <= 15)) {
                gameState.setPendingColorBall(ballNumber);
            }
        } else {
            // 已经分配颜色，检查是否打进了正确的球
            boolean isCorrectBall = false;
            if ("red".equals(playerColor)) {
                isCorrectBall = (ballNumber >= 1 && ballNumber <= 7);
            } else if ("blue".equals(playerColor)) {
                isCorrectBall = (ballNumber >= 9 && ballNumber <= 15);
            }

            if (!isCorrectBall) {
                // 打进了对方的球，设置犯规（如果第一球击中错误）
                if (!gameState.isFirstBallCorrect()) {
                    gameState.setPendingInfraction("进了错误的球");
                }
                return; // 不设置hasScored，这样会换方
            }
        }

        gameState.setHasScored(true);
        gameState.incrementBallsInHole();
    }

    /**
     * 更新玩家的颜色指示器（第九格染料）
     */
    private void updatePlayerColorIndicator(Player player, String color) {
        Material material;
        String displayName;
        switch (color) {
            case "red":
                material = org.bukkit.Material.RED_DYE;
                displayName = "§c红色球";
                break;
            case "blue":
                material = org.bukkit.Material.BLUE_DYE;
                displayName = "§9蓝色球";
                break;
            default:
                material = org.bukkit.Material.GRAY_DYE;
                displayName = "§7未分配";
                break;
        }

        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, 1);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(8, item);
    }

    /**
     * 在结算阶段判断8balls模式黑8进洞时的获胜者
     * 这个方法在所有球停下来并且所有进球事件都处理完毕后调用
     * @param worldName 世界名称
     * @param gameState 游戏状态
     * @param currentPlayer 当前玩家
     * @return 获胜者，如果没有则返回null
     */
    private Player determine8ballsWinnerAfterSettle(String worldName, GameState gameState, Player currentPlayer) {
        if (currentPlayer == null) return null;

        // 检查是否是黑8与其他球同时进洞的犯规情况
        if (gameState.getBallsInHole() > 1) {
            // 黑8与其他球同时进洞，这是犯规，对方获胜
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    return p;
                }
            }
        }

        String playerColor = gameState.getPlayerColor(currentPlayer);

        // 如果玩家还没有分配颜色，说明是开球阶段打进了黑8，这是犯规
        if ("none".equals(playerColor)) {
            // 对方获胜
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    return p;
                }
            }
        }

        // 检查玩家是否已经打完了自己的色球
        boolean hasFinishedColorBalls = false;
        if ("red".equals(playerColor)) {
            hasFinishedColorBalls = !hasColorBallsOnTable(worldName, 1, 7);
        } else if ("blue".equals(playerColor)) {
            hasFinishedColorBalls = !hasColorBallsOnTable(worldName, 9, 15);
        }

        // 8balls: 检查获胜条件

        if (hasFinishedColorBalls && !gameState.isWhiteBallIn() && !GameState.getIsOtherBallInHole()) {
            // 正常获胜：打完色球后打进黑8且母球未进洞
            return currentPlayer;
        } else {
            // 犯规：色球未打完就打进黑8，或者打进黑8时母球也进洞
            // 对方获胜
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    return p;
                }
            }
        }

        return null;
    }
    
    /**
     * 判断8balls模式黑8进洞时的获胜者（旧方法，保留作为备用）
     * @param worldName 世界名称
     * @param gameState 游戏状态
     * @param currentPlayer 当前玩家
     * @return 获胜者，如果没有则返回null
     */
    private Player determine8ballsWinner(String worldName, GameState gameState, Player currentPlayer) {
        if (currentPlayer == null) return null;

        String playerColor = gameState.getPlayerColor(currentPlayer);

        // 如果玩家还没有分配颜色，说明是开球阶段打进了黑8，这是犯规
        if ("none".equals(playerColor)) {
            // 8balls: 开球阶段打进黑8，犯规
            // 对方获胜
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    return p;
                }
            }
        }

        // 检查玩家是否已经打完了自己的色球
        boolean hasFinishedColorBalls = false;
        if ("red".equals(playerColor)) {
            hasFinishedColorBalls = !hasColorBallsOnTable(worldName, 1, 7);
        } else if ("blue".equals(playerColor)) {
            hasFinishedColorBalls = !hasColorBallsOnTable(worldName, 9, 15);
        }

        // 8balls: 检查获胜条件

        if (hasFinishedColorBalls && !gameState.isWhiteBallIn()) {
            // 正常获胜：打完色球后打进黑8且母球未进洞
            return currentPlayer;
        } else {
            // 犯规：色球未打完就打进黑8，或者打进黑8时母球也进洞
            // 对方获胜
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    return p;
                }
            }
        }

        return null;
    }

    /**
     * 结算回合
     * @param worldName 世界名称
     */
    public void settleTurn(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        gameState.setWaitingForBallsToStop(false);
        String gameType = gameState.getGameType();

        if (gameType.equals("8balls")) {
            settle8ballsTurn(worldName, gameState);
        } else {
            settleStandardTurn(worldName, gameState);
        }
    }

    /**
     * 结算标准模式回合
     */
    private void settleStandardTurn(String worldName, GameState gameState) {
        if(!gameState.isWhiteBallIn()){
            int points = gameState.getBallsInHole() * 2;
            gameState.resetBallsInHole();
            scores.put(getCurrentPlayer(worldName).getName(), scores.getOrDefault(getCurrentPlayer(worldName).getName(), 0)+points);
            for(Player i: Bukkit.getWorld(worldName).getPlayers()){
                i.sendMessage("§a玩家 "+getCurrentPlayer(worldName).getName()+" 进球得 "+points+" 分");
                for (Player p : Bukkit.getWorld(worldName).getPlayers()) {
                    int s = RoundManager.scores.getOrDefault(p.getName(), -1);
                    if(s==-1) continue;
                    i.sendMessage("§b" + p.getName() + "§f 得分: §a" + s);
                }
            }
        }

        // 检查是否所有球都进洞了
        outer:
        {
            for (DisplayBall ball : DisplayBall.displayBalls) {
                if (!ball.isMotherBall){
                    break outer;
                }
            }
            plugin.getInGame().endGame(worldName);
            return;
        }

        if (gameState.isWhiteBallIn()) {
            // 母球进洞，切换回合
            ItemStack item = new ItemStack(Material.BIRCH_BOAT, 1);
            endTurn(worldName);
            NbtHook.addTag(item, "tb.whiteBall");
            getCurrentPlayer(worldName).getInventory().addItem(item);
            return;
        }

        if (gameState.hasScored()) {
            // 有效进球，加分并保持回合
            startTurn(worldName);
        } else {
            // 未进球，切换回合
            endTurn(worldName);
        }
    }

    /**
     * 结算8balls模式回合
     */
    private void settle8ballsTurn(String worldName, GameState gameState) {
        Player currentPlayer = getCurrentPlayer(worldName);
        boolean whiteBallIn = gameState.isWhiteBallIn();
        boolean hasScored = gameState.hasScored();

        // 8balls结算回合

        // 处理待分配的颜色
        if (!gameState.areColorsAssigned() && gameState.getPendingColorBall() != -1) {
            assignPlayerColors(worldName, gameState);
        }

        // 检查黑8进洞的情况
        if (gameState.isBlack8InHole()) {
            // 黑8进洞，需要特殊处理
            Player winner = determine8ballsWinnerAfterSettle(worldName, gameState, currentPlayer);
            if (winner != null) {
                end8ballsRound(worldName, gameState, winner);
                return;
            }
        }

        // 将IsOtherBallInHole归位
        GameState.setIsOtherBallInHole(false);
        
        // 检查是否有待处理的获胜者（其他情况）
        Player roundWinner = gameState.getPendingRoundWinner();
        if (roundWinner != null) {
            // 8balls: 检测到待处理获胜者
            end8ballsRound(worldName, gameState, roundWinner);
            return;
        }

        // 检查是否有待处理的犯规
        String pendingInfraction = gameState.getPendingInfraction();
        if (pendingInfraction != null) {
            handleInfraction(worldName, pendingInfraction);
            return;
        }

        if (whiteBallIn) {
            // 母球进洞，对方自由球
            handleInfraction(worldName, "母球进洞");
            return;
        }

        // 检查新的犯规条件
        String infractionReason = check8ballsInfractions(worldName, gameState);
        if (infractionReason != null) {
            handleInfraction(worldName, infractionReason);
            return;
        }

        if (hasScored) {
            // 有进球，继续回合
            gameState.setBreakShot(false); // 不再是开球
            gameState.resetTurnState(); // 重置回合状态
            startTurn(worldName);
        } else {
            // 没进球，切换回合
            gameState.setBreakShot(false); // 不再是开球
            gameState.resetTurnState(); // 重置回合状态
            endTurn(worldName);
        }
    }



    /**
     * 检查场上是否还有指定范围的球
     */
    private boolean hasColorBallsOnTable(String worldName, int minNumber, int maxNumber) {
        for (DisplayBall ball : DisplayBall.displayBalls) {
            if (ball.getWorld().equals(worldName) && !ball.isMotherBall) {
                int ballNumber = plugin.getInGame().extractBallNumberFromDisplayBall(ball);
                if (ballNumber >= minNumber && ballNumber <= maxNumber) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 处理犯规
     * @param worldName 世界名称
     * @param reason 犯规原因
     */
    public void handleInfraction(String worldName, String reason) {
        GameState gameState = gameStates.get(worldName);
        if (gameState == null) return;

        Player currentPlayer = gameState.getCurrentPlayer();

        // 1. 在公屏输出犯规信息
        for (Player p : Bukkit.getWorld(worldName).getPlayers()) {
            p.sendMessage("§c[" + (currentPlayer != null ? currentPlayer.getName() : "玩家") + "] 犯规！理由是：" + reason);
        }

        // 2. 清除场上的母球
        plugin.getInGame().removeMotherBall(worldName);

        // 3. 切换回合
        endTurn(worldName);

        // 4. 给新的当前玩家（对方）母球
        Player newCurrentPlayer = getCurrentPlayer(worldName);
        if (newCurrentPlayer != null) {
            ItemStack item = new ItemStack(Material.BIRCH_BOAT, 1);
            NbtHook.addTag(item, "tb.whiteBall");
            newCurrentPlayer.getInventory().addItem(item);
        }

        // 5. 在计分板显示犯规信息
        plugin.getScoreBoardManager().setInfraction(worldName, reason);

        // 6. 延迟清除犯规状态
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getScoreBoardManager().clearInfraction(worldName);
        }, 60L); // 3秒后清除
    }

    /**
     * 处理碰壁事件
     * @param worldName 世界名称
     */
    public void handleWallHit(String worldName) {
        GameState gameState = gameStates.get(worldName);
        if (gameState != null) {
            gameState.incrementWallHitCount();
        }
    }

    /**
     * 分配玩家颜色（在球停下来后）
     */
    private void assignPlayerColors(String worldName, GameState gameState) {
        Player currentPlayer = getCurrentPlayer(worldName);
        if (currentPlayer == null) return;

        int ballNumber = gameState.getPendingColorBall();

        if (ballNumber >= 1 && ballNumber <= 7) {
            gameState.setPlayerColor(currentPlayer, "red");
            Player otherPlayer = null;
            // 给对方分配蓝色
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    gameState.setPlayerColor(p, "blue");
                    otherPlayer = p;
                    break;
                }
            }
            updatePlayerColorIndicator(currentPlayer, "red");
            if (otherPlayer != null) {
                updatePlayerColorIndicator(otherPlayer, "blue");
            }
            for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
                p.sendMessage("§a颜色分配：" + currentPlayer.getName() + " = §c红色§a，" +
                             (otherPlayer != null ? otherPlayer.getName() : "对方") + " = §9蓝色");
            }
        } else if (ballNumber >= 9 && ballNumber <= 15) {
            gameState.setPlayerColor(currentPlayer, "blue");
            Player otherPlayer = null;
            // 给对方分配红色
            for (Player p : gameState.getPlayers()) {
                if (!p.equals(currentPlayer)) {
                    gameState.setPlayerColor(p, "red");
                    otherPlayer = p;
                    break;
                }
            }
            updatePlayerColorIndicator(currentPlayer, "blue");
            if (otherPlayer != null) {
                updatePlayerColorIndicator(otherPlayer, "red");
            }
            for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
                p.sendMessage("§a颜色分配：" + currentPlayer.getName() + " = §9蓝色§a，" +
                             (otherPlayer != null ? otherPlayer.getName() : "对方") + " = §c红色");
            }
        }

        // 清除待分配的球号
        gameState.setPendingColorBall(-1);
    }

    /**
     * 检查8balls模式的犯规条件
     * @param worldName 世界名称
     * @param gameState 游戏状态
     * @return 犯规原因，如果没有犯规则返回null
     */
    private String check8ballsInfractions(String worldName, GameState gameState) {
        // 1. 检查母球是否击中了任何球
        if (!gameState.hasMotherBallHitAnyBall()) {
            return "母球未击中任何球";
        }

        // 2. 检查基本击球要求
        if (gameState.isBreakShot()) {
            // 开球：至少要有四个球碰壁或者至少有一颗球进球
            if (gameState.getWallHitCount() < 4 && !gameState.hasScored()) {
                return "开球犯规：未达到4球碰壁且无进球";
            }
        } else {
            // 普通击球：至少进一球或有任何球碰壁
            if (!gameState.hasValidShot()) {
                return "击球犯规：无进球且无球碰壁";
            }
        }

        // 3. 黑8进洞时的犯规检查已经在determine8ballsWinnerAfterSettle方法中处理

        return null; // 无犯规
    }

    /**
     * 结束8balls模式的单局
     */
    private void end8ballsRound(String worldName, GameState gameState, Player winner) {
        gameState.addRoundWin(winner);

        for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
            p.sendMessage("§a第 " + gameState.getCurrentRound() + " 局结束！");
            p.sendMessage("§6获胜者：" + winner.getName());

            // 同时以标题形式呈现
            p.sendTitle("§a第 "+gameState.getCurrentRound()+" 局结束",
                    "§6获胜者: "+winner.getName(),
                    20,60,20
            );

            // 显示当前比分
            for (Player player : gameState.getPlayers()) {
                int wins = gameState.getRoundWins(player);
                p.sendMessage("§b" + player.getName() + ": " + wins + "胜");
            }
        }

        // 检查是否有总获胜者
        Player overallWinner = gameState.getOverallWinner();
        if (overallWinner != null) {
            // 整个比赛结束
            for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
                p.sendMessage("§a比赛结束！");
                p.sendMessage("§6总获胜者：" + overallWinner.getName());
            }
            plugin.getInGame().endGame(worldName);
        } else {
            // 开始下一局
            gameState.nextRound();

            // 重置所有玩家的染料为灰色（未分配状态）
            for (Player player : gameState.getPlayers()) {
                updatePlayerColorIndicator(player, "none");
            }

            // 清除场上所有球并重新生成
            plugin.getInGame().clearBalls(worldName);
            plugin.getInGame().spawnBalls(worldName);

            // 如果是8balls模式，设置球的发光效果
            if (gameState.getGameType().equals("8balls")) {
                plugin.getInGame().setGlowingFor8ballsMode(worldName);
            }

            // 将玩家传送到母球旁边
            teleportPlayersToMotherBall(worldName);

            // 重新开始回合
            startTurn(worldName);

            for(Player p : Bukkit.getWorld(worldName).getPlayers()) {
                p.sendMessage("§a第" + gameState.getCurrentRound() + "局开始！");
                Player breakPlayer = gameState.getBreakPlayer();
                p.sendMessage("§e由 " + breakPlayer.getName() + " 开球");
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE/*信标激活*/, 1.0f, 1.0f);
            }
        }
    }

    /**
     * 将玩家传送到母球旁边
     */
    private void teleportPlayersToMotherBall(String worldName) {
        DisplayBall motherBall = plugin.getInGame().getMotherBall(worldName);
        if (motherBall != null) {
            Location motherBallLoc = motherBall.location.clone();
            for (Player player : Bukkit.getWorld(worldName).getPlayers()) {
                if (player.getScoreboardTags().contains("tableball_ingame")) {
                    player.teleport(motherBallLoc);
                }
            }
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
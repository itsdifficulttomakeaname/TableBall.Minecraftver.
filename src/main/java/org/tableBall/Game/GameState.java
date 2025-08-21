package org.tableBall.Game;

import org.bukkit.entity.Player;
import java.util.*;

public class GameState {
    private final List<Player> players;
    private final String gameType;
    private final int totalRounds; // 总对局数
    private int currentPlayerIndex;
    private int tempScore;
    private boolean hasScored; // 是否进球
    private boolean whiteBallIn; // 母球是否进洞
    private boolean isWaitingForBallsToStop; // 是否等待球停止
    private int ballsInHole; // 进球数量

    // 8balls模式专用字段
    private final Map<Player, String> playerColors; // 玩家颜色 ("red", "blue", "none")
    private boolean colorsAssigned; // 是否已分配颜色
    private int currentRound; // 当前对局数
    private final Map<Player, Integer> roundWins; // 每个玩家的获胜局数
    private Player pendingRoundWinner; // 待处理的单局获胜者
    private int wallHitCount; // 当前回合碰壁次数
    private boolean isBreakShot; // 是否为开球
    private String pendingInfraction; // 待处理的犯规原因
    private boolean hasFirstBallHit; // 是否已经击中第一个球
    private boolean firstBallCorrect; // 第一个球是否正确
    private int pendingColorBall; // 待分配颜色的球号
    private boolean black8InHole; // 黑8是否进洞
    private static boolean isOtherBallInHole; // 黑8进洞时有没有除了白球以外的其他球进洞
    private int breakPlayerIndex; // 开球玩家索引
    private boolean motherBallHitAnyBall; // 母球是否击中了任何球

    public GameState(List<Player> players, String gameType, int totalRounds) {
        this.players = players;
        this.gameType = gameType;
        this.totalRounds = totalRounds;
        this.currentPlayerIndex = 0;
        this.tempScore = 0;
        this.hasScored = false;
        this.whiteBallIn = false;
        this.isWaitingForBallsToStop = false;
        this.ballsInHole = 0;
        this.playerColors = new HashMap<>();
        this.colorsAssigned = false;
        this.currentRound = 1;
        this.roundWins = new HashMap<>();
        this.pendingRoundWinner = null;
        this.wallHitCount = 0;
        this.isBreakShot = true;
        this.pendingInfraction = null;
        this.hasFirstBallHit = false;
        this.firstBallCorrect = false;
        this.pendingColorBall = -1;
        this.black8InHole = false;
        this.breakPlayerIndex = 0; // 第一局由第一个玩家开球
        this.motherBallHitAnyBall = false;

        // 初始化玩家数据
        for (Player player : players) {
            playerColors.put(player, "none");
            roundWins.put(player, 0);
        }
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public Player getNextPlayer() {
        return players.get((currentPlayerIndex + 1) % players.size());
    }

    public boolean isCurrentPlayer(Player player) {
        return player.equals(getCurrentPlayer());
    }

    public void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        resetTurnState();
    }

    public void resetTurnState() {
        this.hasScored = false;
        this.whiteBallIn = false;
        this.isWaitingForBallsToStop = false;
        this.ballsInHole = 0;
        this.black8InHole = false;
        this.tempScore = 0;

        this.wallHitCount = 0;
        this.hasFirstBallHit = false;
        this.firstBallCorrect = false;
        this.pendingInfraction = null;
        this.pendingColorBall = -1;
        this.motherBallHitAnyBall = false;
    }

    /**
     * 检查是否满足基本击球要求（进球或碰壁）
     */
    public boolean hasValidShot() {
        return hasScored || wallHitCount > 0;
    }

    public int getPendingColorBall() {
        return pendingColorBall;
    }

    public void setPendingColorBall(int pendingColorBall) {
        this.pendingColorBall = pendingColorBall;
    }

    public int getTempScore() {
        return tempScore;
    }

    public void addTempScore(int points) {
        tempScore += points;
    }

    public void resetTempScore() {
        tempScore = 0;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public String getGameType() {
        return gameType;
    }

    public boolean hasScored() {
        return hasScored;
    }

    public void setHasScored(boolean hasScored) {
        this.hasScored = hasScored;
    }

    public boolean isWhiteBallIn() {
        return whiteBallIn;
    }

    public void setWhiteBallIn(boolean whiteBallIn) {
        this.whiteBallIn = whiteBallIn;
    }

    public boolean isWaitingForBallsToStop() {
        return isWaitingForBallsToStop;
    }

    public void setWaitingForBallsToStop(boolean waitingForBallsToStop) {
        isWaitingForBallsToStop = waitingForBallsToStop;
    }

    public int getBallsInHole() {
        return ballsInHole;
    }

    public void incrementBallsInHole() {
        this.ballsInHole++;
    }

    public void resetBallsInHole() {
        this.ballsInHole = 0;
    }

    // 8balls模式相关方法
    public String getPlayerColor(Player player) {
        return playerColors.getOrDefault(player, "none");
    }

    public void setPlayerColor(Player player, String color) {
        playerColors.put(player, color);
        if (!colorsAssigned && !color.equals("none")) {
            colorsAssigned = true;
        }
    }

    public boolean areColorsAssigned() {
        return colorsAssigned;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void nextRound() {
        currentRound++;
        // 重置颜色分配
        colorsAssigned = false;
        for (Player player : players) {
            playerColors.put(player, "none");
        }
        // 清除待处理的获胜者
        pendingRoundWinner = null;

        // 切换开球玩家（轮换制）
        breakPlayerIndex = (breakPlayerIndex + 1) % players.size();
        // 设置当前玩家为开球玩家
        currentPlayerIndex = breakPlayerIndex;
    }

    public Player getPendingRoundWinner() {
        return pendingRoundWinner;
    }

    public void setPendingRoundWinner(Player winner) {
        this.pendingRoundWinner = winner;
    }

    public int getWallHitCount() {
        return wallHitCount;
    }

    public void incrementWallHitCount() {
        this.wallHitCount++;
    }

    public void resetWallHitCount() {
        this.wallHitCount = 0;
    }

    public boolean isBreakShot() {
        return isBreakShot;
    }

    public void setBreakShot(boolean breakShot) {
        this.isBreakShot = breakShot;
    }

    public String getPendingInfraction() {
        return pendingInfraction;
    }

    public void setPendingInfraction(String pendingInfraction) {
        this.pendingInfraction = pendingInfraction;
    }

    public boolean hasFirstBallHit() {
        return hasFirstBallHit;
    }

    public void setFirstBallHit(boolean firstBallHit) {
        this.hasFirstBallHit = firstBallHit;
    }

    public boolean isFirstBallCorrect() {
        return firstBallCorrect;
    }

    public void setFirstBallCorrect(boolean firstBallCorrect) {
        this.firstBallCorrect = firstBallCorrect;
    }

    public void addRoundWin(Player player) {
        roundWins.put(player, roundWins.getOrDefault(player, 0) + 1);
    }

    public int getRoundWins(Player player) {
        return roundWins.getOrDefault(player, 0);
    }

    public Map<Player, Integer> getAllRoundWins() {
        return new HashMap<>(roundWins);
    }

    /**
     * 检查是否有玩家已经获得足够的胜利局数
     */
    public Player getOverallWinner() {
        int requiredWins = (totalRounds / 2) + 1; // 例如3局2胜，5局3胜
        for (Player player : players) {
            if (getRoundWins(player) >= requiredWins) {
                return player;
            }
        }
        return null;
    }

    /**
     * 检查当前对局是否结束
     */
    public boolean isCurrentRoundFinished() {
        return currentRound > totalRounds;
    }

    public boolean isBlack8InHole() {
        return black8InHole;
    }

    public void setBlack8InHole(boolean black8InHole) {
        this.black8InHole = black8InHole;
    }

    public Player getBreakPlayer() {
        return players.get(breakPlayerIndex);
    }

    public int getBreakPlayerIndex() {
        return breakPlayerIndex;
    }

    public boolean hasMotherBallHitAnyBall() {
        return motherBallHitAnyBall;
    }

    public void setMotherBallHitAnyBall(boolean motherBallHitAnyBall) {
        this.motherBallHitAnyBall = motherBallHitAnyBall;
    }

    public static void setIsOtherBallInHole(boolean isOtherBallInHole){
        GameState.isOtherBallInHole = isOtherBallInHole;
    }

    public static boolean getIsOtherBallInHole(){
        return isOtherBallInHole;
    }

    public static boolean getIsInGame(Player p){
        return p.getScoreboardTags().contains("tableball_ingame");
    }
}
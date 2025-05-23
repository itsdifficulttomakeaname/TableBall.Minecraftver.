package org.tableBall.Game;

import org.bukkit.entity.Player;
import java.util.*;

public class GameState {
    private final List<Player> players;
    private final String gameType;
    private int currentPlayerIndex;
    private int tempScore;
    private boolean hasScored; // 是否进球
    private boolean whiteBallIn; // 母球是否进洞
    private boolean isWaitingForBallsToStop; // 是否等待球停止
    private int ballsInHole; // 进球数量

    public GameState(List<Player> players, String gameType) {
        this.players = players;
        this.gameType = gameType;
        this.currentPlayerIndex = 0;
        this.tempScore = 0;
        this.hasScored = false;
        this.whiteBallIn = false;
        this.isWaitingForBallsToStop = false;
        this.ballsInHole = 0;
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
        this.tempScore = 0;
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
} 
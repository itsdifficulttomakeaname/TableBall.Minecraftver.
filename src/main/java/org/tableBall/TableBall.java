package org.tableBall;

import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.Required;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.tableBall.Commands.*;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Game.GameState;
import org.tableBall.Game.InGame;
import org.tableBall.Game.End;
import org.tableBall.Manager.RoundManager;
import org.tableBall.Listeners.EntityEventListener;
import org.tableBall.Manager.ScoreBoardManager;
import org.tableBall.Utils.WorldUtils;
import org.tableBall.Commands.ScoreBoardCommand;

import java.io.File;
import java.util.ArrayList;

public final class TableBall extends JavaPlugin {
    public static final NamespacedKey BALL_ID_KEY = new NamespacedKey("tableball", "ball_id");
    public static final NamespacedKey BALL_WORLD_KEY = new NamespacedKey("tableball", "ball_world");

    private WorldUtils worldUtils;
    private InGame inGame;
    private End end;
    private ScoreBoardManager scoreBoardManager;
    private RoundManager roundManager;
    private EntityEventListener entityEventListener;
    private GameState gamestate;
    private DisplayBall displayBall;

    @Override
    public void onEnable() {
        PlanetLib.initialize(this, Required.NBT);

        // 保存默认配置
        saveDefaultConfig();
        saveResource("balls.yml", false);
        saveResource("scoreboard.yml", false);

        // 初始化管理器
        this.worldUtils = new WorldUtils(this);
        this.scoreBoardManager = new ScoreBoardManager(this);
        this.inGame = new InGame(this, worldUtils);
        this.end = new End(this, scoreBoardManager);
        this.roundManager = new RoundManager(this);

        // 注册命令
        getCommand("inviteplayer").setExecutor(new InviteCommand(this));
        getCommand("scb").setExecutor(new ScoreBoardCommand(scoreBoardManager));
        getCommand("addscore").setExecutor(new AddScoreCommand(scoreBoardManager));
        getCommand("leave").setExecutor(new LeaveCommand(this));
        getCommand("acceptinvite").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("§c只有玩家才能使用此命令！");
                return true;
            }
            return org.tableBall.Commands.InviteCommand.handleAcceptInvite((org.bukkit.entity.Player) sender, this);
        });
        getCommand("editmode").setExecutor(new EditModeCommand(this));

        // 注册监听器
        EntityEventListener entityEventListener = new EntityEventListener(this, inGame);
        getServer().getPluginManager().registerEvents(entityEventListener, this);
        setEntityEventListener(entityEventListener);

        DisplayBall.plugin = this;

        // 注册桌球物理管理器
        /*
        PlanetLib.getScheduler().runTimer(t->{
            if(DisplayBall.displayBalls.isEmpty()) return;
            String world = DisplayBall.displayBalls.stream().findFirst().get().getWorld();
            int amount = 4;
            for(int i=0;i<amount;i++) {
                DisplayBall.displayBalls.forEach(d->d.updateMovement(amount));
                EntityEventListener.checkCollisions();
            }

            outer: if(EntityEventListener.hasStrike) {
                for(DisplayBall i: DisplayBall.displayBalls){
                    if(i.velocity.length() > 0.001) break outer;
                }
                getRoundManager().settleTurn(world);
                EntityEventListener.hasStrike = false;
            }
        }, 1, 1);
         */
        PlanetLib.getScheduler().runTimer(t->{
            if(DisplayBall.displayBalls.isEmpty()) return;
            String world = DisplayBall.displayBalls.stream().findFirst().get().getWorld();
            int amount = 4;

            for(int i=0;i<amount;i++) {
                new ArrayList<>(DisplayBall.displayBalls).forEach(d->d.updateMovement(amount));
                EntityEventListener.checkCollisions();
            }

            if(EntityEventListener.hasStrike) {
                boolean allStopped = true;
                for(DisplayBall ball : DisplayBall.displayBalls) {
                    if(ball.velocity.length() > DisplayBall.MIN_SPEED || ball.isFalling) {
                        allStopped = false;
                        break;
                    }
                }

                if(allStopped) {
                    getRoundManager().settleTurn(world);
                }
            }
        }, 1, 1);

        getLogger().info("TableBall 插件已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("TableBall 插件已禁用！");
    }

    public WorldUtils getWorldUtils() {
        return worldUtils;
    }

    public InGame getInGame() {
        return inGame;
    }

    public End getEnd() {
        return end;
    }

    public ScoreBoardManager getScoreBoardManager() {
        return scoreBoardManager;
    }

    public RoundManager getRoundManager() {
        return roundManager;
    }

    public EntityEventListener getEntityEventListener() {
        return entityEventListener;
    }

    public void setEntityEventListener(EntityEventListener entityEventListener) {
        this.entityEventListener = entityEventListener;
    }

    public GameState getGamestate(){
        return this.gamestate;
    }

    public DisplayBall getDisplayBall(){
        return this.displayBall;
    }
}

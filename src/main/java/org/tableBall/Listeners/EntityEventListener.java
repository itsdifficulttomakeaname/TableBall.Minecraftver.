package org.tableBall.Listeners;

import cn.jason31416.planetlib.hook.NbtHook;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.tableBall.Game.InGame;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;
import org.bukkit.event.block.Action;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Commands.EditModeCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.tableBall.Manager.PlayerDataManager;
import org.tableBall.Utils.InventoryUtils;

import java.util.*;

import static java.lang.Math.max;

public class EntityEventListener implements Listener {
    private static TableBall plugin;
    private final InGame inGame;
    private final Start start;
    private final Map<String, BukkitTask> movementCheckTasks;
    private final Map<String, BukkitTask> ballInCheckTasks;
    public static final Map<String, Boolean> hitBall = new HashMap<>();
    public static final Map<Vehicle, Vector> velocities = new HashMap<>();
    private BukkitRunnable collisionTask;

    public static boolean hasStrike = false;

    public EntityEventListener(TableBall plugin, InGame inGame) {
        EntityEventListener.plugin = plugin;
        this.inGame = inGame;
        this.start = new Start(plugin, plugin.getWorldUtils(), inGame);
        this.movementCheckTasks = new HashMap<>();
        this.ballInCheckTasks = new HashMap<>();
//        startCollisionTask();

        /*
        PlanetLib.getScheduler().runTimer(t->{
            for(Vehicle v: velocities.keySet()){
                if(velocities.containsKey(v)) {
                    v.setVelocity(velocities.get(v));
                }else velocities.put(v, new Vector(0, 0, 0));
            }
        }, 2, 2);
        */
    }

    private void startCollisionTask() {
        collisionTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkCollisions();
            }
        };
        collisionTask.runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    public static void checkCollisions() {
        for (DisplayBall ball1 : DisplayBall.displayBalls) {
            for (DisplayBall ball2 : DisplayBall.displayBalls) {
                if (ball1 != ball2 && ball1.isColliding(ball2)) {
                    handleBallCollision(ball1, ball2);
                }
            }
        }
    }

    private static void handleBallCollision(DisplayBall ball1, DisplayBall ball2) {
        if (ball1.isFalling || ball2.isFalling) return;

        // 只在8balls模式下检查犯规
        String worldName = ball1.getWorld();
        if (worldName != null && plugin != null) {
            String gameType = plugin.getInGame().getGameType(worldName);
            if ("8balls".equals(gameType)) {
                check8ballsFoul(ball1, ball2);
            }
        }

        Vector deltaPos = ball1.location.toVector().subtract(ball2.location.toVector());
        Vector normal = deltaPos.normalize();
        Vector relativeVel = ball1.velocity.clone().subtract(ball2.velocity.clone()); // 相对速度
        double impulse = relativeVel.dot(normal);

        // 更新速度 (完全弹性碰撞)
        ball1.velocity.subtract(normal.clone().multiply(impulse));
        ball2.velocity.add(normal.clone().multiply(impulse));

        // 防止重叠
        double overlap = Math.sqrt(2) - deltaPos.length();
        if (overlap > 0) {
            Vector correction = normal.clone().multiply(overlap * 0.5);
            ball1.location.add(correction);
            ball2.location.subtract(correction);
        }

        float maxVelocity = (float) (0.33* getHighestKnockbackLevel());
        float loudness = loudnessProcess(max(ball1.velocity.length(),ball2.velocity.length()),maxVelocity);

        Location soundLoc = ball1.location;

        for (Player player : Bukkit.getOnlinePlayers()){
            if(player.getWorld().equals(ball1.location.getWorld())){
                player.playSound(
                        soundLoc, // 音效位置
                        Sound.BLOCK_NOTE_BLOCK_BASEDRUM, // 低音鼓音效（1.12+）
                        loudness, // 音量 (0.0-1.0)
                        1.0f  // 音高 (0.5-2.0)
                );
            }
        }
    }

    /**
     * 检查8balls模式的犯规
     */
    private static void check8ballsFoul(DisplayBall ball1, DisplayBall ball2) {
        if (plugin == null) return;

        String worldName = ball1.getWorld();
        if (worldName == null) return;

        org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
        if (gameState == null || !gameState.getGameType().equals("8balls")) return;

        Player currentPlayer = plugin.getRoundManager().getCurrentPlayer(worldName);
        if (currentPlayer == null) return;

        // 确定哪个是母球，哪个是目标球
        DisplayBall motherBall = null;
        DisplayBall targetBall = null;

        if (ball1.isMotherBall) {
            motherBall = ball1;
            targetBall = ball2;
        } else if (ball2.isMotherBall) {
            motherBall = ball2;
            targetBall = ball1;
        } else {
            return; // 两个都不是母球，不需要检查犯规
        }

        // 只检查第一个击中的球
        if (gameState.hasFirstBallHit()) {
            return; // 已经检查过第一个球了
        }

        gameState.setFirstBallHit(true);

        // 检查第一个击中的球是否正确
        String playerColor = gameState.getPlayerColor(currentPlayer);
        if (!"none".equals(playerColor)) {
            int targetBallNumber = plugin.getInGame().extractBallNumberFromDisplayBall(targetBall);
            boolean isCorrectBall = false;

            if ("red".equals(playerColor)) {
                isCorrectBall = (targetBallNumber >= 1 && targetBallNumber <= 7);
            } else if ("blue".equals(playerColor)) {
                isCorrectBall = (targetBallNumber >= 9 && targetBallNumber <= 15);
            }

            // 如果玩家的色球都打完了，可以打黑8
            if (!isCorrectBall && targetBallNumber == 8) {
                isCorrectBall = hasFinishedColorBalls(worldName, currentPlayer, playerColor);
            }

            gameState.setFirstBallCorrect(isCorrectBall);

            if (!isCorrectBall) {
                // 设置待处理的犯规，等球停下来再处理
                gameState.setPendingInfraction("第一球击中错误目标");
            }
        }
    }

    /**
     * 检查玩家是否已经打完了自己的色球
     */
    private static boolean hasFinishedColorBalls(String worldName, Player player, String playerColor) {
        if ("red".equals(playerColor)) {
            // 检查场上是否还有1-7号球
            return !hasColorBallsOnTable(worldName, 1, 7);
        } else if ("blue".equals(playerColor)) {
            // 检查场上是否还有9-15号球
            return !hasColorBallsOnTable(worldName, 9, 15);
        }
        return false;
    }

    /**
     * 检查场上是否还有指定范围的球
     */
    private static boolean hasColorBallsOnTable(String worldName, int minNumber, int maxNumber) {
        if (plugin == null) return false;

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

/*
    private static void handleBallCollision(DisplayBall ball1, DisplayBall ball2) {
        if(ball1.isFalling||ball2.isFalling) return;

        Vector v1 = ball1.velocity.clone();
        Vector v2 = ball2.velocity.clone();
        double angle = v1.angle(v2);

        Vector collideV = ball1.location.toVector().subtract(ball2.location.toVector()).normalize();
        double angleV1ToCollide = collideV.angle(v1), angleV2ToCollide = collideV.angle(v2);
        if (Double.isNaN(angleV1ToCollide)) angleV1ToCollide=0.0;
        if (Double.isNaN(angleV2ToCollide)) angleV2ToCollide=0.0;
        Vector v1cos = collideV.clone().multiply(v1.length() * Math.cos(angleV1ToCollide));
        Vector v2cos = collideV.clone().multiply(v2.length() * Math.cos(angleV2ToCollide));
        // 如果这里出bug，碰撞超出预期方向，交换90和-90
        Vector v1sin = collideV.clone().rotateAroundY(90).multiply(v1.length() * Math.sin(angleV1ToCollide));
        Vector v2sin = collideV.clone().rotateAroundY(-90).multiply(v2.length() * Math.sin(angleV1ToCollide));

        Vector v1new = v1sin.add(v2cos);
        Vector v2new = v2sin.add(v1cos);

        ball1.velocity = v1new;
        ball2.velocity = v2new;


        

//        Vector deltaX = x2.clone().subtract(x1);
//        double distance = deltaX.length();
//        double minDistance = ball1.getRadius() + ball2.getRadius();
//
//        // 当两球距离>两球判定距离，不碰撞
//        if (distance > minDistance) return;
//
//        Vector normal = deltaX.clone().normalize();
//        double velocityAlongNormal = v1.clone().subtract(v2).dot(normal);
//        Vector impulse = normal.clone().multiply(velocityAlongNormal);
//
//        ball1.velocity.subtract(impulse);
//        ball2.velocity.add(impulse);
//
//        double overlap = minDistance - distance;
//        Vector correction = normal.clone().multiply(overlap * 0.5);
//        ball1.location.subtract(correction);
//        ball2.location.add(correction);


    }

 */

    /*
    private void handleBallCollision(DisplayBall ball1, DisplayBall ball2) {
        Vector v1 = ball1.velocity;
        Vector v2 = ball2.velocity;
        Vector x1 = ball1.location.toVector(), x2 = ball2.location.toVector();

        double dv = x1.clone().distance(x2)*x1.clone().distance(x2);
        double n1 = Math.abs(v2.clone().subtract(v1).dot(x2.clone().subtract(x1))), n2 = Math.abs(v1.clone().subtract(v2).dot(x1.clone().subtract(x2)));

//        plugin.getLogger().info(x2.clone().subtract(x1).multiply(n1/dv)+"; "+x1.clone().subtract(x2).multiply(n2/dv));

        ball1.velocity.add(x2.clone().subtract(x1).multiply(n1/dv));
        ball2.velocity.add(x1.clone().subtract(x2).multiply(n2/dv));

//        Vector v1 = ball1.velocity;
//        Vector v2 = ball2.velocity;
//        Vector l1 = ball1.location.toVector(), l2 = ball2.location.toVector();
//        double theta1 = v1.angle(l2.subtract(l1)), theta2 = v2.angle(l1.subtract(l2));
//        if(Double.isNaN(theta1)) theta1=0;
//        if(Double.isNaN(theta2)) theta2=0;
//        Vector v1x = v1.clone().multiply(Math.cos(theta1)), v1y = v1.clone().multiply(Math.sin(theta1));
//        Vector v2x = v2.clone().multiply(Math.cos(theta2)), v2y = v2.clone().multiply(Math.sin(theta2));
//        Vector v1f = v1y.clone().add(v2x), v2f = v2y.clone().add(v1x);
//
//        plugin.getLogger().info(v1.length()+"; "+v2.length()+"; "+theta1+"; "+theta2+"; "+v1f.length()+"; "+v2f.length());

//            pusherV.setVelocity(v1f);
//            pushedV.setVelocity(v2f);
//        ball1.velocity = v1f;
//        ball2.velocity = v2f;
    }

     */

    /*
    @EventHandler
    public void onBoatPushed(VehicleEntityCollisionEvent e) {
        Entity pusher = e.getEntity();
        Entity bePushedEntity = e.getVehicle();

        if (pusher instanceof Player && bePushedEntity instanceof Boat) {
            e.setCancelled(true);
            return;
        }

        if (pusher instanceof Boat pusherV && bePushedEntity instanceof Boat pushedV) {
            Vector v1 = pusherV.getVelocity();
            Vector v2 = pushedV.getVelocity();
            Vector l1 = pusher.getLocation().toVector(), l2 = bePushedEntity.getLocation().toVector();
            double theta1 = v1.angle(l2.subtract(l1)), theta2 = v2.angle(l1.subtract(l2));
            if(Double.isNaN(theta1)) theta1=0;
            if(Double.isNaN(theta2)) theta2=0;
            Vector v1x = v1.multiply(Math.cos(theta1)), v1y = v1.multiply(Math.sin(theta1));
            Vector v2x = v2.multiply(Math.cos(theta2)), v2y = v2.multiply(Math.sin(theta2));
            Vector v1f = v1y.add(v2x), v2f = v2y.add(v1x);

            plugin.getLogger().info(v1.length()+"; "+v2.length()+"; "+theta1+"; "+theta2+"; "+v1f.length()+"; "+v2f.length());

//            pusherV.setVelocity(v1f);
//            pushedV.setVelocity(v2f);
            velocities.put(pusherV, v1f);
            velocities.put(pushedV, v2f);
            pusherV.setVelocity(velocities.get(pusherV));
            pushedV.setVelocity(velocities.get(pushedV));

            e.setCancelled(true);
            return;
        }
    }
     */

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        Player player = event.getPlayer();
        event.setJoinMessage("§a [台球厅]  §r欢迎玩家 §6" + player.getName() + "§r 来到台球厅！");

        player.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);

        // 设置主城物品栏
        new InventoryUtils(plugin).setLobbyInventory(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        if (inGame.isPlayerInGame(player)) {
            inGame.removePlayer(player);
        }

        event.setQuitMessage("§a [台球厅]  §r玩家 §6" + player.getName() + "§r 离开了台球厅！");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 取消所有方块破坏事件
        if (!EditModeCommand.isEditMode()) event.setCancelled(true);
    }

    private static Material getMaterialOfMotherBall() {
        return Material.WHITE_TERRACOTTA;
    }

    @EventHandler
    public void onPlayerPlaceWhiteBall(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!NbtHook.hasTag(item, "tb.whiteBall"))
            return;

        // 判断点击方块是否为蓝冰
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BLUE_ICE) {
            player.sendMessage("§c母球只能放在蓝冰上！");
            event.setCancelled(true);
            return;
        }

        // 生成母球展示实体
        Location loc = event.getClickedBlock().getLocation().add(Objects.requireNonNull(event.getClickedPosition()));
//        plugin.getLogger().info("\nLoc= "+loc+"\n被右键方块的位置= "+event.getClickedBlock().getLocation());
        Material material = getMaterialOfMotherBall();
        DisplayBall motherBall = new DisplayBall(loc, material, "§f白球", true);
        String worldName = player.getWorld().getName();
        inGame.setMotherBall(worldName, motherBall);
        inGame.addBall(worldName, motherBall);

        // 移除物品
        player.getInventory().setItemInMainHand(null);
        player.sendMessage("§a已成功放置母球！");
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractPersonalInfo(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.NETHER_STAR) return;

        // 检查是否是个人信息物品
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "personal_info"), PersistentDataType.BYTE)) {

            // 检查玩家是否在主城
            String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
            if (!player.getWorld().getName().equals(lobbyWorld)) {
                return; // 不在主城，不处理
            }

            // 显示个人信息
            showPersonalInfo(player);
            event.setCancelled(true);
        }
    }



    @EventHandler
    public void onPlayerInteractDisplay(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interactionEntity)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        DisplayBall ball = findBallByEntity(interactionEntity);
        if (ball == null) return;

        String worldName = player.getWorld().getName();

        if(!inGame.checkAllBallsStatic(event.getPlayer().getWorld().getName())) return;

        // 检查玩家是否空手（优先级最低的检查）
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem == null || mainHandItem.getType() == Material.AIR) {
            player.sendMessage("§c请手持球杆击球！");
            event.setCancelled(true);
            return;
        }

        // 如果既不是当前回合的玩家，又不是母球，就直接return，不做任何提示
        // 检查回合和母球
        if (!plugin.getRoundManager().isCurrentPlayer(worldName, player)&&!hasStrike) {
            player.sendMessage("§c现在不是你的回合！");
            event.setCancelled(true);
            return;
        }

        if (!ball.isMotherBall) {
            player.sendMessage("§c你只能击打母球！");
            event.setCancelled(true);
            return;
        }

        // 计算击球方向
        Vector direction = player.getLocation().getDirection().normalize();
        double knockbackLevel = mainHandItem.getEnchantmentLevel(Enchantment.KNOCKBACK);
        Vector velocity = direction.multiply(0.33 * (knockbackLevel + 1)).setY(0);

        // 应用速度（确保立即生效）
        ball.setVelocity(velocity);
//        ball.updateMovement(1); // 强制更新位置

        // 有问题就删掉这个
        EntityEventListener.hasStrike = true;

        // 处理回合逻辑
        plugin.getRoundManager().handleShot(worldName, player);
        event.setCancelled(true);
    }

    private DisplayBall findBallByEntity(Interaction entity) {
        for (DisplayBall ball : DisplayBall.displayBalls) {
            if (ball.interactor.getUniqueId().equals(entity.getUniqueId())) {
                return ball;
            }
        }
        return null;
    }

    /**
     * 显示玩家个人信息
     * @param player 玩家
     */
    private void showPersonalInfo(Player player) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        var statsMap = dataManager.getPlayerStats(player);

        player.sendMessage("§a个人信息：");

        if (statsMap.isEmpty()) {
            player.sendMessage("§7暂无游戏记录");
            return;
        }

        // 按照指定格式输出每个模式的信息
        for (var entry : statsMap.entrySet()) {
            String gameMode = entry.getKey();
            PlayerDataManager.PlayerStats stats = entry.getValue();

            player.sendMessage("§f\"" + gameMode + "\" 的总次数: " + stats.getTotalGames() +
                             " (游玩该模式的总次数)");
            player.sendMessage("§f\"" + gameMode + "\" 的获胜次数: " + stats.getWins() +
                             " (游玩该模式的获胜次数)");
            player.sendMessage("§f\"" + gameMode + "\" 的失败次数:");
            player.sendMessage("§f    个人弃权: " + stats.getForfeitLosses() +
                             " (弃权而输的次数)");
            player.sendMessage("§f    对方获胜: " + stats.getOpponentWins() +
                             " (对方战胜的次数)");
            player.sendMessage(""); // 空行分隔
        }
    }

    private static float loudnessProcess(double vel, double maxVel){
        if (vel <= maxVel) {
            return (float) ((float) vel/maxVel);
        }else{
            return 1.0f;
        }
    }

    private static int getHighestKnockbackLevel() {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("items");
        if (items == null) return 0;

        int highestLevel = 0;
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.contains("enchantments.knockback")) {
                int level = item.getInt("enchantments.knockback", 0);
                highestLevel = max(highestLevel, level);
            }
        }
        return highestLevel;
    }
}

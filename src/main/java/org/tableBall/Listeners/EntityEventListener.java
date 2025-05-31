package org.tableBall.Listeners;

import cn.jason31416.planetlib.hook.NbtHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.tableBall.Game.InGame;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;
import org.bukkit.event.block.Action;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Commands.EditModeCommand;

import java.util.*;

public class EntityEventListener implements Listener {
    private final TableBall plugin;
    private final InGame inGame;
    private final Start start;
    private final Map<String, BukkitTask> movementCheckTasks;
    private final Map<String, BukkitTask> ballInCheckTasks;
    public static final Map<String, Boolean> hitBall = new HashMap<>();
    public static final Map<Vehicle, Vector> velocities = new HashMap<>();
    private BukkitRunnable collisionTask;

    public static boolean hasStrike = false;

    public EntityEventListener(TableBall plugin, InGame inGame) {
        this.plugin = plugin;
        this.inGame = inGame;
        this.start = new Start(plugin, plugin.getWorldUtils(), inGame);
        this.movementCheckTasks = new HashMap<>();
        this.ballInCheckTasks = new HashMap<>();
        startCollisionTask();

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

    private void checkCollisions() {
        for (DisplayBall ball1 : DisplayBall.displayBalls) {
            for (DisplayBall ball2 : DisplayBall.displayBalls) {
                if (ball1.isColliding(ball2)&&ball1.uuid.toString().compareTo(ball2.uuid.toString())>0) {
                    plugin.getLogger().info("L86 collided");
                    handleBallCollision(ball1, ball2);
                }
            }
        }
    }

    private void handleBallCollision(DisplayBall ball1, DisplayBall ball2) {
        Vector v1 = ball1.velocity;
        Vector v2 = ball2.velocity;
        Vector x1 = ball1.location.toVector(), x2 = ball2.location.toVector();

        double dv = x1.clone().distance(x2)*x1.clone().distance(x2);
        double n1 = v2.clone().subtract(v1).dot(x2.clone().subtract(x1)), n2 = v1.clone().subtract(v2).dot(x1.clone().subtract(x2));

        plugin.getLogger().info(x2.clone().subtract(x1).multiply(n1/dv)+"; "+x1.clone().subtract(x2).multiply(n2/dv));

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

    /*
    @EventHandler
    public void onBoatBlockCollision(VehicleBlockCollisionEvent e){
        plugin.getLogger().info("Collision "+e.getBlock().getType());
        if(velocities.containsValue(e.getVehicle())){
            Vector v = velocities.get(e.getVehicle());
            if(e.getBlock().getType()==Material.SPRUCE_PLANKS) {
                v = new Vector(v.getX(), 0, -v.getZ());
            }else if(e.getBlock().getType()==Material.DARK_OAK_PLANKS){
                v = new Vector(-v.getX(), 0, v.getZ());
            }else if(e.getBlock().getType()==Material.DARK_OAK_LOG || e.getBlock().getType()==Material.SPRUCE_LOG){
                v = new Vector(0, 0, 0);
            }
            velocities.put(e.getVehicle(), v.multiply(0.95));
            e.getVehicle().setVelocity(velocities.get(e.getVehicle()));
        }
    }

    @EventHandler
    public void onBoatDestroyed(VehicleDestroyEvent e){
        Vehicle boat=e.getVehicle();
        velocities.remove(boat);
    }

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

    /*
    @EventHandler
    public void onEntityDamageByEntity(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getAttacker();
        Entity entity = event.getVehicle();
        String worldName = entity.getWorld().getName();

        // 检查是否是当前玩家的回合
        if (plugin.getRoundManager().isCurrentPlayer(worldName, player)) {
            //plugin.getLogger().info("是" + player.getName() + "的回合，伤害设为0 L83");
            event.setDamage(0);
        } else {
            //plugin.getLogger().info("不是" + player.getName() + "的回合，取消事件 L86");
            event.setCancelled(true);
            return;
        }

        // 检查是否是母球
        Boat motherBall = plugin.getInGame().getMotherBall(worldName);
        if (motherBall != null && motherBall.equals(entity)) {
            if(hitBall.getOrDefault(plugin.getInGame().getBallWorld(motherBall), false)){
                return;
            }

            event.setCancelled(true);
            hitBall.put(plugin.getInGame().getBallWorld(motherBall), true);

            velocities.put(motherBall, motherBall.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize().multiply(player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK)+1));

            // 处理击球事件
            plugin.getRoundManager().handleShot(worldName, player);
            // 更新球的物理状态
            plugin.getInGame().updateBallPhysics((Boat) entity);
            // 检查所有球是否静止
            plugin.getInGame().checkAllBallsStatic(worldName);
        } else {
            player.sendMessage("你不可以打除了母球以外的球!");
            event.setCancelled(true);
        }
    }

     */

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.setJoinMessage("§a [台球厅]  §r欢迎玩家 §6" + player.getName() + "§r 来到台球厅！");

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
        Location loc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        DisplayBall motherBall = new DisplayBall(loc, Material.WHITE_TERRACOTTA, "母球", true);
        String worldName = player.getWorld().getName();
        inGame.setMotherBall(worldName, motherBall);
        inGame.addBall(worldName, motherBall);

        // 移除物品
        player.getInventory().setItemInMainHand(null);
        player.sendMessage("§a已成功放置母球！");
        event.setCancelled(true);
    }



    @EventHandler
    public void onPlayerInteractDisplay(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interactionEntity)) return;
        Player player = event.getPlayer();
        if(player == null) return;

        // 查找对应的球
        DisplayBall ball = findBallByEntity(interactionEntity);
        if (ball == null) return;
        
        // 检查是否是当前玩家的回合
        String worldName = player.getWorld().getName();
        if (!plugin.getRoundManager().isCurrentPlayer(worldName, player)||hasStrike) {
            player.sendMessage("§c现在不是你的回合！");
            event.setCancelled(true);
            return;
        }
        
        // 检查是否是母球
        if (!ball.isMotherBall) {
            player.sendMessage("§c你只能击打母球！");
            event.setCancelled(true);
            return;
        }
        
        // 计算击球方向和速度
        Vector direction = player.getLocation().getDirection().normalize();
        double knockbackLevel = player.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.KNOCKBACK);
        Vector velocity = direction.multiply(0.33 * (knockbackLevel + 1)).setY(0);
        
        // 设置球的速度
        ball.setVelocity(velocity);
        
        // 处理击球事件
        plugin.getRoundManager().handleShot(worldName, player);
        
        // 检查所有球是否静止
        plugin.getInGame().checkAllBallsStatic(worldName);

        hasStrike = true;
        
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
}

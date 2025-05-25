package org.tableBall.Listeners;

import cn.jason31416.planetlib.PlanetLib;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.tableBall.Game.InGame;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;
import org.tableBall.Utils.WorldUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityEventListener implements Listener {
    private final TableBall plugin;
    private final InGame inGame;
    private final Start start;
    private final Map<String, BukkitTask> movementCheckTasks;
    private final Map<String, BukkitTask> ballInCheckTasks;
    public static final Map<String, Boolean> hitBall = new HashMap<>();
    public static final Map<Vehicle, Vector> velocities = new HashMap<>();

    public EntityEventListener(TableBall plugin, InGame inGame) {
        this.plugin = plugin;
        this.inGame = inGame;
        this.start = new Start(plugin, plugin.getWorldUtils(), inGame);
        this.movementCheckTasks = new HashMap<>();
        this.ballInCheckTasks = new HashMap<>();

        PlanetLib.getScheduler().runTimer(t->{
            for(Vehicle v: velocities.keySet()){
                if(velocities.containsKey(v)) {
                    v.setVelocity(velocities.get(v));
                }else velocities.put(v, new Vector(0, 0, 0));
            }
        }, 2, 2);
    }

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

//    @EventHandler
//    public void onVehicleMove(VehicleMoveEvent e){}

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
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getVehicle() instanceof Boat) {
            event.setCancelled(true);
    }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 取消所有方块破坏事件
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerPlaceWhiteBall(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("tableball", "white_ball"), PersistentDataType.BYTE)) return;
        // 判断点击方块是否为蓝冰
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BLUE_ICE) {
            player.sendMessage("§c母球只能放在蓝冰上！");
            event.setCancelled(true);
            return;
        }
        // 生成母球船实体
        Location loc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        Boat boat = player.getWorld().spawn(loc, Boat.class);
        EntityEventListener.velocities.put(boat, new Vector(0, 0, 0));
        boat.setBoatType(Boat.Type.BIRCH);
        boat.setCustomName("§f母球");
        boat.setCustomNameVisible(true);
        boat.getPersistentDataContainer().set(org.tableBall.TableBall.BALL_ID_KEY, PersistentDataType.INTEGER, 0);
        boat.getPersistentDataContainer().set(org.tableBall.TableBall.BALL_WORLD_KEY, PersistentDataType.STRING, player.getWorld().getName());
        plugin.getInGame().setMotherBall(inGame.getBallWorld(boat), boat);

        // 移除物品
        item.setAmount(item.getAmount() - 1);
        player.sendMessage("§a已成功放置母球！");
        event.setCancelled(true);
    }
}

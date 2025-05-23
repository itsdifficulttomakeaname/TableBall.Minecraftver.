package org.tableBall.Listeners;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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

    public EntityEventListener(TableBall plugin, InGame inGame) {

        this.plugin = plugin;
        this.inGame = inGame;
        this.start = new Start(plugin, plugin.getWorldUtils(), inGame);
        this.movementCheckTasks = new HashMap<>();
        this.ballInCheckTasks = new HashMap<>();
    }

    @EventHandler
    public void onBoatPushed(VehicleEntityCollisionEvent e) {
        Entity pusher = e.getEntity();
        Entity bePushedEntity = e.getVehicle();

        if (pusher instanceof Player && bePushedEntity instanceof Boat) {
            e.setCancelled(true);
        }

        if (pusher instanceof Boat && bePushedEntity instanceof Boat){

        }
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent e){}

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
            plugin.getLogger().info("是" + player.getName() + "的回合，伤害设为0.01 L83");
            event.setDamage(0.01);
        } else {
            plugin.getLogger().info("不是" + player.getName() + "的回合，取消事件 L86");
            event.setCancelled(true);
            return;
        }

        // 检查是否是母球
        Boat motherBall = plugin.getInGame().getMotherBall(worldName);
        if (motherBall != null && motherBall.equals(entity)) {
            // 处理击球事件
            plugin.getRoundManager().handleShot(worldName, player);
            // 更新球的物理状态
            plugin.getInGame().updateBallPhysics((Boat) entity);
            // 检查所有球是否静止
            plugin.getInGame().checkAllBallsStatic(worldName);
            // 开始检查球进洞
            startBallInCheck(worldName);
        } else {
            player.sendMessage("你不可以打除了母球以外的球!");
            event.setCancelled(true);
        }
    }

    /**
     * 开始检查球进洞
     * @param worldName 世界名称
     */
    public void startBallInCheck(String worldName) {
        // 取消现有的检查任务
        if (ballInCheckTasks.containsKey(worldName)) {
            ballInCheckTasks.get(worldName).cancel();
        }

        // 开始新的检查任务
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    this.cancel();
                    ballInCheckTasks.remove(worldName);
                    return;
                }

                // 检查所有球
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Boat) {
                        Boat boat = (Boat) entity;
                        // 检查是否进洞
                        if (isBallInHole(boat)) {
                            handleBallIn(boat);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L); // 每0.25秒检查一次

        ballInCheckTasks.put(worldName, task);
    }

    /**
     * 检查球是否进洞
     * @param boat 球
     * @return 是否进洞
     */
    private boolean isBallInHole(Boat boat) {
        String worldName = boat.getWorld().getName();
        Location ballLoc = boat.getLocation();
        
        // 从配置文件获取洞的位置
        ConfigurationSection holesConfig = plugin.getConfig().getConfigurationSection("worlds." + worldName + ".holes");
        if (holesConfig == null) return false;

        // 检查每个洞
        for (String holeId : holesConfig.getKeys(false)) {
            ConfigurationSection holeConfig = holesConfig.getConfigurationSection(holeId);
            if (holeConfig == null) continue;

            // 获取洞的判定区域
            double x1 = holeConfig.getDouble("x1");
            double y1 = holeConfig.getDouble("y1");
            double z1 = holeConfig.getDouble("z1");
            double x2 = holeConfig.getDouble("x2");
            double y2 = holeConfig.getDouble("y2");
            double z2 = holeConfig.getDouble("z2");

            // 检查球是否在洞的判定区域内
            if (isInRange(ballLoc.getX(), x1, x2) &&
                isInRange(ballLoc.getY(), y1, y2) &&
                isInRange(ballLoc.getZ(), z1, z2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查值是否在范围内
     * @param value 要检查的值
     * @param min 最小值
     * @param max 最大值
     * @return 是否在范围内
     */
    private boolean isInRange(double value, double min, double max) {
        return value >= Math.min(min, max) && value <= Math.max(min, max);
    }

    /**
     * 处理球进洞事件
     * @param entity 进洞的球
     */
    public void handleBallIn(Entity entity) {
        String worldName = entity.getWorld().getName();
        PersistentDataContainer data = entity.getPersistentDataContainer();

        if (data.has(TableBall.BALL_ID_KEY, PersistentDataType.STRING)) {
            String ballId = data.get(TableBall.BALL_ID_KEY, PersistentDataType.STRING);
            if (ballId != null) {
                // 处理进球事件
                plugin.getRoundManager().handleBallIn(worldName, ballId.equals("white"));
                // 移除球
                entity.remove();
            }
        }
    }

    /**
     * 检查球是否停止
     * @param entity 要检查的球
     */
    public void checkBallStop(Entity entity) {
        String worldName = entity.getWorld().getName();
        double velocity = entity.getVelocity().length();
        if (velocity < 0.1) {
            // 更新球的物理状态
            plugin.getInGame().updateBallPhysics((Boat) entity);
        }
        plugin.getRoundManager().checkBallStop(worldName, velocity);
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
        boat.setBoatType(Boat.Type.BIRCH);
        boat.setCustomName("§f母球");
        boat.setCustomNameVisible(true);
        boat.getPersistentDataContainer().set(org.tableBall.TableBall.BALL_ID_KEY, PersistentDataType.INTEGER, 0);
        boat.getPersistentDataContainer().set(org.tableBall.TableBall.BALL_WORLD_KEY, PersistentDataType.STRING, player.getWorld().getName());
        // 移除物品
        item.setAmount(item.getAmount() - 1);
        player.sendMessage("§a已成功放置母球！");
        event.setCancelled(true);
    }
}

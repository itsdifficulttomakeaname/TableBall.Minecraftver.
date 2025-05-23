package org.tableBall.Events;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.tableBall.TableBall;

public class EntityEventListener implements Listener {

    private final TableBall plugin;

    public EntityEventListener(TableBall plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Boat)) return;

        Player player = (Player) event.getDamager();
        Boat boat = (Boat) event.getEntity();

        // 检查是否是球
        if (!boat.getPersistentDataContainer().has(TableBall.BALL_ID_KEY, PersistentDataType.STRING)) return;

        // 检查是否是当前回合玩家
        if (!plugin.getInGame().canHitBall(player.getWorld().getName(), player, boat)) {
            event.setCancelled(true);
            return;
        }

        // 更新球的物理状态
        plugin.getInGame().updateBallPhysics(boat);

        // 检查所有球是否静止
        plugin.getInGame().checkAllBallsStatic(player.getWorld().getName());
    }
} 
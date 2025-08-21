package org.tableBall.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.tableBall.GUI.QuickMenuGUI;
import org.tableBall.TableBall;
import org.tableBall.Utils.InventoryUtils;

/**
 * 物品栏物品交互监听器
 * 处理快捷菜单物品的右键点击事件
 */
public class InventoryItemListener implements Listener {
    private final TableBall plugin;

    public InventoryItemListener(TableBall plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 检查是否是右键点击
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 检查物品是否是快捷菜单物品
        if (!InventoryUtils.isQuickMenuItem(item, plugin)) {
            return;
        }

        // 取消事件防止其他交互
        event.setCancelled(true);

        // 检查玩家是否在大厅世界
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        if (!player.getWorld().getName().equals(lobbyWorld)) {
            player.sendMessage("§c只能在大厅中使用快捷菜单！");
            return;
        }

        // 创建并打开快捷菜单
        QuickMenuGUI gui = new QuickMenuGUI(plugin, player);
        player.openInventory(gui.getInventory());
        
        // 播放打开音效
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e){
        Player player = (Player) e.getPlayer();

        if(e.getInventory().getType() == InventoryType.CHEST) {
            // 播放关闭音效
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.0f);
        }
    }
}
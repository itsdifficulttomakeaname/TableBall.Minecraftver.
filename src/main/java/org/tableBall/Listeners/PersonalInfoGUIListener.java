package org.tableBall.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.tableBall.GUI.PersonalInfoGUI;
import org.tableBall.TableBall;

public class PersonalInfoGUIListener implements Listener {
    private final TableBall plugin;

    public PersonalInfoGUIListener(TableBall plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        // 检查是否是个人信息GUI
        String title = event.getView().getTitle();
        if (!PersonalInfoGUI.isPersonalInfoGUI(title)) {
            return;
        }

        // 取消所有点击事件，防止物品被移动
        event.setCancelled(true);

        // 获取被点击的物品
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        // 检查是否是个人信息相关的物品
        if (PersonalInfoGUI.isPersonalInfoItem(clickedItem)) {
            // 可以在这里添加点击物品的特殊效果
            // 比如播放声音效果
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        }
    }

    @EventHandler 
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        // 检查是否是个人信息GUI
        String title = event.getView().getTitle();
        if (PersonalInfoGUI.isPersonalInfoGUI(title)) {
            // 取消拖拽事件，防止物品被移动
            event.setCancelled(true);
        }
    }
}
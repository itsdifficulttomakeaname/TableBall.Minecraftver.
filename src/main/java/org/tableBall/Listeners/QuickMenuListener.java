package org.tableBall.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.tableBall.GUI.QuickMenuGUI;
import org.tableBall.Manager.PlayerSettingsManager;
import org.tableBall.TableBall;

/**
 * 快捷菜单事件监听器
 * 处理菜单点击事件和相关逻辑
 */
public class QuickMenuListener implements Listener {
    private final TableBall plugin;

    public QuickMenuListener(TableBall plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuickMenuGUI)) {
            return;
        }

        event.setCancelled(true); // 取消默认行为

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        QuickMenuGUI gui = (QuickMenuGUI) event.getInventory().getHolder();
        int slot = event.getSlot();
        ClickType clickType = event.getClick();

        // 处理不同区域的点击
        if (slot >= 0 && slot <= 26) {
            // 前三排：玩家头颅区域
            handlePlayerHeadClick(player, gui, slot);
        } else if (slot == 36) {
            // (5,1) 世界选择
            handleWorldSelectorClick(player, gui, clickType);
        } else if (slot == 40) {
            // (5,5) 模式选择
            handleModeSelectorClick(player, gui, clickType);
        } else if (slot == 44) {
            // (5,9) 对局数选择
            handleRoundSelectorClick(player, gui, clickType);
        } else if (slot == 47) {
            // (6,3) 上一页
            handlePreviousPageClick(player, gui);
        } else if (slot == 51) {
            // (6,7) 下一页
            handleNextPageClick(player, gui);
        }
    }

    /**
     * 处理玩家头颅点击
     */
    private void handlePlayerHeadClick(Player clicker, QuickMenuGUI gui, int slot) {
        Player targetPlayer = gui.getPlayerAtSlot(slot);
        if (targetPlayer == null) {
            return;
        }
        
        // 防止点击自己的头颅
        if (targetPlayer.equals(clicker)) {
            clicker.sendMessage("§c你不能邀请自己进行对局！");
            clicker.playSound(clicker.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        String selectedWorld = settingsManager.getSelectedWorld(clicker);
        String selectedMode = settingsManager.getSelectedMode(clicker);
        int selectedRounds = settingsManager.getSelectedRounds(clicker);

        // 检查目标玩家是否在游戏中
        if (isPlayerInGame(targetPlayer)) {
            // 执行旁观指令
            clicker.performCommand("spectategame " + targetPlayer.getName());
        } else {
            // 执行邀请指令
            String command = String.format("inviteplayer %s %s %s %d", 
                    targetPlayer.getName(), selectedWorld, selectedMode, selectedRounds);
            clicker.performCommand(command);
        }

        // 关闭菜单
        clicker.closeInventory();
    }

    /**
     * 处理世界选择器点击
     */
    private void handleWorldSelectorClick(Player player, QuickMenuGUI gui, ClickType clickType) {
        boolean next = (clickType == ClickType.LEFT);
        gui.cycleWorld(next);
        
        // 播放音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 处理模式选择器点击
     */
    private void handleModeSelectorClick(Player player, QuickMenuGUI gui, ClickType clickType) {
        boolean next = (clickType == ClickType.LEFT);
        gui.cycleMode(next);
        
        // 播放音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 处理对局数选择器点击
     */
    private void handleRoundSelectorClick(Player player, QuickMenuGUI gui, ClickType clickType) {
        boolean next = (clickType == ClickType.LEFT);
        gui.cycleRounds(next);
        
        // 播放音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 处理上一页按钮点击
     */
    private void handlePreviousPageClick(Player player, QuickMenuGUI gui) {
        gui.previousPage();
        
        // 播放音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 处理下一页按钮点击
     */
    private void handleNextPageClick(Player player, QuickMenuGUI gui) {
        gui.nextPage();
        
        // 播放音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 检查玩家是否在游戏中
     */
    private boolean isPlayerInGame(Player player) {
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        return !player.getWorld().getName().equals(lobbyWorld);
    }

    /**
     * 玩家退出时清理缓存
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerSettingsManager().clearPlayerCache(event.getPlayer());
    }

    /**
     * 处理菜单关闭事件（可选，用于调试或额外处理）
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof QuickMenuGUI) {
            // 可以在这里添加菜单关闭时的额外逻辑
        }
    }
}
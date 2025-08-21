package org.tableBall.GUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.tableBall.TableBall;
import org.tableBall.Manager.PlayerSettingsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 快捷菜单GUI类
 * 实现6x9的箱子界面，包含玩家列表、世界选择、模式选择等功能
 */
public class QuickMenuGUI implements InventoryHolder {
    private final TableBall plugin;
    private final Player viewer;
    private final Inventory inventory;
    private int currentPage = 0;
    private static final int PLAYERS_PER_PAGE = 27; // 前三排共27个位置
    
    // 游戏模式列表
    private static final String[] GAME_MODES = {"8balls", "standard"};
    
    // 对局数选项
    private static final int[] ROUND_OPTIONS = {3, 5, 7, 9, 11};

    public QuickMenuGUI(TableBall plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, "§6台球快捷菜单");
        updateInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 更新整个菜单界面
     */
    public void updateInventory() {
        inventory.clear();
        
        // 填充玩家头颅（前三排）
        fillPlayerHeads();
        
        // 填充分割线（第四排）
        fillSeparatorLine();
        
        // 填充功能按钮（第五排）
        fillFunctionButtons();
        
        // 填充翻页按钮（第六排）
        fillNavigationButtons();
    }

    /**
     * 填充玩家头颅列表（前三排，位置0-26）
     */
    private void fillPlayerHeads() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        // 按字典序排列
        onlinePlayers.sort(Comparator.comparing(Player::getName));
        
        int startIndex = currentPage * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, onlinePlayers.size());
        
        for (int i = 0; i < PLAYERS_PER_PAGE; i++) {
            int slot = i; // 前三排位置0-26
            
            if (startIndex + i < endIndex) {
                Player targetPlayer = onlinePlayers.get(startIndex + i);
                ItemStack playerHead = createPlayerHead(targetPlayer);
                inventory.setItem(slot, playerHead);
            }
        }
    }

    /**
     * 创建玩家头颅物品
     */
    private ItemStack createPlayerHead(Player targetPlayer) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        
        if (meta != null) {
            meta.setOwningPlayer(targetPlayer);
            
            // 检查玩家是否在游戏中
            boolean inGame = isPlayerInGame(targetPlayer);
            
            // 设置显示名称，根据游戏状态显示不同颜色
            String displayName = inGame ? "§c" + targetPlayer.getName() : "§a" + targetPlayer.getName();
            meta.setDisplayName(displayName);
            
            // 设置lore
            List<String> lore = new ArrayList<>();
            if (inGame) {
                lore.add("§7点击旁观对局");
            } else {
                lore.add("§7点击邀请对战");
            }
            meta.setLore(lore);
            
            skull.setItemMeta(meta);
        }
        
        return skull;
    }

    /**
     * 检查玩家是否在游戏中
     */
    private boolean isPlayerInGame(Player player) {
        // 这里需要根据您的游戏状态管理逻辑来判断
        // 临时实现：检查玩家是否在lobby世界之外
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        return !player.getWorld().getName().equals(lobbyWorld);
    }

    /**
     * 填充分割线（第四排，位置27-35）
     */
    private void fillSeparatorLine() {
        // 第四排全部用绿色玻璃板填充
        for (int i = 27; i < 36; i++) {
            ItemStack glass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                glass.setItemMeta(meta);
            }
            
            // 位置(4,5)即索引31使用青色玻璃板
            if (i == 31) {
                glass = createStatusDisplay();
            }
            
            inventory.setItem(i, glass);
        }
    }

    /**
     * 创建状态显示板（青色玻璃板）
     */
    private ItemStack createStatusDisplay() {
        ItemStack glass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(" ");
            
            List<String> lore = new ArrayList<>();
            
            // 检查是否有玩家在对局中
            boolean hasPlayersInGame = hasPlayersInGame();
            String gameStatus = hasPlayersInGame ? "§c有" : "§a无";
            lore.add("当前是否有玩家在对局中：" + gameStatus);
            
            // 在线人数统计
            int onlineCount = Bukkit.getOnlinePlayers().size();
            lore.add("§f在线人数：" + onlineCount);
            
            meta.setLore(lore);
            glass.setItemMeta(meta);
        }
        
        return glass;
    }

    /**
     * 检查是否有玩家在游戏中
     */
    private boolean hasPlayersInGame() {
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        return Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> !p.getWorld().getName().equals(lobbyWorld));
    }

    /**
     * 填充功能按钮（第五排，位置36-44）
     */
    private void fillFunctionButtons() {
        PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        
        // (5,1) 位置36 - 世界选择
        inventory.setItem(36, createWorldSelector());
        
        // (5,5) 位置40 - 模式选择
        inventory.setItem(40, createModeSelector());
        
        // (5,9) 位置44 - 对局数选择
        inventory.setItem(44, createRoundSelector());
    }

    /**
     * 创建世界选择器
     */
    private ItemStack createWorldSelector() {
        ItemStack grass = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = grass.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6选择台球厅");
            
            List<String> lore = new ArrayList<>();
            String[] worlds = plugin.getWorldUtils().getBallsConfigWorlds();
            PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
            String selectedWorld = settingsManager.getSelectedWorld(viewer);
            
            for (String world : worlds) {
                String color = world.equals(selectedWorld) ? "§a" : "§7";
                lore.add(color + world);
            }
            
            lore.add("");
            lore.add("§e右键：向上循环");
            lore.add("§e左键：向下循环");
            
            meta.setLore(lore);
            grass.setItemMeta(meta);
        }
        
        return grass;
    }

    /**
     * 创建模式选择器
     */
    private ItemStack createModeSelector() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6模式选择");
            
            List<String> lore = new ArrayList<>();
            PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
            String selectedMode = settingsManager.getSelectedMode(viewer);
            
            for (String mode : GAME_MODES) {
                String color = mode.equals(selectedMode) ? "§a" : "§7";
                lore.add(color + mode);
            }
            
            lore.add("");
            lore.add("§e右键：向上循环");
            lore.add("§e左键：向下循环");
            
            meta.setLore(lore);
            book.setItemMeta(meta);
        }
        
        return book;
    }

    /**
     * 创建对局数选择器
     */
    private ItemStack createRoundSelector() {
        ItemStack sign = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = sign.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName("§6选择对局数");
            
            List<String> lore = new ArrayList<>();
            PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
            int selectedRounds = settingsManager.getSelectedRounds(viewer);
            
            for (int rounds : ROUND_OPTIONS) {
                String color = (rounds == selectedRounds) ? "§a" : "§7";
                lore.add(color + String.valueOf(rounds));
            }
            
            lore.add("");
            lore.add("§e右键：向上循环");
            lore.add("§e左键：向下循环");
            
            meta.setLore(lore);
            sign.setItemMeta(meta);
        }
        
        return sign;
    }

    /**
     * 填充翻页按钮（第六排，位置45-53）
     */
    private void fillNavigationButtons() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int totalPages = (int) Math.ceil((double) onlinePlayers.size() / PLAYERS_PER_PAGE);
        
        // (6,3) 位置47 - 上一页按钮
        inventory.setItem(47, createPreviousPageButton());
        
        // (6,7) 位置51 - 下一页按钮
        inventory.setItem(51, createNextPageButton(totalPages));
    }

    /**
     * 创建上一页按钮
     */
    private ItemStack createPreviousPageButton() {
        Material material = (currentPage > 0) ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        
        if (meta != null) {
            String displayName = (currentPage > 0) ? "§a翻到上一页" : "§c已经是第一页";
            meta.setDisplayName(displayName);
            glass.setItemMeta(meta);
        }
        
        return glass;
    }

    /**
     * 创建下一页按钮
     */
    private ItemStack createNextPageButton(int totalPages) {
        boolean hasNextPage = (currentPage < totalPages - 1);
        Material material = hasNextPage ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        
        if (meta != null) {
            String displayName = hasNextPage ? "§a翻到下一页" : "§c已经是最后一页";
            meta.setDisplayName(displayName);
            glass.setItemMeta(meta);
        }
        
        return glass;
    }

    /**
     * 翻到下一页
     */
    public void nextPage() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int totalPages = (int) Math.ceil((double) onlinePlayers.size() / PLAYERS_PER_PAGE);
        
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateInventory();
        }
    }

    /**
     * 翻到上一页
     */
    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateInventory();
        }
    }

    /**
     * 循环选择世界
     */
    public void cycleWorld(boolean next) {
        PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        String[] worlds = plugin.getWorldUtils().getBallsConfigWorlds();
        String currentWorld = settingsManager.getSelectedWorld(viewer);
        
        if (worlds.length == 0) return;
        
        int currentIndex = -1;
        for (int i = 0; i < worlds.length; i++) {
            if (worlds[i].equals(currentWorld)) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex == -1) currentIndex = 0;
        
        if (next) {
            currentIndex = (currentIndex + 1) % worlds.length;
        } else {
            currentIndex = (currentIndex - 1 + worlds.length) % worlds.length;
        }
        
        settingsManager.setSelectedWorld(viewer, worlds[currentIndex]);
        updateInventory();
    }

    /**
     * 循环选择游戏模式
     */
    public void cycleMode(boolean next) {
        PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        String currentMode = settingsManager.getSelectedMode(viewer);
        
        int currentIndex = -1;
        for (int i = 0; i < GAME_MODES.length; i++) {
            if (GAME_MODES[i].equals(currentMode)) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex == -1) currentIndex = 0;
        
        if (next) {
            currentIndex = (currentIndex + 1) % GAME_MODES.length;
        } else {
            currentIndex = (currentIndex - 1 + GAME_MODES.length) % GAME_MODES.length;
        }
        
        settingsManager.setSelectedMode(viewer, GAME_MODES[currentIndex]);
        updateInventory();
    }

    /**
     * 循环选择对局数
     */
    public void cycleRounds(boolean next) {
        PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        int currentRounds = settingsManager.getSelectedRounds(viewer);
        
        int currentIndex = -1;
        for (int i = 0; i < ROUND_OPTIONS.length; i++) {
            if (ROUND_OPTIONS[i] == currentRounds) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex == -1) currentIndex = 0;
        
        if (next) {
            currentIndex = (currentIndex + 1) % ROUND_OPTIONS.length;
        } else {
            currentIndex = (currentIndex - 1 + ROUND_OPTIONS.length) % ROUND_OPTIONS.length;
        }
        
        settingsManager.setSelectedRounds(viewer, ROUND_OPTIONS[currentIndex]);
        updateInventory();
    }

    /**
     * 获取指定位置的玩家
     */
    public Player getPlayerAtSlot(int slot) {
        if (slot < 0 || slot >= PLAYERS_PER_PAGE) return null;
        
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.sort(Comparator.comparing(Player::getName));
        
        int playerIndex = currentPage * PLAYERS_PER_PAGE + slot;
        if (playerIndex < onlinePlayers.size()) {
            return onlinePlayers.get(playerIndex);
        }
        
        return null;
    }
}
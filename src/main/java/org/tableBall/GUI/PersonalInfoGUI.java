package org.tableBall.GUI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.tableBall.Manager.PlayerDataManager;
import org.tableBall.TableBall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PersonalInfoGUI {
    private final TableBall plugin;
    private final PlayerDataManager dataManager;

    public PersonalInfoGUI(TableBall plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getPlayerDataManager();
    }

    /**
     * 打开个人信息GUI
     * @param player 玩家
     */
    public void openPersonalInfoGUI(Player player) {
        // 创建3*9的箱子GUI
        Inventory gui = Bukkit.createInventory(null, 27, "§b§l个人信息");

        // 获取玩家统计数据
        Map<String, PlayerDataManager.PlayerStats> statsMap = dataManager.getPlayerStats(player);

        if (statsMap.isEmpty()) {
            // 如果没有数据，显示默认信息
            createDefaultItems(gui);
        } else {
            // 创建统计信息物品
            createStatsItems(gui, statsMap);
        }

        // 打开GUI
        player.openInventory(gui);
    }

    /**
     * 创建默认物品（无统计数据时）
     */
    private void createDefaultItems(Inventory gui) {
        // (2,5) 总次数 - 下界之星
        ItemStack totalItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta totalMeta = totalItem.getItemMeta();
        if (totalMeta != null) {
            totalMeta.setDisplayName("§e§l§n总次数");
            List<String> totalLore = new ArrayList<>();
            totalLore.add("§8▪ §7暂无游戏记录");
            totalLore.add("");
            totalLore.add("§b开始您的第一场台球游戏吧！");
            totalMeta.setLore(totalLore);
            totalItem.setItemMeta(totalMeta);
        }
        gui.setItem(13, totalItem); // (2,5) = slot 13

        // (2,3) 获胜次数 - 绿宝石块
        ItemStack winsItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta winsMeta = winsItem.getItemMeta();
        if (winsMeta != null) {
            winsMeta.setDisplayName("§a§l§n获胜次数");
            List<String> winsLore = new ArrayList<>();
            winsLore.add("§8▪ §7暂无获胜记录");
            winsLore.add("");
            winsLore.add("§a努力获得您的第一场胜利！");
            winsMeta.setLore(winsLore);
            winsItem.setItemMeta(winsMeta);
        }
        gui.setItem(11, winsItem); // (2,3) = slot 11

        // (2,7) 失败次数 - 红石块
        ItemStack lossesItem = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta lossesMeta = lossesItem.getItemMeta();
        if (lossesMeta != null) {
            lossesMeta.setDisplayName("§c§l§n失败次数");
            List<String> lossesLore = new ArrayList<>();
            lossesLore.add("§8▪ §7暂无失败记录");
            lossesLore.add("");
            lossesLore.add("§7失败是成功之母！");
            lossesMeta.setLore(lossesLore);
            lossesItem.setItemMeta(lossesMeta);
        }
        gui.setItem(15, lossesItem); // (2,7) = slot 15
    }

    /**
     * 创建统计信息物品
     */
    private void createStatsItems(Inventory gui, Map<String, PlayerDataManager.PlayerStats> statsMap) {
        // (2,5) 总次数 - 下界之星
        ItemStack totalItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta totalMeta = totalItem.getItemMeta();
        if (totalMeta != null) {
            totalMeta.setDisplayName("§e§l§n总次数");
            List<String> totalLore = new ArrayList<>();
            totalLore.add("§8▸ §7您的游戏统计：");
            totalLore.add("");
            
            for (Map.Entry<String, PlayerDataManager.PlayerStats> entry : statsMap.entrySet()) {
                String gameMode = entry.getKey();
                PlayerDataManager.PlayerStats stats = entry.getValue();
                totalLore.add("§6§l▪ " + gameMode);
                totalLore.add("  §e" + stats.getTotalGames() + " §7次游戏");
                if (!isLastEntry(entry, statsMap)) {
                    totalLore.add(""); // 空行分隔
                }
            }
            
            totalMeta.setLore(totalLore);
            totalItem.setItemMeta(totalMeta);
        }
        gui.setItem(13, totalItem);

        // (2,3) 获胜次数 - 绿宝石块
        ItemStack winsItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta winsMeta = winsItem.getItemMeta();
        if (winsMeta != null) {
            winsMeta.setDisplayName("§a§l§n获胜次数");
            List<String> winsLore = new ArrayList<>();
            winsLore.add("§8▸ §7您的胜利记录：");
            winsLore.add("");
            
            for (Map.Entry<String, PlayerDataManager.PlayerStats> entry : statsMap.entrySet()) {
                String gameMode = entry.getKey();
                PlayerDataManager.PlayerStats stats = entry.getValue();
                int totalGames = stats.getTotalGames();
                int wins = stats.getWins();
                double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;
                
                winsLore.add("§6§l▪ " + gameMode);
                winsLore.add("  §a" + wins + " §7次胜利");
                winsLore.add("  §b胜率: §f" + String.format("%.1f%%", winRate));
                if (!isLastEntry(entry, statsMap)) {
                    winsLore.add(""); // 空行分隔
                }
            }
            
            winsMeta.setLore(winsLore);
            winsItem.setItemMeta(winsMeta);
        }
        gui.setItem(11, winsItem);

        // (2,7) 失败次数 - 红石块
        ItemStack lossesItem = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta lossesMeta = lossesItem.getItemMeta();
        if (lossesMeta != null) {
            lossesMeta.setDisplayName("§c§l§n失败次数");
            List<String> lossesLore = new ArrayList<>();
            lossesLore.add("§8▸ §7您的失败统计：");
            lossesLore.add("");
            
            for (Map.Entry<String, PlayerDataManager.PlayerStats> entry : statsMap.entrySet()) {
                String gameMode = entry.getKey();
                PlayerDataManager.PlayerStats stats = entry.getValue();
                lossesLore.add("§6§l▪ " + gameMode);
                lossesLore.add("  §c弃权而输: §f" + stats.getForfeitLosses() + " §7次");
                lossesLore.add("  §c对方战胜: §f" + stats.getOpponentWins() + " §7次");
                int totalLosses = stats.getForfeitLosses() + stats.getOpponentWins();
                lossesLore.add("  §7总失败: §f" + totalLosses + " §7次");
                if (!isLastEntry(entry, statsMap)) {
                    lossesLore.add(""); // 空行分隔
                }
            }
            
            lossesMeta.setLore(lossesLore);
            lossesItem.setItemMeta(lossesMeta);
        }
        gui.setItem(15, lossesItem);

        // 添加装饰性物品
        addDecorativeItems(gui);
    }

    /**
     * 添加装饰性物品
     */
    private void addDecorativeItems(Inventory gui) {
        // 添加彩色玻璃板作为装饰边框
        ItemStack lightBlueGlass = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = lightBlueGlass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" "); // 空白名称
            lightBlueGlass.setItemMeta(glassMeta);
        }

        // 填充边框位置
        int[] borderSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 19, 20, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, lightBlueGlass);
        }

        // 顶部装饰 - 台球相关物品
        ItemStack poolBall = new ItemStack(Material.ENDER_PEARL);
        ItemMeta ballMeta = poolBall.getItemMeta();
        if (ballMeta != null) {
            ballMeta.setDisplayName("§f§l§n台球统计中心");
            List<String> ballLore = new ArrayList<>();
            ballLore.add("§8▸ §b欢迎来到台球厅！");
            ballLore.add("");
            ballLore.add("§7这里显示您在台球厅的");
            ballLore.add("§7所有游戏统计信息");
            ballLore.add("");
            ballLore.add("§e继续努力，提升您的技术！");
            ballMeta.setLore(ballLore);
            poolBall.setItemMeta(ballMeta);
        }
        gui.setItem(4, poolBall); // 顶部中央

        // 添加左右装饰
        ItemStack leftDecor = new ItemStack(Material.STICK);
        ItemMeta leftMeta = leftDecor.getItemMeta();
        if (leftMeta != null) {
            leftMeta.setDisplayName("§6§l球杆");
            List<String> leftLore = new ArrayList<>();
            leftLore.add("§7台球必备工具");
            leftLore.add("§7精准击球的关键！");
            leftMeta.setLore(leftLore);
            leftDecor.setItemMeta(leftMeta);
        }
        gui.setItem(3, leftDecor); // 左侧装饰

        ItemStack rightDecor = new ItemStack(Material.BOWL);
        ItemMeta rightMeta = rightDecor.getItemMeta();
        if (rightMeta != null) {
            rightMeta.setDisplayName("§3§l球袋");
            List<String> rightLore = new ArrayList<>();
            rightLore.add("§7进球的终点");
            rightLore.add("§7每一球都很重要！");
            rightMeta.setLore(rightLore);
            rightDecor.setItemMeta(rightMeta);
        }
        gui.setItem(5, rightDecor); // 右侧装饰
    }

    /**
     * 检查是否是最后一个条目（用于添加分隔符）
     */
    private boolean isLastEntry(Map.Entry<String, PlayerDataManager.PlayerStats> entry, 
                               Map<String, PlayerDataManager.PlayerStats> statsMap) {
        List<Map.Entry<String, PlayerDataManager.PlayerStats>> entryList = new ArrayList<>(statsMap.entrySet());
        return entryList.indexOf(entry) == entryList.size() - 1;
    }

    /**
     * 检查物品是否是个人信息GUI的物品
     */
    public static boolean isPersonalInfoItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        
        String displayName = item.getItemMeta().getDisplayName();
        return displayName.contains("总次数") || 
               displayName.contains("获胜次数") || 
               displayName.contains("失败次数") ||
               displayName.contains("台球统计中心") ||
               displayName.contains("球杆") ||
               displayName.contains("球袋");
    }

    /**
     * 检查GUI标题是否是个人信息GUI
     */
    public static boolean isPersonalInfoGUI(String title) {
        return "§b§l个人信息".equals(title);
    }
}
package org.tableBall.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class InventoryUtils {
    private final JavaPlugin plugin;

    public InventoryUtils(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 安全设置玩家物品栏物品
     * @return 是否设置成功
     */
    public boolean safeSetItem(Player player, int slot, ItemStack item) {
        if (player == null || !player.isOnline()) return false;
        if (slot < 0 || slot >= 41) return false;

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.getInventory().setItem(slot, item);
            player.updateInventory();
        });
        return true;
    }

    /**
     * 从配置文件加载并设置玩家物品栏
     * @param player 目标玩家
     */
    public void loadAndSetInventoryFromConfig(Player player) {
        // 获取配置文件中Inventory部分
        ConfigurationSection inventorySection = plugin.getConfig().getConfigurationSection("Inventory");
        if (inventorySection == null) return;

        // 遍历每个配置项
        for (String key : inventorySection.getKeys(false)) {
            ConfigurationSection itemSection = inventorySection.getConfigurationSection(key);
            if (itemSection == null) continue;

            // 读取基础属性
            int slot = itemSection.getInt("slot");
            Material material = Material.matchMaterial(itemSection.getString("material"));
            if (material == null) continue;

            // 创建基础物品
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            // 设置显示名称
            if (itemSection.contains("display-name")) {
                meta.setDisplayName(itemSection.getString("display-name"));
            }

            // 设置附魔
            if (itemSection.contains("nbt.Enchantments")) {
                ConfigurationSection enchantsSection = itemSection.getConfigurationSection("nbt.Enchantments");
                for (String enchantKey : enchantsSection.getKeys(false)) {
                    Enchantment enchant = Enchantment.getByName(enchantKey.toUpperCase());
                    if (enchant != null) {
                        meta.addEnchant(enchant, enchantsSection.getInt(enchantKey), true);
                    }
                }
            }

            // 设置HideFlags（隐藏属性）
            if (itemSection.contains("nbt.HideFlags")) {
                int hideFlags = itemSection.getInt("nbt.HideFlags");
                // 根据HideFlags的值添加对应的ItemFlag
                if ((hideFlags & 1) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                if ((hideFlags & 2) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                if ((hideFlags & 4) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
                if ((hideFlags & 8) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_DESTROYS);
                if ((hideFlags & 16) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_PLACED_ON);
                if ((hideFlags & 32) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_POTION_EFFECTS);
                if ((hideFlags & 64) != 0) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_DYE);
            }

            // 应用Meta到物品
            item.setItemMeta(meta);

            // 设置自定义NBT（使用PersistentDataContainer）
            if (itemSection.contains("nbt")) {
                applyCustomNBT(item, itemSection.getConfigurationSection("nbt"));
            }

            // 安全设置物品到指定槽位
            safeSetItem(player, slot, item);
        }
    }

    /**
     * 应用自定义NBT数据到物品
     * @param item 目标物品
     * @param nbtSection NBT配置部分
     */
    private void applyCustomNBT(ItemStack item, ConfigurationSection nbtSection) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 遍历所有NBT键值对
        for (String key : nbtSection.getKeys(false)) {
            // 跳过已经处理的特殊键（如Enchantments和HideFlags）
            if (key.equals("Enchantments") || key.equals("HideFlags")) continue;

            Object value = nbtSection.get(key);
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);

            // 根据类型设置NBT值
            if (value instanceof String) {
                pdc.set(namespacedKey, PersistentDataType.STRING, (String) value);
            } else if (value instanceof Integer) {
                pdc.set(namespacedKey, PersistentDataType.INTEGER, (Integer) value);
            } else if (value instanceof Double) {
                pdc.set(namespacedKey, PersistentDataType.DOUBLE, (Double) value);
            } else if (value instanceof Boolean) {
                pdc.set(namespacedKey, PersistentDataType.BYTE, (Boolean) value ? (byte) 1 : (byte) 0);
            }
            // 可以继续添加其他类型的支持
        }

        item.setItemMeta(meta);
    }

    /**
     * 生成只能放在蓝冰上的母球船物品
     * 注意：CanPlaceOn NBT需在监听器中判断，Bukkit原生API不支持直接写入
     */
    public static ItemStack getWhiteBallItem() {
        ItemStack boat = new ItemStack(Material.BIRCH_BOAT);
        ItemMeta meta = boat.getItemMeta();
        meta.setDisplayName("§f母球");
        // 通过PersistentDataContainer写入自定义标记
        meta.getPersistentDataContainer().set(new NamespacedKey("tableball", "white_ball"), PersistentDataType.BYTE, (byte) 1);
        boat.setItemMeta(meta);
        // 由于Bukkit原生API不支持CanPlaceOn，需要监听放置事件时判断
        return boat;
    }
}
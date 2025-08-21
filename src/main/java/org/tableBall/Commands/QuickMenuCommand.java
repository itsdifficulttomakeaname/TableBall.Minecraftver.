package org.tableBall.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.tableBall.GUI.QuickMenuGUI;
import org.tableBall.TableBall;

/**
 * 快捷菜单命令处理器
 * 处理打开快捷菜单的命令
 */
public class QuickMenuCommand implements CommandExecutor {
    private final TableBall plugin;

    public QuickMenuCommand(TableBall plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        Player player = (Player) sender;

        // 检查玩家是否在大厅世界
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        if (!player.getWorld().getName().equals(lobbyWorld)) {
            player.sendMessage("§c只能在大厅中使用快捷菜单！");
            return true;
        }

        // 创建并打开快捷菜单
        QuickMenuGUI gui = new QuickMenuGUI(plugin, player);
        player.openInventory(gui.getInventory());
        
        // 播放打开音效（可选）
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);

        return true;
    }
}
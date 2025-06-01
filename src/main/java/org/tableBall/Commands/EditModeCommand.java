package org.tableBall.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.ChatColor;
import org.tableBall.TableBall;

import java.util.ArrayList;
import java.util.List;

public class EditModeCommand implements CommandExecutor, TabCompleter {
    private final TableBall plugin;
    private static boolean editMode = false;

    public EditModeCommand(TableBall plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tableball.editmode")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "用法: /editmode <enable/disable/info>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable":
                editMode = true;
                sender.sendMessage(ChatColor.GREEN + "已启用编辑模式");
                break;
            case "disable":
                editMode = false;
                sender.sendMessage(ChatColor.RED + "已禁用编辑模式");
                break;
            case "info":
                String status = editMode ? "enable" : "disable";
                ChatColor color = editMode ? ChatColor.GREEN : ChatColor.RED;
                sender.sendMessage(color + "当前编辑状态: " + status);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "无效的参数！使用 enable/disable/info");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("enable");
            completions.add("disable");
            completions.add("info");
        }
        return completions;
    }

    public static boolean isEditMode() {
        return editMode;
    }
} 
package org.tableBall.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.tableBall.Manager.ScoreBoardManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScoreBoardCommand implements CommandExecutor, TabCompleter {
    private final ScoreBoardManager scoreBoardManager;

    public ScoreBoardCommand(ScoreBoardManager scoreBoardManager) {
        this.scoreBoardManager = scoreBoardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("§c用法: /scb <e/d>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "e":
                scoreBoardManager.setEnabled(true);
                sender.sendMessage("§a计分板已启用");
                break;
            case "d":
                scoreBoardManager.setEnabled(false);
                sender.sendMessage("§c计分板已禁用");
                break;
            default:
                sender.sendMessage("§c无效的参数！使用 e 启用或 d 禁用");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("e", "d");
        }
        return new ArrayList<>();
    }
} 
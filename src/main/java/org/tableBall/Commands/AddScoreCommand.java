package org.tableBall.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tableBall.Manager.ScoreBoardManager;

import java.util.ArrayList;
import java.util.List;

public class AddScoreCommand implements CommandExecutor, TabCompleter {
    private final ScoreBoardManager scoreBoardManager;

    public AddScoreCommand(ScoreBoardManager scoreBoardManager) {
        this.scoreBoardManager = scoreBoardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§c用法: /addscore <分数>");
            return true;
        }

        Player player = (Player) sender;
        String gameType = scoreBoardManager.getGameType(player);
        
        if (!"Custom".equals(gameType)) {
            sender.sendMessage("§c只有在自定义模式下才能使用此命令！");
            return true;
        }

        try {
            int points = Integer.parseInt(args[0]);
            scoreBoardManager.addScore(player, points);
            sender.sendMessage("§a成功添加 " + points + " 分！");
        } catch (NumberFormatException e) {
            sender.sendMessage("§c无效的分数！请输入一个数字。");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
} 
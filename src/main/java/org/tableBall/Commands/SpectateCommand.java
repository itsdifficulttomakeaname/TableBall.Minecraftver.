package org.tableBall.Commands;


import cn.jason31416.planetlib.PlanetLib;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;

import java.util.ArrayList;
import java.util.List;

public class SpectateCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(Start.currentGame==null) sender.sendMessage("没有进行中游戏!");
        if(!(sender instanceof Player pl)) return true;
        pl.teleport(Start.currentGame.getSpawnLocation());
        PlanetLib.getScheduler().runNextTick(t-> pl.setGameMode(GameMode.SPECTATOR));
        sender.sendMessage("成功开始观战");
        return true;
    }
}

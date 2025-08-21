package org.tableBall.Commands;

import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Game.GameState;
import org.tableBall.TableBall;

import java.util.Set;

import static org.tableBall.TableBall.instance;

public class TeleportToWhiteBallCommand implements CommandExecutor {
    private final TableBall plugin;
    public TeleportToWhiteBallCommand(Plugin plugin){
        this.plugin = instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(!(sender instanceof Player p)){
            sender.sendMessage("§c该指令只能玩家使用！");
            return true;
        }

        if(!GameState.getIsInGame(p)){
            p.sendMessage("§c该指令只能在游戏中使用");
            return true;
        }

        String worldName = p.getWorld().getName();
        Set<DisplayBall> worldBalls = instance.getInGame().getBalls(worldName);
        Location loc = null;

        for (DisplayBall ball : worldBalls){
            if(ball.isMotherBall){
                loc = ball.location;
                break;
            }
        }

        if(loc != null){
            p.teleport(loc.clone().add(0,1,0));
            p.sendMessage("§a已经把你传送到母球旁！");
        }else{
            p.sendMessage("§c错误：没有找到母球，将你随机传送到场上一个球的位置！");
            for(DisplayBall ball : worldBalls){
                p.teleport(ball.location.clone().add(0, 1, 0));
                break;
            }
        }

        return true;
    }
}

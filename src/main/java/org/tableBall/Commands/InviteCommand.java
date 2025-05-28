package org.tableBall.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.tableBall.Game.InGame;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class InviteCommand implements CommandExecutor, TabCompleter {
    private final TableBall plugin;
    private final InGame inGame;
    private final Start start;

    // 邀请缓存，key为被邀请玩家UUID，value为邀请数据
    private static final Map<UUID, InviteData> inviteMap = new HashMap<>();
    private static class InviteData {
        public final String worldName;
        public final String gameType;
        public final Player inviter;
        public final long expireTime;
        public InviteData(String worldName, String gameType, Player inviter, long expireTime) {
            this.worldName = worldName;
            this.gameType = gameType;
            this.inviter = inviter;
            this.expireTime = expireTime;
        }
    }

    public InviteCommand(TableBall plugin) {
        this.plugin = plugin;
        this.inGame = plugin.getInGame();
        this.start = new Start(plugin, plugin.getWorldUtils(), plugin.getInGame());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage("§c用法: /inviteplayer <玩家> <世界> <类型>");
            return true;
        }

        Player player = (Player) sender;
        String targetPlayerName = args[0];
        String worldName = args[1];
        String gameType = args[2];

        // 检查游戏类型
        if (!gameType.equals("Standard") && !gameType.equals("Custom")) {
            sender.sendMessage("§c无效的游戏类型！请使用 Standard 或 Custom");
            return true;
        }

        // 检查目标玩家
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c找不到玩家 " + targetPlayerName);
            return true;
        }

        // 检查世界是否存在
        if (!plugin.getWorldUtils().isWorldExists(worldName)) {
            sender.sendMessage("§c" + worldName + " 不是一个有效的世界");
            return true;
        }

        // 检查世界是否在使用中
        if (inGame.isWorldInUse(worldName)) {
            sender.sendMessage("§c世界 " + worldName + " 正在使用中！");
            return true;
        }

        // 检查玩家是否在游戏中
        if (inGame.isPlayerInGame(player) || inGame.isPlayerInGame(targetPlayer)) {
            sender.sendMessage("§c玩家已经在游戏中！");
            return true;
        }

        // 检查游戏配置
        if (!inGame.checkGameConfig(worldName)) {
            sender.sendMessage("§c世界 " + worldName + " 的游戏配置不完整！");
            return true;
        }

        // 发送邀请
        long expire = System.currentTimeMillis() + 60_000L;
        inviteMap.put(targetPlayer.getUniqueId(), new InviteData(worldName, gameType, player, expire));
        Component msg = Component.text("玩家 " + player.getName() + " 向你发出了申请: " + worldName + " ")
                .append(Component.text("[点击接受]")
                        .clickEvent(ClickEvent.runCommand("/acceptinvite"))
                        .hoverEvent(HoverEvent.showText(Component.text("点击同意，1分钟内有效"))));
        targetPlayer.sendMessage(msg);
        sender.sendMessage("§a已向 " + targetPlayer.getName() + " 发送申请");
        // 定时移除邀请
        Bukkit.getScheduler().runTaskLater(plugin, () -> inviteMap.remove(targetPlayer.getUniqueId()), 20*60);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 玩家名称补全
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2) {
            // 世界名称补全（动态读取balls.yml）
            List<String> worldNames = inGame.getAllWorldNamesFromBallsConfig();
            for (String name : worldNames) {
                if (!name.equalsIgnoreCase("world")) {
                    completions.add(name);
                }
            }
        } else if (args.length == 3) {
            // 游戏类型补全
            completions.add("Standard");
            completions.add("Custom");
        }

        return completions;
    }

    // 新增命令注册和处理
    // 在插件主类注册/acceptinvite命令，并在此处理
    public static boolean handleAcceptInvite(Player player, TableBall plugin) {
        InviteData data = inviteMap.get(player.getUniqueId());
        if (data == null || System.currentTimeMillis() > data.expireTime) {
            player.sendMessage("§c邀请已过期或无效");
            return true;
        }
        // 检查世界和玩家状态
        InGame inGame = plugin.getInGame();
        if (inGame.isWorldInUse(data.worldName)) {
            player.sendMessage("§c世界 " + data.worldName + " 正在被使用");
            return true;
        }
        if (inGame.isPlayerInGame(player) || inGame.isPlayerInGame(data.inviter)) {
            player.sendMessage("§c你或邀请者已在游戏中");
            return true;
        }
        // 开始游戏
        List<Player> players = new ArrayList<>();
        players.add(data.inviter);
        players.add(player);
        player.setAllowFlight(true);
        new Start(plugin, plugin.getWorldUtils(), plugin.getInGame()).startGame(data.worldName, players, data.gameType); // 当前暂未支持同时多把对局
        inviteMap.remove(player.getUniqueId());
        return true;
    }
}

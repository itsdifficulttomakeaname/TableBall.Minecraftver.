package org.tableBall.Commands;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
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
        public final int rounds;
        public InviteData(String worldName, String gameType, Player inviter, long expireTime, int rounds) {
            this.worldName = worldName;
            this.gameType = gameType;
            this.inviter = inviter;
            this.expireTime = expireTime;
            this.rounds = rounds;
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

        if (args.length != 4) {
            sender.sendMessage("§c用法: /inviteplayer <玩家> <世界> <类型> <对局数>");
            return true;
        }

        Player player = (Player) sender;
        String targetPlayerName = args[0];
        String worldName = args[1];
        String gameType = args[2];
        String roundsStr = args[3];

        // 检查游戏类型
        if (!gameType.equals("standard") && !gameType.equals("8balls")) {
            sender.sendMessage("§c无效的游戏类型！请使用 standard 或 8balls");
            return true;
        }

        // 检查对局数
        int rounds;
        try {
            rounds = Integer.parseInt(roundsStr);
            if (rounds % 2 == 0) {
                sender.sendMessage("§c对局数无效！只能是奇数");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c对局数必须是数字！");
            return true;
        }

        // 检查目标玩家
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c找不到玩家 " + targetPlayerName);
            return true;
        }

        // 防止玩家邀请自己
        if (targetPlayer.equals(player)) {
            sender.sendMessage("§c你不能邀请自己进行对局！");
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
        inviteMap.put(targetPlayer.getUniqueId(), new InviteData(worldName, gameType, player, expire, rounds));
        
        // 创建美化的邀请消息
        Component msg = Component.text("玩家 ")
                .append(Component.text(player.getName())
                        .color(net.kyori.adventure.text.format.TextColor.color(0x00FF00))
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(" 向你发出了台球对局邀请！"))
                .append(Component.newline())
                .append(Component.text("世界: ")
                        .append(Component.text(worldName)
                                .color(net.kyori.adventure.text.format.TextColor.color(0x00FF00))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)))
                .append(Component.text(" | 模式: ")
                        .append(Component.text(gameType)
                                .color(net.kyori.adventure.text.format.TextColor.color(0x00FF00))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)))
                .append(Component.text(" | 对局数: ")
                        .append(Component.text(String.valueOf(rounds))
                                .color(net.kyori.adventure.text.format.TextColor.color(0x00FF00))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)))
                .append(Component.newline())
                .append(Component.text("[点击接受]")
                        .color(net.kyori.adventure.text.format.TextColor.color(0x00FFFF))
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/acceptinvite " + player.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("点击接受邀请，1分钟内有效"))));
        
        targetPlayer.sendMessage(msg);
        sender.sendMessage("§a已向 " + targetPlayer.getName() + " 发送台球对局邀请！");
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
                // 排除主世界，只显示台球世界
                String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
                if (!name.equalsIgnoreCase(lobbyWorld)) {
                    completions.add(name);
                }
            }
        } else if (args.length == 3) {
            // 游戏类型补全
            completions.add("standard");
            completions.add("8balls");
        } else if (args.length == 4) {
            // 对局数补全
            completions.add("3");
            completions.add("5");
        }

        return completions;
    }

    // 新增命令注册和处理
    // 在插件主类注册/acceptinvite命令，并在此处理
    public static boolean handleAcceptInvite(Player player, TableBall plugin, String inviterName) {
        // 检查是否提供了邀请者名称
        if (inviterName == null || inviterName.isEmpty()) {
            player.sendMessage("§c用法: /acceptinvite <玩家名>");
            player.sendMessage("§e请指定要接受来自哪个玩家的邀请");
            return true;
        }
        
        // 检查邀请者是否在线
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null) {
            player.sendMessage("§c玩家 " + inviterName + " 不在线或不存在！");
            return true;
        }
        
        // 查找对应的邀请数据
        InviteData data = inviteMap.get(player.getUniqueId());
        if (data == null) {
            player.sendMessage("§c你没有收到来自任何玩家的邀请！");
            return true;
        }
        
        // 检查邀请是否来自指定玩家
        if (!data.inviter.equals(inviter)) {
            player.sendMessage("§c你没有收到来自 " + inviterName + " 的邀请！");
            player.sendMessage("§e你当前收到的邀请来自: §a" + data.inviter.getName());
            return true;
        }
        
        // 检查邀请是否过期
        if (System.currentTimeMillis() > data.expireTime) {
            player.sendMessage("§c来自 " + inviterName + " 的邀请已过期！");
            inviteMap.remove(player.getUniqueId());
            return true;
        }
        
        // 检查世界和玩家状态
        InGame inGame = plugin.getInGame();
        if (inGame.isWorldInUse(data.worldName)) {
            player.sendMessage("§c世界 " + data.worldName + " 正在被使用，无法开始游戏！");
            return true;
        }
        
        if (inGame.isPlayerInGame(player)) {
            player.sendMessage("§c你已经在游戏中，无法接受邀请！");
            return true;
        }
        
        if (inGame.isPlayerInGame(data.inviter)) {
            player.sendMessage("§c邀请者 " + data.inviter.getName() + " 已经在其他游戏中！");
            return true;
        }
        
        // 检查邀请者是否仍在线
        if (!data.inviter.isOnline()) {
            player.sendMessage("§c邀请者 " + data.inviter.getName() + " 已离线！");
            inviteMap.remove(player.getUniqueId());
            return true;
        }
        
        // 开始游戏
        try {
            List<Player> players = new ArrayList<>();
            players.add(data.inviter);
            players.add(player);
            player.setAllowFlight(true);
            
            // 通知双方玩家
            player.sendMessage("§a成功接受了来自 " + data.inviter.getName() + " 的邀请！正在开始游戏...");
            data.inviter.sendMessage("§a" + player.getName() + " 接受了你的邀请！正在开始游戏...");
            
            new Start(plugin, plugin.getWorldUtils(), plugin.getInGame()).startGame(data.worldName, players, data.gameType, data.rounds);

            // 发送音效
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            data.inviter.playSound(data.inviter.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

            inviteMap.remove(player.getUniqueId());
        } catch (Exception e) {
            player.sendMessage("§c启动游戏时发生错误，请联系管理员！");
            data.inviter.sendMessage("§c启动游戏时发生错误，请联系管理员！");
            plugin.getLogger().severe("启动游戏时发生错误: " + e.getMessage());
            e.printStackTrace();
            inviteMap.remove(player.getUniqueId());
        }
        
        return true;
    }
}

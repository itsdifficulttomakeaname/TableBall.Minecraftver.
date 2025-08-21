package org.tableBall.Listeners;

import cn.jason31416.planetlib.hook.NbtHook;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.tableBall.Game.InGame;
import org.tableBall.Game.Start;
import org.tableBall.TableBall;
import org.bukkit.event.block.Action;
import org.tableBall.Entity.DisplayBall;
import org.tableBall.Commands.EditModeCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.tableBall.Manager.PlayerDataManager;
import org.tableBall.Utils.InventoryUtils;

import java.util.*;

import static java.lang.Math.max;
import static org.tableBall.TableBall.instance;

public class EntityEventListener implements Listener {
    private static TableBall plugin;
    private final InGame inGame;
    private final Start start;
    private final Map<String, BukkitTask> movementCheckTasks;
    private final Map<String, BukkitTask> ballInCheckTasks;
    public static final Map<String, Boolean> hitBall = new HashMap<>();
    public static final Map<Vehicle, Vector> velocities = new HashMap<>();
    private BukkitRunnable collisionTask;

    public static boolean hasStrike = false;

    public EntityEventListener(TableBall plugin, InGame inGame) {
        EntityEventListener.plugin = plugin;
        this.inGame = inGame;
        this.start = new Start(plugin, plugin.getWorldUtils(), inGame);
        this.movementCheckTasks = new HashMap<>();
        this.ballInCheckTasks = new HashMap<>();
    }

    private void startCollisionTask() {
        collisionTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkCollisions();
            }
        };
        collisionTask.runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    public static void checkCollisions() {
        for (DisplayBall ball1 : DisplayBall.displayBalls) {
            for (DisplayBall ball2 : DisplayBall.displayBalls) {
                if (ball1 != ball2 && ball1.isColliding(ball2)) {
                    handleBallCollision(ball1, ball2);
                }
            }
        }
    }

    private static void handleBallCollision(DisplayBall ball1, DisplayBall ball2) {
        if (ball1.isFalling || ball2.isFalling) return;

        // 只在8balls模式下检查犯规
        String worldName = ball1.getWorld();
        if (worldName != null && plugin != null) {
            String gameType = plugin.getInGame().getGameType(worldName);
            if ("8balls".equals(gameType)) {
                check8ballsFoul(ball1, ball2);
            }
        }

        Vector deltaPos = ball1.location.toVector().subtract(ball2.location.toVector());
        Vector normal = deltaPos.normalize();
        Vector relativeVel = ball1.velocity.clone().subtract(ball2.velocity.clone()); // 相对速度
        double impulse = relativeVel.dot(normal);

        // 更新速度 (完全弹性碰撞)
        ball1.velocity.subtract(normal.clone().multiply(impulse));
        ball2.velocity.add(normal.clone().multiply(impulse));

        // 防止重叠
        double overlap = Math.sqrt(2) - deltaPos.length();
        if (overlap > 0) {
            Vector correction = normal.clone().multiply(overlap * 0.5);
            ball1.location.add(correction);
            ball2.location.subtract(correction);
        }

        float maxVelocity = (float) (0.33* getHighestKnockbackLevel());
        float loudness = loudnessProcess(max(ball1.velocity.length(),ball2.velocity.length()),maxVelocity);

        Location soundLoc = ball1.location;

        for (Player player : Bukkit.getOnlinePlayers()){
            if(player.getWorld().equals(ball1.location.getWorld())){
                player.playSound(
                        soundLoc, // 音效位置
                        Sound.BLOCK_NOTE_BLOCK_BASEDRUM, // 低音鼓音效（1.12+）
                        loudness, // 音量 (0.0-1.0)
                        1.0f  // 音高 (0.5-2.0)
                );
            }
        }
    }

    /**
     * 检查8balls模式的犯规
     */
    private static void check8ballsFoul(DisplayBall ball1, DisplayBall ball2) {
        if (plugin == null) return;

        String worldName = ball1.getWorld();
        if (worldName == null) return;

        org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
        if (gameState == null || !gameState.getGameType().equals("8balls")) return;

        Player currentPlayer = plugin.getRoundManager().getCurrentPlayer(worldName);
        if (currentPlayer == null) return;

        // 确定哪个是母球，哪个是目标球
        DisplayBall motherBall = null;
        DisplayBall targetBall = null;

        if (ball1.isMotherBall) {
            motherBall = ball1;
            targetBall = ball2;
        } else if (ball2.isMotherBall) {
            motherBall = ball2;
            targetBall = ball1;
        } else {
            return; // 两个都不是母球，不需要检查犯规
        }

        // 标记母球已经击中了其他球
        gameState.setMotherBallHitAnyBall(true);

        // 只检查第一个击中的球
        if (gameState.hasFirstBallHit()) {
            return; // 已经检查过第一个球了
        }

        gameState.setFirstBallHit(true);

        // 检查第一个击中的球是否正确
        String playerColor = gameState.getPlayerColor(currentPlayer);
        if (!"none".equals(playerColor)) {
            int targetBallNumber = plugin.getInGame().extractBallNumberFromDisplayBall(targetBall);
            boolean isCorrectBall = false;

            if ("red".equals(playerColor)) {
                isCorrectBall = (targetBallNumber >= 1 && targetBallNumber <= 7);
            } else if ("blue".equals(playerColor)) {
                isCorrectBall = (targetBallNumber >= 9 && targetBallNumber <= 15);
            }

            // 如果玩家的色球都打完了，可以打黑8
            if (!isCorrectBall && targetBallNumber == 8) {
                isCorrectBall = hasFinishedColorBalls(worldName, currentPlayer, playerColor);
            }

            gameState.setFirstBallCorrect(isCorrectBall);

            if (!isCorrectBall) {
                // 设置待处理的犯规，等球停下来再处理
                gameState.setPendingInfraction("第一球击中错误目标");
            }
        }
    }

    /**
     * 检查玩家是否已经打完了自己的色球
     */
    private static boolean hasFinishedColorBalls(String worldName, Player player, String playerColor) {
        if ("red".equals(playerColor)) {
            // 检查场上是否还有1-7号球
            return !hasColorBallsOnTable(worldName, 1, 7);
        } else if ("blue".equals(playerColor)) {
            // 检查场上是否还有9-15号球
            return !hasColorBallsOnTable(worldName, 9, 15);
        }
        return false;
    }

    /**
     * 检查场上是否还有指定范围的球
     */
    private static boolean hasColorBallsOnTable(String worldName, int minNumber, int maxNumber) {
        if (plugin == null) return false;

        for (DisplayBall ball : DisplayBall.displayBalls) {
            if (ball.getWorld().equals(worldName) && !ball.isMotherBall) {
                int ballNumber = plugin.getInGame().extractBallNumberFromDisplayBall(ball);
                if (ballNumber >= minNumber && ballNumber <= maxNumber) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        Player player = event.getPlayer();
        event.setJoinMessage("§a [台球厅]  §r欢迎玩家 §6" + player.getName() + "§r 来到台球厅！");

        player.teleport(Bukkit.getWorld(lobbyWorld).getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);

        // 设置主城物品栏
        new InventoryUtils(plugin).setLobbyInventory(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        
//        plugin.getLogger().info("[DEBUG] 玩家退出事件: " + player.getName() + " 从世界 " + worldName);
//        plugin.getLogger().info("[DEBUG] 玩家是否有对局标签: " + player.getScoreboardTags().contains("tableball_ingame"));
//        plugin.getLogger().info("[DEBUG] inGame.isPlayerInGame结果: " + inGame.isPlayerInGame(player));
        
        // 检查玩家是否在游戏中（使用更直接的方式：检查scoreboard标签）
        if (player.getScoreboardTags().contains("tableball_ingame")) {
//            plugin.getLogger().info("[DEBUG] 玩家有对局标签，调用handlePlayerQuitDuringGame");
            handlePlayerQuitDuringGame(player, worldName);
        } else if (inGame.isPlayerInGame(player)) {
//            plugin.getLogger().info("[DEBUG] 玩家在InGame中，调用handlePlayerQuitDuringGame");
            handlePlayerQuitDuringGame(player, worldName);
        } /*else {
            plugin.getLogger().info("[DEBUG] 玩家不在游戏中，跳过处理");
        }*/

        event.setQuitMessage("§a [台球厅]  §r玩家 §6" + player.getName() + "§r 离开了台球厅！");
    }
    
    /**
     * 处理游戏中玩家退出的情况
     * @param player 退出的玩家
     * @param worldName 世界名称
     */
    private void handlePlayerQuitDuringGame(Player player, String worldName) {
//        plugin.getLogger().info("[DEBUG] 玩家退出处理开始: " + player.getName() + " 在世界: " + worldName);
        
        // 检查玩家是否有对局标签
        if (player.getScoreboardTags().contains("tableball_ingame")) {
//            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 有对局标签，开始弃权处理");
            
            // 获取世界对象
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
//                plugin.getLogger().severe("[DEBUG] 无法获取世界对象: " + worldName);
                return;
            }
            
            // 直接从世界获取所有有对局标签的玩家（包括正在退出的玩家）
            List<Player> playersInWorld = new ArrayList<>();
            for (Player worldPlayer : world.getPlayers()) {
                if (worldPlayer.getScoreboardTags().contains("tableball_ingame")) {
                    playersInWorld.add(worldPlayer);
//                    plugin.getLogger().info("[DEBUG] 从世界中找到对局玩家: " + worldPlayer.getName());
                }
            }
            
            // 如果当前玩家不在列表中，添加他（因为PlayerQuitEvent可能在他从世界中移除之前触发）
            if (!playersInWorld.contains(player)) {
                playersInWorld.add(player);
//                plugin.getLogger().info("[DEBUG] 将退出的玩家添加到列表中: " + player.getName());
            }
            
//            plugin.getLogger().info("[DEBUG] 总共找到 " + playersInWorld.size() + " 个对局玩家");

            // 找到对手（除了退出的玩家之外的其他玩家）
            Player opponent = null;
            for (Player p : playersInWorld) {
                if (!p.equals(player)) {
                    opponent = p;
//                    plugin.getLogger().info("[DEBUG] 找到对手: " + opponent.getName());
                    break;
                }
            }
            
            if (opponent == null) {
                plugin.getLogger().warning("[DEBUG] 未找到对手，可能是单人游戏或数据异常");
            }

            // 显示结算信息（弃权方式）
            String gameType = plugin.getInGame().getGameType(worldName);
            if (gameType == null) {
                gameType = "Standard"; // 默认模式
            }
//            plugin.getLogger().info("[DEBUG] 游戏类型: " + gameType);

            // 记录弃权结果到数据库
            if (opponent != null) {
                // 弃权者记录弃权失败，对方记录获胜
                plugin.getPlayerDataManager().recordGameResult(player, gameType, "forfeit");
                plugin.getPlayerDataManager().recordGameResult(opponent, gameType, "win");
//                plugin.getLogger().info("[DEBUG] 记录弃权结果: " + player.getName() + " 弃权, " + opponent.getName() + " 获胜");
            } else {
                // 没有对手的情况下，只记录弃权者的失败
                plugin.getPlayerDataManager().recordGameResult(player, gameType, "forfeit");
//                plugin.getLogger().info("[DEBUG] 只记录弃权者失败: " + player.getName());
            }

            // 在玩家退出后，向世界中剩余的玩家显示结算信息
            if (world != null) {
//                plugin.getLogger().info("[DEBUG] 开始向世界 " + worldName + " 中的玩家显示结算信息");
//                plugin.getLogger().info("[DEBUG] 世界中当前玩家数量: " + world.getPlayers().size());
                
                for (Player worldPlayer : world.getPlayers()) {
//                    plugin.getLogger().info("[DEBUG] 向玩家 " + worldPlayer.getName() + " 发送结算信息");
                    worldPlayer.sendMessage("§e结算信息:");

                    if ("8balls".equals(gameType)) {
                        // 8balls模式显示局数
                        org.tableBall.Game.GameState gameState = plugin.getRoundManager().getGameState(worldName);
                        if (gameState != null) {
                            for (Player p : playersInWorld) {
                                int wins = gameState.getRoundWins(p);
                                worldPlayer.sendMessage("§b" + p.getName() + ": §a" + wins + "胜");
//                                plugin.getLogger().info("[DEBUG] 显示玩家 " + p.getName() + " 的胜利次数: " + wins);
                            }
                        } else {
                            plugin.getLogger().warning("[DEBUG] 8balls模式下GameState为null");
                        }
                    } else {
                        // 标准模式显示分数
                        for (Player p : playersInWorld) {
                            int s = org.tableBall.Manager.RoundManager.scores.getOrDefault(p.getName(), 0);
                            worldPlayer.sendMessage("§b" + p.getName() + ": §a" + s);
//                            plugin.getLogger().info("[DEBUG] 显示玩家 " + p.getName() + " 的分数: " + s);
                        }
                    }

                    // 判定对方获胜（弃权）
                    if (opponent != null) {
                        worldPlayer.sendMessage("§6获胜者：" + opponent.getName() + " (对方弃权)");
                    } else {
                        worldPlayer.sendMessage("§6游戏结束：对手已退出");
                    }
                    worldPlayer.sendMessage("§a你已被传送回主城！");
                }

                // 结束游戏
//                plugin.getLogger().info("[DEBUG] 调用endGameForRealLikeDeepseekSTFU结束游戏");
                org.tableBall.Commands.LeaveCommand.endGameForRealLikeDeepseekSTFU(world);
//                plugin.getLogger().info("[DEBUG] endGameForRealLikeDeepseekSTFU调用完成");
            } else {
                plugin.getLogger().severe("[DEBUG] 无法获取世界对象: " + worldName + "，无法结束游戏！");
            }
        } else {
//            plugin.getLogger().info("[DEBUG] 玩家 " + player.getName() + " 没有对局标签，按观战者处理");
            // 玩家不在对局中，只是在观战，直接移除
            inGame.removePlayer(player);
        }
    }

    private static Material getMaterialOfMotherBall() {
        return Material.WHITE_TERRACOTTA;
    }

    @EventHandler
    public void onPlayerPlaceWhiteBall(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!NbtHook.hasTag(item, "tb.whiteBall"))
            return;

        // 判断点击方块是否为蓝冰
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BLUE_ICE) {
            player.sendMessage("§c母球只能放在蓝冰上！");
            event.setCancelled(true);
            return;
        }

        // 生成母球展示实体
        Location loc = event.getClickedBlock().getLocation().add(Objects.requireNonNull(event.getClickedPosition()));
//        plugin.getLogger().info("\nLoc= "+loc+"\n被右键方块的位置= "+event.getClickedBlock().getLocation());
        Material material = getMaterialOfMotherBall();
        DisplayBall motherBall = new DisplayBall(loc, material, "§f白球", true);
        String worldName = player.getWorld().getName();
        inGame.setMotherBall(worldName, motherBall);
        inGame.addBall(worldName, motherBall);

        // 移除物品
        player.getInventory().setItemInMainHand(null);
        player.sendMessage("§a已成功放置母球！");
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractPersonalInfo(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.NETHER_STAR) return;

        // 检查是否是个人信息物品
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "personal_info"), PersistentDataType.BYTE)) {

            // 检查玩家是否在主城
            String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
            if (!player.getWorld().getName().equals(lobbyWorld)) {
                return; // 不在主城，不处理
            }

            // 显示个人信息
            showPersonalInfo(player);
            // 播放打开音效
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractQuickQuit(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.RED_BED) return;

        // 检查是否是快速回到主城物品
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "quick_quit"), PersistentDataType.BYTE)) {

            // 检查玩家是否在主城
            String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
            if (!player.getWorld().getName().equals(lobbyWorld)) {
                return; // 不在主城，不处理
            }

            // 执行指令
            player.performCommand("hub");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractDisplay(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interactionEntity)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        DisplayBall ball = findBallByEntity(interactionEntity);
        if (ball == null) return;

        String worldName = player.getWorld().getName();

        if(!inGame.checkAllBallsStatic(event.getPlayer().getWorld().getName())) return;

        // 检查玩家是否空手（优先级最低的检查）
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem == null || mainHandItem.getType() == Material.AIR) {
            player.sendMessage("§c请手持球杆击球！");
            event.setCancelled(true);
            return;
        }

        // 如果既不是当前回合的玩家，又不是母球，就直接return，不做任何提示
        // 检查回合和母球
        if (!plugin.getRoundManager().isCurrentPlayer(worldName, player)&&!hasStrike) {
            player.sendMessage("§c现在不是你的回合！");
            event.setCancelled(true);
            return;
        }

        if (!ball.isMotherBall) {
            player.sendMessage("§c你只能击打母球！");
            event.setCancelled(true);
            return;
        }
        double knockbackLevel = mainHandItem.getEnchantmentLevel(Enchantment.KNOCKBACK);

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                (float) knockbackLevel * 0.4f,
                1.0f
        );

        // 计算击球方向
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(0.33 * (knockbackLevel + 1)).setY(0);

        // 应用速度（确保立即生效）
        ball.setVelocity(velocity);

        // 有问题就删掉这个
        EntityEventListener.hasStrike = true;

        // 处理回合逻辑
        plugin.getRoundManager().handleShot(worldName, player);
        event.setCancelled(true);
    }

    private DisplayBall findBallByEntity(Interaction entity) {
        for (DisplayBall ball : DisplayBall.displayBalls) {
            if (ball.interactor.getUniqueId().equals(entity.getUniqueId())) {
                return ball;
            }
        }
        return null;
    }

    /**
     * 显示玩家个人信息
     * @param player 玩家
     */
    private void showPersonalInfo(Player player) {
        // 使用GUI显示个人信息
        org.tableBall.GUI.PersonalInfoGUI personalInfoGUI = new org.tableBall.GUI.PersonalInfoGUI((TableBall) plugin);
        personalInfoGUI.openPersonalInfoGUI(player);
    }

    /**
     * 检查玩家是否应该被限制物品操作
     * @param player 玩家
     * @return 如果应该限制返回true
     */
    private boolean shouldRestrictItemOperations(Player player) {
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "world");
        boolean isInLobby = player.getWorld().getName().equals(lobbyWorld);
        boolean isInGame = player.getScoreboardTags().contains("tableball_ingame");
        return isInLobby || isInGame;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 检查是否是个人信息GUI，如果是则不处理（由专门的监听器处理）
        String title = event.getView().getTitle();
        if (org.tableBall.GUI.PersonalInfoGUI.isPersonalInfoGUI(title)) {
            return;
        }

        // 如果玩家在主城或游戏中，禁止移动物品
        if (shouldRestrictItemOperations(player) && !EditModeCommand.isEditMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 检查是否是个人信息GUI，如果是则不处理（由专门的监听器处理）
        String title = event.getView().getTitle();
        if (org.tableBall.GUI.PersonalInfoGUI.isPersonalInfoGUI(title)) {
            return;
        }

        // 如果玩家在主城或游戏中，禁止拖拽物品
        if (shouldRestrictItemOperations(player) && !EditModeCommand.isEditMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // 如果玩家在主城或游戏中，禁止丢弃物品
        if (shouldRestrictItemOperations(player) && !EditModeCommand.isEditMode()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // 如果玩家在主城或游戏中，禁止交换主副手物品
        if (shouldRestrictItemOperations(player) && !EditModeCommand.isEditMode()) {
            event.setCancelled(true);
        }
    }

    private static float loudnessProcess(double vel, double maxVel){
        if (vel <= maxVel) {
            return (float) ((float) vel/maxVel);
        }else{
            return 1.0f;
        }
    }

    private static int getHighestKnockbackLevel() {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("items");
        if (items == null) return 0;

        int highestLevel = 0;
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.contains("enchantments.knockback")) {
                int level = item.getInt("enchantments.knockback", 0);
                highestLevel = max(highestLevel, level);
            }
        }
        return highestLevel;
    }

    @EventHandler
    private void onPlayerDamage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.VOID){
            for(DisplayBall ball : instance.getInGame().getBalls(p.getWorld().getName())){
                p.teleport(ball.location.clone().add(0, 1, 0));
                break;
            }
            p.sendMessage("§c检测到你掉入虚空，已将你随机传送到场上的一颗球！");
            e.setCancelled(true);
        }
    }
}

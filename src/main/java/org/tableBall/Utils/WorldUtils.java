package org.tableBall.Utils;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.tableBall.TableBall;

import java.util.Collection;
import java.util.Random;

public class WorldUtils {
    private final Plugin plugin;
    private final MultiverseCore mvCore;

    public WorldUtils(TableBall plugin) {
        this.plugin = plugin;
        this.mvCore = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
    }

    /**
     * 获取指定名称的世界实例
     * @param worldName 世界名称
     * @return 世界实例，如果世界不存在则返回null
     */
    public World getWorld(String worldName) {
        if (mvCore != null) {
            MVWorldManager worldManager = mvCore.getMVWorldManager();
            if (worldManager.isMVWorld(worldName)) {
                return Bukkit.getWorld(worldName);
            }
        }
        return null;
    }

    /**
     * 获取世界的在线玩家数量
     * @param worldName 世界名称
     * @return 在线玩家数量，如果世界不存在则返回0
     */
    public int getWorldPlayerCount(String worldName) {
        World world = getWorld(worldName);
        return world != null ? world.getPlayers().size() : 0;
    }

    /**
     * 检查世界是否存在
     * @param worldName 世界名称
     * @return 如果世界存在返回true，否则返回false
     */
    public boolean isWorldExists(String worldName) {
        if (mvCore != null) {
            MVWorldManager worldManager = mvCore.getMVWorldManager();
            return worldManager.isMVWorld(worldName);
        }
        return false;
    }

    /**
     * 获取世界的出生点位置
     * @param worldName 世界名称
     * @return 出生点位置，如果世界不存在则返回null
     */
    public Location getWorldSpawn(String worldName) {
        if (mvCore != null) {
            MVWorldManager worldManager = mvCore.getMVWorldManager();
            MultiverseWorld mvWorld = worldManager.getMVWorld(worldName);
            if (mvWorld != null) {
                return mvWorld.getSpawnLocation();
    }
        }
        return null;
    }

    /**
     * 传送玩家到指定世界的出生点
     * @param player 要传送的玩家
     * @param worldName 目标世界名称
     * @return 是否传送成功
     */
    public boolean teleportToWorldSpawn(Player player, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        Location spawnLocation = world.getSpawnLocation();
        return player.teleport(spawnLocation);
    }

    /**
     * 获取所有可用的世界名称（除了lobby）
     * @return 世界名称数组
     */
    public String[] getAvailableWorlds() {
        if (mvCore != null) {
            MVWorldManager worldManager = mvCore.getMVWorldManager();
            return worldManager.getMVWorlds().stream()
                    .map(MultiverseWorld::getName)
                    .filter(name -> !name.equalsIgnoreCase("lobby"))
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    /**
     * 将玩家传送到大厅
     * @param player 要传送的玩家
     * @return 是否成功传送
     */
    public boolean teleportToLobby(Player player) {
        // 从配置文件中获取大厅世界名称
        String lobbyWorld = plugin.getConfig().getString("lobby-world", "lobby");
        
        // 检查大厅世界是否存在
        if (!isWorldExists(lobbyWorld)) {
            plugin.getLogger().warning("大厅世界 " + lobbyWorld + " 不存在！");
            return false;
        }
        
        // 传送到大厅出生点
        return teleportToWorldSpawn(player, lobbyWorld);
    }

    /**
     * 创建游戏世界
     * @param worldName 世界名称
     * @return 创建的世界
     */
    public World createGameWorld(String worldName) {
        // 如果世界已存在，先删除
        if (Bukkit.getWorld(worldName) != null) {
            Bukkit.unloadWorld(worldName, false);
            Bukkit.getWorlds().remove(Bukkit.getWorld(worldName));
        }

        // 创建世界
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\": [{\"block\": \"barrier\", \"height\": 1}]}");
        World world = creator.createWorld();

        if (world != null) {
            // 设置世界属性
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setGameRule(GameRule.FALL_DAMAGE, false);
            world.setGameRule(GameRule.DROWNING_DAMAGE, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.DO_TILE_DROPS, false);
            world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
            world.setGameRule(GameRule.DO_INSOMNIA, false);
            world.setGameRule(GameRule.DO_VINES_SPREAD, false);
            world.setGameRule(GameRule.DO_LIMITED_CRAFTING, false);

            // 设置世界边界
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(100);

            // 设置世界时间
            world.setTime(6000);
            world.setStorm(false);
            world.setThundering(false);

            // 设置世界难度
            world.setDifficulty(Difficulty.PEACEFUL);

            // 设置世界生成器
            world.setSpawnLocation(0, 1, 0);
        }

        return world;
    }

    /**
     * 删除世界
     * @param worldName 世界名称
     */
    public void deleteWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            // 将所有玩家传送回主世界
            for (Player player : world.getPlayers()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }

            // 卸载并删除世界
            Bukkit.unloadWorld(world, false);
            Bukkit.getWorlds().remove(world);
        }
    }
}

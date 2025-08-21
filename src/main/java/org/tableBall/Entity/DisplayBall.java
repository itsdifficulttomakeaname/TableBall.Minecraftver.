package org.tableBall.Entity;

import cn.jason31416.planetlib.PlanetLib;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.tableBall.TableBall;

import java.util.*;

@SuppressWarnings("all")
public class DisplayBall {
    public static TableBall plugin;
    public static final Set<DisplayBall> displayBalls = new HashSet<>();

    public final BlockDisplay blockDisplay;
    public final Interaction interactor;
    public final ArmorStand textDisplay;
    public Location location;
    public final String text;
    public final Material color;
    public Vector velocity;
    public UUID uuid;
    public boolean isMotherBall=false;
    public double FRICTION;
    public static final double MIN_SPEED = 0.2; // 最小速度阈值
    public double BALL_SIZE; // 球的大小
    private boolean isJudging;

    public boolean isFalling = false;

    public DisplayBall(Location location, Material color, String text, boolean isMotherBall) {
        // 检查插件实例是否已初始化
        if (plugin == null) {
            throw new IllegalStateException("DisplayBall.plugin 未初始化！请确保在使用DisplayBall前初始化plugin字段。");
        }
        
        // 检查位置参数
        if (location == null) {
            throw new IllegalArgumentException("Location不能为null");
        }
        
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location的世界不能为null");
        }
        
        this.location = location.clone();
        this.color = color != null ? color : Material.STONE;
        this.text = text != null ? text : "";
        this.velocity = new Vector(0, 0, 0);
        this.uuid = UUID.randomUUID();
        this.isMotherBall = isMotherBall;
        
        // 初始化FRICTION和BALL_SIZE
        String worldName = location.getWorld().getName();
        try {
            if (plugin.getInGame() != null && plugin.getInGame().ballsConfig != null) {
                this.FRICTION = plugin.getInGame().ballsConfig.getDouble(worldName + ".friction", 0.6) / 100.0;
                this.BALL_SIZE = plugin.getInGame().ballsConfig.getDouble(worldName + ".ball_size", 1.0);
            } else {
                this.FRICTION = 0.006; // 默认0.6%摩擦力
            }
        } catch (Exception e) {
            plugin.getLogger().warning("读取球配置时出错: " + e.getMessage());
            this.FRICTION = 0.006;
        }
        
        // 创建碰撞检测
        this.interactor = (Interaction) location.getWorld().spawnEntity(location.clone().add(new Vector(0.5, 0, 0.5)), EntityType.INTERACTION);

        // 创建方块展示实体
        this.blockDisplay = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(this.color.createBlockData());

        textDisplay = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        textDisplay.setInvisible(true);
        textDisplay.setCustomName(this.text);
        textDisplay.setCustomNameVisible(true);

        displayBalls.add(this);
    }

    /**
     * 设置球的发光效果
     * @param glowColor 发光颜色，null表示不发光
     */
    public void setGlowing(ChatColor glowColor) {
        if (glowColor != null) {
            blockDisplay.setGlowing(true);
            blockDisplay.setGlowColorOverride(org.bukkit.Color.fromRGB(getColorFromChatColor(glowColor)));
        } else {
            blockDisplay.setGlowing(false);
            blockDisplay.setGlowColorOverride(null);
        }
    }

    /**
     * 将ChatColor转换为RGB颜色值
     */
    private int getColorFromChatColor(ChatColor chatColor) {
        return switch (chatColor) {
            case RED -> 0xFF0000;      // 红色
            case BLUE -> 0x0000FF;     // 蓝色
            case WHITE -> 0xFFFFFF;    // 白色
            case YELLOW -> 0xFFFF00;   // 黄色
            case GREEN -> 0x00FF00;    // 绿色
            case AQUA -> 0x00FFFF;     // 青色
            case LIGHT_PURPLE -> 0xFF00FF; // 紫色
            default -> 0xFFFFFF;       // 默认白色
        };
    }

    public void destroy() {
        blockDisplay.remove();
        interactor.remove();
        textDisplay.remove();
        displayBalls.remove(this);
        
        // 安全地从游戏中移除球
        try {
            if (plugin != null && plugin.getInGame() != null && location != null && location.getWorld() != null) {
                plugin.getInGame().getBalls(getWorld()).remove(this);
            }
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("移除球时出错: " + e.getMessage());
            }
        }
    }

    public void updateMovement(int amount) {
        if (plugin == null || location == null || location.getWorld() == null) {
            return; // 如果基本数据无效，不执行更新
        }
        
        String worldName = getWorld();
        ConfigurationSection section = plugin.getInGame().ballsConfig.getConfigurationSection(worldName + ".holes");
        if (section == null) {
            return; // 如果找不到洞配置，不执行更新
        }
        
        //如果在掉落状态，处理掉落
        if(isFalling) {
            velocity.setY(velocity.getY()*1.01);
            velocity.setX(0).setZ(0);
            if(plugin.getInGame().ballsConfig.getInt(worldName + ".holes.y") >= location.getY()){
                // 检查游戏类型以决定使用哪种进球处理方法
                String gameType = plugin.getInGame().getGameType(worldName);
                // 球进洞处理

                if ("8balls".equals(gameType)) {
                    int ballNumber = plugin.getInGame().extractBallNumberFromDisplayBall(this);
                    // 8balls模式进球处理
                    plugin.getRoundManager().handle8ballsIn(worldName, ballNumber);
                } else {
                    // 标准模式进球处理
                    plugin.getRoundManager().handleBallIn(worldName, isMotherBall);
                }
                destroy();
            }
        }else{
            // 应用摩擦力
            velocity.multiply(1 - FRICTION/amount);

            // 阻止球在Y轴上异常运动
            velocity.setY(0);

            // 检查速度是否低于阈值
            if (velocity.length() < MIN_SPEED && velocity.length() != 0.0) {
                velocity = new Vector(0, 0, 0);
                return;
            }
            /*
            else{
                isJudging = true;
            }
            */

            int x1 = plugin.getInGame().ballsConfig.getInt(worldName + ".bounds.x1");
            int z1 = plugin.getInGame().ballsConfig.getInt(worldName + ".bounds.z1");
            int x2 = plugin.getInGame().ballsConfig.getInt(worldName + ".bounds.x2");
            int z2 = plugin.getInGame().ballsConfig.getInt(worldName + ".bounds.z2");

            // 弹性系数
            double restitution;
            try{
                restitution = plugin.getInGame().ballsConfig.getDouble(worldName + ".restitution");
            }catch (NullPointerException e){
                restitution = 0.8;
            }

            // 假设x2>x1, z2>z1
            if(x1>x2){
                int tmp=x2;
                x2=x1;
                x1=tmp;
            }
            if(z1>z2){
                int tmp=z2;
                z2=z1;
                z1=tmp;
            }
            // 碰壁检测
            boolean hitWall = false;
            if (location.getX()+0.25/*校准值0.25*/ < x1) {
                velocity.setX(-velocity.getX()*restitution);
                location.setX(x1-0.25);
                hitWall = true;
            }
            if (location.getX()-0.25/*校准值0.25*/ > x2) {
                velocity.setX(-velocity.getX()*restitution);
                location.setX(x2+0.25);
                hitWall = true;
            }

            if (location.getZ()+0.25/*校准值0.25*/ < z1) {
                velocity.setZ(-velocity.getZ()*restitution);
                location.setZ(z1-0.25);
                hitWall = true;
            }
            if (location.getZ()-0.25/*校准值0.25*/ > z2) {
                velocity.setZ(-velocity.getZ()*restitution);
                location.setZ(z2+0.25);
                hitWall = true;
            }

            // 如果碰壁了，通知游戏管理器
            if (hitWall) {
                plugin.getRoundManager().handleWallHit(worldName);
            }
        }

        location.add(velocity.clone().multiply(1.0/amount));
        this.blockDisplay.setVelocity(velocity.clone().multiply(20));
        blockDisplay.teleport(location);

        interactor.teleport(location.clone().add(new Vector(0.5, 0, 0.5)));

        textDisplay.setVelocity(velocity.clone().multiply(20));
        textDisplay.teleport(location.clone().add(new Vector(0.5, -0.8, 0.5)));

        if(!isFalling) for(String key: section.getKeys(false)){
            if (key.equals("y")) continue; // 跳过y键
            
            int hx1, hx2, hz1, hz2;
            try {
                hx1 = section.getInt(key+".x1");
                hx2 = section.getInt(key+".x2");
                hz1 = section.getInt(key+".z1");
                hz2 = section.getInt(key+".z2");
            } catch (Exception e) {
                continue; // 如果获取洞坐标失败，跳过此洞
            }

            if (hx1 > hx2) {
                int temp = hx1;
                hx1 = hx2;
                hx2 = temp;
            }
            if (hz1 > hz2) {
                int temp = hz1;
                hz1 = hz2;
                hz2 = temp;
            }

            if(
                    location.getX()-0.5 > hx1
                    &&
                    location.getX()+0.5 < hx2
                    &&
                    location.getZ()-0.5 > hz1
                    &&
                    location.getZ()+0.5 < hz2
            ){
                isFalling = true;
                velocity.setY(-0.07).setX(0).setZ(0);
            }
        }
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }

    public boolean isColliding(DisplayBall other) {
        return other != null && other.location != null && location != null && 
               other.location.distance(location) < Math.sqrt(2);
    }

    public String getWorld(){
        return location != null && location.getWorld() != null ? 
               location.getWorld().getName() : null;
    }
} 
package org.tableBall.Entity;

import cn.jason31416.planetlib.PlanetLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.tableBall.TableBall;

import java.util.*;

@SuppressWarnings("all")
public class DisplayBall {
    private double radius = BALL_SIZE / 2;
    public static TableBall plugin;
    public static final Set<DisplayBall> displayBalls = new HashSet<>();

    public final BlockDisplay blockDisplay;
    public final Interaction interactor;
    public Location location;
    public final String text;
    public final Material color;
    public Vector velocity;
    public UUID uuid;
    public BukkitRunnable movementTask;
    public boolean isMotherBall=false;
    public static final double FRICTION = 0.004; // 0.4% 摩擦力
    public static final double MIN_SPEED = 0.2; // 最小速度阈值
    public static final float BALL_SIZE = 2.0f; // 球的大小

    public boolean isFalling = false;

    public DisplayBall(Location location, Material color, String text, boolean isMotherBall) {
        this.location = location;
        this.color = color;
        this.text = text;
        this.velocity = new Vector(0, 0, 0);
        this.uuid = UUID.randomUUID();
        this.isMotherBall = isMotherBall;
        // 创建碰撞检测
        this.interactor = (Interaction) location.getWorld().spawnEntity(location.clone().add(new Vector(0.5, 0, 0.5)), EntityType.INTERACTION);

        // 创建方块展示实体
        this.blockDisplay = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(color.createBlockData());
//        interactor.setPassenger(blockDisplay);

        displayBalls.add(this);
    }

    public void destroy() {
        blockDisplay.remove();
        interactor.remove();
        displayBalls.remove(this);
    }

    public void updateMovement(int amount) {
        ConfigurationSection section = plugin.getInGame().ballsConfig.getConfigurationSection(getWorld()+".holes");
        //如果在掉落状态，处理掉落
        if(isFalling) {
            velocity.setY(velocity.getY()*1.01);
            //velocity.setX(velocity.getX()*0.8);
            //velocity.setZ(velocity.getZ()*0.8);
            velocity.setX(0).setZ(0);
            if(plugin.getInGame().ballsConfig.getInt(getWorld() + ".holes.y") >= location.getY()){
                destroy();
                plugin.getRoundManager().handleBallIn(getWorld(), isMotherBall);
            }
        }else{
            // 应用摩擦力
            velocity.multiply(1 - FRICTION/amount);

            // 阻止球在Y轴上异常运动
            velocity.setY(0);

            // 检查速度是否低于阈值
            if (velocity.length() < MIN_SPEED && velocity.length() != 0.0) {
                velocity = new Vector(0, 0, 0);
//                plugin.getLogger().info("Velocity is now lower than MIN_SPEED.Stopped");
                return;
            }
//            else if(velocity.length() > MIN_SPEED){
//                plugin.getLogger().info("Velocity(Higher than MIN_SPEED): "+velocity.clone().length()+"; Location at: "+location);
//            }

            int x1 = plugin.getInGame().ballsConfig.getInt(getWorld() + ".bounds.x1");
            int z1 = plugin.getInGame().ballsConfig.getInt(getWorld() + ".bounds.z1");
            int x2 = plugin.getInGame().ballsConfig.getInt(getWorld() + ".bounds.x2");
            int z2 = plugin.getInGame().ballsConfig.getInt(getWorld() + ".bounds.z2");

            // 假设x2>x1, z2>z1
            //碰壁检测，建议加0.25校准值防止卡在墙里

            // 弹性系数
            double restitution = 0.8;

            if (location.getX() < x1 - 0.25) {
                velocity.setX(-velocity.getX()*restitution);
                location.setX(x1);
            }
            if (location.getX() > x2 + 0.25) {
                velocity.setX(-velocity.getX()*restitution);
                location.setX(x2);
            }

            if (location.getZ() < z1 - 0.25) {
                velocity.setZ(-velocity.getZ()*restitution);
                location.setZ(z1);
            }
            if (location.getZ() > z2 + 0.25) {
                velocity.setZ(-velocity.getZ()*restitution);
                location.setZ(z2);
            }
        }

        location.add(velocity.clone().multiply(1.0/amount));
//        location.setY(Math.round(location.getY())+0.01);
        this.blockDisplay.setVelocity(velocity.clone().multiply(20));
        blockDisplay.teleport(location);
        interactor.teleport(location.clone().add(new Vector(0.5, 0, 0.5)));
//        plugin.getLogger().info("Teleported: "+velocity.clone().length()+"; Location at: "+location);

        if(!isFalling) for(String key: section.getKeys(false)){
            int hx1 = section.getInt(key+".x1");
            int hx2 = section.getInt(key+".x2");
            int hz1 = section.getInt(key+".z1");
            int hz2 = section.getInt(key+".z2");

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

            // hx1 < hx2
            // hz1 < hz2

            if (hx1 < location.getX()-0.75 && hx2 > location.getX()-0.75 &&
                    hz1 < location.getZ()-0.75 && hz2 > location.getZ()-0.75 &&
                    hx1 < location.getX()+0.75 && hx2 > location.getX()+0.75 &&
                    hz1 < location.getZ()+0.75 && hz2 > location.getZ()+0.75 ) {
                isFalling = true;
                velocity.setY(-0.07).setX(0).setZ(0);
            }
        }
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }

    public boolean isColliding(DisplayBall other) {
        return other.location.distance(location)<Math.sqrt(2);
    }

    public String getWorld(){
        return location.getWorld().getName();
    }

    public double getRadius() {
        return this.radius;
    }

    public boolean getIsFalling() {
        return this.isFalling;
    }
} 
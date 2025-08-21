package org.tableBall.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.tableBall.Commands.EditModeCommand;
import org.tableBall.TableBall;

public class BlockEventListener implements Listener {
    
    private final TableBall plugin;
    
    public BlockEventListener(TableBall plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 检查是否启用编辑模式
        boolean editMode = plugin.getConfig().getBoolean("editmode", false);
        
        if (!editMode && !EditModeCommand.isEditMode()) {
            // 如果编辑模式未启用，取消破坏事件
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 检查是否启用编辑模式
        boolean editMode = plugin.getConfig().getBoolean("editmode", false);
        
        if (!editMode && !EditModeCommand.isEditMode()) {
            // 如果编辑模式未启用，取消放置事件
            event.setCancelled(true);
        }
    }
}

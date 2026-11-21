package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.station.StationManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

public class StationProtectionListener implements Listener {

    private final Cigaro plugin;

    public StationProtectionListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isStation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isStation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isStation(block.getLocation())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isStation(block.getLocation())) { event.setCancelled(true); return; }
        }
    }

    private boolean isStation(Location loc) {
        StationManager sm = plugin.getStationManager();
        return sm.getDryingStation(loc) != null
                || sm.getRollingStation(loc) != null
                || sm.isCuttingTable(loc)
                || sm.isInfuser(loc)
                || sm.getAutoDryingRack(loc) != null
                || sm.getCuttingMachine(loc) != null
                || sm.getRollingPress(loc) != null
                || sm.getPackingPress(loc) != null
                || sm.getHookahStation(loc) != null
                || sm.isBlendingTable(loc)
                || sm.getPackingStation(loc) != null;
    }
}
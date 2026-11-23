package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.gui.BlendingTableGui;
import com.cigaro.station.StationBreakCheckEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class BlendingListener implements Listener {

    private final Cigaro plugin;
    private final Map<Location, Long> interactLock = new HashMap<>();
    private static final long LOCK_MS = 300;

    public BlendingListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private boolean tryLock(Location loc) {
        long now = System.currentTimeMillis();
        Long last = interactLock.get(loc);
        if (last != null && (now - last) < LOCK_MS) return false;
        interactLock.put(loc, now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItemInHand();
        if (!plugin.getItemManager().isBlendingTable(item)) return;

        plugin.getStationManager().createBlendingTable(event.getBlock().getLocation());
        event.getPlayer().sendMessage(
                plugin.getLanguageManager().get("blending_placed"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event instanceof StationBreakCheckEvent) return;
        Block block = event.getBlock();
        if (!plugin.getStationManager().isBlendingTable(block.getLocation())) return;

        event.setDropItems(false);
        plugin.getStationManager().removeBlendingTable(block.getLocation());
        block.getWorld().dropItemNaturally(
                block.getLocation(),
                plugin.getItemManager().createBlendingTable());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        Location loc = plugin.getStationManager().getInteractionLocation(entity.getUniqueId());
        if (loc == null) return;

        if (!plugin.getStationManager().isBlendingTable(loc)) return;

        event.setCancelled(true);
        if (!tryLock(loc)) return;

        new BlendingTableGui(event.getPlayer(), plugin).open();
    }
}

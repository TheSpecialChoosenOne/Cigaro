package com.cigaro.listener;

import com.cigaro.Cigaro;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemProtectionListener implements Listener {

    private final Cigaro plugin;

    public ItemProtectionListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isCigaroItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (isCigaroItem(event.getSource())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockCook(BlockCookEvent event) {
        if (isCigaroItem(event.getSource())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (isCigaroItem(event.getFuel())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isCigaroItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        if (isCigaroItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (isCigaroItem(first) || isCigaroItem(second)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAnimal(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Animals) {
            ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
            if (isCigaroItem(item)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isCigaroItem(item)) return;
        String id = getCigaroId(item);
        if (id == null) return;

        switch (id) {
            case "drying_rack", "rolling_machine", "cutting_table", "infuser",
                 "packing_machine", "auto_drying_rack", "cutting_machine",
                 "rolling_press", "packing_press", "hookah", "blending_table":
                return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (item == null || !isCigaroItem(item)) return;
        String id = getCigaroId(item);
        if (id == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material clickedType = event.getClickedBlock().getType();
            if (clickedType == Material.COMPOSTER || clickedType.name().contains("POTTED")
                    || clickedType == Material.FLOWER_POT || clickedType.name().contains("SIGN")) {
                event.setCancelled(true);
                return;
            }
        }

        switch (id) {
            case "cigarette", "cigarette_pack", "tobacco_seeds",
                 "tobacco_fertilizer", "drying_timer", "test_strip",
                 "pipe", "cigar", "nicotine_patch":
                return;

            case "lighter_stats": {
                event.setCancelled(true);
                return;
            }

            case "drying_rack", "rolling_machine", "cutting_table", "infuser",
                 "packing_machine", "auto_drying_rack", "cutting_machine",
                 "rolling_press", "packing_press", "hookah", "blending_table":
                return;
        }

        if (id.startsWith("flavor_")) return;
        if (id.startsWith("raw_ingredient_")) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            var loc = event.getClickedBlock().getLocation();
            var sm = plugin.getStationManager();
            if (sm.getDryingStation(loc) != null || sm.getRollingStation(loc) != null
                    || sm.isCuttingTable(loc) || sm.isInfuser(loc)
                    || sm.getAutoDryingRack(loc) != null || sm.getCuttingMachine(loc) != null
                    || sm.getRollingPress(loc) != null || sm.getPackingPress(loc) != null
                    || sm.getHookahStation(loc) != null) {
                return;
            }
        }

        event.setCancelled(true);
    }

    private boolean isCigaroItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getItemManager().CIGARO_ITEM_KEY, PersistentDataType.STRING);
    }

    private String getCigaroId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().CIGARO_ITEM_KEY, PersistentDataType.STRING);
    }
}


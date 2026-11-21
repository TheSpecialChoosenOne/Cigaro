package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.station.PackingStation;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PackingListener implements Listener {

    private final Cigaro plugin;
    private final Map<UUID, Long> crankCooldown = new HashMap<>();
    private static final long CRANK_COOLDOWN_MS = 800;

    public PackingListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItemInHand();
        if (!plugin.getItemManager().isPackingMachine(item)) return;

        plugin.getStationManager().addPackingStation(
                new PackingStation(event.getBlock().getLocation()));

        event.getPlayer().sendMessage(
                plugin.getLanguageManager().get("packing_placed"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPackingInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        Location loc = plugin.getStationManager().getInteractionLocation(entity.getUniqueId());
        if (loc == null) return;

        PackingStation station = plugin.getStationManager().getPackingStation(loc);
        if (station == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (station.isDone()) {
            collectPack(player, station);
            return;
        }

        if (station.isReadyToCrank()) {
            handleCrank(player, station);
            return;
        }

        if (!station.hasItems()) {
            if (!plugin.getItemManager().isCigarette(hand)) {
                player.sendMessage(plugin.getLanguageManager().get("packing_hint_cigarettes"));
                return;
            }

            if (!plugin.getItemManager().isFreshCigarette(hand)) {
                player.sendMessage(plugin.getLanguageManager().get("packing_only_fresh"));
                return;
            }

            loadCigarettes(player, station, hand);
            return;
        }

        if (station.hasItems() && station.getDyeColor() == null) {
            DyeColor dye = getDyeColor(hand);
            if (dye == null) {
                player.sendMessage(plugin.getLanguageManager().get("packing_hint_dye"));
                return;
            }

            loadDye(player, station, hand, dye);
            return;
        }
    }

    private void loadCigarettes(Player player, PackingStation station, ItemStack hand) {
        int amount = Math.min(hand.getAmount(), 20);

        List<ItemStack> cigs = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            ItemStack single = hand.clone();
            single.setAmount(1);
            cigs.add(single);
        }

        station.loadCigarettes(cigs);
        station.setDyeColor(null);

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - amount);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.2f);

        player.sendMessage(plugin.getLanguageManager().get("packing_cigs_loaded",
                Placeholder.unparsed("amount", String.valueOf(amount))));

        plugin.getDatabaseManager().savePackingStation(station);
    }

    private void loadDye(Player player, PackingStation station, ItemStack hand, DyeColor dye) {
        station.setDyeColor(dye);

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        String colorName = plugin.getItemManager().formatDyeColor(dye);

        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.7f, 1.0f);

        player.sendMessage(plugin.getLanguageManager().get("packing_dye_loaded",
                Placeholder.unparsed("color", colorName)));

        plugin.getDatabaseManager().savePackingStation(station);
    }

    private void handleCrank(Player player, PackingStation station) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastCrank = crankCooldown.get(uuid);
        if (lastCrank != null && (now - lastCrank) < CRANK_COOLDOWN_MS) {
            return;
        }
        crankCooldown.put(uuid, now);

        station.decrementCrank();
        int remaining = station.getCranksRemaining();

        float pitch = 0.8f + (5 - remaining) * 0.1f;
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.6f, pitch);

        Location loc = station.getLocation();
        loc.getWorld().spawnParticle(
                Particle.CRIT,
                loc.getX() + 0.5, loc.getY() + 1.0, loc.getZ() + 0.5,
                8, 0.2, 0.1, 0.2, 0.05);

        plugin.getDatabaseManager().savePackingStation(station);

        if (remaining > 0) {
            int done = 5 - remaining;
            StringBuilder bar = new StringBuilder("  [");
            for (int i = 0; i < 5; i++) {
                bar.append(i < done ? "■" : "□");
            }
            bar.append("]  ");

            player.sendActionBar(
                    plugin.getLanguageManager().get("packing_crank_progress",
                            Placeholder.unparsed("bar", bar.toString())));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);

            player.sendActionBar(
                    plugin.getLanguageManager().get("packing_crank_done"));

            player.sendMessage(
                    plugin.getLanguageManager().get("packing_ready_collect"));
        }
    }

    private void collectPack(Player player, PackingStation station) {
        List<ItemStack> cigarettes = new ArrayList<>(station.getLoadedCigarettes());
        DyeColor color = station.getDyeColor();

        ItemStack pack = plugin.getItemManager().createCigarettePack(cigarettes, color);

        var overflow = player.getInventory().addItem(pack);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        station.clear();

        plugin.getDatabaseManager().savePackingStation(station);

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);

        player.sendMessage(plugin.getLanguageManager().get("packing_collected",
                Placeholder.unparsed("count", String.valueOf(cigarettes.size()))));
    }

    private DyeColor getDyeColor(ItemStack item) {
        if (item == null) return null;
        String name = item.getType().name();
        if (!name.endsWith("_DYE")) return null;
        String colorPart = name.replace("_DYE", "");
        try {
            return DyeColor.valueOf(colorPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Location normalize(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void cleanup(UUID uuid) {
        crankCooldown.remove(uuid);
    }
}
package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import com.cigaro.station.AutoDryingRack;
import com.cigaro.station.DryingStation;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class UtilityItemListener implements Listener {

    private final Cigaro plugin;

    public UtilityItemListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDryingTimerUseBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.getItemManager().isDryingTimer(item)) return;

        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) return;

        showTimerAtLocation(player, block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDryingTimerUseEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.getItemManager().isDryingTimer(item)) return;

        Location loc = plugin.getStationManager().getInteractionLocation(entity.getUniqueId());
        if (loc == null) return;

        event.setCancelled(true);
        showTimerAtLocation(player, loc);
    }

    private void showTimerAtLocation(Player player, Location loc) {
        DryingStation station = plugin.getStationManager().getDryingStation(loc);
        if (station != null) {
            int leafCount = station.getLeafEntries().size();
            if (leafCount == 0) {
                player.sendMessage(plugin.getLanguageManager().get("util_timer_empty"));
                return;
            }

            long now = System.currentTimeMillis();
            player.sendMessage(plugin.getLanguageManager().get("util_timer_header"));
            player.sendMessage(plugin.getLanguageManager().get("util_timer_leaves",
                    Placeholder.unparsed("count", String.valueOf(leafCount))));

            for (DryingStation.LeafEntry entry : station.getLeafEntries()) {
                int dryingSeconds = (int) ((now - entry.getStartTime()) / 1000);
                var dryingLevel = plugin.getConfigManager().getDryingLevel(dryingSeconds);

                player.sendMessage(plugin.getLanguageManager().get("util_timer_entry",
                        Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(entry.getTobaccoType())),
                        Placeholder.unparsed("seconds", String.valueOf(dryingSeconds)),
                        Placeholder.unparsed("level", plugin.getItemManager().formatDryingLevelName(dryingLevel))));
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f);
            return;
        }

        AutoDryingRack autoRack = plugin.getStationManager().getAutoDryingRack(loc);
        if (autoRack != null) {
            int leafCount = autoRack.getLeafEntries().size();
            if (leafCount == 0) {
                player.sendMessage(plugin.getLanguageManager().get("util_timer_empty"));
                return;
            }

            player.sendMessage(plugin.getLanguageManager().get("util_timer_header"));
            player.sendMessage(plugin.getLanguageManager().get("util_timer_leaves",
                    Placeholder.unparsed("count", String.valueOf(leafCount))));

            for (AutoDryingRack.AutoDryEntry entry : autoRack.getLeafEntries()) {
                int dryingSeconds = entry.getEffectiveSeconds();
                var dryingLevel = plugin.getConfigManager().getDryingLevel(dryingSeconds);

                player.sendMessage(plugin.getLanguageManager().get("util_timer_entry",
                        Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(entry.getTobaccoType())),
                        Placeholder.unparsed("seconds", String.valueOf(dryingSeconds)),
                        Placeholder.unparsed("level", plugin.getItemManager().formatDryingLevelName(dryingLevel))));
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f);
            return;
        }

        player.sendMessage(plugin.getLanguageManager().get("util_timer_not_rack"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLighterOfStatsUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isLighterOfStats(item)) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        plugin.getGuiListener().openStatsGui(player, data);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTestStripUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isTestStrip(item)) return;

        event.setCancelled(true);

        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (!plugin.getItemManager().isCutTobacco(offHand)) {
            player.sendMessage(plugin.getLanguageManager().get("util_strip_need_tobacco"));
            return;
        }

        int cutQuality = plugin.getItemManager().getCutQuality(offHand);

        String qualityKey = switch (cutQuality) {
            case -1 -> "bad";
            case 0 -> "rough";
            case 1 -> "good";
            case 2 -> "perfect";
            default -> "unknown";
        };

        String descKey = "util_strip_desc_" + qualityKey;

        player.sendMessage(plugin.getLanguageManager().get("util_strip_header"));
        player.sendMessage(plugin.getLanguageManager().get("util_strip_quality_" + qualityKey));
        player.sendMessage(plugin.getLanguageManager().get(descKey));

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.2f);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}


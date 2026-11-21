package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.*;

public class PassiveSmokingManager implements Listener {

    public enum SmokeSource {
        CIGARETTE("passive_smoke_addicted", "passive_smoke_clean"),
        CIGAR("passive_smoke_addicted", "passive_smoke_clean"),
        PIPE("passive_smoke_addicted", "passive_smoke_clean"),
        HOOKAH("hookah_passive_addicted", "hookah_passive_normal");

        private final String addictedKey;
        private final String cleanKey;

        SmokeSource(String addictedKey, String cleanKey) {
            this.addictedKey = addictedKey;
            this.cleanKey = cleanKey;
        }

        public String getAddictedKey() { return addictedKey; }
        public String getCleanKey() { return cleanKey; }
    }

    public static final double DEFAULT_RADIUS = 8.0;
    private static final long COOLDOWN = 60_000;

    private final Cigaro plugin;
    private final Map<UUID, Long> notifyCooldown = new HashMap<>();

    public PassiveSmokingManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSmoke(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player smoker = event.getPlayer();

        if (!plugin.getSmokingListener().isSmoking(smoker.getUniqueId())) return;

        if (!plugin.getItemManager().isCigarette(
                smoker.getInventory().getItemInMainHand())) return;

        notifyNearby(smoker, SmokeSource.CIGARETTE);
    }

    public void notifyNearby(Player smoker, SmokeSource source) {
        notifyNearby(smoker, source, DEFAULT_RADIUS);
    }

    public void notifyNearby(Player smoker, SmokeSource source, double radius) {
        long now = System.currentTimeMillis();

        for (Entity entity : smoker.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player nearby)) continue;
            if (nearby.equals(smoker)) continue;

            UUID nearbyUuid = nearby.getUniqueId();

            Long last = notifyCooldown.get(nearbyUuid);
            if (last != null && (now - last) < COOLDOWN) continue;
            notifyCooldown.put(nearbyUuid, now);

            PlayerData data = plugin.getPlayerDataManager().getData(nearbyUuid);

            if (data.getAddictionStage() >= 2
                    && !plugin.getWithdrawalTask().isInGracePeriod(data)) {
                nearby.sendMessage(
                        plugin.getLanguageManager().get(source.getAddictedKey()));
            } else if (data.getAddictionStage() == 0
                    || source == SmokeSource.HOOKAH) {
                nearby.sendMessage(
                        plugin.getLanguageManager().get(source.getCleanKey()));
            }
        }
    }
}

package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BreathingListener implements Listener {

    private final Cigaro plugin;
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();
    private final Map<UUID, Long> breathCooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 8000;

    public BreathingListener(Cigaro plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickSprinting, 5L, 5L);
    }

    private void tickSprinting() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (!player.isSprinting()) {
                Integer current = sprintTicks.get(uuid);
                if (current != null && current > 0) {
                    sprintTicks.put(uuid, Math.max(0, current - 10));
                }
                continue;
            }

            PlayerData data = plugin.getPlayerDataManager().getData(uuid);

            int stage = data.getPhysicalStage();

            if (stage < 2) {
                sprintTicks.remove(uuid);
                continue;
            }

            if (plugin.getWithdrawalTask().isInGracePeriod(data)) {
                sprintTicks.remove(uuid);
                continue;
            }

            if (data.isPatchActive()) {
                sprintTicks.remove(uuid);
                continue;
            }

            int ticks = sprintTicks.getOrDefault(uuid, 0) + 5;
            sprintTicks.put(uuid, ticks);

            int threshold = switch (stage) {
                case 2 -> 160;
                case 3 -> 96;
                case 4 -> 48;
                default -> 200;
            };

            if (ticks >= threshold) {
                triggerBreathlessness(player, stage);
            }

            if (ticks >= threshold - 30 && ticks < threshold) {
                if (ticks % 20 == 0) {
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_PLAYER_BREATH, 0.3f, 0.8f);
                }
            }
        }
    }

    private void triggerBreathlessness(Player player, int stage) {
        UUID uuid = player.getUniqueId();

        Location lastBreath = breathCooldown.get(uuid) == null ? null : new Location(player.getWorld(), 0, 0, 0);
        long now = System.currentTimeMillis();
        Long lastTime = breathCooldown.get(uuid);
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) return;
        breathCooldown.put(uuid, now);

        sprintTicks.put(uuid, 0);
        player.setSprinting(false);

        if (stage >= 4) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DARKNESS, 80, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 60, 1, true, false, false));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 60, 0, true, false, false));

            player.playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_HURT_DROWN, 0.6f, 0.7f);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.playSound(player.getLocation(),
                        Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.0f);
            }, 10L);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.playSound(player.getLocation(),
                        Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.1f);
            }, 25L);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.playSound(player.getLocation(),
                        Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 1.2f);
            }, 40L);

            player.sendActionBar(
                    plugin.getLanguageManager().get("breathing_cant_breathe"));
        } else {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 40, 0, true, false, false));

            player.playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_HURT_DROWN, 0.3f, 1.0f);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.playSound(player.getLocation(),
                        Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 1.0f);
            }, 10L);

            player.sendActionBar(
                    plugin.getLanguageManager().get("breathing_lungs_burn"));
        }
    }

    public void cleanup(UUID uuid) {
        sprintTicks.remove(uuid);
        breathCooldown.remove(uuid);
    }
}
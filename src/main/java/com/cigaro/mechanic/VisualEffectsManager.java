package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.UUID;

public class VisualEffectsManager {

    private final Cigaro plugin;
    private final Random random = new Random();
    private int taskId = -1;

    public VisualEffectsManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 60L, 60L)
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData data = plugin.getPlayerDataManager().getData(uuid);

            int stage = data.getAddictionStage();
            if (stage < 2) continue;

            if (plugin.getWithdrawalTask().isInGracePeriod(data)) continue;
            if (data.isPatchActive()) continue;
            if (plugin.getSmokingListener().isInAnimation(uuid)) continue;

            if (random.nextDouble() < 0.05) {
                phantomSmell(player);
            }

            if (stage >= 3) {
                spawnFloaters(player, stage);
            }

            if (stage >= 3 && random.nextDouble() < 0.12) {
                flickerScreen(player);
            }

            if (stage >= 4 && random.nextDouble() < 0.08) {
                pulseDarkness(player);
            }
        }
    }

    private void phantomSmell(Player player) {
        Location eyeLoc = player.getEyeLocation().clone();
        Vector dir = eyeLoc.getDirection().normalize();

        Location smokeLoc = eyeLoc.add(dir.multiply(0.4)).add(0, -0.1, 0);
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, smokeLoc, 1, 0.0, 0.0, 0.0, 0.01);

        player.sendMessage(plugin.getLanguageManager().get("withdrawal_phantom_smell"));
    }

    private void spawnFloaters(Player player, int stage) {
        if (random.nextDouble() > 0.25) return;

        Location eyeLoc = player.getEyeLocation().clone();
        Vector dir = eyeLoc.getDirection().normalize();

        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 1.5 + random.nextDouble() * 1.0;
        double offsetY = (random.nextDouble() - 0.5) * 0.8;

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = new Vector(0, 1, 0);

        Location floaterLoc = eyeLoc.clone()
                .add(dir.clone().multiply(2.0))
                .add(right.clone().multiply(Math.cos(angle) * distance))
                .add(up.clone().multiply(Math.sin(angle) * distance * 0.5 + offsetY));

        int count = stage >= 4 ? 3 : 1;

        player.getWorld().spawnParticle(
                Particle.DUST, floaterLoc, count,
                0.1, 0.1, 0.1, 0,
                new Particle.DustOptions(Color.fromRGB(10, 10, 10), 0.3f + random.nextFloat() * 0.3f)
        );
    }

    private void flickerScreen(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 3, 0, true, false, false));
    }

    private void pulseDarkness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 8, 0, true, false, false));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 6, 0, true, false, false));
        }, 12L);
    }

    public void applyPostSmokeWarmth(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));

        for (int i = 0; i < 5; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Location loc = player.getEyeLocation().add(
                        (random.nextDouble() - 0.5) * 2,
                        (random.nextDouble() - 0.5),
                        (random.nextDouble() - 0.5) * 2
                );
                player.getWorld().spawnParticle(
                        Particle.DUST, loc, 1,
                        0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 170, 80), 0.4f)
                );
            }, i * 8L);
        }
    }
}
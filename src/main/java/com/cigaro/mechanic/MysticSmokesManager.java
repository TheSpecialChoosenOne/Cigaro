package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.api.CigaroPuffEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class MysticSmokesManager implements Listener {

    private static final int CLOUD_TICKS = 80;
    private static final int PUFFS_PER_TICK = 10;
    private static final int PARTICLES_PER_PUFF = 4;
    private static final double PUFF_SPREAD = 0.45;
    private static final double VERTICAL_SQUASH = 0.45;

    private final Cigaro plugin;
    private final Random random = new Random();

    public MysticSmokesManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPuff(CigaroPuffEvent event) {
        String specialType = plugin.getItemManager().getSpecialType(event.getCigaretteItem());

        if ("smokescreen".equals(specialType)) {
            Player player = event.getPlayer();

            spawnSmokeCloud(player.getLocation(), 4.0);

            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 0.5f, 0.5f);
        }
    }

    private void spawnSmokeCloud(Location baseCenter, double radius) {
        Location center = baseCenter.clone().add(0, 1, 0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > CLOUD_TICKS) {
                    this.cancel();
                    return;
                }

                double progress = (double) ticks / CLOUD_TICKS;
                double currentRadius = radius * (0.5 + 0.5 * progress);
                double density = 1.0 - 0.6 * progress;

                spawnPuffs(center, currentRadius, density);

                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, 2, radius)) {
                    if (entity instanceof LivingEntity living) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, false, false, false));
                    }
                }

                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnPuffs(Location center, double radius, double density) {
        int puffs = Math.max(2, (int) Math.round(PUFFS_PER_TICK * density));

        for (int i = 0; i < puffs; i++) {
            double r = radius * Math.cbrt(random.nextDouble());
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);

            double offsetX = r * Math.sin(phi) * Math.cos(theta);
            double offsetZ = r * Math.sin(phi) * Math.sin(theta);
            double offsetY = r * Math.cos(phi) * VERTICAL_SQUASH;

            Location pLoc = center.clone().add(offsetX, offsetY, offsetZ);

            center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    pLoc, PARTICLES_PER_PUFF, PUFF_SPREAD, PUFF_SPREAD * VERTICAL_SQUASH, PUFF_SPREAD, 0.005);
        }

        center.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                center, 6, radius * 0.35, radius * 0.35 * VERTICAL_SQUASH, radius * 0.35, 0.01);
    }
}

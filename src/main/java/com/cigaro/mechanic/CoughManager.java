package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.CigaroAchievement;
import com.cigaro.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class CoughManager implements Listener {

    private final Cigaro plugin;
    private final Random random = new Random();

    private final Map<UUID, Long> coughCooldown = new HashMap<>();
    private final Set<UUID> currentlyCoughing = new HashSet<>();
    private final Map<UUID, Integer> runTicks = new HashMap<>();
    private final Map<UUID, Boolean> wasUnderwater = new HashMap<>();

    private static final long COOLDOWN_MS = 20000;

    public CoughManager(Cigaro plugin) {
        this.plugin = plugin;

        plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickTracking, 10L, 10L);
    }

    private void tickTracking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            boolean underwater = player.isInWater()
                    && player.getEyeLocation().getBlock()
                    .getType().name().contains("WATER");
            boolean wasUW = wasUnderwater.getOrDefault(uuid, false);

            if (wasUW && !underwater) {
                tryCough(player, "surface");
            }
            wasUnderwater.put(uuid, underwater);

            if (!player.isSprinting()) {
                Integer current = runTicks.get(uuid);
                if (current != null && current > 0) {
                    runTicks.put(uuid, Math.max(0, current - 5));
                }
                continue;
            }

            int ticks = runTicks.getOrDefault(uuid, 0) + 10;
            runTicks.put(uuid, ticks);

            if (ticks >= 80 && ticks % 40 == 0) {
                tryCough(player, "running");
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        tryCough(player, "damage");
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(
                plugin, () -> tryCough(player, "eating"), 15L);
    }

    public void tryCough(Player player, String trigger) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        int stage = data.getAddictionStage();
        if (stage < 2) return;

        if (plugin.getWithdrawalTask().isInGracePeriod(data)) return;
        if (data.isPatchActive()) return;
        if (currentlyCoughing.contains(uuid)) return;
        if (plugin.getSmokingListener().isInAnimation(uuid)) return;

        Long lastCough = coughCooldown.get(uuid);
        long now = System.currentTimeMillis();
        if (lastCough != null && (now - lastCough) < COOLDOWN_MS) return;

        double chance = getCoughChance(stage, trigger);
        if (random.nextDouble() > chance) return;

        coughCooldown.put(uuid, now);
        startCoughSequence(player, stage);
    }

    public void forceCough(Player player, int stage) {
        if (currentlyCoughing.contains(player.getUniqueId())) return;
        startCoughSequence(player, Math.max(stage, 3));
    }

    private double getCoughChance(int stage, String trigger) {
        double base = switch (stage) {
            case 2 -> 0.08;
            case 3 -> 0.18;
            case 4 -> 0.35;
            default -> 0.0;
        };

        double mult = switch (trigger) {
            case "running" -> 1.5;
            case "surface" -> 2.5;
            case "damage" -> 1.2;
            case "eating" -> 0.7;
            default -> 1.0;
        };

        return base * mult;
    }


    private void startCoughSequence(Player player, int stage) {
        UUID uuid = player.getUniqueId();
        currentlyCoughing.add(uuid);

        int coughCount = switch (stage) {
            case 2 -> 1;
            case 3 -> 1 + random.nextInt(2);
            case 4 -> 2 + random.nextInt(2);
            default -> 1;
        };

        phaseTickle(player, stage);

        int coughStartDelay = 25 + random.nextInt(10);

        for (int i = 0; i < coughCount; i++) {
            int gap = 12 + random.nextInt(9);
            int delay = coughStartDelay + (i * gap);
            final int coughIndex = i;

            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) {
                            currentlyCoughing.remove(uuid);
                            return;
                        }
                        singleCough(player, stage, coughIndex);
                    }, delay);
        }

        int totalDuration = coughStartDelay + (coughCount * 16) + 20;

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) {
                        currentlyCoughing.remove(uuid);
                        return;
                    }
                    phaseRecover(player, stage);
                    currentlyCoughing.remove(uuid);
                }, totalDuration);
    }


    private void phaseTickle(Player player, int stage) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_BREATH, 0.2f, 0.6f);

        String key = "cough_tickle_" + stage;
        player.sendActionBar(plugin.getLanguageManager().get(key));

        player.addPotionEffect(
                new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS,
                        40 + stage * 10, 0, true, false, false));
    }


    private void singleCough(Player player, int stage, int index) {
        double downForce = switch (stage) {
            case 2 -> 0.08;
            case 3 -> 0.14;
            case 4 -> 0.22;
            default -> 0.05;
        };
        downForce *= (1.0 - index * 0.12);

        final double finalDownForce = downForce;

        Vector dir = player.getLocation().getDirection().normalize();

        Vector coughForce = new Vector(
                dir.getX() * 0.03, -finalDownForce, dir.getZ() * 0.03);

        player.setVelocity(player.getVelocity().add(coughForce));

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    double secondPush = finalDownForce * 0.4;
                    Vector bounce = new Vector(
                            (random.nextDouble() - 0.5) * 0.02,
                            -secondPush,
                            (random.nextDouble() - 0.5) * 0.02);
                    player.setVelocity(player.getVelocity().add(bounce));
                }, 3L);

        float pitch = 0.5f + random.nextFloat() * 0.3f;
        float volume = 0.3f + (stage * 0.1f);

        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_HURT, volume, pitch);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_PLAYER_BREATH,
                            volume * 0.6f, pitch * 0.7f);
                }, 2L);

        int dropCount = switch (stage) {
            case 2 -> 3;
            case 3 -> 6;
            case 4 -> 10;
            default -> 2;
        };

        for (int i = 0; i < dropCount; i++) {
            int dropDelay = random.nextInt(5);

            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;

                        Location mouthLoc = player.getEyeLocation().clone()
                                .add(dir.clone().multiply(0.3 + random.nextDouble() * 0.2))
                                .add(0, -0.15, 0)
                                .add((random.nextDouble() - 0.5) * 0.15,
                                        (random.nextDouble() - 0.5) * 0.08,
                                        (random.nextDouble() - 0.5) * 0.15);

                        player.getWorld().spawnParticle(
                                Particle.FALLING_DRIPSTONE_WATER,
                                mouthLoc, 1, 0.03, 0.01, 0.03, 0);
                    }, dropDelay);
        }

        if (stage >= 3) {
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        Location drip = player.getEyeLocation().clone()
                                .add(dir.clone().multiply(0.4))
                                .add(0, -0.1, 0);
                        player.getWorld().spawnParticle(
                                Particle.DRIPPING_WATER,
                                drip, stage, 0.06, 0.02, 0.06, 0);
                    }, 3L);
        }

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    Location smokeLoc = player.getEyeLocation().clone()
                            .add(dir.clone().multiply(0.4));
                    player.getWorld().spawnParticle(
                            Particle.CAMPFIRE_COSY_SMOKE,
                            smokeLoc, 2, 0.04, 0.03, 0.04, 0.005);
                }, 2L);

        if (stage >= 4 && random.nextDouble() < 0.4) {
            spawnBloodCough(player, dir);
        }

        String coughKey = "cough_bar_" + stage;
        player.sendActionBar(plugin.getLanguageManager().get(coughKey));
    }


    private void spawnFlyingDrop(Player player, Vector dir, int stage, int dropIndex) {
        Location startLoc = player.getEyeLocation().clone()
                .add(dir.clone().multiply(0.3))
                .add(0, -0.15, 0);

        double spreadX = (random.nextDouble() - 0.5) * 0.2;
        double spreadZ = (random.nextDouble() - 0.5) * 0.2;

        double speed = 0.15 + random.nextDouble() * 0.1;
        double upward = 0.02 + random.nextDouble() * 0.03;

        Vector velocity = dir.clone().multiply(speed);
        velocity.setX(velocity.getX() + spreadX * 0.3);
        velocity.setY(upward);
        velocity.setZ(velocity.getZ() + spreadZ * 0.3);

        float size = stage >= 4 ? 0.8f : 0.5f;
        Color dropColor = Color.fromRGB(180, 180, 160);

        final double gravity = 0.025;
        int startDelay = random.nextInt(4);

        for (int frame = 0; frame < 6; frame++) {
            final int f = frame;

            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;

                        double px = startLoc.getX() + velocity.getX() * f;
                        double py = startLoc.getY() + (velocity.getY() * f) - (gravity * f * f * 0.5);
                        double pz = startLoc.getZ() + velocity.getZ() * f;

                        Location dropLoc = new Location(player.getWorld(), px, py, pz);

                        if (dropLoc.getBlock().getType().isSolid()) {
                            Location surfaceLoc = dropLoc.clone();
                            surfaceLoc.setY(dropLoc.getBlockY() + 1.02);
                            spawnSplat(player.getWorld(), surfaceLoc, dropColor, size);
                            return;
                        }

                        player.getWorld().spawnParticle(
                                Particle.DUST, dropLoc, 1,
                                0.01, 0.01, 0.01, 0,
                                new Particle.DustOptions(dropColor, size));

                        if (f == 5) {
                            Location groundLoc = findGround(dropLoc);
                            if (groundLoc != null) {
                                spawnSplat(player.getWorld(), groundLoc, dropColor, size);
                            }
                        }
                    }, startDelay + frame * 2L);
        }
    }


    private void spawnGroundPuddle(Player player, Vector dir, int stage, boolean isBlood) {
        double dist = 0.5 + random.nextDouble() * 1.0;
        Location baseLoc = player.getLocation().clone()
                .add(dir.clone().multiply(dist));

        Location groundLoc = findGround(baseLoc);
        if (groundLoc == null) return;

        Color puddleColor;
        float puddleSize;
        int particleCount;

        if (isBlood) {
            puddleColor = Color.fromRGB(100, 5, 5);
            puddleSize = 1.2f;
            particleCount = 12;
        } else {
            puddleColor = Color.fromRGB(160, 160, 140);
            puddleSize = 0.8f;
            particleCount = stage >= 3 ? 8 : 4;
        }

        player.getWorld().spawnParticle(
                Particle.SPLASH, groundLoc, particleCount / 2,
                0.1, 0.02, 0.1, 0.03);

        double puddleRadius = isBlood ? 0.25 : 0.15;

        spawnPuddleParticles(player.getWorld(), groundLoc,
                puddleColor, puddleSize, particleCount, puddleRadius);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    Color fadedColor = isBlood
                            ? Color.fromRGB(70, 5, 5)
                            : Color.fromRGB(130, 130, 120);
                    spawnPuddleParticles(player.getWorld(), groundLoc,
                            fadedColor, puddleSize * 0.8f,
                            particleCount / 2, puddleRadius);
                }, 40L);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    Color dimColor = isBlood
                            ? Color.fromRGB(40, 3, 3)
                            : Color.fromRGB(100, 100, 90);
                    spawnPuddleParticles(player.getWorld(), groundLoc,
                            dimColor, puddleSize * 0.5f,
                            Math.max(2, particleCount / 4), puddleRadius * 0.7);
                }, 80L);

        if (isBlood || stage >= 3) {
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        Color lastColor = isBlood
                                ? Color.fromRGB(30, 2, 2)
                                : Color.fromRGB(80, 80, 70);
                        spawnPuddleParticles(player.getWorld(), groundLoc,
                                lastColor, puddleSize * 0.3f,
                                2, puddleRadius * 0.5);
                    }, 120L);
        }
    }

    private void spawnPuddleParticles(World world, Location center,
                                      Color color, float size,
                                      int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = random.nextDouble() * radius;

            Location point = center.clone().add(
                    Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

            world.spawnParticle(Particle.DUST, point, 1,
                    0.01, 0.001, 0.01, 0,
                    new Particle.DustOptions(color, size));
        }
    }

    private void spawnSplat(World world, Location loc, Color color, float size) {
        world.spawnParticle(Particle.SPLASH, loc, 3,
                0.05, 0.01, 0.05, 0.01);

        for (int i = 0; i < 4; i++) {
            Location splatPoint = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.15, 0,
                    (random.nextDouble() - 0.5) * 0.15);
            world.spawnParticle(Particle.DUST, splatPoint, 1,
                    0.005, 0.001, 0.005, 0,
                    new Particle.DustOptions(color, size * 0.7f));
        }
    }


    private void spawnBloodCough(Player player, Vector dir) {
        plugin.getAchievementManager().grant(player, CigaroAchievement.COUGH_BLOOD);

        Location mouthLoc = player.getEyeLocation().clone()
                .add(dir.clone().multiply(0.35))
                .add(0, -0.15, 0);

        player.getWorld().spawnParticle(
                Particle.BLOCK, mouthLoc, 8,
                0.08, 0.04, 0.08, 0.3,
                Material.REDSTONE_BLOCK.createBlockData());

        player.getWorld().spawnParticle(
                Particle.BLOCK, mouthLoc, 5,
                0.06, 0.03, 0.06, 0.5,
                Material.RED_CONCRETE.createBlockData());

        for (int wave = 0; wave < 3; wave++) {
            final int w = wave;

            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;

                        Location waveLoc = player.getEyeLocation().clone()
                                .add(dir.clone().multiply(0.4 + w * 0.25))
                                .add(0, -0.15 - w * 0.08, 0)
                                .add((random.nextDouble() - 0.5) * 0.12, 0,
                                        (random.nextDouble() - 0.5) * 0.12);

                        player.getWorld().spawnParticle(
                                Particle.BLOCK, waveLoc, 4 - w,
                                0.06 + w * 0.03, 0.04 + w * 0.02,
                                0.06 + w * 0.03, 0.4,
                                Material.REDSTONE_BLOCK.createBlockData());

                        player.getWorld().spawnParticle(
                                Particle.BLOCK, waveLoc, 3 - w,
                                0.05 + w * 0.02, 0.03 + w * 0.02,
                                0.05 + w * 0.02, 0.6,
                                Material.RED_CONCRETE.createBlockData());
                    }, wave * 3L);
        }

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;

                    Location groundLoc = player.getLocation().clone()
                            .add(dir.clone().multiply(0.5 + random.nextDouble() * 0.8));
                    groundLoc.setY(player.getLocation().getY() + 0.05);

                    player.getWorld().spawnParticle(
                            Particle.BLOCK, groundLoc, 6,
                            0.2, 0.01, 0.2, 0.1,
                            Material.REDSTONE_BLOCK.createBlockData());

                    player.getWorld().spawnParticle(
                            Particle.BLOCK, groundLoc, 4,
                            0.15, 0.01, 0.15, 0.05,
                            Material.RED_CONCRETE.createBlockData());
                }, 10L);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_PLAYER_SPLASH, 0.15f, 1.5f);
                }, 5L);
    }


    private Location findGround(Location from) {
        Location check = from.clone();

        for (int i = 0; i < 5; i++) {
            Block block = check.getBlock();
            if (block.getType().isSolid()) {
                return new Location(check.getWorld(),
                        check.getX(), check.getBlockY() + 1.02, check.getZ());
            }
            check.add(0, -1, 0);
        }
        return null;
    }


    private void phaseRecover(Player player, int stage) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_BREATH, 0.15f, 1.2f);

        if (stage >= 4) {
            player.sendActionBar(
                    plugin.getLanguageManager().get("cough_recover_severe"));
        } else {
            player.sendActionBar(Component.empty());
        }
    }

    public boolean isCoughing(UUID uuid) {
        return currentlyCoughing.contains(uuid);
    }

    public void cleanup(UUID uuid) {
        coughCooldown.remove(uuid);
        currentlyCoughing.remove(uuid);
        runTicks.remove(uuid);
        wasUnderwater.remove(uuid);
    }
}
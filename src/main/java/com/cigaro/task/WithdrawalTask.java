package com.cigaro.task;

import com.cigaro.Cigaro;
import com.cigaro.data.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class WithdrawalTask implements Runnable {

    private final Cigaro plugin;
    private final Random random = new Random();
    private int taskId = -1;
    private int rapidTaskId = -1;

    private final Map<UUID, Long> nextWaveTime = new HashMap<>();

    private final Map<UUID, Float> lastYaws = new HashMap<>();
    private final Map<UUID, Float> lastPitches = new HashMap<>();
    private final Map<UUID, Long> lastMoveTimes = new HashMap<>();
    private final Map<UUID, Boolean> afkPlayers = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    private final Map<UUID, Long> lastBoxChecks = new HashMap<>();

    private static final long BOX_CHECK_INTERVAL_MS = 10_000L;

    private static final String[] CRAVING_LIGHT_KEYS = {
            "craving_light_1", "craving_light_2", "craving_light_3"
    };
    private static final String[] CRAVING_MEDIUM_KEYS = {
            "craving_medium_1", "craving_medium_2", "craving_medium_3",
            "craving_medium_4", "craving_medium_5"
    };
    private static final String[] CRAVING_HEAVY_KEYS = {
            "craving_heavy_1", "craving_heavy_2", "craving_heavy_3",
            "craving_heavy_4", "craving_heavy_5", "craving_heavy_6",
            "craving_heavy_7", "craving_heavy_8"
    };
    private static final String[] CRAVING_SEVERE_KEYS = {
            "craving_severe_1", "craving_severe_2", "craving_severe_3",
            "craving_severe_4", "craving_severe_5", "craving_severe_6",
            "craving_severe_7", "craving_severe_8"
    };

    public WithdrawalTask(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        rescheduleWithdrawal();

        rapidTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tickAfkAndRegen, 40L, 40L)
                .getTaskId();
    }

    public void rescheduleWithdrawal() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        long interval = plugin.getConfigManager().getWithdrawalCheckSeconds() * 20L;

        taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this, interval, interval)
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        if (rapidTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(rapidTaskId);
        }
    }

    public boolean isInGracePeriod(PlayerData data) {
        if (data.getLastSmokeTime() <= 0) return false;
        long timeSinceSmoke = System.currentTimeMillis() - data.getLastSmokeTime();
        long graceMs = plugin.getConfigManager().getWithdrawalGraceMinutes() * 60_000L;
        return timeSinceSmoke < graceMs;
    }

    public void triggerActivityCheck(Player player, String activity) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        int stage = data.getAddictionStage();
        if (stage < 2) return;
        if (isInGracePeriod(data) || data.isPatchActive()) return;

        double chance = switch (activity) {
            case "mining" -> stage * 0.03;
            case "eating" -> stage * 0.04;
            case "running" -> stage * 0.02;
            case "combat" -> stage * 0.05;
            default -> 0.0;
        };

        if (random.nextDouble() > chance) return;
        nextWaveTime.put(uuid, 0L);
    }


    private void tickAfkAndRegen() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData data = plugin.getPlayerDataManager().getData(uuid);

            int stage = data.getAddictionStage();
            if (stage < 2 || isInGracePeriod(data)) {
                afkPlayers.put(uuid, false);
                continue;
            }

            Location loc = player.getLocation();
            float yaw = loc.getYaw();
            float pitch = loc.getPitch();

            Float lastYaw = lastYaws.get(uuid);
            Float lastPitch = lastPitches.get(uuid);

            if (lastYaw == null || lastPitch == null || Math.abs(yaw - lastYaw) > 0.1f || Math.abs(pitch - lastPitch) > 0.1f) {
                lastYaws.put(uuid, yaw);
                lastPitches.put(uuid, pitch);
                lastMoveTimes.put(uuid, now);
                afkPlayers.put(uuid, false);
            } else {
                long lastMove = lastMoveTimes.getOrDefault(uuid, now);
                if (now - lastMove > 60000L) {
                    afkPlayers.put(uuid, true);
                }
            }

            if (afkPlayers.getOrDefault(uuid, false) && !data.isPatchActive()) {
                if (player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                }

                Long lastDmg = lastDamageTimes.get(uuid);
                boolean damageReady = lastDmg == null || now - lastDmg > 60000L;
                Long lastCheck = lastBoxChecks.get(uuid);
                boolean checkReady = lastCheck == null || now - lastCheck > BOX_CHECK_INTERVAL_MS;

                if (damageReady && checkReady) {
                    lastBoxChecks.put(uuid, now);
                    if (isInBox(player)) {
                        lastDamageTimes.put(uuid, now);
                        player.damage(1.0);
                        player.sendMessage(plugin.getLanguageManager().get("withdrawal_claustrophobia_damage"));
                    }
                }
            }
        }
    }

    private boolean isInBox(Player player) {
        Location loc = player.getLocation();
        Block headBlock = player.getEyeLocation().getBlock();

        if (headBlock.getLightFromSky() > 0) return false;

        BlockFace[] faces = {
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        };

        Block feetBlock = loc.getBlock();

        int solidCount = 0;
        for (BlockFace face : faces) {
            if (headBlock.getRelative(face).getType().isSolid()) solidCount++;
        }

        if (feetBlock.getRelative(BlockFace.DOWN).getType().isSolid()) solidCount++;
        for (BlockFace face : faces) {
            if (face != BlockFace.UP && feetBlock.getRelative(face).getType().isSolid()) solidCount++;
        }

        return solidCount >= 6;
    }


    @Override
    public void run() {
        int checkSeconds = plugin.getConfigManager().getWithdrawalCheckSeconds();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData data = plugin.getPlayerDataManager().getData(uuid);

            int mStage = data.getPsychologicalStage();
            int maxStage = data.getAddictionStage();

            if (maxStage == 0) {
                if (data.wasAddicted()) {
                    tickLungRecovery(player, data, checkSeconds);
                    plugin.getPlayerDataManager().save(uuid);
                }
                continue;
            }

            data.resetLungRecoveryProgress();

            if (!data.isPatchActive() && !data.isPreWarningSent()) {
                long timeSinceSmoke = System.currentTimeMillis() - data.getLastSmokeTime();
                long graceMs = plugin.getConfigManager().getWithdrawalGraceMinutes() * 60_000L;
                long alertMs = graceMs - (3 * 60_000L);

                if (timeSinceSmoke >= alertMs && timeSinceSmoke < graceMs) {
                    data.setPreWarningSent(true);
                    player.sendMessage(plugin.getLanguageManager().get("withdrawal_pre_warning"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.5f, 0.8f);
                }
            }

            if (isInGracePeriod(data)) {
                nextWaveTime.remove(uuid);
                continue;
            }

            boolean isAfk = afkPlayers.getOrDefault(uuid, false);

            if (!isAfk && !data.isPatchActive() && mStage >= 2 && random.nextDouble() < 0.05) {
                triggerInvoluntaryGesture(player);
            }

            long secondsToAdd = checkSeconds;
            if (isAfk) {
                secondsToAdd = (long) (checkSeconds * 0.3);
            }

            double hourFraction = secondsToAdd / 3600.0;

            double decay = PlayerData.scoreDecayPerHour() * hourFraction;
            double oldPhysical = data.getPhysicalScore(), oldPsychological = data.getPsychologicalScore();
            data.setPhysicalScore(data.getPhysicalScore() - decay);
            data.setPsychologicalScore(data.getPsychologicalScore() - decay * 1.2);

            if (oldPhysical != data.getPhysicalScore() || oldPsychological != data.getPsychologicalScore())
                plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroAddictionChangeEvent(
                        uuid, oldPhysical, oldPsychological, data.getPhysicalScore(), data.getPsychologicalScore()));
            data.checkAndTriggerPhysicalStageDown(player);
            data.checkAndTriggerPsychologicalStageDown(player);

            if (data.getAddictionStage() < maxStage) {
                onStageDropped(player, data, uuid);
                if (data.getAddictionStage() == 0) {
                    plugin.getPlayerDataManager().save(uuid);
                    continue;
                }
                maxStage = data.getAddictionStage();
            }

            if (random.nextDouble() < 0.02) {
                data.checkAndTriggerPhysicalStageUp(player);
                data.checkAndTriggerPsychologicalStageUp(player);
            }

            if (data.isPatchActive()) {
                data.addOnlineSoberSeconds(secondsToAdd);
                double decayPerCheck = plugin.getConfigManager()
                        .getToleranceDecayPerHour() * (checkSeconds / 3600.0);
                for (CigaretteType type : CigaretteType.values()) {
                    double current = data.getTolerance(type);
                    if (current > 0) {
                        data.addTolerance(type, -decayPerCheck);
                    }
                }
                plugin.getPlayerDataManager().save(uuid);
                continue;
            }

            data.addOnlineSoberSeconds(secondsToAdd);

            double decayPerCheck = plugin.getConfigManager()
                    .getToleranceDecayPerHour() * (checkSeconds / 3600.0);
            for (CigaretteType type : CigaretteType.values()) {
                double current = data.getTolerance(type);
                if (current > 0) {
                    data.addTolerance(type, -decayPerCheck);
                }
            }

            double worstScore = Math.max(data.getPhysicalScore(), data.getPsychologicalScore());
            double lower = PlayerData.stageThreshold(maxStage);
            double upper = maxStage < 4 ? PlayerData.stageThreshold(maxStage + 1) : PlayerData.maxScore();
            if (maxStage >= 2 && upper > lower && worstScore <= lower + (upper - lower) * 0.2) {
                handleCrisis(player, data, uuid, maxStage);
                continue;
            }

            long waveNow = System.currentTimeMillis();
            Long nextWave = nextWaveTime.get(uuid);

            if (nextWave == null || waveNow >= nextWave) {
                executeWave(player, data, uuid, maxStage, waveNow);
            }

            plugin.getPlayerDataManager().save(uuid);
        }
    }

    private void onStageDropped(Player player, PlayerData data, UUID uuid) {
        nextWaveTime.remove(uuid);
        data.resetOnlineSoberSeconds();

        if (data.getAddictionStage() == 0) {
            removeAllWithdrawalEffects(player);
        }

        plugin.getAchievementManager().checkAddictionAchievements(player, data);
    }

    private void tickLungRecovery(Player player, PlayerData data, int seconds) {
        if (!plugin.getConfigManager().isLungRecoveryEnabled()) return;
        if (!data.wasAddicted() || data.getAddictionStage() > 0) return;
        if (isInGracePeriod(data)) return;

        int maxHearts = plugin.getLungDamageManager()
                .getRecoverableHearts(data.getTotalSmoked());
        if (data.getLungRecoveredHearts() >= maxHearts) return;

        data.addLungRecoverySeconds(seconds);
        long required = plugin.getConfigManager().getLungRecoverySecondsPerHeart();
        if (data.getLungRecoverySeconds() < required) return;

        data.setLungRecoverySeconds(data.getLungRecoverySeconds() - required);
        int recovered = data.getLungRecoveredHearts() + 1;
        data.setLungRecoveredHearts(recovered);

        plugin.getLungDamageManager().updateLungHealth(player, data);
        player.sendMessage(plugin.getLanguageManager().get("lung_recovery_heart",
                Placeholder.unparsed("hearts", String.valueOf(recovered)),
                Placeholder.unparsed("max", String.valueOf(maxHearts))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
    }

    private void triggerInvoluntaryGesture(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int smokeSlot = -1;
        ItemStack smokeItem = null;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && (plugin.getItemManager().isCigarette(item) || plugin.getItemManager().isCigar(item) || plugin.getItemManager().isPipe(item))) {
                smokeSlot = i;
                smokeItem = item;
                break;
            }
        }

        if (smokeItem == null) return;

        if (smokeSlot >= 0 && smokeSlot < 9) {
            inv.setHeldItemSlot(smokeSlot);
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_hand_reaching"));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.4f, 1.2f);
            return;
        }

        int targetSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack hotbarItem = inv.getItem(i);
            if (hotbarItem == null || hotbarItem.getType().isAir()) {
                targetSlot = i;
                break;
            }
        }

        if (targetSlot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack hotbarItem = inv.getItem(i);
                if (hotbarItem != null && !isProtectedTool(hotbarItem.getType())) {
                    targetSlot = i;
                    break;
                }
            }
        }

        if (targetSlot != -1) {
            ItemStack swap = inv.getItem(targetSlot);
            inv.setItem(targetSlot, smokeItem);
            inv.setItem(smokeSlot, swap);
            inv.setHeldItemSlot(targetSlot);
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_hand_reaching"));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.4f, 1.2f);
        }
    }

    private boolean isProtectedTool(Material mat) {
        String name = mat.name();
        return name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW")
                || name.contains("TRIDENT") || name.contains("PICKAXE") || name.contains("AXE")
                || name.contains("SHOVEL") || name.contains("HOE") || name.contains("SHIELD")
                || name.contains("STEW") || name.contains("SOUP") || name.contains("POTION")
                || name.contains("GOLDEN_APPLE") || mat == Material.ENDER_PEARL || mat == Material.TOTEM_OF_UNDYING;
    }

    private void triggerConditionedCues(Player player, PlayerData data) {
        if (random.nextDouble() > 0.05) return;

        boolean rainActive = player.getWorld().hasStorm();
        boolean groupActive = player.getNearbyEntities(8.0, 8.0, 8.0).stream()
                .filter(e -> e instanceof Player)
                .count() >= 2;

        if (rainActive && data.getRainSmokes() >= 8) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, false, false));
            if (random.nextBoolean()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 0.9f);
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_cue_rain"));
        } else if (groupActive && data.getGroupSmokes() >= 8) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, true, false, false));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 0.9f);
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_cue_group"));
        }
    }

    private void handleCrisis(Player player, PlayerData data,
                              UUID uuid, int stage) {
        long waveNow = System.currentTimeMillis();
        Long nextWave = nextWaveTime.get(uuid);

        if (nextWave != null && waveNow < nextWave) {
            plugin.getPlayerDataManager().save(uuid);
            return;
        }

        applyCrisisWithdrawal(player, stage);

        player.sendMessage(plugin.getLanguageManager().get("withdrawal_crisis"));

        int crisisDelay = 10 + random.nextInt(30);
        nextWaveTime.put(uuid, waveNow + crisisDelay * 1000L);

        plugin.getPlayerDataManager().save(uuid);
    }

    private void executeWave(Player player, PlayerData data,
                             UUID uuid, int stage, long waveNow) {
        applyWithdrawal(player, stage);

        int minDelay = switch (stage) {
            case 1 -> 90;
            case 2 -> 60;
            case 3 -> 30;
            case 4 -> 15;
            default -> 120;
        };
        int maxDelay = switch (stage) {
            case 1 -> 240;
            case 2 -> 180;
            case 3 -> 120;
            case 4 -> 90;
            default -> 300;
        };

        int delaySec = minDelay + random.nextInt(maxDelay - minDelay + 1);
        nextWaveTime.put(uuid, waveNow + delaySec * 1000L);

        sendCravingMessage(player, stage);

        if (stage >= 3 && random.nextDouble() < 0.40) {
            player.playSound(player.getLocation(),
                    Sound.ITEM_FLINTANDSTEEL_USE, 0.4f, 1.0f);
        }

        if (stage >= 3 && random.nextDouble() < 0.08) {
            spawnFakeSound(player);
        }
    }

    private void applyWithdrawal(Player player, int stage) {
        int duration = switch (stage) {
            case 1 -> 200;
            case 2 -> 300;
            case 3 -> 400;
            case 4 -> 600;
            default -> 200;
        };

        switch (stage) {
            case 1 -> {
                if (random.nextDouble() < 0.3) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, duration, 0, true, false, false));
                }
            }
            case 2 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 0, true, false, false));
                if (random.nextDouble() < 0.4) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.WEAKNESS, duration, 0, true, false, false));
                }
            }
            case 3 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 0, true, false, false));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE, duration, 0, true, false, false));
                if (random.nextDouble() < 0.25) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.NAUSEA, 100, 0, true, false, false));
                }
            }
            case 4 -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, duration, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, duration, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE, duration, 1, true, false, false));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HUNGER, duration, 0, true, false, false));
                if (random.nextDouble() < 0.40) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.NAUSEA, 140, 0, true, false, false));
                }
                if (random.nextDouble() < 0.15) {
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.DARKNESS, 80, 0, true, false, false));
                }
            }
        }
    }

    private void applyCrisisWithdrawal(Player player, int stage) {
        int duration = 400;
        int amp = Math.min(2, stage - 1);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, duration, amp, true, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS, duration, amp, true, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.MINING_FATIGUE, duration, amp, true, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, 160, 0, true, false, false));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline() || plugin.getPlayerDataManager().getData(player.getUniqueId()).isPatchActive()) return;
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.DARKNESS, 30, 0, true, false, false));
                    }, 20L + idx * 40L);
        }

        player.playSound(player.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.9f);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline() || plugin.getPlayerDataManager().getData(player.getUniqueId()).isPatchActive()) return;
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.0f);
                }, 20L);

        applyRedScreen(player);
    }

    private void applyRedScreen(Player player) {
        org.bukkit.WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(player.getLocation());
        border.setSize(10000);
        border.setWarningDistance(10000);
        player.setWorldBorder(border);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setWorldBorder(null);
            }
        }, 60L);
    }

    private void removeAllWithdrawalEffects(Player player) {
        PotionEffectType[] effects = {
                PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS,
                PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
                PotionEffectType.HUNGER, PotionEffectType.DARKNESS
        };
        for (PotionEffectType effect : effects) {
            if (player.hasPotionEffect(effect)) {
                player.removePotionEffect(effect);
            }
        }
    }

    private void sendCravingMessage(Player player, int stage) {
        String[] pool = switch (stage) {
            case 1 -> CRAVING_LIGHT_KEYS;
            case 2 -> CRAVING_MEDIUM_KEYS;
            case 3 -> CRAVING_HEAVY_KEYS;
            case 4 -> CRAVING_SEVERE_KEYS;
            default -> CRAVING_LIGHT_KEYS;
        };

        String key = pool[random.nextInt(pool.length)];
        player.sendMessage(plugin.getLanguageManager().get(key));
    }

    private void spawnFakeSound(Player player) {
        int delay = random.nextInt(100);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;

                    Sound fakeSound = switch (random.nextInt(4)) {
                        case 0 -> Sound.ITEM_FLINTANDSTEEL_USE;
                        case 1 -> Sound.BLOCK_FIRE_EXTINGUISH;
                        case 2 -> Sound.ENTITY_ITEM_PICKUP;
                        default -> Sound.BLOCK_GRASS_PLACE;
                    };

                    double angle = random.nextDouble() * Math.PI * 2;
                    double dist = 3 + random.nextDouble() * 5;

                    Location soundLoc = player.getLocation().clone().add(
                            Math.cos(angle) * dist,
                            random.nextDouble() * 2 - 1,
                            Math.sin(angle) * dist);

                    player.playSound(soundLoc, fakeSound,
                            0.15f + random.nextFloat() * 0.2f,
                            0.8f + random.nextFloat() * 0.4f);
                }, delay);
    }

    public void cleanup(UUID uuid) {
        nextWaveTime.remove(uuid);
        lastYaws.remove(uuid);
        lastPitches.remove(uuid);
        lastMoveTimes.remove(uuid);
        afkPlayers.remove(uuid);
        lastDamageTimes.remove(uuid);
        lastBoxChecks.remove(uuid);
    }
}

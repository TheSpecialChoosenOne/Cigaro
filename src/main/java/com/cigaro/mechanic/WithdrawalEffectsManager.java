package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.station.StationBreakCheckEvent;
import com.cigaro.data.CigaroAchievement;
import com.cigaro.data.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class WithdrawalEffectsManager implements Listener {

    private final Cigaro plugin;
    private final Random random = new Random();
    private final NamespacedKey glitchBlockKey;

    private final Map<UUID, BukkitTask> bowShakeTasks = new HashMap<>();
    private final Map<UUID, Set<Location>> fakeOres = new HashMap<>();
    private final Map<UUID, BukkitTask> fakeOreTasks = new HashMap<>();
    private final Map<UUID, Long> miningInterruptCooldown = new HashMap<>();
    private final Map<UUID, Long> swingMissCooldown = new HashMap<>();
    private final Map<UUID, Long> blockGlitchCooldown = new HashMap<>();

    private static final long MINING_INTERRUPT_CD = 15000;
    private static final long SWING_MISS_CD = 10000;
    private static final long BLOCK_GLITCH_CD = 250;
    private static final int FAKE_ORE_RADIUS = 12;
    private static final int FAKE_ORE_VANISH_DIST = 4;

    private static final String[] SWING_MISS_KEYS = {
            "withdrawal_swing_miss_1", "withdrawal_swing_miss_2",
            "withdrawal_swing_miss_3", "withdrawal_swing_miss_4"
    };

    private static final String[] MINING_INTERRUPT_KEYS = {
            "withdrawal_mining_interrupt_1", "withdrawal_mining_interrupt_2",
            "withdrawal_mining_interrupt_3", "withdrawal_mining_interrupt_4"
    };

    public WithdrawalEffectsManager(Cigaro plugin) {
        this.plugin = plugin;
        this.glitchBlockKey = new NamespacedKey(plugin, "glitch_block");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickBowShake, 5L, 5L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickOres, 20L, 20L);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        if (data.getAddictionStage() < 2 || (data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        int oldFood = player.getFoodLevel();
        int newFood = event.getFoodLevel();

        if (newFood > oldFood) {
            int restored = newFood - oldFood;
            double reductionPct = 0.1 + random.nextDouble() * 0.1;
            int penalty = (int) Math.ceil(restored * reductionPct);

            event.setFoodLevel(newFood - penalty);
        }
    }

    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() < 2 || (data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        if (random.nextDouble() <= 0.30) {
            event.setCancelled(true);
            villager.shakeHead();
            player.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendActionBar(plugin.getLanguageManager().get("withdrawal_villager_refuse"));
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        if (data.getAddictionStage() < 2 || (data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        if (random.nextDouble() <= 0.20) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, true, false, false));
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_weak_knees"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() < 2 || (data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        if (random.nextDouble() <= 0.05) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);
            player.sendActionBar(plugin.getLanguageManager().get("withdrawal_fumble_block"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakGlitch(BlockBreakEvent event) {
        if (event instanceof StationBreakCheckEvent) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        if (data.getAddictionStage() == 4) {
            long now = System.currentTimeMillis();
            Long last = blockGlitchCooldown.get(uuid);
            if (last != null && (now - last) < BLOCK_GLITCH_CD) return;
            blockGlitchCooldown.put(uuid, now);

            Block block = event.getBlock();
            org.bukkit.block.data.BlockData blockData = block.getBlockData();
            Location loc = block.getLocation();

            BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, e -> {
                e.setBlock(blockData);
                e.setPersistent(false);
                e.getPersistentDataContainer().set(glitchBlockKey, PersistentDataType.BYTE, (byte) 1);
            });

            plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);
        int stage = data.getAddictionStage();

        if (stage < 2) return;
        if ((data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        double missChance = switch (stage) {
            case 2 -> 0.05;
            case 3 -> 0.12;
            case 4 -> 0.20;
            default -> 0.0;
        };

        long now = System.currentTimeMillis();
        Long lastMiss = swingMissCooldown.get(uuid);
        if (lastMiss != null && (now - lastMiss) < SWING_MISS_CD) return;

        if (random.nextDouble() > missChance) return;

        swingMissCooldown.put(uuid, now);
        event.setCancelled(true);

        double sideForce = (random.nextBoolean() ? 1 : -1) * (0.08 + stage * 0.04);
        Vector right = player.getLocation().getDirection().normalize().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector miss = right.multiply(sideForce);
        miss.setY(0.02);

        player.setVelocity(player.getVelocity().add(miss));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 0.6f);

        String key = SWING_MISS_KEYS[random.nextInt(SWING_MISS_KEYS.length)];
        player.sendActionBar(plugin.getLanguageManager().get(key));
    }

    private void tickBowShake() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isHandRaised()) continue;

            Material mat = player.getInventory().getItemInMainHand().getType();
            if (mat != Material.BOW && mat != Material.CROSSBOW && mat != Material.TRIDENT) continue;

            PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
            int stage = data.getAddictionStage();
            if (stage < 2) continue;
            if ((data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) continue;

            applyAimShake(player, stage);
        }
    }

    private void tickOres() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getY() >= 50) continue;

            PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
            int stage = data.getAddictionStage();
            if (stage < 3) continue;
            if ((data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) continue;

            tickFakeOres(player, stage);
        }
    }

    private void applyAimShake(Player player, int stage) {
        float intensity = switch (stage) {
            case 2 -> 0.3f;
            case 3 -> 0.7f;
            case 4 -> 1.5f;
            default -> 0.1f;
        };

        if (random.nextDouble() > 0.6) return;

        float dYaw = (random.nextFloat() - 0.5f) * intensity;
        float dPitch = (random.nextFloat() - 0.5f) * intensity;

        Location loc = player.getLocation();
        float newYaw = loc.getYaw() + dYaw;
        float newPitch = Math.max(-90f, Math.min(90f, loc.getPitch() + dPitch));

        player.setRotation(newYaw, newPitch);
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        int stage = data.getAddictionStage();
        if (stage < 3) return;
        if ((data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        double interruptChance = switch (stage) {
            case 3 -> 0.06;
            case 4 -> 0.15;
            default -> 0.0;
        };

        long now = System.currentTimeMillis();
        Long lastInterrupt = miningInterruptCooldown.get(uuid);
        if (lastInterrupt != null && (now - lastInterrupt) < MINING_INTERRUPT_CD) return;

        if (random.nextDouble() > interruptChance) return;

        miningInterruptCooldown.put(uuid, now);

        int delay = 10 + random.nextInt(20);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.getPlayerDataManager().getData(player.getUniqueId()).isPatchActive()) return;
            if (!player.isSneaking() && player.getTargetBlockExact(5) != null) {
                interruptMining(player, stage);
            }
        }, delay);
    }

    private void interruptMining(Player player, int stage) {
        Vector push = new Vector(
                (random.nextDouble() - 0.5) * 0.06, 0.05, (random.nextDouble() - 0.5) * 0.06);
        player.setVelocity(player.getVelocity().add(push));

        player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.5f, 0.5f);
        String key = MINING_INTERRUPT_KEYS[random.nextInt(MINING_INTERRUPT_KEYS.length)];
        player.sendActionBar(plugin.getLanguageManager().get(key));
    }

    private void tickFakeOres(Player player, int stage) {
        UUID uuid = player.getUniqueId();
        Location playerLoc = player.getLocation();

        Set<Location> fakes = fakeOres.getOrDefault(uuid, new HashSet<>());
        Iterator<Location> it = fakes.iterator();

        while (it.hasNext()) {
            Location fakeLoc = it.next();

            if (!Objects.equals(fakeLoc.getWorld(), playerLoc.getWorld())) {
                it.remove();
                continue;
            }

            if (!fakeLoc.getWorld().isChunkLoaded(fakeLoc.getBlockX() >> 4, fakeLoc.getBlockZ() >> 4)) {
                it.remove();
                continue;
            }

            if (fakeLoc.distance(playerLoc) < FAKE_ORE_VANISH_DIST) {
                Block real = fakeLoc.getBlock();
                player.sendBlockChange(fakeLoc, real.getBlockData());
                it.remove();

                if (real.getType() == Material.STONE || real.getType() == Material.DEEPSLATE) {
                    player.playSound(fakeLoc, Sound.BLOCK_STONE_BREAK, 0.3f, 0.5f);
                    if (random.nextDouble() < 0.4) {
                        player.sendActionBar(plugin.getLanguageManager().get("withdrawal_fake_ore_vanish"));
                    }
                }
            }
        }

        double spawnChance = switch (stage) {
            case 3 -> 0.012;
            case 4 -> 0.032;
            default -> 0.0;
        };

        if (random.nextDouble() > spawnChance) return;
        if (fakes.size() >= 3) return;

        Location fakeLoc = findStoneNearby(player);
        if (fakeLoc == null) return;

        Material fakeOre = pickFakeOre(fakeLoc);
        player.sendBlockChange(fakeLoc, Bukkit.createBlockData(fakeOre));
        fakes.add(fakeLoc);
        fakeOres.put(uuid, fakes);

        plugin.getAchievementManager().grant(player, CigaroAchievement.FAKE_DIAMONDS);
    }

    private Location findStoneNearby(Player player) {
        Location center = player.getLocation();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(FAKE_ORE_RADIUS * 2) - FAKE_ORE_RADIUS;
            int dy = random.nextInt(6) - 3;
            int dz = random.nextInt(FAKE_ORE_RADIUS * 2) - FAKE_ORE_RADIUS;

            Location check = center.clone().add(dx, dy, dz);
            if (!check.getWorld().isChunkLoaded(check.getBlockX() >> 4, check.getBlockZ() >> 4)) continue;

            Block block = check.getBlock();

            if (block.getType() == Material.STONE || block.getType() == Material.DEEPSLATE) {
                if (hasExposedFace(block)) {
                    if (check.distance(center) > FAKE_ORE_VANISH_DIST + 1) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasExposedFace(Block block) {
        return block.getRelative(0, 1, 0).getType().isAir()
                || block.getRelative(0, -1, 0).getType().isAir()
                || block.getRelative(1, 0, 0).getType().isAir()
                || block.getRelative(-1, 0, 0).getType().isAir()
                || block.getRelative(0, 0, 1).getType().isAir()
                || block.getRelative(0, 0, -1).getType().isAir();
    }

    private Material pickFakeOre(Location loc) {
        boolean deep = loc.getY() < 0;
        double roll = random.nextDouble();

        if (deep) {
            if (roll < 0.15) return Material.DEEPSLATE_DIAMOND_ORE;
            if (roll < 0.35) return Material.DEEPSLATE_EMERALD_ORE;
            if (roll < 0.55) return Material.DEEPSLATE_GOLD_ORE;
            if (roll < 0.75) return Material.DEEPSLATE_LAPIS_ORE;
            return Material.DEEPSLATE_IRON_ORE;
        } else {
            if (roll < 0.15) return Material.DIAMOND_ORE;
            if (roll < 0.35) return Material.EMERALD_ORE;
            if (roll < 0.55) return Material.GOLD_ORE;
            if (roll < 0.75) return Material.LAPIS_ORE;
            return Material.IRON_ORE;
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        int stage = data.getAddictionStage();
        if (stage < 2) return;
        if ((data.isPatchActive() || plugin.getWithdrawalTask().isInGracePeriod(data))) return;

        double deviateChance = switch (stage) {
            case 2 -> 0.08;
            case 3 -> 0.18;
            case 4 -> 0.30;
            default -> 0.0;
        };

        if (random.nextDouble() > deviateChance) return;

        Vector velocity = event.getProjectile().getVelocity();
        double deviation = stage * 0.08;

        velocity.setX(velocity.getX() + (random.nextDouble() - 0.5) * deviation);
        velocity.setY(velocity.getY() + (random.nextDouble() - 0.5) * deviation * 0.5);
        velocity.setZ(velocity.getZ() + (random.nextDouble() - 0.5) * deviation);

        event.getProjectile().setVelocity(velocity);
        player.sendActionBar(plugin.getLanguageManager().get("withdrawal_bow_tremble"));
    }

    public void cleanup(UUID uuid) {
        Set<Location> fakes = fakeOres.remove(uuid);
        if (fakes != null) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                for (Location loc : fakes) {
                    if (Objects.equals(loc.getWorld(), player.getWorld())
                            && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        player.sendBlockChange(loc, loc.getBlock().getBlockData());
                    }
                }
            }
        }

        BukkitTask task = bowShakeTasks.remove(uuid);
        if (task != null) task.cancel();

        BukkitTask oreTask = fakeOreTasks.remove(uuid);
        if (oreTask != null) oreTask.cancel();

        miningInterruptCooldown.remove(uuid);
        swingMissCooldown.remove(uuid);
        blockGlitchCooldown.remove(uuid);
    }
}


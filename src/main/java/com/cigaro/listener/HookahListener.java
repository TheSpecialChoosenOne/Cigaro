package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.*;
import com.cigaro.mechanic.PassiveSmokingManager;
import com.cigaro.station.HookahStation;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class HookahListener implements Listener {

    private final Cigaro plugin;
    private final Map<Location, Long> interactLock = new HashMap<>();
    private final Set<UUID> inhaling = new HashSet<>();
    private final Map<UUID, Bat> activeAnchors = new HashMap<>();
    private static final int PUFFS_PER_SMOKE = 5;
    private final Map<UUID, Integer> puffCounters = new HashMap<>();
    private static final long LOCK_MS = 300;
    private static final double PASSIVE_RADIUS = 5.0;
    private static final int INHALE_TICKS = 40;

    public HookahListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private boolean tryLock(Location loc) {
        long now = System.currentTimeMillis();
        Long last = interactLock.get(loc);
        if (last != null && (now - last) < LOCK_MS) return false;
        interactLock.put(loc, now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        ItemStack item = event.getItemInHand();
        if (!plugin.getItemManager().isHookah(item)) return;

        Block block = event.getBlock();
        HookahStation station = new HookahStation(block.getLocation());
        plugin.getStationManager().addHookahStation(station);

        event.getPlayer().sendMessage(
                plugin.getLanguageManager().get("hookah_placed")
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHookahInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        Location loc = plugin.getStationManager().getInteractionLocation(entity.getUniqueId());
        if (loc == null) return;

        HookahStation station = plugin.getStationManager().getHookahStation(loc);
        if (station == null) return;

        event.setCancelled(true);
        if (!tryLock(loc)) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.WATER_BUCKET && !station.hasWater()) {
            station.addWater();
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
            }
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.7f, 1.0f);
            player.sendMessage(plugin.getLanguageManager().get("hookah_water_added"));
            plugin.getDatabaseManager().saveHookahStation(station);
            return;
        }

        if (plugin.getItemManager().isCutTobacco(hand)
                || plugin.getItemManager().isBlendedCutTobacco(hand)) {
            if (!station.hasWater()) {
                player.sendMessage(plugin.getLanguageManager().get("hookah_need_water"));
                return;
            }

            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (!plugin.getItemManager().isFlavorItem(offHand)) {
                player.sendMessage(plugin.getLanguageManager().get("hookah_need_flavor_offhand"));
                return;
            }

            CigaretteType flavorType = plugin.getItemManager().getFlavorType(offHand);
            if (flavorType == null) return;

            BlendData blend = plugin.getItemManager().getBlendData(hand);
            if (blend != null) {
                station.fillBlend(blend);
            } else {
                station.fill(flavorType);
            }

            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
                offHand.setAmount(offHand.getAmount() - 1);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.2f);
            player.sendMessage(
                    plugin.getLanguageManager().get("hookah_loaded",
                            Placeholder.unparsed("charges", String.valueOf(station.getChargesLeft())))
            );
            plugin.getDatabaseManager().saveHookahStation(station);
            return;
        }

        if (station.isReady()) {
            if (!station.canPuff(player.getUniqueId())) return;
            if (inhaling.contains(player.getUniqueId())) return;

            startInhale(player, station);
            return;
        }

        showStatus(player, station);
    }

    private void startInhale(Player player, HookahStation station) {
        UUID uuid = player.getUniqueId();

        if (plugin.getSmokingListener().isSmoking(uuid)) {
            player.sendMessage(plugin.getLanguageManager().get("hookah_finish_cigarette"));
            return;
        }

        Location hookahLoc = station.getLocation();
        if (!Objects.equals(player.getWorld(), hookahLoc.getWorld())
                || player.getLocation().distance(hookahLoc.clone().add(0.5, 0.5, 0.5)) > 4.0) {
            player.sendMessage(plugin.getLanguageManager().get("hookah_too_far"));
            return;
        }

        inhaling.add(uuid);

        Location anchorLoc = hookahLoc.clone().add(0.5, 0.8, 0.5);
        Bat anchor = hookahLoc.getWorld().spawn(anchorLoc, Bat.class, bat -> {
            bat.setInvisible(true);
            bat.setInvulnerable(true);
            bat.setAI(false);
            bat.setSilent(true);
            bat.setAwake(true);
            bat.setPersistent(false);
            bat.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "hookah_anchor"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        });

        activeAnchors.put(uuid, anchor);
        anchor.setLeashHolder(player);

        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.1f, 2.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(hookahLoc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.3f, 1.8f);

            hookahLoc.getWorld().spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE,
                    anchorLoc, 3, 0.1, 0.1, 0.1, 0.01
            );
        }, 5L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                anchor.setLeashHolder(null);
            } catch (Exception ignored) {}
            anchor.remove();
            activeAnchors.remove(uuid);
            inhaling.remove(uuid);

            if (!player.isOnline()) return;

            finishInhale(player, station);
        }, INHALE_TICKS);
    }

    @EventHandler
    public void onLeashDrop(EntityDropItemEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "hookah_anchor"),
                PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "hookah_anchor"),
                PersistentDataType.BYTE)) {
            event.setDropLeash(false);
            event.getEntity().remove();
        }
    }

    private void finishInhale(Player player, HookahStation station) {
        UUID uuid = player.getUniqueId();

        CigaretteType smokedType = resolveFlavorType(station);
        BlendData smokedBlend = station.getBlendData();

        station.recordPuff(uuid);
        station.puff();

        plugin.getAnalyticsManager().recordSmoke("hookah", smokedType);

        long nearbyCount = player.getNearbyEntities(
                        PASSIVE_RADIUS, PASSIVE_RADIUS, PASSIVE_RADIUS).stream()
                .filter(e -> e instanceof Player)
                .count();
        if (nearbyCount >= 3) {
            plugin.getAchievementManager().grant(player, CigaroAchievement.HOOKAH_PARTY);
        }

        Vector dir = player.getEyeLocation().getDirection().normalize();

        for (int i = 0; i < 8; i++) {
            final int tick = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                Location pLoc = player.getEyeLocation().clone()
                        .add(dir.clone().multiply(0.3 + tick * 0.2));
                pLoc.add(0, tick * 0.05, 0);

                player.getWorld().spawnParticle(
                        Particle.CAMPFIRE_COSY_SMOKE,
                        pLoc, 5 - (tick / 2),
                        0.06 + tick * 0.02,
                        0.04 + tick * 0.015,
                        0.06 + tick * 0.02,
                        0.005
                );
            }, tick * 3L);
        }

        Location loc = station.getLocation();
        loc.getWorld().spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                loc.getX() + 0.5, loc.getY() + 1.5,
                loc.getZ() + 0.5,
                10, 0.15, 0.3, 0.15, 0.01
        );

        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.15f, 0.8f);
        }, 8L);

        double hookahMult = SmokableType.HOOKAH.getEffectMultiplier();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);
        if (smokedBlend != null) {
            applyTypeEffects(player, data, smokedBlend.getPrimary(), hookahMult * 0.5);
            applyTypeEffects(player, data, smokedBlend.getSecondary(), hookahMult * 0.5);
        } else {
            applyTypeEffects(player, data, smokedType, hookahMult);
        }

        updateAddiction(player, smokedType);

        player.sendActionBar(
                plugin.getLanguageManager().get("hookah_charges_bar",
                        Placeholder.unparsed("charges", String.valueOf(station.getChargesLeft())))
        );

        plugin.getPassiveSmokingManager().notifyNearby(
                player, PassiveSmokingManager.SmokeSource.HOOKAH, PASSIVE_RADIUS);

        plugin.getDatabaseManager().saveHookahStation(station);
        plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroTobaccoEvent(
                com.cigaro.api.CigaroTobaccoEvent.Action.HOOKAH_PUFF, player, station.getLocation(), null));
    }

    private void updateAddiction(Player player, CigaretteType type) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        if (!data.isPredispositionGenerated()) {
            plugin.getPlayerDataManager().generatePredisposition(data);
        }

        double hookahMult = SmokableType.HOOKAH.getEffectMultiplier();

        boolean wasInWithdrawal = !plugin.getWithdrawalTask().isInGracePeriod(data)
                && data.getAddictionStage() > 0;

        data.addTolerance(type, plugin.getConfigManager().getTolerancePerSmoke()
                * hookahMult / PUFFS_PER_SMOKE);
        data.setLastSmokeTime(System.currentTimeMillis());
        data.setPreWarningSent(false);
        data.resetOnlineSoberSeconds();
        data.resetLungRecoveryProgress();
        data.addMastery(0.0005);

        int puffs = puffCounters.merge(uuid, 1, Integer::sum);
        if (puffs >= PUFFS_PER_SMOKE) {
            puffCounters.remove(uuid);
            data.incrementTotalSmoked();
            data.incrementSmoked(type);
            data.damageLungRecovery();
            plugin.getAddictionCalculator().onSmoked(
                    data, type, CigaretteQuality.STANDARD, wasInWithdrawal, hookahMult);
            plugin.getLungDamageManager().updateLungHealth(player, data);
            plugin.getAchievementManager().checkSmokingAchievements(player, data);
            plugin.getAchievementManager().checkAddictionAchievements(player, data);
        }

        plugin.getPlayerDataManager().save(uuid);
    }

    private CigaretteType resolveFlavorType(HookahStation station) {
        if (station.getBlendData() != null) return station.getBlendData().getPrimary();
        if (station.getFlavorType() != null) return station.getFlavorType();
        return CigaretteType.HERBAL;
    }

    private void applyTypeEffects(Player player, PlayerData data,
                                  CigaretteType type, double mult) {
        int pred = data.getPredisposition(type);
        double tol = data.getTolerance(type);
        double tolMult = 1.0 - (tol * 0.7);

        if (pred <= -2) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 0, true, false, false));
            player.sendMessage(plugin.getLanguageManager().get("hookah_bad_choice"));
            return;
        }

        double finalMult = mult * tolMult * (pred >= 0 ? 1.0 : 0.3);
        boolean isFav = pred >= 2;

        for (SmokeEffect effect : plugin.getConfigManager().getSmokeEffects(type)) {
            int duration;
            int amp = effect.getAmplifier();

            if (effect.isSideEffect()) {
                duration = effect.getBaseDuration() * 20;
            } else {
                duration = Math.max(60, (int) (effect.getBaseDuration() * 20 * finalMult));
                if (isFav) amp += effect.getFavoriteBonusAmplifier();
            }

            player.addPotionEffect(
                    new PotionEffect(
                            effect.getEffectType(),
                            duration, amp,
                            true, false,
                            !effect.isSideEffect()
                    ));
        }
    }

    private void showStatus(Player player, HookahStation station) {
        player.sendMessage(plugin.getLanguageManager().get("hookah_status_header"));

        player.sendMessage(plugin.getLanguageManager().get("hookah_status_water",
                Placeholder.unparsed("status", station.hasWater() ? "✔" : "✘")));

        String flavor = plugin.getLanguageManager().getRaw("hookah_status_flavor_none");
        if (station.isBlend()) {
            BlendData blend = station.getBlendData();
            int pct = (int) Math.round(blend.getPrimaryRatio() * 100);
            flavor = pct + "% " + plugin.getItemManager().formatTypeName(blend.getPrimary()) + " / "
                    + (100 - pct) + "% " + plugin.getItemManager().formatTypeName(blend.getSecondary());
        } else if (station.getFlavorType() != null) {
            flavor = plugin.getItemManager().formatTypeName(station.getFlavorType());
        }

        player.sendMessage(plugin.getLanguageManager().get("hookah_status_flavor",
                Placeholder.unparsed("flavor", flavor)));

        player.sendMessage(plugin.getLanguageManager().get("hookah_status_charges",
                Placeholder.unparsed("charges", String.valueOf(station.getChargesLeft()))));

        if (!station.hasWater()) {
            player.sendMessage(plugin.getLanguageManager().get("hookah_hint_add_water"));
        } else if (station.getChargesLeft() <= 0) {
            player.sendMessage(plugin.getLanguageManager().get("hookah_hint_load"));
        }
    }

    public void cleanup(UUID uuid) {
        inhaling.remove(uuid);
        puffCounters.remove(uuid);
        Bat anchor = activeAnchors.remove(uuid);
        if (anchor != null) {
            try {
                anchor.setLeashHolder(null);
            } catch (Exception ignored) {}
            anchor.remove();
        }
    }

    public void cleanupAll() {
        for (UUID uuid : new HashSet<>(activeAnchors.keySet())) {
            cleanup(uuid);
        }
    }
}


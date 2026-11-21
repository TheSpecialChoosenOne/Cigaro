package com.cigaro.listener;


import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.*;
import com.cigaro.mechanic.PassiveSmokingManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PipeListener implements Listener {

    private final Cigaro plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long PUFF_COOLDOWN = 3000;

    public PipeListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPipeUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction()
                != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory()
                .getItemInMainHand();

        if (!plugin.getItemManager().isPipe(mainHand)) return;

        event.setCancelled(true);

        if (plugin.getSmokingListener()
                .isSmoking(player.getUniqueId())) return;

        PipeData pipeData = plugin.getItemManager()
                .getPipeData(mainHand);
        if (pipeData == null) return;

        if (pipeData.isBroken()) {
            player.sendMessage(plugin.getLanguageManager().get("pipe_broken"));
            return;
        }

        if (!pipeData.isLoaded()) {
            handlePack(player, pipeData);
            return;
        }

        handleSmoke(player, pipeData);
    }

    private void handlePack(Player player, PipeData pipeData) {
        ItemStack offHand = player.getInventory()
                .getItemInOffHand();

        boolean isCut = plugin.getItemManager().isCutTobacco(offHand);
        boolean isBlend = plugin.getItemManager().isBlendedCutTobacco(offHand);

        if (!isCut && !isBlend) {
            player.sendMessage(plugin.getLanguageManager().get("pipe_need_tobacco"));
            return;
        }

        int cutQuality = plugin.getItemManager()
                .getCutQuality(offHand);
        int charges = plugin.getItemManager()
                .getPipeCharges(cutQuality);

        BlendData blend = plugin.getItemManager().getBlendData(offHand);
        if (blend != null) {
            pipeData.loadBlend(blend, charges);
        } else {
            TobaccoType tobType = plugin.getItemManager().getTobaccoType(offHand);
            CigaretteType flavor = plugin.getItemManager().tobaccoToCigaretteType(
                    tobType != null ? tobType : TobaccoType.VIRGINIA);
            pipeData.load(flavor, charges);
        }
        pipeData.useDurability();

        offHand.setAmount(offHand.getAmount() - 1);
        if (offHand.getAmount() <= 0) {
            player.getInventory().setItemInOffHand(null);
        }

        ItemStack updated = plugin.getItemManager()
                .createPipeItem(pipeData);
        player.getInventory().setItemInMainHand(updated);

        player.playSound(player.getLocation(),
                Sound.BLOCK_GRASS_PLACE, 0.7f, 0.8f);
        player.sendMessage(plugin.getLanguageManager().get("pipe_packed",
                Placeholder.unparsed("charges", String.valueOf(charges))));
    }

    private void handleSmoke(Player player,
                             PipeData pipeData) {
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && (now - last) < PUFF_COOLDOWN) return;
        cooldowns.put(uuid, now);

        pipeData.puff();
        spawnPipeSmoke(player);

        player.playSound(player.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH, 0.12f, 1.8f);

        plugin.getFireHazardManager().checkFireRisk(player);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    spawnPipeExhale(player);
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_FIRE_EXTINGUISH,
                            0.15f, 1.0f);
                }, 16L);

        applyPipeEffects(player, pipeData);

        Component bar = pipeData.isEmpty()
                ? plugin.getLanguageManager().get("pipe_empty_actionbar")
                : plugin.getLanguageManager().get("pipe_charges_actionbar",
                Placeholder.unparsed("charges", String.valueOf(pipeData.getChargesLeft())),
                Placeholder.unparsed("max", String.valueOf(pipeData.getMaxCharges())));

        player.sendActionBar(bar);

        ItemStack updated = plugin.getItemManager()
                .createPipeItem(pipeData);
        player.getInventory().setItemInMainHand(updated);

        if (pipeData.isBroken()) {
            plugin.getAchievementManager()
                    .grant(player, CigaroAchievement.PIPE_BROKEN);
        }

        plugin.getPlayerDataManager().getData(uuid)
                .addMastery(0.001);
    }

    private void applyPipeEffects(Player player,
                                  PipeData pipeData) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager()
                .getData(uuid);

        data.setPreWarningSent(false);
        data.resetLungRecoveryProgress();

        if (!data.isPredispositionGenerated()) {
            plugin.getPlayerDataManager()
                    .generatePredisposition(data);
        }

        double pipeMult = SmokableType.PIPE
                .getEffectMultiplier();

        BlendData blend = pipeData.getBlendData();
        if (blend != null) {
            applyTypeEffectsSimple(player, data, blend.getPrimary(), pipeMult * 0.5);
            applyTypeEffectsSimple(player, data, blend.getSecondary(), pipeMult * 0.5);
            double tolPerSmoke = plugin.getConfigManager().getTolerancePerSmoke();
            data.addTolerance(blend.getPrimary(), tolPerSmoke * blend.getPrimaryRatio());
            data.addTolerance(blend.getSecondary(), tolPerSmoke * blend.getSecondaryRatio());
        } else if (pipeData.getLoadedType() != null) {
            applyTypeEffectsSimple(player, data, pipeData.getLoadedType(), pipeMult);
            data.addTolerance(pipeData.getLoadedType(),
                    plugin.getConfigManager()
                            .getTolerancePerSmoke());
        }

        if (pipeData.getChargesLeft() <= 0
                || pipeData.isEmpty()) {
            data.incrementTotalSmoked();
            data.setLastSmokeTime(System.currentTimeMillis());
            data.resetOnlineSoberSeconds();
            data.damageLungRecovery();

            CigaretteType smokedType = pipeData.getLoadedType() != null
                    ? pipeData.getLoadedType()
                    : blend != null ? blend.getPrimary() : CigaretteType.VANILLA;

            plugin.getAddictionCalculator().onSmoked(
                    data, smokedType, CigaretteQuality.STANDARD, false,
                    SmokableType.PIPE.getEffectMultiplier()
            );
            plugin.getLungDamageManager().updateLungHealth(player, data);
        }

        plugin.getPlayerDataManager().save(uuid);

        plugin.getPassiveSmokingManager().notifyNearby(
                player, PassiveSmokingManager.SmokeSource.PIPE);
    }

    private void applyTypeEffectsSimple(Player player,
                                        PlayerData data,
                                        CigaretteType type,
                                        double mult) {
        int pred = data.getPredisposition(type);
        double tol = data.getTolerance(type);
        double tolMult = 1.0 - (tol * 0.7);

        if (pred <= -2) {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.NAUSEA,
                            60, 0, true, false, false));
            return;
        }

        double finalMult = mult * tolMult
                * (pred >= 0 ? 1.0 : 0.3);
        boolean isFav = pred >= 2;

        for (SmokeEffect effect
                : plugin.getConfigManager()
                .getSmokeEffects(type)) {
            int duration;
            int amp = effect.getAmplifier();

            if (effect.isSideEffect()) {
                duration = effect.getBaseDuration() * 20;
            } else {
                duration = Math.max(40, (int) (effect.getBaseDuration() * 20 * finalMult));
                if (isFav) amp += effect
                        .getFavoriteBonusAmplifier();
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

    private void spawnPipeSmoke(Player player) {
        Location hand = player.getEyeLocation().clone();
        Vector dir = hand.getDirection().normalize();
        hand.add(dir.multiply(0.3));
        hand.add(0, -0.2, 0);

        player.getWorld().spawnParticle(
                Particle.SMOKE, hand, 3,
                0.02, 0.02, 0.02, 0.01
        );
    }

    private void spawnPipeExhale(Player player) {
        Location eyeLoc = player.getEyeLocation().clone();
        Vector dir = eyeLoc.getDirection().normalize();

        Location p1 = eyeLoc.clone().add(dir.multiply(0.3));
        player.getWorld().spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                p1, 4, 0.04, 0.03, 0.04, 0.006
        );
    }

    public void cleanup(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
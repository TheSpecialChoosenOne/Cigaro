package com.cigaro.listener;

import com.cigaro.Cigaro;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PatchListener implements Listener {

    private final Cigaro plugin;

    public PatchListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPatchUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isNicotinePatch(item)) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        var data = plugin.getPlayerDataManager().getData(uuid);

        long cooldownMs = plugin.getConfigManager().getPatchCooldownMinutes() * 60L * 1000L;

        if (data.getAddictionStage() <= 0) {
            player.sendMessage(plugin.getLanguageManager().get("patch_no_need"));
            return;
        }

        if (data.isPatchActive()) {
            long remainingSec = data.getPatchRemainingMs() / 1000;
            long min = remainingSec / 60;
            long sec = remainingSec % 60;

            player.sendMessage(plugin.getLanguageManager().get("patch_already_active",
                    Placeholder.unparsed("min", String.format("%02d", min)),
                    Placeholder.unparsed("sec", String.format("%02d", sec))));
            return;
        }

        long timeSincePatch = System.currentTimeMillis() - data.getPatchAppliedTime();
        if (data.getPatchAppliedTime() > 0 && timeSincePatch < cooldownMs) {
            long cdLeftSec = (cooldownMs - timeSincePatch) / 1000;
            long min = cdLeftSec / 60;
            long sec = cdLeftSec % 60;

            player.sendMessage(plugin.getLanguageManager().get("patch_cooldown",
                    Placeholder.unparsed("min", String.format("%02d", min)),
                    Placeholder.unparsed("sec", String.format("%02d", sec))));
            player.setCooldown(item.getType(), (int) (cdLeftSec * 20));
            return;
        }

        data.setPatchAppliedTime(System.currentTimeMillis());

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        PotionEffectType[] debuffs = {
                PotionEffectType.SLOWNESS,
                PotionEffectType.WEAKNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.NAUSEA,
                PotionEffectType.HUNGER,
                PotionEffectType.DARKNESS
        };
        for (PotionEffectType effect : debuffs) {
            if (player.hasPotionEffect(effect)) {
                player.removePotionEffect(effect);
            }
        }

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.2f);

        player.sendMessage(plugin.getLanguageManager().get("patch_applied"));
        player.sendMessage(plugin.getLanguageManager().get("patch_not_cure"));

        plugin.getAnalyticsManager().recordPatchUsed();
        player.setCooldown(item.getType(), (int) (cooldownMs / 50));
        plugin.getPlayerDataManager().save(uuid);
    }
}
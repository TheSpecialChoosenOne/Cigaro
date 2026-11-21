package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PackListener implements Listener {

    private final Cigaro plugin;

    public PackListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPackInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (!plugin.getItemManager().isCigarettePack(mainHand)) return;

        if (plugin.getSmokingListener().isSmoking(
                player.getUniqueId())) return;

        event.setCancelled(true);

        if (plugin.getItemManager().isCigarette(offHand)) {
            putCigaretteIn(player, mainHand, offHand);
            return;
        }

        takeCigaretteOut(player, mainHand);
    }

    private void putCigaretteIn(Player player,
                                ItemStack pack,
                                ItemStack cigarette) {
        if (!plugin.getItemManager().isFreshCigarette(cigarette)) {
            player.sendMessage(plugin.getLanguageManager().get("pack_only_fresh"));
            player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        List<ItemStack> cigarettes = plugin.getItemManager()
                .getPackCigarettes(pack);

        if (cigarettes.size() >= 20) {
            player.sendMessage(plugin.getLanguageManager().get("pack_full"));
            player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        ItemStack singleCig = cigarette.clone();
        singleCig.setAmount(1);

        cigarettes.add(singleCig);

        cigarette.setAmount(cigarette.getAmount() - 1);
        if (cigarette.getAmount() <= 0) {
            player.getInventory().setItemInOffHand(null);
        }

        DyeColor color = plugin.getItemManager().getPackColor(pack);
        plugin.getItemManager().updatePackMeta(
                pack, cigarettes, color);

        player.playSound(player.getLocation(),
                Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.3f);

        player.sendActionBar(plugin.getLanguageManager().get("pack_put_actionbar",
                Placeholder.unparsed("count", String.valueOf(cigarettes.size()))));
    }

    private void takeCigaretteOut(Player player, ItemStack pack) {
        List<ItemStack> cigarettes = plugin.getItemManager()
                .getPackCigarettes(pack);

        if (cigarettes.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().get("pack_empty"));
            player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        ItemStack cig = cigarettes.remove(cigarettes.size() - 1);

        DyeColor color = plugin.getItemManager().getPackColor(pack);
        plugin.getItemManager().updatePackMeta(
                pack, cigarettes, color);

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand == null || offHand.getType().isAir()) {
            player.getInventory().setItemInOffHand(cig);
        } else {
            var overflow = player.getInventory().addItem(cig);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(), leftover
                );
            }
        }

        player.playSound(player.getLocation(),
                Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);

        player.sendActionBar(plugin.getLanguageManager().get("pack_take_actionbar",
                Placeholder.unparsed("count", String.valueOf(cigarettes.size()))));
    }
}
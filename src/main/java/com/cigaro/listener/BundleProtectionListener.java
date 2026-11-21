
package com.cigaro.listener;

import com.cigaro.Cigaro;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class BundleProtectionListener implements Listener {

    private final Cigaro plugin;

    public BundleProtectionListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (current != null && isBundle(current.getType())
                && cursor != null
                && plugin.getItemManager().isCigarette(cursor)) {
            event.setCancelled(true);
            return;
        }

        if (cursor != null && isBundle(cursor.getType())
                && current != null
                && plugin.getItemManager().isCigarette(current)) {
            event.setCancelled(true);
        }
    }

    private boolean isBundle(Material mat) {
        return mat.name().contains("BUNDLE");
    }
}
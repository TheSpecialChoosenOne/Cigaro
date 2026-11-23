package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.TobaccoType;
import org.bukkit.Material;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class CaravanTraderListener implements Listener {
    private final Cigaro plugin;
    public CaravanTraderListener(Cigaro plugin) { this.plugin = plugin; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) return;
        if (ThreadLocalRandom.current().nextDouble() >= 0.5) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!trader.isValid()) return;
            var recipes = new ArrayList<>(trader.getRecipes());
            var recipe = new MerchantRecipe(plugin.getItemManager().createTobaccoSeeds(TobaccoType.CARAVAN, 2), 4);
            recipe.addIngredient(new ItemStack(Material.EMERALD, 3));
            recipes.add(recipe);
            trader.setRecipes(recipes);
        }, 2L);
    }
}

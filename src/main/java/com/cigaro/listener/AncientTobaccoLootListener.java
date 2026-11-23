package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.TobaccoType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BrushableBlock;
import org.bukkit.persistence.PersistentDataType;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AncientTobaccoLootListener implements Listener {
    private final Cigaro plugin;
    private static final Set<String> TABLES = Set.of("chests/desert_pyramid", "chests/shipwreck_supply", "chests/shipwreck_treasure", "chests/simple_dungeon");
    private static final Set<String> ARCHAEOLOGY = Set.of("archaeology/desert_pyramid", "archaeology/desert_well", "archaeology/trail_ruins_rare", "archaeology/ocean_ruin_warm", "archaeology/ocean_ruin_cold");
    public AncientTobaccoLootListener(Cigaro plugin) { this.plugin = plugin; }
    private double chance() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("tobacco.ancient-loot-chance", 0.08)));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLoot(LootGenerateEvent event) {
        if (event.isPlugin()) return;
        var key = event.getLootTable().getKey();
        if (!key.getNamespace().equals("minecraft") || !TABLES.contains(key.getKey())) return;
        if (ThreadLocalRandom.current().nextDouble() < chance()) {
            var loot = new java.util.ArrayList<>(event.getLoot());
            loot.add(plugin.getItemManager().createTobaccoSeeds(TobaccoType.ANCIENT, 1));
            event.setLoot(loot);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBrush(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null
                || event.getItem().getType() != Material.BRUSH || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof BrushableBlock state)) return;
        var table = state.getLootTable();
        if (table == null || !table.getKey().getNamespace().equals("minecraft")
                || !ARCHAEOLOGY.contains(table.getKey().getKey())) return;
        var checked = new NamespacedKey(plugin, "ancient_loot_checked");
        if (state.getPersistentDataContainer().has(checked, PersistentDataType.BYTE)) return;
        state.getPersistentDataContainer().set(checked, PersistentDataType.BYTE, (byte) 1);
        if (ThreadLocalRandom.current().nextDouble() < chance()) {
            state.setLootTable(null);
            state.setItem(plugin.getItemManager().createTobaccoSeeds(TobaccoType.ANCIENT, 1));
        }
        state.update(true, false);
    }
}

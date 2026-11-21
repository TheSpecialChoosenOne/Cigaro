package com.cigaro.listener;

import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import java.util.List;
import org.bukkit.event.block.BlockFadeEvent;
import com.cigaro.Cigaro;
import com.cigaro.data.CigaroAchievement;
import com.cigaro.data.TobaccoType;
import com.cigaro.data.DryingLevel;
import com.cigaro.tobacco.TobaccoBlock;
import com.cigaro.tobacco.TobaccoManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class TobaccoGrowthListener implements Listener {

    private final Cigaro plugin;
    private final Random random = new Random();

    public TobaccoGrowthListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlantSeeds(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isTobaccoSeeds(item)) return;
        if (!player.hasPermission("cigaro.use")) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (clicked.getType() != Material.FARMLAND) return;

        Block plantBlock = clicked.getRelative(0, 1, 0);
        if (plantBlock.getType() != Material.AIR) {
            if (plugin.getConfigManager().isShowPlantMessages()) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_no_space"));
            }
            return;
        }

        TobaccoType seedType = plugin.getItemManager().getTobaccoType(item);
        if (seedType == null) seedType = TobaccoType.VIRGINIA;

        event.setCancelled(true);

        boolean success = plugin.getTobaccoManager().plantTobacco(plantBlock, seedType);

        if (success) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            if (plugin.getConfigManager().isShowPlantMessages()) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("success_planted",
                        Placeholder.unparsed("type", seedType.getDisplayName())));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakBelowPlant(BlockBreakEvent event) {
        Block broken = event.getBlock();
        Block above = broken.getRelative(0, 1, 0);
        if (above.getType() != Material.WHEAT) return;

        TobaccoManager manager = plugin.getTobaccoManager();
        if (!manager.isTobaccoPlant(above.getLocation())) return;

        TobaccoBlock plant = manager.getPlantAt(above.getLocation());
        if (plant == null) return;

        ItemStack seeds = plugin.getItemManager().createTobaccoSeeds(plant.getTobaccoType(), 1);

        above.getWorld().dropItemNaturally(above.getLocation(), seeds);
        manager.removePlantSilent(above.getLocation());
        above.setType(Material.AIR);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaGrow(BlockGrowEvent event) {
        Block block = event.getBlock();

        if (block.getType() != Material.WHEAT) return;
        if (!plugin.getTobaccoManager().isTobaccoPlant(block.getLocation())) return;

        event.setCancelled(true);
        plugin.getTobaccoManager().onVanillaGrowTick(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreakHiddenWheat(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WHEAT) return;
        if (!plugin.getTobaccoManager().isTobaccoPlant(block.getLocation())) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePistonBlocks(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePistonBlocks(event.getBlocks());
    }

    private void handlePistonBlocks(List<Block> blocks) {
        TobaccoManager manager = plugin.getTobaccoManager();
        for (Block block : blocks) {
            if (block.getType() == Material.FARMLAND) {
                Block above = block.getRelative(0, 1, 0);
                if (above.getType() == Material.WHEAT
                        && manager.isTobaccoPlant(above.getLocation())) {
                    killPlantWithDrop(manager, above);
                }
            }
            if (block.getType() == Material.WHEAT
                    && manager.isTobaccoPlant(block.getLocation())) {
                killPlantWithDrop(manager, block);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFluidFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        TobaccoManager manager = plugin.getTobaccoManager();

        if (to.getType() == Material.WHEAT
                && manager.isTobaccoPlant(to.getLocation())) {
            event.setCancelled(true);
            killPlantWithDrop(manager, to);
            return;
        }

        if (to.getType() == Material.FARMLAND) {
            Block above = to.getRelative(0, 1, 0);
            if (above.getType() == Material.WHEAT
                    && manager.isTobaccoPlant(above.getLocation())) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (above.getType() != Material.WHEAT) {
                        killPlantWithDrop(manager, above);
                    }
                }, 2L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    private void handleExplosionBlocks(List<Block> blocks) {
        TobaccoManager manager = plugin.getTobaccoManager();
        for (Block block : blocks) {
            if (block.getType() == Material.WHEAT
                    && manager.isTobaccoPlant(block.getLocation())) {
                killPlantWithDrop(manager, block);
            }
            if (block.getType() == Material.FARMLAND) {
                Block above = block.getRelative(0, 1, 0);
                if (above.getType() == Material.WHEAT
                        && manager.isTobaccoPlant(above.getLocation())) {
                    killPlantWithDrop(manager, above);
                }
            }
        }
    }

    private void killPlantWithDrop(TobaccoManager manager, Block wheatBlock) {
        TobaccoBlock plant = manager.getPlantAt(wheatBlock.getLocation());
        if (plant == null) return;

        ItemStack seeds = plugin.getItemManager().createTobaccoSeeds(plant.getTobaccoType(), 1);

        wheatBlock.getWorld().dropItemNaturally(
                wheatBlock.getLocation().add(0.5, 0.5, 0.5), seeds
        );

        if (wheatBlock.getType() == Material.WHEAT) {
            wheatBlock.setType(Material.AIR);
        }

        manager.removePlantSilent(wheatBlock.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickCrop(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        TobaccoManager manager = plugin.getTobaccoManager();

        if (!manager.isCropInteraction(entity)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        TobaccoBlock plant = manager.getPlantByInteraction(entity.getUniqueId());
        if (plant == null) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        Block block = plant.getLocation().getBlock();

        if (hand.getType() == Material.BONE_MEAL) {
            if (plant.isFullyGrown()) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_fully_grown"));
                return;
            }

            boolean success = manager.bonemealPlant(plant);
            if (success) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                block.getWorld().spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        block.getLocation().add(0.5, 0.5, 0.5),
                        15, 0.3, 0.3, 0.3, 0.05
                );
                player.playSound(player.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1f, 1f);
            } else {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_bonemeal_burned"));
            }
            return;
        }

        if (plugin.getItemManager().isTobaccoFertilizer(hand)) {
            if (plant.isFullyGrown()) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_fully_grown"));
                return;
            }
            if (plant.getFertilizerBonus() >= 6) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_max_fertilizer"));
                return;
            }

            if (!manager.applyFertilizer(plant)) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_fertilizer_damage"));
                updateHandItem(player, hand);
                return;
            }

            player.sendMessage(plugin.getLanguageManager().getWithPrefix("success_fertilizer"));
            block.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    block.getLocation().add(0.5, 0.5, 0.5),
                    15, 0.3, 0.3, 0.3, 0.05
            );
            updateHandItem(player, hand);
            return;
        }

        if (plant.isFullyGrown()) {
            harvestCrop(player, plant);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClickCrop(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        TobaccoManager manager = plugin.getTobaccoManager();

        if (!manager.isCropInteraction(target)) return;

        event.setCancelled(true);

        if (!(event.getDamager() instanceof Player player)) return;

        TobaccoBlock plant = manager.getPlantByInteraction(target.getUniqueId());
        if (plant == null) return;

        destroyCrop(player, plant);
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmlandDry(BlockFadeEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;

        Block above = event.getBlock().getRelative(0, 1, 0);
        if (above.getType() != Material.WHEAT) return;

        TobaccoManager manager = plugin.getTobaccoManager();
        if (!manager.isTobaccoPlant(above.getLocation())) return;

        TobaccoBlock plant = manager.getPlantAt(above.getLocation());
        if (plant == null) return;

        ItemStack seeds = plugin.getItemManager().createTobaccoSeeds(plant.getTobaccoType(), 1);

        above.getWorld().dropItemNaturally(above.getLocation(), seeds);
        manager.removePlantSilent(above.getLocation());
        above.setType(Material.AIR);
    }

    private void harvestCrop(Player player, TobaccoBlock plant) {
        TobaccoManager manager = plugin.getTobaccoManager();
        TobaccoType tobaccoType = plant.getTobaccoType();
        Block block = plant.getLocation().getBlock();

        Block soilBlock = block.getRelative(0, -1, 0);
        int soilBonus = manager.getEliteBonus(soilBlock.getType());
        int fertilizerBonus = plant.getFertilizerBonus();

        double eliteChance = plugin.getConfigManager().getEliteLeafChance()
                + (soilBonus * 0.01)
                + (fertilizerBonus * 0.01);

        int leafCount = 1 + random.nextInt(3);

        for (int i = 0; i < leafCount; i++) {
            boolean elite = random.nextDouble() < eliteChance;
            ItemStack leaf = plugin.getItemManager().createTobaccoLeaf(tobaccoType, elite);
            block.getWorld().dropItemNaturally(block.getLocation(), leaf);
        }

        block.getWorld().dropItemNaturally(block.getLocation(),
                plugin.getItemManager().createTobaccoSeeds(tobaccoType, 1));

        if (plugin.getConfigManager().isShowHarvestMessages()) {
            player.sendMessage(plugin.getLanguageManager().getWithPrefix("success_harvest",
                    Placeholder.unparsed("type", tobaccoType.getDisplayName())));
        }

        plugin.getAchievementManager().grant(player, CigaroAchievement.FIRST_HARVEST);

        block.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                block.getLocation().add(0.5, 0.5, 0.5),
                10, 0.3, 0.3, 0.3, 0.02
        );
        player.playSound(player.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);

        manager.removePlant(plant.getLocation());
    }

    private void destroyCrop(Player player, TobaccoBlock plant) {
        TobaccoManager manager = plugin.getTobaccoManager();
        TobaccoType tobaccoType = plant.getTobaccoType();
        Block block = plant.getLocation().getBlock();

        if (plant.isFullyGrown()) {
            harvestCrop(player, plant);
        } else {
            ItemStack seeds = plugin.getItemManager().createTobaccoSeeds(tobaccoType, 1);

            block.getWorld().dropItemNaturally(block.getLocation(), seeds);

            if (plugin.getConfigManager().isShowHarvestMessages()) {
                player.sendMessage(plugin.getLanguageManager().getWithPrefix("error_harvest_early"));
            }

            manager.removePlant(plant.getLocation());
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
    }

    private void updateHandItem(Player player, ItemStack item) {
        ItemStack result = plugin.getItemManager().useFertilizer(item);
        player.getInventory().setItemInMainHand(result);
    }
}
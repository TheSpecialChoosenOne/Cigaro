package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.TobaccoType;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Random;

public class FlavorDropListener implements Listener {

    private final Cigaro plugin;
    private final Random random = new Random();

    public FlavorDropListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (hassilk(player)) return;

        if (plugin.getTobaccoManager().isTobaccoPlant(block.getLocation())) return;

        Biome biome = block.getBiome();
        String biomeName = biome.getKey().getKey().toUpperCase(Locale.ROOT);

        if (block.getType() == Material.SHORT_GRASS || block.getType() == Material.TALL_GRASS) {
            dropSeedsForBiome(block, biomeName);
        }

        CigaretteType flavorType = getFlavorForBreak(block.getType(), biome);
        if (flavorType == null) return;

        double chance = getChanceForType(flavorType);
        if (random.nextDouble() > chance) return;

        ItemStack flavor = plugin.getItemManager().createRawIngredient(flavorType);
        block.getWorld().dropItemNaturally(block.getLocation(), flavor);
    }
    private boolean hassilk(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) return false;
        return tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
    }
    private void dropSeedsForBiome(Block block, String biomeName) {
        TobaccoType selectedType = null;
        double roll = random.nextDouble();

        double globalChance = plugin.getConfigManager().getSeedDropChance();

        for (TobaccoType type : TobaccoType.values()) {
            if (type.isNativeBiome(biomeName)) {
                if (roll < type.getDropChanceForBiome(biomeName)) {
                    selectedType = type;
                    break;
                }
            }
        }

        if (selectedType == null) {
            if (roll < globalChance * TobaccoType.values().size()) {
                TobaccoType[] typesArr = TobaccoType.getRandomTypes();
                selectedType = typesArr[random.nextInt(typesArr.length)];
            }
        }

        if (selectedType != null) {
            block.getWorld().dropItemNaturally(block.getLocation(),
                    plugin.getItemManager().createTobaccoSeeds(selectedType, 1));
        }
    }

    private double getChanceForType(CigaretteType type) {
        var cfg = plugin.getConfigManager();
        String id = type.name();
        return switch (id) {
            case "MINT" -> cfg.getMintDropChance();
            case "COFFEE" -> cfg.getCoffeeDropChance();
            case "CARAMEL" -> cfg.getCaramelDropChance();
            case "VANILLA" -> cfg.getVanillaDropChance();
            case "CHERRY" -> cfg.getCherryDropChance();
            case "SPICE" -> cfg.getSpiceDropChance();
            case "HERBAL" -> cfg.getHerbalDropChance();
            case "SHADOW" -> cfg.getShadowDropChance();
            default -> 0.15;
        };
    }

    private CigaretteType getFlavorForBreak(Material mat, Biome biome) {
        String biomeName = biome.getKey().getKey().toUpperCase(Locale.ROOT);

        if (isSpruceLeaves(mat) && isTaiga(biomeName)) {
            return CigaretteType.MINT;
        }

        if (mat == Material.COCOA && isJungle(biomeName)) {
            return CigaretteType.COFFEE;
        }

        if ((mat == Material.HAY_BLOCK || mat == Material.WHEAT) && isPlains(biomeName)) {
            return CigaretteType.CARAMEL;
        }

        if ((mat == Material.DEAD_BUSH || mat == Material.CACTUS) && isDesert(biomeName)) {
            return CigaretteType.VANILLA;
        }

        if ((mat == Material.CHERRY_LEAVES || mat == Material.PINK_PETALS) && biomeName.contains("CHERRY")) {
            return CigaretteType.CHERRY;
        }

        if ((mat == Material.NETHER_QUARTZ_ORE || mat == Material.NETHER_GOLD_ORE
                || mat == Material.ANCIENT_DEBRIS) && isNether(biomeName)) {
            return CigaretteType.SPICE;
        }

        if ((mat == Material.MOSS_BLOCK || mat == Material.MOSS_CARPET
                || mat == Material.SPORE_BLOSSOM || mat == Material.FLOWERING_AZALEA_LEAVES)
                && isLushCave(biomeName)) {
            return CigaretteType.HERBAL;
        }

        if ((mat == Material.SCULK || mat == Material.SCULK_VEIN
                || mat == Material.SCULK_CATALYST) && isDeepDark(biomeName)) {
            return CigaretteType.SHADOW;
        }

        return null;
    }

    private boolean isSpruceLeaves(Material mat) {
        return mat == Material.SPRUCE_LEAVES || mat == Material.OAK_LEAVES || mat == Material.BIRCH_LEAVES;
    }

    private boolean isTaiga(String name) {
        return name.contains("TAIGA") || name.contains("GROVE") || name.contains("SNOWY");
    }

    private boolean isJungle(String name) {
        return name.contains("JUNGLE");
    }

    private boolean isPlains(String name) {
        return name.contains("PLAINS") || name.contains("MEADOW") || name.contains("SAVANNA");
    }

    private boolean isDesert(String name) {
        return name.contains("DESERT") || name.contains("BADLANDS");
    }

    private boolean isNether(String name) {
        return name.contains("NETHER") || name.contains("BASALT") || name.contains("SOUL")
                || name.contains("CRIMSON") || name.contains("WARPED");
    }

    private boolean isLushCave(String name) {
        return name.contains("LUSH");
    }

    private boolean isDeepDark(String name) {
        return name.contains("DEEP_DARK");
    }
}

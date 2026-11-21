package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.Random;

public class FireHazardManager {

    private final Cigaro plugin;
    private final Random random = new Random();

    private static final double BASE_CHANCE = 0.005;
    private static final int RADIUS = 2;

    private static final Material[] FLAMMABLE = {
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
            Material.CHERRY_PLANKS, Material.MANGROVE_PLANKS,
            Material.BAMBOO_PLANKS,
            Material.OAK_LOG, Material.SPRUCE_LOG,
            Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.OAK_FENCE, Material.SPRUCE_FENCE,
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS,
            Material.OAK_SLAB, Material.SPRUCE_SLAB,
            Material.BOOKSHELF, Material.CRAFTING_TABLE,
            Material.HAY_BLOCK, Material.DRIED_KELP_BLOCK,
            Material.OAK_DOOR, Material.SPRUCE_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
    };

    public FireHazardManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void checkFireRisk(Player player) {
        PlayerData data = plugin.getPlayerDataManager()
                .getData(player.getUniqueId());

        int stage = data.getAddictionStage();
        if (stage < 3) return;

        Location playerLoc = player.getLocation();
        if (playerLoc.getWorld().hasStorm()) {
            int highestY = playerLoc.getWorld().getHighestBlockYAt(playerLoc);
            if (highestY <= playerLoc.getBlockY() + 2) {
                return;
            }
        }

        double chance = switch (stage) {
            case 3 -> BASE_CHANCE * 0.5;
            case 4 -> BASE_CHANCE;
            default -> 0.0;
        };

        if (random.nextDouble() > chance) return;

        Block target = findFlammableBlock(playerLoc);
        if (target == null) return;

        Block fireSpot = findFireSpot(target);
        if (fireSpot == null) return;

        org.bukkit.event.block.BlockIgniteEvent igniteEvent =
                new org.bukkit.event.block.BlockIgniteEvent(
                        fireSpot,
                        org.bukkit.event.block.BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL,
                        player);
        plugin.getServer().getPluginManager().callEvent(igniteEvent);

        if (igniteEvent.isCancelled()) return;

        fireSpot.setType(Material.FIRE);

        player.getWorld().spawnParticle(
                Particle.LAVA,
                fireSpot.getLocation().add(0.5, 0.5, 0.5),
                5, 0.2, 0.2, 0.2, 0);

        player.playSound(player.getLocation(),
                Sound.ITEM_FLINTANDSTEEL_USE, 0.4f, 0.8f);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.sendMessage(
                            plugin.getLanguageManager().get("fire_hazard_ash_dropped"));
                }, 20L);
    }

    private Block findFlammableBlock(Location center) {
        for (int attempt = 0; attempt < 15; attempt++) {
            int dx = random.nextInt(RADIUS * 2 + 1) - RADIUS;
            int dy = random.nextInt(3) - 1;
            int dz = random.nextInt(RADIUS * 2 + 1) - RADIUS;

            Block block = center.clone().add(dx, dy, dz).getBlock();
            if (isFlammable(block.getType())) return block;
        }
        return null;
    }

    private Block findFireSpot(Block target) {
        BlockFace[] faces = {
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST
        };
        for (BlockFace face : faces) {
            Block adjacent = target.getRelative(face);
            if (adjacent.getType() == Material.AIR) return adjacent;
        }
        return null;
    }

    private boolean isFlammable(Material mat) {
        for (Material m : FLAMMABLE) {
            if (m == mat) return true;
        }
        String name = mat.name();
        return name.contains("PLANKS") || name.contains("LOG")
                || name.contains("WOOD") || name.contains("FENCE")
                || name.contains("WOOL");
    }
}
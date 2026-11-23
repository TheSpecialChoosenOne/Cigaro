package com.cigaro.tobacco;

import com.cigaro.Cigaro;
import com.cigaro.data.TobaccoType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class TobaccoManager {

    private final Cigaro plugin;
    private final Map<Location, TobaccoBlock> activePlants = new HashMap<>();
    private final Map<PlantChunkKey, Set<Location>> plantsByChunk = new HashMap<>();
    private final Map<UUID, Location> interactionToPlant = new HashMap<>();
    private final Random random = new Random();
    private int watchdogTaskId = -1;
    private static final Material CROP_MATERIAL = Material.WHEAT;

    private record PlantChunkKey(UUID world, long packed) {}

    private static PlantChunkKey chunkKey(UUID world, int cx, int cz) {
        return new PlantChunkKey(world, ((long) cx << 32) | (cz & 0xFFFFFFFFL));
    }

    private static PlantChunkKey chunkKeyOf(Location loc) {
        return chunkKey(loc.getWorld().getUID(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void registerPlant(Location norm, TobaccoBlock plant) {
        activePlants.put(norm, plant);
        plantsByChunk.computeIfAbsent(chunkKeyOf(norm), k -> new HashSet<>()).add(norm);
    }

    private TobaccoBlock unregisterPlant(Location norm) {
        TobaccoBlock plant = activePlants.remove(norm);
        PlantChunkKey key = chunkKeyOf(norm);
        Set<Location> inChunk = plantsByChunk.get(key);
        if (inChunk != null) {
            inChunk.remove(norm);
            if (inChunk.isEmpty()) plantsByChunk.remove(key);
        }
        return plant;
    }

    private static final float[] INTERACTION_HEIGHTS = {0.45f, 1.0f, 1.6f, 2.2f};
    private static final float INTERACTION_WIDTH = 0.8f;

    private static final float[] DISPLAY_SCALES = {0.8f, 1.0f, 2.2f, 1.4f};
    private static final float[] DISPLAY_Y_OFFSET = {0.4f, 0.5f, 1.1f, 0.7f};

    private static final Map<Material, Integer> SOIL_ELITE_BONUS = new HashMap<>();
    static {
        SOIL_ELITE_BONUS.put(Material.DIRT, 0);
        SOIL_ELITE_BONUS.put(Material.GRASS_BLOCK, 0);
        SOIL_ELITE_BONUS.put(Material.FARMLAND, 1);
    }

    private NamespacedKey TOBACCO_DISPLAY_KEY;

    public TobaccoManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        TOBACCO_DISPLAY_KEY = new NamespacedKey(plugin, "tobacco_display");

        List<TobaccoBlock> saved = plugin.getDatabaseManager().loadAllTobaccoPlants();
        for (TobaccoBlock plant : saved) {
            Location norm = normalize(plant.getLocation());
            registerPlant(norm, plant);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ItemDisplay || entity instanceof Interaction) {
                        if (entity.getPersistentDataContainer().has(
                                TOBACCO_DISPLAY_KEY, PersistentDataType.BYTE)) {
                            entity.remove();
                        }
                    }
                }
            }

            for (Location loc : new ArrayList<>(activePlants.keySet())) {
                if (!isLoaded(loc)) continue;

                TobaccoBlock plant = activePlants.get(loc);
                if (plant == null) continue;
                Block block = plant.getLocation().getBlock();

                if (block.getType() != CROP_MATERIAL) {
                    plugin.getDatabaseManager().removeTobaccoPlant(plant.getLocation());
                    unregisterPlant(loc);
                    continue;
                }

                resetWheatAge(block);
                spawnEntities(plant);
            }

            plugin.getLogger().info("Restored " + activePlants.size() + " tobacco plants in "
                    + plantsByChunk.size() + " chunks.");
            startWatchdog();
        }, 100L);

        plugin.getServer().getPluginManager().registerEvents(
                new org.bukkit.event.Listener() {
                    @org.bukkit.event.EventHandler
                    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
                        List<Location> inChunk = plantsInChunk(event.getChunk());
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!event.getChunk().isLoaded()) return;

                            Set<UUID> chunkOwnedIds = new HashSet<>();
                            for (Location loc : inChunk) {
                                TobaccoBlock plant = activePlants.get(loc);
                                if (plant == null) continue;
                                if (plant.getDisplayId() != null) chunkOwnedIds.add(plant.getDisplayId());
                                if (plant.getInteractionId() != null) chunkOwnedIds.add(plant.getInteractionId());
                            }

                            for (Entity entity : event.getChunk().getEntities()) {
                                if (!(entity instanceof ItemDisplay
                                        || entity instanceof Interaction)) continue;
                                if (!entity.getPersistentDataContainer().has(
                                        TOBACCO_DISPLAY_KEY, PersistentDataType.BYTE)) continue;

                                if (!chunkOwnedIds.contains(entity.getUniqueId())) {
                                    entity.remove();
                                }
                            }

                            if (!inChunk.isEmpty()) {
                                reregisterEntitiesInChunk(event.getChunk());
                            }
                        }, 2L);
                    }
                },
                plugin
        );

        plugin.getLogger().info("Loaded tobacco manager.");
    }

    public void reregisterEntitiesInChunk(org.bukkit.Chunk chunk) {
        List<Location> toRespawn = new ArrayList<>();

        for (Location loc : plantsInChunk(chunk)) {
            TobaccoBlock plant = activePlants.get(loc);
            if (plant == null) continue;

            Block block = loc.getBlock();
            if (block.getType() != CROP_MATERIAL) {
                toRespawn.add(loc);
                continue;
            }

            if (plant.getInteractionId() != null) {
                Entity interEntity = plugin.getServer().getEntity(plant.getInteractionId());
                if (interEntity instanceof Interaction) {
                    interactionToPlant.put(plant.getInteractionId(), loc);

                    if (plant.getDisplayId() != null) {
                        Entity dispEntity = plugin.getServer().getEntity(plant.getDisplayId());
                        if (!(dispEntity instanceof ItemDisplay)) {
                            toRespawn.add(loc);
                        }
                    }
                    continue;
                }
            }

            toRespawn.add(loc);
        }

        for (Location loc : toRespawn) {
            TobaccoBlock plant = activePlants.get(loc);
            if (plant == null) continue;

            Block block = loc.getBlock();
            if (block.getType() != CROP_MATERIAL) {
                unregisterPlant(loc);
                cleanupEntities(plant);
                plugin.getDatabaseManager().removeTobaccoPlant(loc);
            } else {
                cleanupEntities(plant);
                spawnEntities(plant);
            }
        }
    }

    private List<Location> plantsInChunk(org.bukkit.Chunk chunk) {
        Set<Location> indexed = plantsByChunk.get(chunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        return indexed == null ? List.of() : new ArrayList<>(indexed);
    }

    public void removePlantSilent(Location location) {
        Location norm = normalize(location);
        TobaccoBlock plant = unregisterPlant(norm);
        if (plant != null) {
            cleanupEntities(plant);
            plugin.getDatabaseManager().removeTobaccoPlant(norm);
        }
    }

    public void stop() {
        if (watchdogTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(watchdogTaskId);
            watchdogTaskId = -1;
        }
        for (TobaccoBlock plant : activePlants.values()) {
            plugin.getDatabaseManager().saveTobaccoPlantSync(plant);
            cleanupEntities(plant);
        }
        activePlants.clear();
        plantsByChunk.clear();
        interactionToPlant.clear();
    }

    public void startWatchdog() {
        if (watchdogTaskId != -1) return;

        watchdogTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::runWatchdog, 100L, 60L)
                .getTaskId();
    }

    private void runWatchdog() {
        List<Location> dead = new ArrayList<>();

        for (Map.Entry<Location, TobaccoBlock> entry : activePlants.entrySet()) {
            Location loc = entry.getKey();
            TobaccoBlock plant = entry.getValue();

            if (!isLoaded(loc)) continue;

            Block block = loc.getBlock();

            if (block.getType() != CROP_MATERIAL) {
                dead.add(loc);
                continue;
            }

            boolean displayMissing = plant.getDisplayId() == null
                    || !(plugin.getServer().getEntity(plant.getDisplayId()) instanceof ItemDisplay);
            boolean interactionMissing = plant.getInteractionId() == null
                    || !(plugin.getServer().getEntity(plant.getInteractionId()) instanceof Interaction);

            if (displayMissing || interactionMissing) {
                cleanupEntities(plant);
                spawnEntities(plant);
            }
        }

        for (Location loc : dead) {
            TobaccoBlock plant = activePlants.get(loc);
            if (plant == null) continue;

            loc.getWorld().dropItemNaturally(
                    loc.clone().add(0.5, 0.5, 0.5),
                    plugin.getItemManager().createTobaccoSeeds(plant.getTobaccoType(), 1)
            );

            cleanupEntities(plant);
            unregisterPlant(loc);
            plugin.getDatabaseManager().removeTobaccoPlant(loc);
        }
    }

    private boolean isLoaded(Location loc) {
        org.bukkit.World world = loc.getWorld();
        return world != null && world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public boolean plantTobacco(Block block, TobaccoType type) {
        Block below = block.getRelative(0, -1, 0);
        if (!isValidSoil(below.getType())) return false;

        block.setType(CROP_MATERIAL);
        resetWheatAge(block);

        TobaccoBlock plant = new TobaccoBlock(block.getLocation(), type, 0);

        Location norm = normalize(block.getLocation());
        registerPlant(norm, plant);

        spawnEntities(plant);
        plugin.getDatabaseManager().saveTobaccoPlant(plant);
        return true;
    }

    public void onVanillaGrowTick(Block block) {
        Location norm = normalize(block.getLocation());
        TobaccoBlock plant = activePlants.get(norm);
        if (plant == null) return;

        if (plant.isFullyGrown()) {
            resetWheatAge(block);
            return;
        }

        double chance = Math.min(1.0, (double) TobaccoType.VIRGINIA.getBaseGrowthTicks()
                / plant.getTobaccoType().getBaseGrowthTicks());
        if (random.nextDouble() >= chance) return;

        plant.setStage(plant.getStage() + 1);
        resetWheatAge(block);
        updateEntities(plant);

        block.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                block.getLocation().add(0.5, 0.5, 0.5),
                6, 0.3, 0.2, 0.3, 0.02
        );

        plugin.getDatabaseManager().saveTobaccoPlant(plant);
        plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroTobaccoEvent(
                com.cigaro.api.CigaroTobaccoEvent.Action.GROWN, null, block.getLocation(),
                plugin.getItemManager().createTobaccoSeeds(plant.getTobaccoType(), 1)));
    }

    public TobaccoBlock getPlantByInteraction(UUID interactionId) {
        Location loc = interactionToPlant.get(interactionId);
        if (loc == null) return null;
        return activePlants.get(loc);
    }

    public boolean isCropInteraction(Entity entity) {
        return entity instanceof Interaction
                && interactionToPlant.containsKey(entity.getUniqueId());
    }

    public boolean forceGrow(TobaccoBlock plant) {
        if (plant.isFullyGrown()) return false;

        plant.setStage(plant.getStage() + 1);
        updateEntities(plant);

        plugin.getDatabaseManager().saveTobaccoPlant(plant);
        return true;
    }

    public boolean bonemealPlant(TobaccoBlock plant) {
        if (!plugin.getConfigManager().isBoneMealEnabled()) return false;
        if (plant.isFullyGrown()) return false;

        if (random.nextDouble() < 0.08) {
            burnPlant(plant);
            return false;
        }

        return forceGrow(plant);
    }

    public boolean applyFertilizer(TobaccoBlock plant) {
        if (plant.isFullyGrown()) return false;
        if (random.nextDouble() < 0.05) {
            damagePlant(plant);
            return false;
        }
        plant.addFertilizerBonus(2);

        plugin.getDatabaseManager().saveTobaccoPlant(plant);
        return true;
    }

    public void removePlant(Location location) {
        Location norm = normalize(location);
        TobaccoBlock plant = unregisterPlant(norm);
        if (plant != null) {
            cleanupEntities(plant);
            plugin.getDatabaseManager().removeTobaccoPlant(norm);

            if (isLoaded(norm)) {
                Block block = norm.getBlock();
                if (block.getType() == CROP_MATERIAL) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    private void spawnEntities(TobaccoBlock plant) {
        Location blockLoc = plant.getLocation();
        if (!isLoaded(blockLoc)) return;

        int stage = plant.getStage();
        int s = Math.min(stage, DISPLAY_SCALES.length - 1);

        ItemStack model = plugin.getItemManager().createTobaccoCropItem(
                plant.getTobaccoType(), stage
        );

        Location displayLoc = blockLoc.clone().add(0.5, 0.0, 0.5);

        float scale = DISPLAY_SCALES[s];
        float yOffset = DISPLAY_Y_OFFSET[s];

        ItemDisplay display = blockLoc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(model);
            entity.setPersistent(false);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, yOffset, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            entity.getPersistentDataContainer().set(
                    TOBACCO_DISPLAY_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        });
        plant.setDisplayId(display.getUniqueId());

        float height = INTERACTION_HEIGHTS[
                Math.min(stage, INTERACTION_HEIGHTS.length - 1)
                ];

        Interaction interaction = blockLoc.getWorld().spawn(displayLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(INTERACTION_WIDTH);
            entity.setInteractionHeight(height);
            entity.setPersistent(false);
            entity.setResponsive(true);
            entity.getPersistentDataContainer().set(
                    TOBACCO_DISPLAY_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        });
        plant.setInteractionId(interaction.getUniqueId());

        Location norm = normalize(blockLoc);
        interactionToPlant.put(interaction.getUniqueId(), norm);
    }

    private void updateEntities(TobaccoBlock plant) {
        int stage = plant.getStage();
        int s = Math.min(stage, DISPLAY_SCALES.length - 1);

        if (plant.getDisplayId() != null) {
            Entity displayEntity = plugin.getServer().getEntity(plant.getDisplayId());
            if (displayEntity instanceof ItemDisplay display) {
                ItemStack model = plugin.getItemManager().createTobaccoCropItem(
                        plant.getTobaccoType(), stage
                );
                display.setItemStack(model);

                float scale = DISPLAY_SCALES[s];
                float yOffset = DISPLAY_Y_OFFSET[s];

                display.setTransformation(new Transformation(
                        new Vector3f(0, yOffset, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(scale, scale, scale),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
            }
        }

        if (plant.getInteractionId() != null) {
            Entity interEntity = plugin.getServer().getEntity(plant.getInteractionId());
            if (interEntity instanceof Interaction interaction) {
                float height = INTERACTION_HEIGHTS[
                        Math.min(stage, INTERACTION_HEIGHTS.length - 1)
                        ];
                interaction.setInteractionHeight(height);
                interaction.setInteractionWidth(INTERACTION_WIDTH);
            }
        }
    }

    private void cleanupEntities(TobaccoBlock plant) {
        if (plant.getDisplayId() != null) {
            Entity e = plugin.getServer().getEntity(plant.getDisplayId());
            if (e != null) e.remove();
            plant.setDisplayId(null);
        }
        if (plant.getInteractionId() != null) {
            interactionToPlant.remove(plant.getInteractionId());
            Entity e = plugin.getServer().getEntity(plant.getInteractionId());
            if (e != null) e.remove();
            plant.setInteractionId(null);
        }
    }

    private void resetWheatAge(Block block) {
        if (block.getType() != CROP_MATERIAL) return;
        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    private void burnPlant(TobaccoBlock plant) {
        Block block = plant.getLocation().getBlock();
        block.getWorld().spawnParticle(
                Particle.LAVA,
                block.getLocation().add(0.5, 0.3, 0.5),
                8, 0.2, 0.2, 0.2, 0.05
        );
        block.getWorld().spawnParticle(
                Particle.SMOKE,
                block.getLocation().add(0.5, 0.5, 0.5),
                5, 0.3, 0.3, 0.3, 0.02
        );
        block.getWorld().playSound(
                block.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.5f
        );
        block.setType(Material.DEAD_BUSH);
        removePlant(plant.getLocation());
    }

    private void damagePlant(TobaccoBlock plant) {
        Block block = plant.getLocation().getBlock();
        block.getWorld().spawnParticle(
                Particle.DUST,
                block.getLocation().add(0.5, 0.3, 0.5),
                10, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 50, 50), 0.5f)
        );
        block.getWorld().playSound(
                block.getLocation(),
                Sound.ENTITY_SLIME_ATTACK, 0.6f, 0.8f
        );
    }

    public int getEliteBonus(Material soilType) {
        return SOIL_ELITE_BONUS.getOrDefault(soilType, 0);
    }

    public boolean isTobaccoPlant(Location location) {
        return activePlants.containsKey(normalize(location));
    }

    public TobaccoBlock getPlantAt(Location location) {
        return activePlants.get(normalize(location));
    }

    public boolean isFullyGrown(TobaccoBlock plant) {
        return plant.isFullyGrown();
    }

    private boolean isValidSoil(Material mat) {
        return mat == Material.FARMLAND;
    }

    private Location normalize(Location loc) {
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    public Map<Location, TobaccoBlock> getActivePlants() {
        return activePlants;
    }
}


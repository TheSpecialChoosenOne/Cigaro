package com.cigaro.station;

import com.cigaro.Cigaro;
import com.cigaro.data.*;
import com.cigaro.mechanic.CigaretteQualityCalculator;
import com.cigaro.mechanic.TobaccoFlavorCompatibility;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Bat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StationManager implements Listener {

    private final Cigaro plugin;
    private final Map<Location, DryingStation> dryingStations = new ConcurrentHashMap<>();
    private final Map<Location, RollingStation> rollingStations = new ConcurrentHashMap<>();
    private final Set<Location> cuttingTables = ConcurrentHashMap.newKeySet();
    private final Set<Location> infusers = ConcurrentHashMap.newKeySet();
    private final Set<Location> blendingTables = ConcurrentHashMap.newKeySet();
    private final Map<Location, AutoDryingRack> autoDryingRacks = new ConcurrentHashMap<>();
    private final Map<Location, CuttingMachine> cuttingMachines = new ConcurrentHashMap<>();
    private final Map<Location, RollingPress> rollingPresses = new ConcurrentHashMap<>();
    private final Map<Location, PackingPress> packingPresses = new ConcurrentHashMap<>();
    private final Map<Location, PackingStation> packingStations = new ConcurrentHashMap<>();
    private final Map<Location, HookahStation> hookahStations = new ConcurrentHashMap<>();
    private int taskId = -1;

    private final Map<ChunkKey, Map<Location, String>> stationsByChunk = new HashMap<>();

    private record ChunkKey(UUID world, long packed) {
        static ChunkKey of(Location loc) {
            return new ChunkKey(loc.getWorld().getUID(),
                    pack(loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        }
        static ChunkKey of(org.bukkit.Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), pack(chunk.getX(), chunk.getZ()));
        }
        static long pack(int cx, int cz) {
            return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        }
        int chunkX() { return (int) (packed >> 32); }
        int chunkZ() { return (int) packed; }
    }

    private final Map<Location, ItemDisplay> activeBlockDisplays = new HashMap<>();
    private final Map<Location, Interaction> activeInteractions = new HashMap<>();
    private final Map<UUID, Location> interactionToLocation = new HashMap<>();
    private final Map<Location, Integer> machineHits = new HashMap<>();
    private final Map<Location, BukkitTask> hitResetTasks = new HashMap<>();

    private final Random random = new Random();
    private final Set<Location> dirtyStations = ConcurrentHashMap.newKeySet();
    private static final long FLUSH_PERIOD_TICKS = 600L;
    private int flushTaskId = -1;

    private static final double PARTICLE_RADIUS = 24.0;
    private int watchdogTickCounter = 0;

    public StationManager(Cigaro plugin) { this.plugin = plugin; }

    public void start() {
        clearAllOrphanedEntities();
        loadAll();
        capDryingTimesAfterRestart();
        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L).getTaskId();
        flushTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::flushDirty, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS)
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        if (flushTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        saveAll();
        for (DryingStation drying : dryingStations.values()) {
            plugin.getDisplayManager().removeDisplays(drying.getDisplayEntityIds());
        }
        for (RollingStation rolling : rollingStations.values()) {
            plugin.getDisplayManager().removeDisplay(rolling.getDisplayEntityId());
        }
        for (Location loc : new HashSet<>(activeBlockDisplays.keySet())) {
            cleanupMachineEntities(loc);
        }
        dryingStations.clear(); rollingStations.clear(); cuttingTables.clear(); infusers.clear();
        blendingTables.clear();
        autoDryingRacks.clear(); cuttingMachines.clear(); rollingPresses.clear(); packingPresses.clear();
        packingStations.clear();
        hookahStations.clear();
        stationsByChunk.clear();
        dirtyStations.clear();
    }

    private void markDirty(Location loc) {
        dirtyStations.add(norm(loc));
    }

    private void flushDirty() {
        if (dirtyStations.isEmpty()) return;

        List<Location> batch = new ArrayList<>(dirtyStations);
        dirtyStations.clear();

        var db = plugin.getDatabaseManager();
        for (Location loc : batch) {
            CuttingMachine cutter = cuttingMachines.get(loc);
            if (cutter != null) { db.saveCuttingMachine(cutter); continue; }

            RollingPress press = rollingPresses.get(loc);
            if (press != null) { db.saveRollingPress(press); continue; }

            PackingPress packer = packingPresses.get(loc);
            if (packer != null) { db.savePackingPress(packer); continue; }

            AutoDryingRack rack = autoDryingRacks.get(loc);
            if (rack != null) { db.saveAutoDryingRack(rack); continue; }

            DryingStation drying = dryingStations.get(loc);
            if (drying != null) { db.saveDryingStation(drying); continue; }

            RollingStation rolling = rollingStations.get(loc);
            if (rolling != null) { db.saveRollingStation(rolling); continue; }

            HookahStation hookah = hookahStations.get(loc);
            if (hookah != null) db.saveHookahStation(hookah);
        }
    }

    private void tick() {
        tickDryingStations();
        tickRollingStations();
        tickAutoDryingRacks();
        tickCuttingMachines();
        tickRollingPresses();
        tickPackingPresses();
        tickHookahStations();

        watchdogTickCounter++;
        if (watchdogTickCounter >= 3) {
            watchdogTickCounter = 0;
            runWatchdog();
        }
    }

    public void clearAllOrphanedEntities() {
        NamespacedKey stationKey = new NamespacedKey(plugin, "station_display");
        NamespacedKey hookahAnchorKey = new NamespacedKey(plugin, "hookah_anchor");
        NamespacedKey glitchBlockKey = new NamespacedKey(plugin, "glitch_block");
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay || entity instanceof Interaction
                        || entity instanceof Bat || entity instanceof BlockDisplay) {
                    var pdc = entity.getPersistentDataContainer();
                    if (pdc.has(stationKey, PersistentDataType.BYTE)
                            || pdc.has(hookahAnchorKey, PersistentDataType.BYTE)
                            || pdc.has(glitchBlockKey, PersistentDataType.BYTE)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    private void indexStation(Location loc, String type) {
        Location n = norm(loc);
        stationsByChunk.computeIfAbsent(ChunkKey.of(n), k -> new HashMap<>()).put(n, type);
    }

    private void unindexStation(Location loc) {
        Location n = norm(loc);
        ChunkKey key = ChunkKey.of(n);
        Map<Location, String> inChunk = stationsByChunk.get(key);
        if (inChunk == null) return;
        inChunk.remove(n);
        if (inChunk.isEmpty()) stationsByChunk.remove(key);
    }

    private void rebuildIndex() {
        stationsByChunk.clear();
        for (Location loc : dryingStations.keySet()) indexStation(loc, "drying_rack");
        for (Location loc : rollingStations.keySet()) indexStation(loc, "rolling_machine");
        for (Location loc : cuttingTables) indexStation(loc, "cutting_table");
        for (Location loc : infusers) indexStation(loc, "infuser");
        for (Location loc : blendingTables) indexStation(loc, "blending_table");
        for (Location loc : autoDryingRacks.keySet()) indexStation(loc, "auto_drying_rack");
        for (Location loc : cuttingMachines.keySet()) indexStation(loc, "cutting_machine");
        for (Location loc : rollingPresses.keySet()) indexStation(loc, "rolling_press");
        for (Location loc : packingPresses.keySet()) indexStation(loc, "packing_press");
        for (Location loc : packingStations.keySet()) indexStation(loc, "packing_machine");
        for (Location loc : hookahStations.keySet()) indexStation(loc, "hookah");
    }

    private boolean isLoaded(Location loc) {
        org.bukkit.World world = loc.getWorld();
        return world != null && world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private boolean hasNearbyPlayer(Location loc) {
        org.bukkit.World world = loc.getWorld();
        if (world == null) return false;
        double radiusSq = PARTICLE_RADIUS * PARTICLE_RADIUS;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSq) return true;
        }
        return false;
    }

    public Location getInteractionLocation(UUID id) {
        return interactionToLocation.get(id);
    }

    public ItemStack getDisplayItem(String type) {
        var im = plugin.getItemManager();
        return switch (type.toLowerCase()) {
            case "drying_rack" -> im.createDryingRackDisplayModel();
            case "rolling_machine" -> im.createRollingMachineDisplayModel();
            case "cutting_table" -> im.createCuttingTableDisplayModel();
            case "packing_machine" -> im.createPackingMachineDisplayModel();
            case "infuser" -> im.createInfuserDisplayModel();
            case "blending_table" -> im.createBlendingTableDisplayModel();
            case "auto_drying_rack" -> im.createAutoDryingRack();
            case "cutting_machine" -> im.createCuttingMachine();
            case "rolling_press" -> im.createRollingPress();
            case "packing_press" -> im.createPackingPress();
            case "hookah" -> im.createHookah();
            default -> new ItemStack(Material.BARRIER);
        };
    }

    private void tickDryingStations() {
        for (DryingStation station : dryingStations.values()) {
            if (station.isEmpty()) continue;
            Location loc = station.getLocation();
            if (!isLoaded(loc)) continue;

            double weatherMult = getWeatherMultiplier(station);

            if (weatherMult < 1.0) {
                long slowdownMs = (long) ((1.0 - weatherMult) * 1000);

                for (DryingStation.LeafEntry entry : station.getLeafEntries()) {
                    entry.setStartTime(entry.getStartTime() + slowdownMs);
                }
            }

            if (!hasNearbyPlayer(loc)) continue;

            loc.getWorld().spawnParticle(Particle.SMOKE, loc.getX()+0.5, loc.getY()+1.2, loc.getZ()+0.5, 2, 0.1, 0.1, 0.1, 0.01);

            if (weatherMult < 1.0 && loc.getWorld().hasStorm()) {
                loc.getWorld().spawnParticle(
                        Particle.DRIPPING_WATER,
                        loc.getX() + 0.5, loc.getY() + 1.5, loc.getZ() + 0.5,
                        3, 0.2, 0.1, 0.2, 0
                );
            }

            boolean overdried = station.getLeafEntries().stream()
                    .anyMatch(e -> plugin.getConfigManager().getDryingLevel(e.getElapsedSeconds()) == DryingLevel.OVERDRIED);
            if (overdried) {
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX()+0.5, loc.getY()+1.3, loc.getZ()+0.5, 5, 0.2, 0.1, 0.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(139,0,0), 1.0f));
            }
        }
    }

    private double getWeatherMultiplier(DryingStation station) {
        Location loc = station.getLocation();
        org.bukkit.World world = loc.getWorld();
        if (world == null || !station.isOpenSky()) return 1.0;

        if (world.isThundering()) {
            return 0.3;
        }

        if (world.hasStorm()) {
            return 0.5;
        }

        return 1.0;
    }

    private void tickRollingStations() {
        for (RollingStation station : rollingStations.values()) {
            if (!station.isRolling()) continue;
            Location loc = station.getLocation();
            if (!isLoaded(loc) || !hasNearbyPlayer(loc)) continue;
            loc.getWorld().spawnParticle(Particle.CRIT, loc.getX()+0.5, loc.getY()+1.0, loc.getZ()+0.5, 5, 0.2, 0.1, 0.2, 0.02);
        }
    }

    private void tickAutoDryingRacks() {
        for (AutoDryingRack rack : autoDryingRacks.values()) {
            if (rack.getLeafEntries().isEmpty()) continue;

            Location loc = rack.getLocation();
            if (!isLoaded(loc)) continue;

            if (!rack.hasFuel()) {
                for (AutoDryingRack.AutoDryEntry entry : rack.getLeafEntries()) {
                    entry.setStartTime(entry.getStartTime() + 1000L);
                }
                continue;
            }

            rack.setFuelSeconds(rack.getFuelSeconds() - 1);
            rack.syncFuelItem();

            for (int i = rack.getLeafEntries().size() - 1; i >= 0; i--) {
                AutoDryingRack.AutoDryEntry entry = rack.getLeafEntries().get(i);
                int effectiveSeconds = entry.getEffectiveSeconds();
                DryingLevel level = plugin.getConfigManager().getDryingLevel(effectiveSeconds);

                if (level == DryingLevel.PERFECT || level == DryingLevel.OVERDRIED) {
                    int snapped = plugin.getConfigManager().getThresholdStart(level);
                    ItemStack leaf = plugin.getItemManager().createTobaccoLeaf(
                            entry.getTobaccoType(), entry.isElite(), snapped);
                    loc.getWorld().dropItemNaturally(
                            loc.clone().add(0.5, 1.2, 0.5), leaf);
                    loc.getWorld().playSound(loc,
                            Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
                    rack.removeLeaf(i);
                    markDirty(loc);
                }
            }
        }
    }

    private void tickCuttingMachines() {
        for (CuttingMachine cutter : cuttingMachines.values()) {
            if (!cutter.hasItem() || !cutter.hasFuel()) continue;
            Location loc = cutter.getLocation();
            if (!isLoaded(loc)) continue;

            cutter.setFuelSeconds(cutter.getFuelSeconds() - 1);
            cutter.syncFuelItem();
            cutter.progress += 20;

            if (cutter.progress >= 100) {
                int cutQuality = random.nextDouble() < 0.05 ? -1 : 1;
                ItemStack result = plugin.getItemManager().createCutTobacco(
                        cutter.getTobaccoType(), cutter.isElite(), cutter.getDryingLevel(),
                        cutter.getDryingLevel() != null ? plugin.getConfigManager().getThresholdStart(cutter.getDryingLevel()) : 0,
                        cutQuality);
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1.2, 0.5), result);
                loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.4f, 1.5f);
                cutter.clearItem();
                markDirty(loc);
            }
        }
    }

    private void tickRollingPresses() {
        for (RollingPress press : rollingPresses.values()) {
            if (!press.hasItems() || press.tobaccoAmount < 8 || press.paperAmount < 1 || press.tobaccoType == null || !press.hasFuel()) continue;
            Location loc = press.getLocation();
            if (!isLoaded(loc)) continue;

            press.setFuelSeconds(press.getFuelSeconds() - 1);
            press.syncFuelItem();
            press.progress += 10;

            if (press.progress >= 100) {
                ItemStack tobaccoItem = press.getTobaccoItem();
                BlendData blend = plugin.getItemManager().getBlendData(tobaccoItem);
                DryingLevel dryingLevel = plugin.getItemManager().getDryingLevel(tobaccoItem);
                int cutQuality = plugin.getItemManager().getCutQuality(tobaccoItem);
                boolean eliteLeaf = plugin.getItemManager().isCutElite(tobaccoItem);

                TobaccoType leafType = blend == null
                        ? plugin.getItemManager().getTobaccoType(tobaccoItem)
                        : null;
                double leafBonus = blend != null
                        ? plugin.getItemManager().getBlendLeafQualityBonus(blend)
                        : leafType != null ? leafType.getQualityBonus() : 0.0;
                FlavorHarmony harmony = blend == null
                        ? TobaccoFlavorCompatibility.evaluate(leafType, press.tobaccoType)
                        : FlavorHarmony.NEUTRAL;

                CigaretteQuality quality = CigaretteQualityCalculator.calculate(
                        dryingLevel,
                        cutQuality,
                        eliteLeaf,
                        leafBonus,
                        press.getOperatorMastery(),
                        press.getOperatorFlavorPredisposition(),
                        harmony);
                int maxPuffs = quality.getPuffs();

                ItemStack cigs = blend != null
                        ? plugin.getItemManager().createBlendedCigarette(
                                blend, quality, 8, maxPuffs, maxPuffs, harmony)
                        : plugin.getItemManager().createCigarette(
                                press.tobaccoType, quality, 8, maxPuffs, maxPuffs, harmony);

                if (blend == null) plugin.getItemManager().setProductTobacco(cigs, leafType);
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1.2, 0.5), cigs);
                loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.4f, 1.3f);
                press.consumeItems();
                markDirty(loc);
            }
        }
    }

    private void tickPackingPresses() {
        for (PackingPress packer : packingPresses.values()) {
            if (!packer.hasItems() || !packer.isReady()) continue;
            Location loc = packer.getLocation();
            if (!isLoaded(loc)) continue;

            packer.progress += 34;

            if (packer.progress >= 100) {
                CigaretteType type = packer.getCigaretteType();
                if (type == null) type = CigaretteType.MINT;

                DyeColor color = packer.getDyeColor();
                if (color == null) color = DyeColor.WHITE;

                CigaretteQuality quality = plugin.getItemManager().getCigaretteQuality(packer.getCigaretteItem());
                if (quality == null) quality = CigaretteQuality.STANDARD;

                BlendData blend = plugin.getItemManager().getBlendData(packer.getCigaretteItem());
                FlavorHarmony harmony = plugin.getItemManager()
                        .getFlavorHarmony(packer.getCigaretteItem());
                int maxPuffs = plugin.getItemManager().getPuffsMax(packer.getCigaretteItem());
                if (maxPuffs <= 0) maxPuffs = quality.getPuffs();

                List<ItemStack> packCigs = new ArrayList<>();
                for (int i = 0; i < PackingPress.CIGARETTES_PER_PACK; i++) {
                    packCigs.add(blend != null
                            ? plugin.getItemManager().createBlendedCigarette(
                                    blend, quality, 1, maxPuffs, maxPuffs, harmony)
                            : plugin.getItemManager().createCigarette(
                                    type, quality, 1, maxPuffs, maxPuffs, harmony));
                }

                ItemStack pack = plugin.getItemManager().createCigarettePack(packCigs, color);
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1.2, 0.5), pack);
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
                packer.consumeItems();
                markDirty(loc);
            }
        }
    }

    private void tickHookahStations() {
        for (HookahStation station : hookahStations.values()) {
            if (!station.isReady()) continue;
            Location loc = station.getLocation();
            if (!isLoaded(loc) || !hasNearbyPlayer(loc)) continue;

            loc.getWorld().spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE,
                    loc.getX() + 0.5, loc.getY() + 1.5,
                    loc.getZ() + 0.5,
                    3, 0.1, 0.2, 0.1, 0.005
            );
        }
    }

    private void runWatchdog() {
        for (Map.Entry<ChunkKey, Map<Location, String>> entry : stationsByChunk.entrySet()) {
            ChunkKey key = entry.getKey();
            org.bukkit.World world = plugin.getServer().getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) continue;

            for (Map.Entry<Location, String> station : entry.getValue().entrySet()) {
                checkAndSpawnIfNeeded(station.getKey(), station.getValue());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent event) {
        spawnEntitiesInChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Location, String> inChunk = stationsByChunk.get(ChunkKey.of(event.getChunk()));
        if (inChunk == null || inChunk.isEmpty()) return;

        for (Location loc : new ArrayList<>(inChunk.keySet())) {
            cleanupMachineEntities(loc);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyBlockPlace(BlockPlaceEvent event) {
        invalidateSkyCacheAbove(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkyBlockBreak(BlockBreakEvent event) {
        if (event instanceof StationBreakCheckEvent) return;
        invalidateSkyCacheAbove(event.getBlock());
    }

    private void invalidateSkyCacheAbove(Block block) {
        Map<Location, String> inChunk = stationsByChunk.get(ChunkKey.of(block.getChunk()));
        if (inChunk == null || inChunk.isEmpty()) return;

        for (Map.Entry<Location, String> entry : inChunk.entrySet()) {
            if (!"drying_rack".equals(entry.getValue())) continue;

            Location loc = entry.getKey();
            if (loc.getBlockX() != block.getX()
                    || loc.getBlockZ() != block.getZ()
                    || block.getY() <= loc.getBlockY()) continue;

            DryingStation station = dryingStations.get(loc);
            if (station != null) station.invalidateSkyCache();
        }
    }

    private void spawnEntitiesInChunk(org.bukkit.Chunk chunk) {
        Map<Location, String> inChunk = stationsByChunk.get(ChunkKey.of(chunk));
        if (inChunk == null || inChunk.isEmpty()) return;

        for (Map.Entry<Location, String> entry : inChunk.entrySet()) {
            Location loc = entry.getKey();
            String type = entry.getValue();
            setupMachineEntities(loc, type);

            if (type.equals("drying_rack")) {
                DryingStation station = dryingStations.get(loc);
                if (station != null) station.invalidateSkyCache();
                updateDryingStationLeavesDisplay(station);
            } else if (type.equals("rolling_machine")) {
                updateRollingStationDisplay(rollingStations.get(loc));
            }
        }
    }

    public void checkAndSpawnIfNeeded(Location loc, String type) {
        Location normLoc = norm(loc);
        if (!isLoaded(normLoc)) return;

        ItemDisplay display = activeBlockDisplays.get(normLoc);
        Interaction interaction = activeInteractions.get(normLoc);

        boolean displayMissing = display == null || !display.isValid();
        boolean interactionMissing = interaction == null || !interaction.isValid();

        if (displayMissing || interactionMissing) {
            setupMachineEntities(normLoc, type);

            if (type.equalsIgnoreCase("drying_rack")) {
                updateDryingStationLeavesDisplay(dryingStations.get(normLoc));
            } else if (type.equalsIgnoreCase("rolling_machine")) {
                updateRollingStationDisplay(rollingStations.get(normLoc));
            }
        }
    }

    public void setupMachineEntities(Location loc, String type) {
        Location normLoc = norm(loc);
        Block block = normLoc.getBlock();
        if (block.getType() != Material.BARRIER) {
            block.setType(Material.BARRIER);
        }

        cleanupMachineEntities(normLoc);

        ItemStack displayItem = getDisplayItem(type);
        Location displayLoc = normLoc.clone().add(0.5, 0.5, 0.5);

        float scale = displayItem.getType().isBlock() ? 2.0f : 1.0f;

        ItemDisplay display = normLoc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(displayItem);
            entity.setPersistent(false);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "station_display"), PersistentDataType.BYTE, (byte) 1);
        });
        activeBlockDisplays.put(normLoc, display);

        Location interactionLoc = normLoc.clone().add(0.5, 0.0, 0.5);
        Interaction interaction = normLoc.getWorld().spawn(interactionLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(1.02f);
            entity.setInteractionHeight(1.02f);
            entity.setPersistent(false);
            entity.setResponsive(true);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "station_display"), PersistentDataType.BYTE, (byte) 1);
        });
        activeInteractions.put(normLoc, interaction);
        interactionToLocation.put(interaction.getUniqueId(), normLoc);
    }

    public void cleanupMachineEntities(Location loc) {
        Location normLoc = norm(loc);
        ItemDisplay display = activeBlockDisplays.remove(normLoc);
        if (display != null && display.isValid()) {
            display.remove();
        }
        Interaction interaction = activeInteractions.remove(normLoc);
        if (interaction != null && interaction.isValid()) {
            interactionToLocation.remove(interaction.getUniqueId());
            interaction.remove();
        }
    }

    public void updateDryingStationLeavesDisplay(DryingStation station) {
        if (station == null) return;
        plugin.getDisplayManager().removeDisplays(station.getDisplayEntityIds());
        if (station.isEmpty()) {
            station.setDisplayEntityIds(new ArrayList<>());
            return;
        }
        DryingStation.LeafEntry first = station.getLeafEntries().get(0);
        boolean hasElite = station.getLeafEntries().stream().anyMatch(DryingStation.LeafEntry::isElite);
        ItemStack leafItem = plugin.getItemManager().createTobaccoLeaf(first.getTobaccoType(), hasElite, 0);

        List<UUID> ids = spawnLeafDisplaysNonPersistent(station.getLocation(), station.getTotalLeaves(), leafItem);
        station.setDisplayEntityIds(ids);
    }

    private List<UUID> spawnLeafDisplaysNonPersistent(Location blockLocation, int count, ItemStack leafItem) {
        List<UUID> ids = new ArrayList<>();
        int toSpawn = Math.min(count, 16);

        float[][] LEAF_POSITIONS = {
                { 0.0f,  0.0f}, {-0.2f, -0.2f}, { 0.2f, -0.2f}, {-0.2f,  0.2f},
                { 0.2f,  0.2f}, { 0.0f, -0.25f}, { 0.0f,  0.25f}, {-0.25f, 0.0f},
                { 0.25f, 0.0f}, {-0.1f, -0.1f}, { 0.1f,  0.1f}, {-0.15f, 0.15f},
                { 0.15f,-0.15f}, { 0.0f, -0.1f}, {-0.1f,  0.0f}, { 0.1f,  0.0f},
        };

        for (int i = 0; i < toSpawn; i++) {
            float offsetX = LEAF_POSITIONS[i][0];
            float offsetZ = LEAF_POSITIONS[i][1];
            float offsetY = 1.02f + (i * 0.008f);
            float rotation = random.nextFloat() * 360f;

            Location spawnLoc = blockLocation.clone().add(0.5 + offsetX, offsetY, 0.5 + offsetZ);

            ItemDisplay display = blockLocation.getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(leafItem);
                entity.setPersistent(false);
                entity.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                        new Vector3f(0.25f, 0.25f, 0.25f),
                        new AxisAngle4f((float) Math.toRadians(rotation), 0, 1, 0)
                ));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "station_display"), PersistentDataType.BYTE, (byte) 1);
            });

            ids.add(display.getUniqueId());
        }
        return ids;
    }

    public void updateRollingStationDisplay(RollingStation station) {
        if (station == null) return;
        plugin.getDisplayManager().removeDisplay(station.getDisplayEntityId());
        station.setDisplayEntityId(null);

        if (!station.hasLeaf()) return;

        ItemStack leafItem;
        if (station.isCigarMode()) {
            leafItem = plugin.getItemManager().createTobaccoLeaf(station.getLeafType(), station.isEliteLeaf(), 300);
        } else if (station.isBlend()) {
            leafItem = plugin.getItemManager().createBlendedCutTobacco(
                    station.getBlend(), station.isEliteLeaf(), station.getDryingLevel(),
                    station.getDryingLevel() != null ? plugin.getConfigManager().getThresholdStart(station.getDryingLevel()) : 0,
                    station.getCutQuality(), 1);
        } else {
            leafItem = plugin.getItemManager().createCutTobacco(
                    station.getLeafType(), station.isEliteLeaf(), station.getDryingLevel(),
                    station.getDryingLevel() != null ? plugin.getConfigManager().getThresholdStart(station.getDryingLevel()) : 0,
                    station.getCutQuality());
        }

        Location spawnLoc = station.getLocation().clone().add(0.5, 1.05, 0.5);
        ItemDisplay display = station.getLocation().getWorld().spawn(spawnLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(leafItem);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "station_display"), PersistentDataType.BYTE, (byte) 1);
        });

        station.setDisplayEntityId(display.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Interaction)) return;
        if (!entity.getPersistentDataContainer().has(new NamespacedKey(plugin, "station_display"), PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        handleMachineHit(player, entity);
    }

    public void handleMachineHit(Player player, Entity interactionEntity) {
        UUID interactionId = interactionEntity.getUniqueId();
        Location loc = interactionToLocation.get(interactionId);
        if (loc == null) return;

        BlockBreakEvent breakCheck = new StationBreakCheckEvent(loc.getBlock(), player);
        plugin.getServer().getPluginManager().callEvent(breakCheck);
        if (breakCheck.isCancelled()) return;

        if (player.getGameMode() == GameMode.CREATIVE) {
            breakMachine(loc, player);
            return;
        }

        int hits = machineHits.getOrDefault(loc, 0) + 1;
        machineHits.put(loc, hits);

        loc.getWorld().playSound(loc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);

        BukkitTask existing = hitResetTasks.remove(loc);
        if (existing != null) existing.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            machineHits.remove(loc);
            hitResetTasks.remove(loc);
        }, 80L);
        hitResetTasks.put(loc, task);

        if (hits >= 3) {
            breakMachine(loc, player);
        } else {
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                bar.append(i < hits ? "■" : "□");
            }
            player.sendActionBar(plugin.getLanguageManager().get("machine_dismantle_progress",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                            "bar", "[" + bar + "]")));
        }
    }

    private void breakMachine(Location loc, Player player) {
        Location normLoc = norm(loc);

        BukkitTask task = hitResetTasks.remove(normLoc);
        if (task != null) task.cancel();
        machineHits.remove(normLoc);

        normLoc.getWorld().playSound(normLoc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.0f);
        normLoc.getWorld().spawnParticle(Particle.BLOCK, normLoc.clone().add(0.5, 0.5, 0.5), 30, 0.2, 0.2, 0.2, 0.1, Material.ANVIL.createBlockData());

        ItemStack dropItem = null;

        if (dryingStations.containsKey(normLoc)) {
            DryingStation drying = dryingStations.get(normLoc);
            for (DryingStation.LeafEntry entry : drying.getLeafEntries()) {
                int elapsed = entry.getElapsedSeconds();
                DryingLevel level = plugin.getConfigManager().getDryingLevel(elapsed);
                int snappedSeconds = plugin.getConfigManager().getThresholdStart(level);
                normLoc.getWorld().dropItemNaturally(normLoc, plugin.getItemManager().createTobaccoLeaf(entry.getTobaccoType(), entry.isElite(), snappedSeconds));
            }
            plugin.getDisplayManager().removeDisplays(drying.getDisplayEntityIds());
            removeDryingStation(normLoc);
            dropItem = plugin.getItemManager().createDryingRack();
        }
        else if (rollingStations.containsKey(normLoc)) {
            RollingStation rolling = rollingStations.get(normLoc);
            if (rolling.isCigarMode() && rolling.getCigarLeavesCount() > 0 && rolling.getLeafType() != null) {
                int perfectSec = plugin.getConfigManager().getThresholdStart(DryingLevel.PERFECT);
                for (int i = 0; i < rolling.getCigarLeavesCount(); i++) {
                    normLoc.getWorld().dropItemNaturally(normLoc,
                            plugin.getItemManager().createTobaccoLeaf(rolling.getLeafType(), true, perfectSec));
                }
            } else if (rolling.hasLeaf() && rolling.getLeafType() != null) {
                DryingLevel level = rolling.getDryingLevel();
                int sec = level != null ? plugin.getConfigManager().getThresholdStart(level) : 0;
                ItemStack stored = rolling.isBlend()
                        ? plugin.getItemManager().createBlendedCutTobacco(
                                rolling.getBlend(), rolling.isEliteLeaf(), level, sec, rolling.getCutQuality(), 1)
                        : plugin.getItemManager().createCutTobacco(
                                rolling.getLeafType(), rolling.isEliteLeaf(), level, sec, rolling.getCutQuality());
                normLoc.getWorld().dropItemNaturally(normLoc, stored);
            }
            if (rolling.hasFlavor() && rolling.getFlavorType() != null) {
                normLoc.getWorld().dropItemNaturally(normLoc,
                        plugin.getItemManager().createFlavorBottle(rolling.getFlavorType()));
            }
            plugin.getDisplayManager().removeDisplay(rolling.getDisplayEntityId());
            removeRollingStation(normLoc);
            dropItem = plugin.getItemManager().createRollingMachine();
        }
        else if (cuttingTables.contains(normLoc)) {
            removeCuttingTable(normLoc);
            dropItem = plugin.getItemManager().createCuttingTable();
        }
        else if (infusers.contains(normLoc)) {
            removeInfuser(normLoc);
            dropItem = plugin.getItemManager().createInfuser();
        }
        else if (blendingTables.contains(normLoc)) {
            removeBlendingTable(normLoc);
            dropItem = plugin.getItemManager().createBlendingTable();
        }
        else if (autoDryingRacks.containsKey(normLoc)) {
            AutoDryingRack rack = autoDryingRacks.get(normLoc);
            rack.syncFuelItem();
            if (rack.getFuelItem() != null) {
                normLoc.getWorld().dropItemNaturally(normLoc, rack.getFuelItem());
            }
            for (AutoDryingRack.AutoDryEntry entry : rack.getLeafEntries()) {
                int elapsed = entry.getEffectiveSeconds();
                DryingLevel level = plugin.getConfigManager().getDryingLevel(elapsed);
                int snapped = plugin.getConfigManager().getThresholdStart(level);
                normLoc.getWorld().dropItemNaturally(normLoc, plugin.getItemManager().createTobaccoLeaf(entry.getTobaccoType(), entry.isElite(), snapped));
            }
            removeAutoDryingRack(normLoc);
            dropItem = plugin.getItemManager().createAutoDryingRack();
        }
        else if (cuttingMachines.containsKey(normLoc)) {
            CuttingMachine cutter = cuttingMachines.get(normLoc);
            if (cutter.hasItem() && cutter.progress < 100) {
                if (cutter.getLeafItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, cutter.getLeafItem());
            }
            cutter.syncFuelItem();
            if (cutter.getFuelItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, cutter.getFuelItem());
            removeCuttingMachine(normLoc);
            dropItem = plugin.getItemManager().createCuttingMachine();
        }
        else if (rollingPresses.containsKey(normLoc)) {
            RollingPress press = rollingPresses.get(normLoc);
            if (press.progress < 100) {
                if (press.getTobaccoItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, press.getTobaccoItem());
                if (press.getPaperItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, press.getPaperItem());
            }
            press.syncFuelItem();
            if (press.getFuelItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, press.getFuelItem());
            removeRollingPress(normLoc);
            dropItem = plugin.getItemManager().createRollingPress();
        }
        else if (packingPresses.containsKey(normLoc)) {
            PackingPress packer = packingPresses.get(normLoc);
            if (packer.progress < 100) {
                if (packer.getCigaretteItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, packer.getCigaretteItem());
                if (packer.getCardboardItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, packer.getCardboardItem());
                if (packer.getDyeItem() != null) normLoc.getWorld().dropItemNaturally(normLoc, packer.getDyeItem());
            }
            removePackingPress(normLoc);
            dropItem = plugin.getItemManager().createPackingPress();
        }
        else if (packingStations.containsKey(normLoc)) {
            PackingStation station = packingStations.get(normLoc);
            for (ItemStack cig : station.getLoadedCigarettes()) {
                normLoc.getWorld().dropItemNaturally(normLoc, cig);
            }
            removePackingStation(normLoc);
            dropItem = plugin.getItemManager().createPackingMachine();
        }
        else if (hookahStations.containsKey(normLoc)) {
            HookahStation station = hookahStations.get(normLoc);
            if (station.getDisplayEntityId() != null) {
                plugin.getDisplayManager().removeDisplay(station.getDisplayEntityId());
            }
            removeHookahStation(normLoc);
            dropItem = plugin.getItemManager().createHookah();
        }

        cleanupMachineEntities(normLoc);

        if (dropItem != null && (player == null || player.getGameMode() != GameMode.CREATIVE)) {
            normLoc.getWorld().dropItemNaturally(normLoc, dropItem);
        }

        if (normLoc.getBlock().getType() == Material.BARRIER) {
            normLoc.getBlock().setType(Material.AIR);
        }
    }

    public DryingStation createDryingStation(Location loc) { Location n=norm(loc); DryingStation s=new DryingStation(n); dryingStations.put(n,s); indexStation(n, "drying_rack"); plugin.getDatabaseManager().saveDryingStation(s); setupMachineEntities(n, "drying_rack"); return s; }
    public DryingStation getDryingStation(Location loc) { return dryingStations.get(norm(loc)); }
    public void removeDryingStation(Location loc) { Location n=norm(loc); dryingStations.remove(n); unindexStation(n); plugin.getDatabaseManager().removeDryingStation(n); cleanupMachineEntities(n); }

    public RollingStation createRollingStation(Location loc) { Location n=norm(loc); RollingStation s=new RollingStation(n); rollingStations.put(n,s); indexStation(n, "rolling_machine"); plugin.getDatabaseManager().saveRollingStation(s); setupMachineEntities(n, "rolling_machine"); return s; }
    public RollingStation getRollingStation(Location loc) { return rollingStations.get(norm(loc)); }
    public void removeRollingStation(Location loc) { Location n=norm(loc); rollingStations.remove(n); unindexStation(n); plugin.getDatabaseManager().removeRollingStation(n); cleanupMachineEntities(n); }

    public void createCuttingTable(Location loc) { Location n=norm(loc); cuttingTables.add(n); indexStation(n, "cutting_table"); plugin.getDatabaseManager().saveCuttingTable(n); setupMachineEntities(n, "cutting_table"); }
    public boolean isCuttingTable(Location loc) { return cuttingTables.contains(norm(loc)); }
    public void removeCuttingTable(Location loc) { Location n=norm(loc); cuttingTables.remove(n); unindexStation(n); plugin.getDatabaseManager().removeCuttingTable(n); cleanupMachineEntities(n); }

    public void createInfuser(Location loc) { Location n=norm(loc); infusers.add(n); indexStation(n, "infuser"); plugin.getDatabaseManager().saveInfuser(n); setupMachineEntities(n, "infuser"); }
    public boolean isInfuser(Location loc) { return infusers.contains(norm(loc)); }
    public void removeInfuser(Location loc) { Location n=norm(loc); infusers.remove(n); unindexStation(n); plugin.getDatabaseManager().removeInfuser(n); cleanupMachineEntities(n); }

    public void createBlendingTable(Location loc) { Location n=norm(loc); blendingTables.add(n); indexStation(n, "blending_table"); plugin.getDatabaseManager().saveBlendingTable(n); setupMachineEntities(n, "blending_table"); }
    public boolean isBlendingTable(Location loc) { return blendingTables.contains(norm(loc)); }
    public void removeBlendingTable(Location loc) { Location n=norm(loc); blendingTables.remove(n); unindexStation(n); plugin.getDatabaseManager().removeBlendingTable(n); cleanupMachineEntities(n); }

    public void addAutoDryingRack(AutoDryingRack r) { Location n=norm(r.getLocation()); autoDryingRacks.put(n,r); indexStation(n, "auto_drying_rack"); plugin.getDatabaseManager().saveAutoDryingRack(r); setupMachineEntities(n, "auto_drying_rack"); }
    public AutoDryingRack getAutoDryingRack(Location loc) { return autoDryingRacks.get(norm(loc)); }
    public void removeAutoDryingRack(Location loc) { Location n=norm(loc); autoDryingRacks.remove(n); unindexStation(n); plugin.getDatabaseManager().removeAutoDryingRack(n); cleanupMachineEntities(n); }

    public void addCuttingMachine(CuttingMachine c) { Location n=norm(c.getLocation()); cuttingMachines.put(n,c); indexStation(n, "cutting_machine"); plugin.getDatabaseManager().saveCuttingMachine(c); setupMachineEntities(n, "cutting_machine"); }
    public CuttingMachine getCuttingMachine(Location loc) { return cuttingMachines.get(norm(loc)); }
    public void removeCuttingMachine(Location loc) { Location n=norm(loc); cuttingMachines.remove(n); unindexStation(n); plugin.getDatabaseManager().removeCuttingMachine(n); cleanupMachineEntities(n); }

    public void addRollingPress(RollingPress p) { Location n=norm(p.getLocation()); rollingPresses.put(n,p); indexStation(n, "rolling_press"); plugin.getDatabaseManager().saveRollingPress(p); setupMachineEntities(n, "rolling_press"); }
    public RollingPress getRollingPress(Location loc) { return rollingPresses.get(norm(loc)); }
    public void removeRollingPress(Location loc) { Location n=norm(loc); rollingPresses.remove(n); unindexStation(n); plugin.getDatabaseManager().removeRollingPress(n); cleanupMachineEntities(n); }

    public void addPackingPress(PackingPress p) { Location n=norm(p.getLocation()); packingPresses.put(n,p); indexStation(n, "packing_press"); plugin.getDatabaseManager().savePackingPress(p); setupMachineEntities(n, "packing_press"); }
    public PackingPress getPackingPress(Location loc) { return packingPresses.get(norm(loc)); }
    public void removePackingPress(Location loc) { Location n=norm(loc); packingPresses.remove(n); unindexStation(n); plugin.getDatabaseManager().removePackingPress(n); cleanupMachineEntities(n); }

    public void addPackingStation(PackingStation s) { Location n=norm(s.getLocation()); packingStations.put(n,s); indexStation(n, "packing_machine"); plugin.getDatabaseManager().savePackingStation(s); setupMachineEntities(n, "packing_machine"); }
    public PackingStation getPackingStation(Location loc) { return packingStations.get(norm(loc)); }
    public void removePackingStation(Location loc) { Location n=norm(loc); packingStations.remove(n); unindexStation(n); plugin.getDatabaseManager().removePackingStation(n); cleanupMachineEntities(n); }
    public PackingStation createPackingStation(Location loc) { PackingStation s = new PackingStation(loc); addPackingStation(s); return s; }

    public void addHookahStation(HookahStation h) { Location n=norm(h.getLocation()); hookahStations.put(n,h); indexStation(n, "hookah"); plugin.getDatabaseManager().saveHookahStation(h); setupMachineEntities(n, "hookah"); }
    public HookahStation getHookahStation(Location loc) { return hookahStations.get(norm(loc)); }
    public void removeHookahStation(Location loc) { Location n=norm(loc); hookahStations.remove(n); unindexStation(n); plugin.getDatabaseManager().removeHookahStation(n); cleanupMachineEntities(n); }

    private void loadAll() {
        dryingStations.clear(); rollingStations.clear(); cuttingTables.clear(); infusers.clear();
        blendingTables.clear();
        autoDryingRacks.clear(); cuttingMachines.clear(); rollingPresses.clear(); packingPresses.clear();
        packingStations.clear();
        hookahStations.clear();
        for (DryingStation s : plugin.getDatabaseManager().loadAllDryingStations()) dryingStations.put(s.getLocation(), s);
        for (RollingStation s : plugin.getDatabaseManager().loadAllRollingStations()) rollingStations.put(s.getLocation(), s);
        cuttingTables.addAll(plugin.getDatabaseManager().loadAllCuttingTables());
        infusers.addAll(plugin.getDatabaseManager().loadAllInfusers());
        blendingTables.addAll(plugin.getDatabaseManager().loadAllBlendingTables());
        for (AutoDryingRack r : plugin.getDatabaseManager().loadAllAutoDryingRacks()) autoDryingRacks.put(norm(r.getLocation()), r);
        for (CuttingMachine c : plugin.getDatabaseManager().loadAllCuttingMachines()) cuttingMachines.put(norm(c.getLocation()), c);
        for (RollingPress p : plugin.getDatabaseManager().loadAllRollingPresses()) rollingPresses.put(norm(p.getLocation()), p);
        for (PackingPress p : plugin.getDatabaseManager().loadAllPackingPresses()) packingPresses.put(norm(p.getLocation()), p);
        for (PackingStation ps : plugin.getDatabaseManager().loadAllPackingStations()) packingStations.put(norm(ps.getLocation()), ps);
        for (HookahStation h : plugin.getDatabaseManager().loadAllHookahStations()) hookahStations.put(norm(h.getLocation()), h);

        rebuildIndex();

        plugin.getLogger().info("Loaded stations: " + dryingStations.size() + " drying, " + rollingStations.size() + " rolling, "
                + autoDryingRacks.size() + " auto-dry, " + cuttingMachines.size() + " cutters, "
                + rollingPresses.size() + " r-press, " + packingPresses.size() + " p-press, "
                + packingStations.size() + " packing (manual), "
                + hookahStations.size() + " hookah, " + blendingTables.size() + " blending"
                + " in " + stationsByChunk.size() + " chunks");
    }

    private void saveAll() {
        dirtyStations.clear();

        var db = plugin.getDatabaseManager();
        new ArrayList<>(dryingStations.values()).forEach(db::saveDryingStationSync);
        new ArrayList<>(rollingStations.values()).forEach(db::saveRollingStationSync);
        new ArrayList<>(autoDryingRacks.values()).forEach(db::saveAutoDryingRackSync);
        new ArrayList<>(cuttingMachines.values()).forEach(db::saveCuttingMachineSync);
        new ArrayList<>(rollingPresses.values()).forEach(db::saveRollingPressSync);
        new ArrayList<>(packingPresses.values()).forEach(db::savePackingPressSync);
        new ArrayList<>(packingStations.values()).forEach(db::savePackingStationSync);
        new ArrayList<>(hookahStations.values()).forEach(db::saveHookahStationSync);
    }

    private Location norm(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private void capDryingTimesAfterRestart() {
        long now = System.currentTimeMillis();
        long maxOfflineDryingMs = 30 * 60 * 1000L;

        for (DryingStation station : dryingStations.values()) {
            for (DryingStation.LeafEntry entry : station.getLeafEntries()) {
                long elapsed = now - entry.getStartTime();
                if (elapsed > maxOfflineDryingMs) {
                    entry.setStartTime(now - maxOfflineDryingMs);
                }
            }
        }

        for (AutoDryingRack rack : autoDryingRacks.values()) {
            for (AutoDryingRack.AutoDryEntry entry : rack.getLeafEntries()) {
                long elapsed = now - entry.getStartTime();
                if (elapsed > maxOfflineDryingMs) {
                    entry.setStartTime(now - maxOfflineDryingMs);
                }
            }
        }
    }
}


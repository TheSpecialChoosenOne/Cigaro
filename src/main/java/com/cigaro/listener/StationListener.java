package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.config.LanguageManager;
import com.cigaro.data.*;
import com.cigaro.gui.CuttingTableGui;
import com.cigaro.mechanic.CigaretteQualityCalculator;
import com.cigaro.mechanic.TobaccoFlavorCompatibility;
import com.cigaro.station.DryingStation;
import com.cigaro.station.DryingStation.LeafEntry;
import com.cigaro.station.RollingStation;
import com.cigaro.station.StationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class StationListener implements Listener {

    private final Cigaro plugin;
    private final Map<Location, Long> interactLock = new HashMap<>();
    private static final long LOCK_MS = 200;

    public StationListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    private boolean tryLock(Location loc) {
        long now = System.currentTimeMillis();
        Long last = interactLock.get(loc);
        if (last != null && (now - last) < LOCK_MS) return false;
        interactLock.put(loc, now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (plugin.getItemManager().isDryingRack(item)) {
            plugin.getStationManager().createDryingStation(event.getBlock().getLocation());
            if (plugin.getConfigManager().isShowPlantMessages())
                player.sendMessage(lang().get("station_dryrack_placed"));
            return;
        }
        if (plugin.getItemManager().isInfuser(item)) {
            plugin.getStationManager().createInfuser(event.getBlock().getLocation());
            if (plugin.getConfigManager().isShowPlantMessages())
                player.sendMessage(lang().get("station_infuser_placed"));
            return;
        }
        if (plugin.getItemManager().isRollingMachine(item)) {
            plugin.getStationManager().createRollingStation(event.getBlock().getLocation());
            if (plugin.getConfigManager().isShowPlantMessages())
                player.sendMessage(lang().get("station_roller_placed"));
            return;
        }
        if (plugin.getItemManager().isCuttingTable(item)) {
            plugin.getStationManager().createCuttingTable(event.getBlock().getLocation());
            if (plugin.getConfigManager().isShowPlantMessages())
                player.sendMessage(lang().get("station_cuttable_placed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStationInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        Location loc = plugin.getStationManager().getInteractionLocation(entity.getUniqueId());
        if (loc == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isDryingTimer(hand)) {
            return;
        }

        StationManager sm = plugin.getStationManager();

        if (sm.isCuttingTable(loc)) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            new CuttingTableGui(player, plugin).open();
            return;
        }

        if (sm.isInfuser(loc)) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleInfuser(player, loc);
            return;
        }

        DryingStation drying = sm.getDryingStation(loc);
        if (drying != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleDrying(player, drying);
            return;
        }

        RollingStation rolling = sm.getRollingStation(loc);
        if (rolling != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleRolling(player, rolling);
        }
    }

    private void handleInfuser(Player player, Location loc) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand.getType() == Material.AIR) {
            player.sendMessage(lang().get("machine_infuser_header"));
            player.sendMessage(lang().get("machine_infuser_hint_main"));
            player.sendMessage(lang().get("machine_infuser_hint_off"));
            player.sendMessage(lang().get("machine_infuser_hint_brew"));
            return;
        }

        if (!plugin.getItemManager().isRawIngredient(mainHand)) {
            player.sendMessage(lang().get("machine_infuser_need_ingredient"));
            return;
        }

        if (offHand.getType() != Material.GLASS_BOTTLE) {
            player.sendMessage(lang().get("machine_infuser_need_bottle"));
            return;
        }

        CigaretteType type = plugin.getItemManager().getRawIngredientType(mainHand);
        if (type == null) {
            player.sendMessage(lang().get("machine_infuser_unknown"));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            offHand.setAmount(offHand.getAmount() - 1);
        }

        ItemStack bottle = plugin.getItemManager().createFlavorBottle(type);
        var leftovers = player.getInventory().addItem(bottle);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        loc.getWorld().playSound(loc.clone().add(0.5, 0.5, 0.5), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.2f);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.1, 0.5), 8, 0.2, 0.2, 0.2, 0.02);

        player.sendMessage(lang().get("machine_infuser_brewed",
                Placeholder.unparsed("type", plugin.getItemManager().formatTypeName(type))));
    }

    private void handleDrying(Player player, DryingStation station) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean sneaking = player.isSneaking();

        if (hand.getType() == Material.AIR && !station.isEmpty()) {
            if (sneaking) collectAllLeaves(player, station);
            else collectOneLeaf(player, station);
            return;
        }

        if (plugin.getItemManager().isTobaccoLeaf(hand)) {
            if (station.isFull()) {
                player.sendMessage(lang().get("station_dry_full"));
                return;
            }

            boolean elite = plugin.getItemManager().isEliteLeaf(hand);
            TobaccoType tobaccoType = plugin.getItemManager().getTobaccoType(hand);
            if (tobaccoType == null) tobaccoType = TobaccoType.VIRGINIA;

            int alreadyDried = plugin.getItemManager().getDryingSeconds(hand);

            if (sneaking) {
                int toAdd = Math.min(hand.getAmount(), 16 - station.getTotalLeaves());
                int added = station.addLeaves(tobaccoType, toAdd, elite, alreadyDried);
                if (player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - added);
                player.sendMessage(lang().get("station_dry_add_many",
                        Placeholder.unparsed("count", String.valueOf(added)),
                        Placeholder.unparsed("total", String.valueOf(station.getTotalLeaves()))));
            } else {
                station.addLeaf(tobaccoType, elite, alreadyDried);
                if (player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                player.sendMessage(lang().get("station_dry_add_one",
                        Placeholder.unparsed("total", String.valueOf(station.getTotalLeaves()))));
            }

            plugin.getStationManager().updateDryingStationLeavesDisplay(station);
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            plugin.getDatabaseManager().saveDryingStation(station);
            return;
        }

        showDryingStatus(player, station);
    }

    private void collectOneLeaf(Player player, DryingStation station) {
        LeafEntry entry = station.removeLastLeaf();
        if (entry == null) return;

        int elapsed = entry.getElapsedSeconds();
        DryingLevel level = plugin.getConfigManager().getDryingLevel(elapsed);
        int snappedSeconds = plugin.getConfigManager().getThresholdStart(level);

        ItemStack leaf = plugin.getItemManager().createTobaccoLeaf(entry.getTobaccoType(), entry.isElite(), snappedSeconds);

        var leftovers = player.getInventory().addItem(leaf);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        plugin.getStationManager().updateDryingStationLeavesDisplay(station);

        player.sendMessage(lang().get("station_dry_remove_one",
                TagResolver.resolver("level_color", Tag.styling(level.getColor())),
                Placeholder.unparsed("level", level.getSymbol() + " " + plugin.getItemManager().formatDryingLevelName(level)),
                Placeholder.unparsed("total", String.valueOf(station.getTotalLeaves()))));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        plugin.getDatabaseManager().saveDryingStation(station);
    }

    private void collectAllLeaves(Player player, DryingStation station) {
        List<LeafEntry> entries = station.removeAllLeaves();
        if (entries.isEmpty()) return;

        int count = 0;
        Map<DryingLevel, Integer> levelCounts = new LinkedHashMap<>();

        for (LeafEntry entry : entries) {
            int elapsed = entry.getElapsedSeconds();
            DryingLevel level = plugin.getConfigManager().getDryingLevel(elapsed);
            int snappedSeconds = plugin.getConfigManager().getThresholdStart(level);
            ItemStack leaf = plugin.getItemManager().createTobaccoLeaf(entry.getTobaccoType(), entry.isElite(), snappedSeconds);

            var leftovers = player.getInventory().addItem(leaf);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }

            levelCounts.merge(level, 1, Integer::sum);
            count++;
        }

        plugin.getStationManager().updateDryingStationLeavesDisplay(station);

        Component msg = lang().get("station_dry_remove_all",
                Placeholder.unparsed("count", String.valueOf(count)));
        boolean first = true;
        for (var e : levelCounts.entrySet()) {
            if (!first) msg = msg.append(Component.text(", ").color(CigaroColors.ASH));
            msg = msg.append(Component.text(e.getValue() + "x " + plugin.getItemManager().formatDryingLevelName(e.getKey())).color(e.getKey().getColor()));
            first = false;
        }
        player.sendMessage(msg);

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        plugin.getDatabaseManager().saveDryingStation(station);
    }

    private void showDryingStatus(Player player, DryingStation station) {
        if (station.isEmpty()) {
            player.sendMessage(lang().get("station_dry_empty_info"));
            return;
        }

        Map<DryingLevel, Integer> levelCounts = new LinkedHashMap<>();
        for (LeafEntry entry : station.getLeafEntries()) {
            int elapsed = entry.getElapsedSeconds();
            DryingLevel level = plugin.getConfigManager().getDryingLevel(elapsed);
            levelCounts.merge(level, 1, Integer::sum);
        }

        player.sendMessage(lang().get("station_dry_status_header",
                Placeholder.unparsed("total", String.valueOf(station.getTotalLeaves()))));

        for (var e : levelCounts.entrySet()) {
            player.sendMessage(Component.text("  " + plugin.getItemManager().formatDryingLevelName(e.getKey()) + ": " + e.getValue())
                    .color(e.getKey().getColor()));
        }

        LeafEntry oldest = station.getLeafEntries().get(0);
        int oldestElapsed = oldest.getElapsedSeconds();
        DryingLevel currentLevel = plugin.getConfigManager().getDryingLevel(oldestElapsed);

        if (currentLevel != DryingLevel.OVERDRIED) {
            DryingLevel[] levels = DryingLevel.values();
            if (currentLevel.getLevel() < levels.length - 1) {
                DryingLevel nextLevel = levels[currentLevel.getLevel() + 1];
                int nextThreshold = plugin.getConfigManager().getThresholdStart(nextLevel);
                int timeToNext = nextThreshold - oldestElapsed;
                if (timeToNext > 0) {
                    player.sendMessage(lang().get("station_dry_next_in",
                            Placeholder.unparsed("seconds", String.valueOf(timeToNext))));
                }
            }
        }
    }

    private void handleRolling(Player player, RollingStation station) {
        int rollTime = plugin.getConfigManager().getRollingTimeSeconds();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (station.isDone(rollTime)) {
            if (station.isCigarMode()) collectCigar(player, station);
            else collectCigarettes(player, station);
            return;
        }

        if (station.isRolling()) {
            int remaining = station.getSecondsRemaining(rollTime);
            player.sendMessage(lang().get("station_roll_in_progress",
                    Placeholder.unparsed("seconds", String.valueOf(remaining))));
            return;
        }

        if (station.isCigarMode() && station.getCigarLeavesCount() >= 3) {
            if (plugin.getItemManager().isFlavorItem(hand)) {
                CigaretteType flavorType = plugin.getItemManager().getFlavorType(hand);
                if (flavorType == null) return;

                station.addFlavor(flavorType);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }

                player.sendMessage(lang().get("station_roll_cigar_rolling",
                        Placeholder.unparsed("seconds", String.valueOf(rollTime))));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
                plugin.getDatabaseManager().saveRollingStation(station);
                return;
            }

            player.sendMessage(lang().get("station_roll_need_flavor"));
            return;
        }

        boolean isElitePerfectLeaf = plugin.getItemManager().isEliteLeaf(hand)
                && plugin.getItemManager().getDryingLevel(hand) == DryingLevel.PERFECT;

        if (isElitePerfectLeaf) {
            TobaccoType newLeafType = plugin.getItemManager().getTobaccoType(hand);

            if (station.isCigarMode() && station.getLeafType() != newLeafType) {
                player.sendMessage(lang().get("station_roll_cigar_wrong_type"));
                return;
            }

            if (!station.isCigarMode()) {
                station.loadLeaf(newLeafType, true, DryingLevel.PERFECT);
            }

            station.addCigarLeaf();

            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);

            if (station.getCigarLeavesCount() < 3) {
                player.sendMessage(lang().get("station_roll_cigar_leaf_added",
                        Placeholder.unparsed("count",
                                String.valueOf(station.getCigarLeavesCount()))));
            } else {
                player.sendMessage(lang().get("station_roll_cigar_need_flavor"));
            }

            plugin.getDatabaseManager().saveRollingStation(station);
            return;
        }

        if (!station.hasLeaf() && !station.isCigarMode()) {
            if (!plugin.getItemManager().isCutTobacco(hand)
                    && !plugin.getItemManager().isBlendedCutTobacco(hand)) {
                player.sendMessage(lang().get("station_roll_empty_info"));
                return;
            }

            TobaccoType tobaccoType = plugin.getItemManager().getTobaccoType(hand);
            boolean elite = plugin.getItemManager().isCutElite(hand);
            DryingLevel dryLevel = plugin.getItemManager().getDryingLevel(hand);
            int cutQuality = plugin.getItemManager().getCutQuality(hand);
            if (tobaccoType == null) tobaccoType = TobaccoType.VIRGINIA;

            BlendData blend = plugin.getItemManager().getBlendData(hand);

            station.loadBlendedLeaf(tobaccoType, elite, dryLevel, blend);
            station.setCutQuality(cutQuality);

            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }

            plugin.getStationManager().updateRollingStationDisplay(station);

            String dryName = plugin.getItemManager().formatDryingLevelName(dryLevel);
            TextColor dryColor = dryLevel != null ? dryLevel.getColor() : CigaroColors.ASH;

            player.sendMessage(lang().get("station_roll_tobacco_loaded",
                    TagResolver.resolver("level_color", Tag.styling(dryColor)),
                    Placeholder.unparsed("level", dryName)));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            plugin.getDatabaseManager().saveRollingStation(station);
            return;
        }

        if (station.hasLeaf() && !station.hasFlavor() && !station.isCigarMode()) {

            if (hand.getType() == Material.AIR) {
                TobaccoType type = station.getLeafType();
                boolean elite = station.isEliteLeaf();
                DryingLevel level = station.getDryingLevel();
                int cutQual = station.getCutQuality();
                int seconds = level != null
                        ? plugin.getConfigManager().getThresholdStart(level) : 0;

                ItemStack back = station.isBlend()
                        ? plugin.getItemManager().createBlendedCutTobacco(
                                station.getBlend(), elite, level, seconds, cutQual, 1)
                        : plugin.getItemManager().createCutTobacco(
                                type, elite, level, seconds, cutQual);
                player.getInventory().addItem(back);
                station.clear();
                plugin.getStationManager().updateRollingStationDisplay(station);

                player.sendMessage(lang().get("station_roll_tobacco_retrieved"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                plugin.getDatabaseManager().saveRollingStation(station);
                return;
            }

            if (!plugin.getItemManager().isFlavorItem(hand)) {
                player.sendMessage(lang().get("station_roll_need_flavor"));
                return;
            }

            CigaretteType flavorType = plugin.getItemManager().getFlavorType(hand);
            if (flavorType == null) return;

            station.addFlavor(flavorType);
            if (player.getGameMode() != GameMode.CREATIVE) {
                hand.setAmount(hand.getAmount() - 1);
            }

            player.sendMessage(lang().get("station_roll_flavoring_added",
                    Placeholder.unparsed("seconds", String.valueOf(rollTime))));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
            plugin.getDatabaseManager().saveRollingStation(station);
        }
    }

    private void collectCigar(Player player, RollingStation station) {
        CigaretteType type;
        if (station.hasFlavor() && station.getFlavorType() != null) {
            type = station.getFlavorType();
        } else {
            type = plugin.getItemManager().tobaccoToCigaretteType(station.getLeafType());
        }

        FlavorHarmony harmony = resolveHarmony(station, type);
        ItemStack cigar = station.isBlend()
                ? plugin.getItemManager().createBlendedCigar(
                        station.getBlend(), CigaretteQuality.VINTAGE, 1, harmony)
                : plugin.getItemManager().createCigar(type, CigaretteQuality.VINTAGE, 1, harmony);
        player.getInventory().addItem(cigar);
        notifyHarmony(player, harmony);

        station.clear();
        plugin.getStationManager().updateRollingStationDisplay(station);

        player.sendMessage(lang().get("station_roll_cigar_done"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);

        plugin.getPlayerDataManager().getData(player.getUniqueId()).addMastery(0.05);
        plugin.getDatabaseManager().saveRollingStation(station);
    }

    private void collectCigarettes(Player player, RollingStation station) {
        int amount = plugin.getConfigManager().getCigarettesPerBatch();

        CigaretteType type = station.hasFlavor() && station.getFlavorType() != null
                ? station.getFlavorType()
                : plugin.getItemManager().tobaccoToCigaretteType(station.getLeafType());

        FlavorHarmony harmony = resolveHarmony(station, type);
        CigaretteQuality quality = calculateQuality(player, station, harmony);
        int maxPuffs = quality.getPuffs();

        ItemStack cigarettes = station.isBlend()
                ? plugin.getItemManager().createBlendedCigarette(
                        station.getBlend(), quality, amount, maxPuffs, maxPuffs, harmony)
                : plugin.getItemManager().createCigarette(
                        type, quality, amount, maxPuffs, maxPuffs, harmony);
        player.getInventory().addItem(cigarettes);
        notifyHarmony(player, harmony);

        if (quality == CigaretteQuality.VINTAGE) {
            plugin.getAchievementManager().grant(player, CigaroAchievement.PERFECTIONIST);
        }

        station.clear();
        plugin.getStationManager().updateRollingStationDisplay(station);

        String qualityName = plugin.getItemManager().formatQualityName(quality);
        player.sendMessage(lang().get("station_roll_cigarettes_done",
                TagResolver.resolver("quality_color", Tag.styling(CigaroColors.forQuality(quality))),
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("quality", qualityName)));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);

        plugin.getPlayerDataManager().getData(player.getUniqueId()).addMastery(0.01);
        plugin.getDatabaseManager().saveRollingStation(station);
    }

    private CigaretteQuality calculateQuality(Player player, RollingStation station,
                                              FlavorHarmony harmony) {
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        double leafBonus;
        int flavorPredisposition;
        if (station.isBlend()) {
            BlendData blend = station.getBlend();
            leafBonus = plugin.getItemManager().getBlendLeafQualityBonus(blend);
            flavorPredisposition = blend.getBlendedPredisposition(data);
        } else {
            TobaccoType leafType = station.getLeafType();
            leafBonus = leafType != null ? leafType.getQualityBonus() : 0.0;
            CigaretteType flavorType = station.getFlavorType();
            flavorPredisposition = flavorType != null
                    ? data.getPredisposition(flavorType) : 0;
        }

        return CigaretteQualityCalculator.calculate(
                station.getDryingLevel(),
                station.getCutQuality(),
                station.isEliteLeaf(),
                leafBonus,
                data.getMastery(),
                flavorPredisposition,
                harmony);
    }

    private FlavorHarmony resolveHarmony(RollingStation station, CigaretteType flavorType) {
        if (station.isBlend() || !station.hasFlavor() || flavorType == null) {
            return FlavorHarmony.NEUTRAL;
        }
        return TobaccoFlavorCompatibility.evaluate(station.getLeafType(), flavorType);
    }

    private void notifyHarmony(Player player, FlavorHarmony harmony) {
        if (harmony == FlavorHarmony.MATCHED) {
            player.sendMessage(lang().get("station_roll_harmony_match"));
        } else if (harmony == FlavorHarmony.CLASHED) {
            player.sendMessage(lang().get("station_roll_harmony_clash"));
        }
    }
}


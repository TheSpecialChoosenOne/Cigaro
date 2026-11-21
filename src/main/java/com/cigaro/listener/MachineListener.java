package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.BlendData;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.DryingLevel;
import com.cigaro.data.PlayerData;
import com.cigaro.data.TobaccoType;
import com.cigaro.station.*;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
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

import java.util.HashMap;
import java.util.Map;

public class MachineListener implements Listener {

    private final Cigaro plugin;
    private final Map<Location, Long> interactLock = new HashMap<>();
    private static final long LOCK_MS = 300;

    public MachineListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    private boolean tryLock(Location loc) {
        long now = System.currentTimeMillis();
        Long last = interactLock.get(loc);
        if (last != null && (now - last) < LOCK_MS) return false;
        interactLock.put(loc, now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMachinePlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        Block block = event.getBlock();

        if (plugin.getItemManager().isAutoDryingRack(item)) {
            plugin.getStationManager().addAutoDryingRack(new AutoDryingRack(block.getLocation()));
            player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_placed"));
        } else if (plugin.getItemManager().isCuttingMachine(item)) {
            plugin.getStationManager().addCuttingMachine(new CuttingMachine(block.getLocation()));
            player.sendMessage(plugin.getLanguageManager().get("machine_cutting_placed"));
        } else if (plugin.getItemManager().isRollingPress(item)) {
            RollingPress press = new RollingPress(block.getLocation());
            updateRollingOperator(player, press);
            plugin.getStationManager().addRollingPress(press);
            player.sendMessage(plugin.getLanguageManager().get("machine_rolling_placed"));
        } else if (plugin.getItemManager().isPackingPress(item)) {
            plugin.getStationManager().addPackingPress(new PackingPress(block.getLocation()));
            player.sendMessage(plugin.getLanguageManager().get("machine_packing_placed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMachineInteractEntity(PlayerInteractEntityEvent event) {
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

        AutoDryingRack rack = plugin.getStationManager().getAutoDryingRack(loc);
        if (rack != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleAutoDryingRack(player, rack);
            return;
        }

        CuttingMachine cutter = plugin.getStationManager().getCuttingMachine(loc);
        if (cutter != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleCuttingMachine(player, cutter);
            return;
        }

        RollingPress press = plugin.getStationManager().getRollingPress(loc);
        if (press != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handleRollingPress(player, press);
            return;
        }

        PackingPress packer = plugin.getStationManager().getPackingPress(loc);
        if (packer != null) {
            event.setCancelled(true);
            if (!tryLock(loc)) return;
            handlePackingPress(player, packer);
        }
    }

    private void handleAutoDryingRack(Player player, AutoDryingRack rack) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isFuelPellets(hand)) {
            int space = 64 - (rack.getFuelItem() == null ? 0 : rack.getFuelItem().getAmount());
            if (space <= 0) {
                player.sendMessage(plugin.getLanguageManager().get("machine_fuel_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack fuelCopy = hand.clone();
            fuelCopy.setAmount(adding);
            rack.addFuel(adding * AutoDryingRack.FUEL_SECONDS_PER_PELLET, fuelCopy);
            hand.setAmount(hand.getAmount() - adding);
            player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.0f);
            player.sendMessage(plugin.getLanguageManager().get("machine_fuel_added",
                    Placeholder.unparsed("seconds", String.valueOf(rack.getFuelSeconds()))));
            plugin.getDatabaseManager().saveAutoDryingRack(rack);
            return;
        }

        if (plugin.getItemManager().isTobaccoLeaf(hand)) {
            if (!rack.hasSpace()) {
                player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_full"));
                return;
            }
            TobaccoType type = plugin.getItemManager().getTobaccoType(hand);
            boolean elite = plugin.getItemManager().isEliteLeaf(hand);
            if (type == null) type = TobaccoType.VIRGINIA;

            int alreadyDried = plugin.getItemManager().getDryingSeconds(hand);

            if (player.isSneaking()) {
                int toAdd = Math.min(hand.getAmount(), 4 - rack.getLeafEntries().size());
                for (int i = 0; i < toAdd; i++) {
                    ItemStack single = hand.clone();
                    single.setAmount(1);
                    rack.addLeaf(type, elite, single, alreadyDried);
                }
                hand.setAmount(hand.getAmount() - toAdd);
                player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_add_multiple",
                        Placeholder.unparsed("count", String.valueOf(toAdd)),
                        Placeholder.unparsed("total", String.valueOf(rack.getLeafEntries().size()))));
            } else {
                ItemStack single = hand.clone();
                single.setAmount(1);
                rack.addLeaf(type, elite, single, alreadyDried);
                hand.setAmount(hand.getAmount() - 1);
                player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_add_single",
                        Placeholder.unparsed("total", String.valueOf(rack.getLeafEntries().size()))));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            plugin.getDatabaseManager().saveAutoDryingRack(rack);
            return;
        }

        if (hand.getType() == Material.AIR) {
            player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_status_header"));

            TagResolver fuelColorTag = rack.hasFuel()
                    ? TagResolver.resolver("fuel_color", Tag.styling(CigaroColors.EMBER))
                    : TagResolver.resolver("fuel_color", Tag.styling(CigaroColors.ASH));

            player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_status_fuel",
                    Placeholder.unparsed("seconds", String.valueOf(rack.getFuelSeconds())),
                    fuelColorTag));

            player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_status_leaves",
                    Placeholder.unparsed("count", String.valueOf(rack.getLeafEntries().size()))));

            for (AutoDryingRack.AutoDryEntry entry : rack.getLeafEntries()) {
                int eff = entry.getEffectiveSeconds();
                DryingLevel level = plugin.getConfigManager().getDryingLevel(eff);

                TagResolver levelColorTag = TagResolver.resolver(
                        "level_color", Tag.styling(level.getColor())
                );

                player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_status_entry",
                        Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(entry.getTobaccoType())),
                        Placeholder.unparsed("level", plugin.getItemManager().formatDryingLevelName(level)),
                        levelColorTag,
                        Placeholder.unparsed("time", String.valueOf(eff))));
            }

            if (!rack.hasFuel() && !rack.getLeafEntries().isEmpty())
                player.sendMessage(plugin.getLanguageManager().get("machine_no_fuel"));
            return;
        }

        player.sendMessage(plugin.getLanguageManager().get("machine_auto_dry_use_hint"));
    }

    private void handleCuttingMachine(Player player, CuttingMachine cutter) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isFuelPellets(hand)) {
            int space = 64 - (cutter.getFuelItem() == null ? 0 : cutter.getFuelItem().getAmount());
            if (space <= 0) {
                player.sendMessage(plugin.getLanguageManager().get("machine_fuel_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack fuelCopy = hand.clone();
            fuelCopy.setAmount(adding);
            cutter.addFuel(adding * AutoDryingRack.FUEL_SECONDS_PER_PELLET, fuelCopy);
            hand.setAmount(hand.getAmount() - adding);
            player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.0f);
            player.sendMessage(plugin.getLanguageManager().get("machine_fuel_added",
                    Placeholder.unparsed("seconds", String.valueOf(cutter.getFuelSeconds()))));
            plugin.getDatabaseManager().saveCuttingMachine(cutter);
            return;
        }

        if (plugin.getItemManager().isDriedTobaccoLeaf(hand)) {
            if (cutter.hasItem()) {
                player.sendMessage(plugin.getLanguageManager().get("machine_cutting_already_processing"));
                return;
            }
            TobaccoType type = plugin.getItemManager().getTobaccoType(hand);
            boolean elite = plugin.getItemManager().isEliteLeaf(hand);
            DryingLevel level = plugin.getItemManager().getDryingLevel(hand);
            if (type == null) type = TobaccoType.VIRGINIA;

            ItemStack leafCopy = hand.clone();
            leafCopy.setAmount(1);
            cutter.setItem(type, elite, level, leafCopy);
            hand.setAmount(hand.getAmount() - 1);

            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            player.sendMessage(plugin.getLanguageManager().get("machine_cutting_leaf_loaded"));
            if (!cutter.hasFuel())
                player.sendMessage(plugin.getLanguageManager().get("machine_no_fuel"));
            plugin.getDatabaseManager().saveCuttingMachine(cutter);
            return;
        }

        if (hand.getType() == Material.AIR) {
            String status = cutter.hasItem()
                    ? cutter.progress + "%"
                    : plugin.getLanguageManager().getRaw("machine_status_empty");

            TagResolver fuelColorTag = cutter.hasFuel()
                    ? TagResolver.resolver("fuel_color", Tag.styling(CigaroColors.EMBER))
                    : TagResolver.resolver("fuel_color", Tag.styling(CigaroColors.ASH));

            player.sendMessage(plugin.getLanguageManager().get("machine_cutting_status_header",
                    Placeholder.unparsed("status", status)));
            player.sendMessage(plugin.getLanguageManager().get("machine_cutting_status_fuel",
                    Placeholder.unparsed("seconds", String.valueOf(cutter.getFuelSeconds())),
                    fuelColorTag));
            return;
        }

        player.sendMessage(plugin.getLanguageManager().get("machine_cutting_use_hint"));
    }

    private void handleRollingPress(Player player, RollingPress press) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isFuelPellets(hand)) {
            int space = 64 - (press.getFuelItem() == null ? 0 : press.getFuelItem().getAmount());
            if (space <= 0) {
                player.sendMessage(plugin.getLanguageManager().get("machine_fuel_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack fuelCopy = hand.clone();
            fuelCopy.setAmount(adding);
            press.addFuel(adding * AutoDryingRack.FUEL_SECONDS_PER_PELLET, fuelCopy);
            hand.setAmount(hand.getAmount() - adding);
            player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.7f, 1.0f);
            player.sendMessage(plugin.getLanguageManager().get("machine_fuel_added",
                    Placeholder.unparsed("seconds", String.valueOf(press.getFuelSeconds()))));
            checkRollingReady(press);
            plugin.getDatabaseManager().saveRollingPress(press);
            return;
        }

        if (plugin.getItemManager().isFlavorItem(hand)) {
            CigaretteType type = plugin.getItemManager().getFlavorType(hand);
            if (type == null) return;

            if (press.tobaccoType != null && press.tobaccoType != type) {
                player.sendMessage(plugin.getLanguageManager().get("machine_rolling_different_flavor"));
                return;
            }

            press.tobaccoType = type;
            updateRollingOperator(player, press);
            hand.setAmount(hand.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.2f);
            player.sendMessage(plugin.getLanguageManager().get("machine_rolling_flavor_set",
                    Placeholder.unparsed("type", plugin.getItemManager().formatTypeName(type))));
            checkRollingReady(press);
            plugin.getDatabaseManager().saveRollingPress(press);
            return;
        }

        if (plugin.getItemManager().isCutTobacco(hand) || plugin.getItemManager().isBlendedCutTobacco(hand)) {
            if (press.tobaccoAmount >= 64) {
                player.sendMessage(plugin.getLanguageManager().get("machine_rolling_tobacco_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), 64 - press.tobaccoAmount);
            ItemStack tobaccoClone = hand.clone();
            tobaccoClone.setAmount(adding);

            if (press.getTobaccoItem() != null && press.tobaccoAmount > 0
                    && press.getTobaccoItem().isSimilar(tobaccoClone)) {
                hand.setAmount(hand.getAmount() - adding);
                press.tobaccoAmount += adding;
                ItemStack merged = press.getTobaccoItem().clone();
                merged.setAmount(Math.min(64, press.tobaccoAmount));
                press.setTobaccoItem(merged);
            } else if (press.getTobaccoItem() == null || press.tobaccoAmount == 0) {
                hand.setAmount(hand.getAmount() - adding);
                press.tobaccoAmount = adding;
                press.setTobaccoItem(tobaccoClone);
            } else {
                int replaceAdding = Math.min(hand.getAmount(), 64);
                ItemStack replacement = hand.clone();
                replacement.setAmount(replaceAdding);
                hand.setAmount(hand.getAmount() - replaceAdding);

                ItemStack oldTobacco = press.getTobaccoItem().clone();
                oldTobacco.setAmount(Math.min(64, press.tobaccoAmount));
                var overflow = player.getInventory().addItem(oldTobacco);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                press.tobaccoAmount = replaceAdding;
                press.setTobaccoItem(replacement);
            }

            updateRollingOperator(player, press);
            player.sendMessage(plugin.getLanguageManager().get("machine_rolling_tobacco_loaded",
                    Placeholder.unparsed("amount", String.valueOf(press.tobaccoAmount))));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            checkRollingReady(press);
            plugin.getDatabaseManager().saveRollingPress(press);
            return;
        }

        if (plugin.getItemManager().isCigarettePaper(hand)) {
            if (press.paperAmount >= 64) {
                player.sendMessage(plugin.getLanguageManager().get("machine_rolling_paper_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), 64 - press.paperAmount);
            ItemStack paperClone = hand.clone();
            paperClone.setAmount(adding);
            hand.setAmount(hand.getAmount() - adding);

            if (press.getPaperItem() != null && press.paperAmount > 0) {
                press.paperAmount += adding;
                ItemStack merged = press.getPaperItem().clone();
                merged.setAmount(Math.min(64, press.paperAmount));
                press.setPaperItem(merged);
            } else {
                press.paperAmount = adding;
                press.setPaperItem(paperClone);
            }

            player.sendMessage(plugin.getLanguageManager().get("machine_rolling_paper_loaded",
                    Placeholder.unparsed("amount", String.valueOf(press.paperAmount))));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            checkRollingReady(press);
            plugin.getDatabaseManager().saveRollingPress(press);
            return;
        }

        if (hand.getType() == Material.AIR) {
            showRollingStatus(player, press);
            return;
        }

        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_use_hint"));
    }

    private void updateRollingOperator(Player player, RollingPress press) {
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        press.setOperatorMastery(data.getMastery());

        BlendData blend = plugin.getItemManager().getBlendData(press.getTobaccoItem());
        if (blend != null) {
            press.setOperatorFlavorPredisposition(
                    blend.getBlendedPredisposition(data));
        } else if (press.tobaccoType != null) {
            press.setOperatorFlavorPredisposition(
                    data.getPredisposition(press.tobaccoType));
        } else {
            press.setOperatorFlavorPredisposition(0);
        }
    }

    private void showRollingStatus(Player player, RollingPress press) {
        String typeName = press.tobaccoType != null
                ? plugin.getItemManager().formatTypeName(press.tobaccoType)
                : plugin.getLanguageManager().getRaw("machine_none");

        String status;
        if (press.hasItems() && press.hasFuel()) {
            status = press.progress + "%";
        } else if (press.hasItems() && !press.hasFuel()) {
            status = plugin.getLanguageManager().getRaw("machine_no_fuel_short");
        } else {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (press.tobaccoType == null)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_flavor"));
            if (press.tobaccoAmount < 8)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_tobacco")
                        + " (" + press.tobaccoAmount + "/8)");
            if (press.paperAmount < 1)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_paper"));
            status = missing.isEmpty()
                    ? plugin.getLanguageManager().getRaw("machine_status_ready")
                    : plugin.getLanguageManager().getRaw("machine_status_need")
                      + " " + String.join(", ", missing);
        }

        TagResolver flavorColorTag = press.tobaccoType != null
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));
        TagResolver tobaccoColorTag = press.tobaccoAmount >= 8
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));
        TagResolver paperColorTag = press.paperAmount >= 1
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));
        TagResolver fuelColorTag = press.hasFuel()
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.EMBER))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));

        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_header"));
        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_status",
                Placeholder.unparsed("status", status)));
        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_flavor_line",
                Placeholder.unparsed("type", typeName),
                flavorColorTag));
        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_tobacco_line",
                Placeholder.unparsed("amount", String.valueOf(press.tobaccoAmount)),
                tobaccoColorTag));
        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_paper_line",
                Placeholder.unparsed("amount", String.valueOf(press.paperAmount)),
                paperColorTag));
        player.sendMessage(plugin.getLanguageManager().get("machine_rolling_fuel_line",
                Placeholder.unparsed("seconds", String.valueOf(press.getFuelSeconds())),
                fuelColorTag));
    }

    private void checkRollingReady(RollingPress press) {
        boolean ready = press.tobaccoAmount >= 8
                && press.paperAmount >= 1
                && press.tobaccoType != null;
        if (ready && !press.hasItems) {
            press.hasItems = true;
            press.progress = 0;
        } else if (!ready) {
            press.hasItems = false;
        }
    }

    private void handlePackingPress(Player player, PackingPress packer) {
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (plugin.getItemManager().isCigarette(hand)) {
            if (!plugin.getItemManager().isFreshCigarette(hand)) {
                player.sendMessage(plugin.getLanguageManager().get("machine_packing_only_fresh"));
                return;
            }
            CigaretteType type = plugin.getItemManager().getCigaretteType(hand);
            if (packer.cigaretteType != null && packer.cigaretteCount > 0 && packer.cigaretteType != type) {
                player.sendMessage(plugin.getLanguageManager().get("machine_packing_different_type"));
                return;
            }

            int space = PackingPress.CIGARETTES_PER_PACK - packer.cigaretteCount;
            if (space <= 0) {
                player.sendMessage(plugin.getLanguageManager().get("machine_packing_cigarettes_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack cigClone = hand.clone();
            cigClone.setAmount(adding);
            hand.setAmount(hand.getAmount() - adding);
            packer.cigaretteType = type;

            if (packer.getCigaretteItem() != null && packer.cigaretteCount > 0
                    && packer.getCigaretteItem().isSimilar(cigClone)) {
                packer.cigaretteCount += adding;
                ItemStack merged = packer.getCigaretteItem().clone();
                merged.setAmount(packer.cigaretteCount);
                packer.setCigaretteItem(merged);
            } else if (packer.getCigaretteItem() == null || packer.cigaretteCount == 0) {
                packer.cigaretteCount = adding;
                cigClone.setAmount(adding);
                packer.setCigaretteItem(cigClone);
            } else {
                var overflow = player.getInventory().addItem(packer.getCigaretteItem());
                for (ItemStack leftover : overflow.values())
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                packer.cigaretteCount = adding;
                cigClone.setAmount(adding);
                packer.setCigaretteItem(cigClone);
            }

            player.sendMessage(plugin.getLanguageManager().get("machine_packing_cigarettes_loaded",
                    Placeholder.unparsed("count", String.valueOf(packer.cigaretteCount)),
                    Placeholder.unparsed("max", String.valueOf(PackingPress.CIGARETTES_PER_PACK))));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            checkPackingReady(packer);
            plugin.getDatabaseManager().savePackingPress(packer);
            return;
        }

        if (plugin.getItemManager().isCardboard(hand)) {
            int space = 1 - packer.cardboardCount;
            if (space <= 0) {
                player.sendMessage(plugin.getLanguageManager().get("machine_packing_cardboard_full"));
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack cardClone = hand.clone();
            cardClone.setAmount(adding);
            hand.setAmount(hand.getAmount() - adding);

            if (packer.getCardboardItem() != null && packer.cardboardCount > 0) {
                packer.cardboardCount += adding;
                ItemStack merged = packer.getCardboardItem().clone();
                merged.setAmount(packer.cardboardCount);
                packer.setCardboardItem(merged);
            } else {
                packer.cardboardCount = adding;
                cardClone.setAmount(adding);
                packer.setCardboardItem(cardClone);
            }

            player.sendMessage(plugin.getLanguageManager().get("machine_packing_cardboard_loaded",
                    Placeholder.unparsed("count", String.valueOf(packer.cardboardCount))));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            checkPackingReady(packer);
            plugin.getDatabaseManager().savePackingPress(packer);
            return;
        }

        if (isDye(hand)) {
            DyeColor color = getDyeColor(hand);
            if (color == null) {
                player.sendMessage(plugin.getLanguageManager().get("machine_packing_unknown_dye"));
                return;
            }

            if (packer.getDyeColor() != null && packer.getDyeColor() != color) {
                if (packer.getDyeItem() != null) {
                    var overflow = player.getInventory().addItem(packer.getDyeItem());
                    for (ItemStack leftover : overflow.values())
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                packer.setDyeItem(null);
            }

            int currentAmount = (packer.getDyeItem() != null && packer.getDyeColor() == color)
                    ? packer.getDyeItem().getAmount() : 0;
            int space = 64 - currentAmount;
            if (space <= 0) {
                return;
            }

            int adding = Math.min(hand.getAmount(), space);
            ItemStack dyeClone = hand.clone();
            dyeClone.setAmount(currentAmount + adding);
            packer.setDyeItem(dyeClone);
            packer.setDyeColor(color);
            hand.setAmount(hand.getAmount() - adding);

            String colorName = plugin.getItemManager().formatDyeColor(color);
            player.sendMessage(plugin.getLanguageManager().get("machine_packing_dye_set",
                    Placeholder.unparsed("color", colorName)));
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1f, 1f);
            checkPackingReady(packer);
            plugin.getDatabaseManager().savePackingPress(packer);
            return;
        }

        if (hand.getType() == Material.AIR) {
            showPackingStatus(player, packer);
            return;
        }

        player.sendMessage(plugin.getLanguageManager().get("machine_packing_use_hint"));
    }

    private void showPackingStatus(Player player, PackingPress packer) {
        String status;
        if (packer.hasItems()) {
            status = packer.progress + "%";
        } else {
            java.util.List<String> missing = new java.util.ArrayList<>();
            if (packer.cigaretteCount < PackingPress.CIGARETTES_PER_PACK)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_cigarettes")
                        + " (" + packer.cigaretteCount + "/" + PackingPress.CIGARETTES_PER_PACK + ")");
            if (packer.cardboardCount < 1)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_cardboard"));
            if (packer.getDyeColor() == null)
                missing.add(plugin.getLanguageManager().getRaw("machine_word_dye"));
            status = missing.isEmpty()
                    ? plugin.getLanguageManager().getRaw("machine_status_ready")
                    : plugin.getLanguageManager().getRaw("machine_status_need")
                      + " " + String.join(", ", missing);
        }

        String colorName = packer.getDyeColor() != null
                ? plugin.getItemManager().formatDyeColor(packer.getDyeColor())
                : plugin.getLanguageManager().getRaw("machine_none");

        TagResolver cigsColorTag = packer.cigaretteCount >= PackingPress.CIGARETTES_PER_PACK
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));
        TagResolver cardColorTag = packer.cardboardCount >= 1
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));
        TagResolver dyeColorTag = packer.getDyeColor() != null
                ? TagResolver.resolver("color", Tag.styling(CigaroColors.SUCCESS))
                : TagResolver.resolver("color", Tag.styling(CigaroColors.ASH));

        player.sendMessage(plugin.getLanguageManager().get("machine_packing_header"));
        player.sendMessage(plugin.getLanguageManager().get("machine_packing_status",
                Placeholder.unparsed("status", status)));
        player.sendMessage(plugin.getLanguageManager().get("machine_packing_cigs_line",
                Placeholder.unparsed("count", String.valueOf(packer.cigaretteCount)),
                Placeholder.unparsed("max", String.valueOf(PackingPress.CIGARETTES_PER_PACK)),
                cigsColorTag));
        player.sendMessage(plugin.getLanguageManager().get("machine_packing_card_line",
                Placeholder.unparsed("count", String.valueOf(packer.cardboardCount)),
                cardColorTag));
        player.sendMessage(plugin.getLanguageManager().get("machine_packing_dye_line",
                Placeholder.unparsed("color_name", colorName),
                dyeColorTag));
    }

    private void checkPackingReady(PackingPress packer) {
        boolean ready = packer.isReady();
        if (ready && !packer.hasItems) {
            packer.hasItems = true;
            packer.progress = 0;
        } else if (!ready) {
            packer.hasItems = false;
        }
    }

    private boolean isDye(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_DYE");
    }

    private DyeColor getDyeColor(ItemStack item) {
        if (item == null) return null;
        String name = item.getType().name();
        if (!name.endsWith("_DYE")) return null;
        try { return DyeColor.valueOf(name.replace("_DYE", "")); }
        catch (IllegalArgumentException e) { return null; }
    }
}


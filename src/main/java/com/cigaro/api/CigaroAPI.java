package com.cigaro.api;

import com.cigaro.Cigaro;
import com.cigaro.data.*;
import com.cigaro.station.*;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CigaroAPI {


    public static PlayerData getPlayerData(UUID uuid) {
        return Cigaro.getInstance().getPlayerDataManager().getData(uuid);
    }

    public static double getPhysicalScore(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data != null ? data.getPhysicalScore() : 0.0;
    }

    public static double getPsychologicalScore(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data != null ? data.getPsychologicalScore() : 0.0;
    }

    public static void setPhysicalScore(UUID uuid, double score) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.setPhysicalScore(score);
            Cigaro.getInstance().getPlayerDataManager().save(uuid);
        }
    }

    public static void setPsychologicalScore(UUID uuid, double score) {
        PlayerData data = getPlayerData(uuid);
        if (data != null) {
            data.setPsychologicalScore(score);
            Cigaro.getInstance().getPlayerDataManager().save(uuid);
        }
    }


    public static ItemStack extractLeafFromDryingStation(Location loc) {
        DryingStation station = Cigaro.getInstance().getStationManager().getDryingStation(loc);
        if (station == null || station.isEmpty()) return null;

        DryingStation.LeafEntry entry = station.removeLastLeaf();
        if (entry == null) return null;

        int elapsed = entry.getElapsedSeconds();
        DryingLevel level = Cigaro.getInstance().getConfigManager().getDryingLevel(elapsed);
        int snapped = Cigaro.getInstance().getConfigManager().getThresholdStart(level);

        ItemStack leaf = Cigaro.getInstance().getItemManager().createTobaccoLeaf(
                entry.getTobaccoType(), entry.isElite(), snapped
        );

        Cigaro.getInstance().getStationManager().updateDryingStationLeavesDisplay(station);
        Cigaro.getInstance().getDatabaseManager().saveDryingStation(station);
        return leaf;
    }

    public static ItemStack extractItemFromAutoDryingRack(Location loc, int slot) {
        AutoDryingRack rack = Cigaro.getInstance().getStationManager().getAutoDryingRack(loc);
        if (rack == null) return null;

        ItemStack item = rack.takeLeaf(slot);
        if (item != null) {
            Cigaro.getInstance().getDatabaseManager().saveAutoDryingRack(rack);
        }
        return item;
    }

    public static ItemStack extractLeafFromCuttingMachine(Location loc) {
        CuttingMachine cutter = Cigaro.getInstance().getStationManager().getCuttingMachine(loc);
        if (cutter == null || !cutter.hasItem()) return null;

        ItemStack item = cutter.takeLeaf();
        Cigaro.getInstance().getDatabaseManager().saveCuttingMachine(cutter);
        return item;
    }

    public static ItemStack extractFuelFromCuttingMachine(Location loc) {
        CuttingMachine cutter = Cigaro.getInstance().getStationManager().getCuttingMachine(loc);
        if (cutter == null || cutter.getFuelItem() == null) return null;

        cutter.syncFuelItem();
        ItemStack fuel = cutter.getFuelItem();
        if (fuel == null) return null;
        cutter.setFuelItem(null);
        cutter.setFuelSeconds(0);
        Cigaro.getInstance().getDatabaseManager().saveCuttingMachine(cutter);
        return fuel;
    }

    public static ItemStack extractTobaccoFromRollingPress(Location loc) {
        RollingPress press = Cigaro.getInstance().getStationManager().getRollingPress(loc);
        if (press == null || press.getTobaccoItem() == null) return null;

        ItemStack tobacco = press.getTobaccoItem();
        press.setTobaccoItem(null);
        press.tobaccoAmount = 0;
        press.tobaccoType = null;
        press.hasItems = false;
        Cigaro.getInstance().getDatabaseManager().saveRollingPress(press);
        return tobacco;
    }

    public static ItemStack extractPaperFromRollingPress(Location loc) {
        RollingPress press = Cigaro.getInstance().getStationManager().getRollingPress(loc);
        if (press == null || press.getPaperItem() == null) return null;

        ItemStack paper = press.getPaperItem();
        press.setPaperItem(null);
        press.paperAmount = 0;
        press.hasItems = false;
        Cigaro.getInstance().getDatabaseManager().saveRollingPress(press);
        return paper;
    }

    public static ItemStack extractFuelFromRollingPress(Location loc) {
        RollingPress press = Cigaro.getInstance().getStationManager().getRollingPress(loc);
        if (press == null || press.getFuelItem() == null) return null;

        press.syncFuelItem();
        ItemStack fuel = press.getFuelItem();
        if (fuel == null) return null;
        press.setFuelItem(null);
        press.setFuelSeconds(0);
        Cigaro.getInstance().getDatabaseManager().saveRollingPress(press);
        return fuel;
    }

    public static ItemStack extractCigarettesFromPackingPress(Location loc) {
        PackingPress press = Cigaro.getInstance().getStationManager().getPackingPress(loc);
        if (press == null || press.getCigaretteItem() == null) return null;

        ItemStack item = press.getCigaretteItem();
        press.setCigaretteItem(null);
        press.cigaretteCount = 0;
        press.cigaretteType = null;
        press.hasItems = false;
        Cigaro.getInstance().getDatabaseManager().savePackingPress(press);
        return item;
    }

    public static ItemStack extractCardboardFromPackingPress(Location loc) {
        PackingPress press = Cigaro.getInstance().getStationManager().getPackingPress(loc);
        if (press == null || press.getCardboardItem() == null) return null;

        ItemStack item = press.getCardboardItem();
        press.setCardboardItem(null);
        press.cardboardCount = 0;
        press.hasItems = false;
        Cigaro.getInstance().getDatabaseManager().savePackingPress(press);
        return item;
    }

    public static ItemStack extractDyeFromPackingPress(Location loc) {
        PackingPress press = Cigaro.getInstance().getStationManager().getPackingPress(loc);
        if (press == null || press.getDyeItem() == null) return null;

        ItemStack item = press.getDyeItem();
        press.setDyeItem(null);
        press.dyeColor = null;
        press.hasItems = false;
        Cigaro.getInstance().getDatabaseManager().savePackingPress(press);
        return item;
    }


    public static ItemStack extractLeafFromRollingStation(Location loc) {
        RollingStation station = Cigaro.getInstance().getStationManager().getRollingStation(loc);
        if (station == null || !station.hasLeaf()) return null;

        ItemStack item;
        if (station.isCigarMode()) {
            item = Cigaro.getInstance().getItemManager().createTobaccoLeaf(station.getLeafType(), station.isEliteLeaf(), 300);
        } else {
            item = Cigaro.getInstance().getItemManager().createCutTobacco(
                    station.getLeafType(), station.isEliteLeaf(), station.getDryingLevel(),
                    station.getDryingLevel() != null ? Cigaro.getInstance().getConfigManager().getThresholdStart(station.getDryingLevel()) : 0,
                    station.getCutQuality()
            );
        }

        station.clear();
        Cigaro.getInstance().getStationManager().updateRollingStationDisplay(station);
        Cigaro.getInstance().getDatabaseManager().saveRollingStation(station);
        return item;
    }

    public static List<ItemStack> extractCigarettesFromPackingStation(Location loc) {
        com.cigaro.station.PackingStation station = Cigaro.getInstance().getStationManager().getPackingStation(loc);
        if (station == null || station.getLoadedCigarettes().isEmpty()) return null;

        List<ItemStack> copied = new ArrayList<>(station.getLoadedCigarettes());
        station.clear();
        Cigaro.getInstance().getDatabaseManager().savePackingStation(station);
        return copied;
    }
}
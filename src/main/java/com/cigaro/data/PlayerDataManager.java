package com.cigaro.data;

import com.cigaro.Cigaro;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private static final long FLUSH_PERIOD_TICKS = 600L;

    private final Cigaro plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private int flushTaskId = -1;

    public PlayerDataManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void preload(UUID uuid) {
        if (cache.containsKey(uuid)) return;
        PlayerData data = plugin.getDatabaseManager().loadPlayerData(uuid);
        cache.putIfAbsent(uuid, data);
    }

    public void discardPreloaded(UUID uuid) {
        if (Bukkit.getPlayer(uuid) == null) cache.remove(uuid);
    }

    public PlayerData getData(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        PlayerData loaded = plugin.getDatabaseManager().loadPlayerData(uuid);
        PlayerData existing = cache.putIfAbsent(uuid, loaded);
        return existing != null ? existing : loaded;
    }

    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) data.markDirty();
    }

    public void saveNow(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        data.clearDirty();
        plugin.getDatabaseManager().savePlayerData(data);
    }

    public void startFlushTask() {
        if (flushTaskId != -1) return;
        flushTaskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::flushDirty, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS)
                .getTaskId();
    }

    public void stopFlushTask() {
        if (flushTaskId == -1) return;
        plugin.getServer().getScheduler().cancelTask(flushTaskId);
        flushTaskId = -1;
    }

    private void flushDirty() {
        for (PlayerData data : cache.values()) {
            if (!data.isDirty()) continue;
            data.clearDirty();
            plugin.getDatabaseManager().savePlayerData(data);
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            data.clearDirty();
            plugin.getDatabaseManager().savePlayerDataSync(data);
        }
    }

    public void unload(UUID uuid) {
        saveNow(uuid);
        cache.remove(uuid);
    }

    public Collection<PlayerData> getAllLoaded() {
        return cache.values();
    }

    public void generatePredisposition(PlayerData data) {
        if (data.isPredispositionGenerated()) return;

        Random random = new Random();

        List<CigaretteType> types = new ArrayList<>(CigaretteType.values());
        Collections.shuffle(types, random);

        int i = 0;
        for (CigaretteType type : types) {
            int pred = 0;
            if (i == 0) pred = 2;
            else if (i == 1 || i == 2) pred = 1;
            else if (i == 3) pred = -2;
            else if (i == 4 || i == 5) pred = -1;

            if (pred == 1 && random.nextDouble() < 0.20) pred = 2;
            if (pred == -1 && random.nextDouble() < 0.10) pred = -2;

            data.setPredisposition(type, pred);
            i++;
        }
        data.setPredispositionGenerated(true);
        data.markDirty();
    }
}

package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;

public final class HabitManager implements Listener {
    private static final long RECENT_MS = 120_000L;
    private static final long COOLDOWN_MS = 20 * 60_000L;
    private static final long AFTER_SMOKE_MS = 10 * 60_000L;
    private final Cigaro plugin;
    private final Map<UUID, Map<String, Long>> recent = new HashMap<>();
    private final Map<UUID, Long> lastReminder = new HashMap<>();
    private final Random random = new Random();

    public HabitManager(Cigaro plugin) { this.plugin = plugin; }
    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L); }

    private void mark(Player player, String key) {
        recent.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(key, System.currentTimeMillis());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFight(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player p) mark(p, "combat");
        if (event.getDamager() instanceof Player p) mark(p, "combat");
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) mark(event.getPlayer(), "meal");
    }
    @EventHandler public void onWake(PlayerBedLeaveEvent event) { mark(event.getPlayer(), "wake"); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        recent.remove(event.getPlayer().getUniqueId());
    }
    private boolean recent(Player player, String key, long minAge, long maxAge) {
        Long time = recent.getOrDefault(player.getUniqueId(), Map.of()).get(key);
        long age = time == null ? Long.MAX_VALUE : System.currentTimeMillis() - time;
        return age >= minAge && age <= maxAge;
    }
    private String place(Player p) {
        Location l = p.getLocation();
        return "place:" + l.getWorld().getUID() + ":" + (l.getBlockX() >> 4) + ":" + (l.getBlockZ() >> 4);
    }
    private boolean nearFire(Player player) {
        Location l = player.getLocation();
        for (int x = -3; x <= 3; x++) for (int y = -2; y <= 2; y++) for (int z = -3; z <= 3; z++) {
            Block b = l.getBlock().getRelative(x, y, z);
            Material t = b.getType();
            if (t == Material.FIRE || t == Material.SOUL_FIRE) return true;
            if ((t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE || t == Material.FURNACE
                    || t == Material.BLAST_FURNACE || t == Material.SMOKER)
                    && b.getBlockData() instanceof Lightable lit && lit.isLit()) return true;
        }
        return false;
    }
    private boolean group(Player p) {
        return p.getNearbyEntities(8, 8, 8).stream().filter(e -> e instanceof Player).limit(2).count() >= 2;
    }
    public void recordSmoke(Player player, PlayerData data) {
        Set<String> contexts = new HashSet<>();
        if (player.getWorld().hasStorm()) contexts.add("rain");
        if (group(player)) contexts.add("group");
        if (nearFire(player)) contexts.add("fire");
        if (recent(player, "combat", 0, RECENT_MS)) contexts.add("combat");
        if (recent(player, "meal", 0, RECENT_MS)) contexts.add("meal");
        if (recent(player, "wake", 0, RECENT_MS)) contexts.add("wake");
        if (recent(player, "smoker", 0, RECENT_MS)) contexts.add("smoker");
        long daytime = player.getWorld().getTime();
        if (daytime >= 13000 && daytime < 23000) contexts.add("night");
        contexts.add(place(player));
        data.recordHabitSample(contexts);
        for (var entity : player.getNearbyEntities(8, 8, 8)) if (entity instanceof Player nearby) mark(nearby, "smoker");
    }
    private boolean learned(PlayerData data, String key) {
        int count = data.getHabitCount(key);
        if (key.equals("rain")) count = Math.max(count, data.getRainSmokes());
        if (key.equals("group")) count = Math.max(count, data.getGroupSmokes());
        int samples = Math.max(data.getHabitSamples(), data.getTotalSmoked());
        return count >= 4 && count * 10L >= Math.max(1, samples) * 3L;
    }
    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
            if ((data.getAddictionStage() == 0 && !data.wasAddicted()) || data.isPatchActive()) continue;
            if (now - data.getLastSmokeTime() < AFTER_SMOKE_MS || now - lastReminder.getOrDefault(p.getUniqueId(), 0L) < COOLDOWN_MS) continue;
            List<String> matches = new ArrayList<>();
            if (p.getWorld().hasStorm() && learned(data, "rain")) matches.add("rain");
            if (group(p) && learned(data, "group")) matches.add("group");
            if (learned(data, "fire") && nearFire(p)) matches.add("fire");
            if (recent(p, "combat", 10_000L, RECENT_MS) && learned(data, "combat")) matches.add("combat");
            if (recent(p, "meal", 0, RECENT_MS) && learned(data, "meal")) matches.add("meal");
            if (recent(p, "wake", 0, RECENT_MS) && learned(data, "wake")) matches.add("wake");
            if (recent(p, "smoker", 0, RECENT_MS) && learned(data, "smoker")) matches.add("smoker");
            if (learned(data, place(p))) matches.add("place");
            long day = p.getWorld().getTime();
            if (day >= 13000 && day < 23000 && learned(data, "night")) matches.add("night");
            if (matches.isEmpty() || random.nextDouble() >= 0.20) continue;
            String context = matches.get(random.nextInt(matches.size()));
            String tone = data.getAddictionStage() == 0 ? "recovered" : data.getAddictionStage() >= 3 ? "strong" : "mild";
            p.sendMessage(plugin.getLanguageManager().get("habit_" + context + "_" + tone));
            lastReminder.put(p.getUniqueId(), now);
        }
    }
}

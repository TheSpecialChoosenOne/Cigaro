package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.CigaretteType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsManager {

    private final Cigaro plugin;
    private final File file;
    private YamlConfiguration config;

    private int totalCigarettes = 0;
    private int totalCigars = 0;
    private int totalHookahPuffs = 0;
    private int lungDamageCases = 0;
    private int patchesUsed = 0;
    private final Map<String, Integer> flavorPopularity = new HashMap<>();

    public AnalyticsManager(Cigaro plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "analytics_report.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);

        totalCigarettes = config.getInt("stats.total_cigarettes", 0);
        totalCigars = config.getInt("stats.total_cigars", 0);
        totalHookahPuffs = config.getInt("stats.total_hookah_puffs", 0);
        lungDamageCases = config.getInt("stats.lung_damage_cases", 0);
        patchesUsed = config.getInt("stats.patches_used", 0);

        if (config.getConfigurationSection("flavors") != null) {
            for (String key : config.getConfigurationSection("flavors").getKeys(false)) {
                flavorPopularity.put(key, config.getInt("flavors." + key));
            }
        }
    }

    public void save() {
        config.set("stats.total_cigarettes", totalCigarettes);
        config.set("stats.total_cigars", totalCigars);
        config.set("stats.total_hookah_puffs", totalHookahPuffs);
        config.set("stats.lung_damage_cases", lungDamageCases);
        config.set("stats.patches_used", patchesUsed);

        for (Map.Entry<String, Integer> entry : flavorPopularity.entrySet()) {
            config.set("flavors." + entry.getKey(), entry.getValue());
        }

        try { config.save(file); } catch (IOException ignored) {}
    }


    public void recordSmoke(String type, CigaretteType flavor) {
        if (type.equals("cigarette")) totalCigarettes++;
        if (type.equals("cigar")) totalCigars++;
        if (type.equals("hookah")) totalHookahPuffs++;

        if (flavor != null) {
            flavorPopularity.put(flavor.name(), flavorPopularity.getOrDefault(flavor.name(), 0) + 1);
        }
    }

    public void recordLungDamage() { lungDamageCases++; }
    public void recordPatchUsed() { patchesUsed++; }

    public void printReportToConsole() {
        plugin.getLogger().info("=== CIGARO ANALYTICS REPORT ===");
        plugin.getLogger().info("Total Cigarettes Smoked: " + totalCigarettes);
        plugin.getLogger().info("Total Cigars Smoked: " + totalCigars);
        plugin.getLogger().info("Total Hookah Puffs: " + totalHookahPuffs);
        plugin.getLogger().info("Lung Damage Occurrences: " + lungDamageCases);
        plugin.getLogger().info("Nicotine Patches Used: " + patchesUsed);
        plugin.getLogger().info("- Flavor Popularity -");
        flavorPopularity.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> plugin.getLogger().info("  " + e.getKey() + ": " + e.getValue()));
        plugin.getLogger().info("===============================");
        save();
    }
}

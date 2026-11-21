package com.cigaro.command;

import com.cigaro.Cigaro;
import com.cigaro.CigaroColors;
import com.cigaro.data.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CigaroCommand implements CommandExecutor {

    private final Cigaro plugin;

    private record ResolvedLeaderEntry(String name, int totalSmoked, int addictionStage, int rank) {}

    private volatile List<ResolvedLeaderEntry> cachedTop = List.of();
    private volatile long cachedTopAt = 0L;
    private final AtomicBoolean topRefreshing = new AtomicBoolean(false);
    private static final long TOP_CACHE_TTL_MS = 60_000L;

    public CigaroCommand(Cigaro plugin) {
        this.plugin = plugin;
    }

    private Component msg(String key, TagResolver... p) {
        return plugin.getLanguageManager().get(key, p);
    }

    private List<Component> msgList(String key, TagResolver... p) {
        return plugin.getLanguageManager().getLore(key, p);
    }

    private String raw(String key) {
        return plugin.getLanguageManager().getRaw(key);
    }

    private String getStageName(int stage) {
        return raw("cmd_stage_" + Math.min(stage, 4));
    }

    private TextColor getStageColor(int stage) {
        return switch (stage) {
            case 0 -> CigaroColors.SUCCESS;
            case 1 -> CigaroColors.WARNING;
            case 2 -> CigaroColors.CARAMEL;
            case 3 -> CigaroColors.EMBER;
            case 4 -> CigaroColors.ERROR;
            default -> CigaroColors.ASH;
        };
    }

    private String getPredLabel(int value) {
        return switch (value) {
            case 2 -> raw("cmd_pred_favorite");
            case 1 -> raw("cmd_pred_golden");
            case 0 -> raw("cmd_pred_neutral");
            case -1 -> raw("cmd_pred_bad");
            case -2 -> raw("cmd_pred_catastrophic");
            default -> "?";
        };
    }

    private TextColor getPredColor(int value) {
        return switch (value) {
            case 2, 1 -> CigaroColors.SUCCESS;
            case 0 -> CigaroColors.WARNING;
            case -1 -> CigaroColors.EMBER;
            case -2 -> CigaroColors.ERROR;
            default -> CigaroColors.ASH;
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) { showHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "stats" -> handleStats(sender);
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "top" -> handleTop(sender);
            case "achievements" -> handleAchievements(sender);
            case "analytics" -> handleAnalytics(sender);
            default -> sender.sendMessage(msg("cmd_unknown"));
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg("cmd_help_header",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion())));
        sender.sendMessage(msg("cmd_help_subtitle"));
        sender.sendMessage(Component.empty());
        for (Component line : msgList("cmd_help_lines")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(Component.empty());
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("cmd_players_only")); return; }
        if (!player.hasPermission("cigaro.use")) { player.sendMessage(msg("cmd_no_permission")); return; }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        int stage = data.getAddictionStage();

        player.sendMessage(Component.empty());
        player.sendMessage(msg("cmd_stats_header"));
        player.sendMessage(msg("cmd_stats_addiction",
                Placeholder.styling("stage_color", getStageColor(stage)),
                Placeholder.unparsed("stage_name", getStageName(stage))));
        player.sendMessage(msg("cmd_stats_total",
                Placeholder.unparsed("total", String.valueOf(data.getTotalSmoked()))));

        long soberSec = data.getOnlineSoberSeconds();
        String soberStr = soberSec < 3600 ? (soberSec / 60) + "m" : String.format("%.1fh", soberSec / 3600.0);
        player.sendMessage(msg("cmd_stats_sober", Placeholder.unparsed("time", soberStr)));

        CigaretteType[] types = CigaretteType.values().toArray(new CigaretteType[0]);

        player.sendMessage(msg("cmd_stats_smoked_header"));
        for (int i = 0; i < types.length; i += 2) {
            Component line = formatSmokedEntry(types[i], data);
            if (i + 1 < types.length) line = line.append(Component.text("  ")).append(formatSmokedEntry(types[i + 1], data));
            player.sendMessage(line);
        }

        player.sendMessage(msg("cmd_stats_tolerance_header"));
        for (int i = 0; i < types.length; i += 2) {
            Component line = formatToleranceEntry(types[i], data);
            if (i + 1 < types.length) line = line.append(Component.text("  ")).append(formatToleranceEntry(types[i + 1], data));
            player.sendMessage(line);
        }

        player.sendMessage(msg("cmd_stats_mastery",
                Placeholder.unparsed("mastery", String.format("%.0f%%", data.getMastery() * 100))));

        if (data.isPredispositionGenerated()) {
            player.sendMessage(msg("cmd_stats_pred_header"));
            for (CigaretteType type : types) {
                player.sendMessage(formatPredEntry(type, data));
            }
        } else {
            player.sendMessage(msg("cmd_stats_pred_unknown"));
        }
        player.sendMessage(Component.empty());
    }

    private Component formatSmokedEntry(CigaretteType type, PlayerData data) {
        return msg("cmd_stats_smoked_entry",
                Placeholder.styling("type_color", CigaroColors.forType(type)),
                Placeholder.unparsed("name", formatName(type)),
                Placeholder.unparsed("count", String.valueOf(data.getSmoked(type))));
    }

    private Component formatToleranceEntry(CigaretteType type, PlayerData data) {
        return msg("cmd_stats_tolerance_entry",
                Placeholder.styling("type_color", CigaroColors.forType(type)),
                Placeholder.unparsed("name", formatName(type)),
                Placeholder.unparsed("value", String.format("%.0f%%", data.getTolerance(type) * 100)));
    }

    private Component formatPredEntry(CigaretteType type, PlayerData data) {
        int value = data.getPredisposition(type);
        return msg("cmd_stats_pred_entry",
                Placeholder.styling("type_color", CigaroColors.forType(type)),
                Placeholder.unparsed("name", formatName(type)),
                Placeholder.styling("value_color", getPredColor(value)),
                Placeholder.unparsed("label", getPredLabel(value)));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("cmd_players_only")); return; }
        if (!player.hasPermission("cigaro.admin")) { player.sendMessage(msg("cmd_no_permission")); return; }

        if (args.length < 2) {
            player.sendMessage(Component.empty());
            player.sendMessage(msg("cmd_give_header"));
            for (Component line : msgList("cmd_give_help")) player.sendMessage(line);
            player.sendMessage(Component.empty());
            return;
        }

        var im = plugin.getItemManager();

        switch (args[1].toLowerCase()) {
            case "seeds" -> {
                if (args.length >= 3) {
                    try {
                        TobaccoType t = TobaccoType.valueOf(args[2].toUpperCase());
                        player.getInventory().addItem(im.createTobaccoSeeds(t, 16));
                        player.sendMessage(msg("cmd_give_seeds_type",
                                Placeholder.unparsed("amount", "16"),
                                Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(t))));
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", tobaccoTypeList())));
                    }
                } else {
                    for (TobaccoType t : TobaccoType.values()) player.getInventory().addItem(im.createTobaccoSeeds(t, 4));
                    player.sendMessage(msg("cmd_give_seeds_all"));
                }
            }
            case "pack" -> {
                DyeColor color = DyeColor.RED;
                if (args.length >= 3) try { color = DyeColor.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException ignored) {}
                player.getInventory().addItem(im.createCigarettePack(new ArrayList<>(), color));
                player.sendMessage(msg("cmd_give_pack"));
            }
            case "packer" -> {
                player.getInventory().addItem(im.createPackingMachine());
                player.sendMessage(msg("cmd_give_packer"));
            }
            case "fertilizer" -> {
                player.getInventory().addItem(im.createTobaccoFertilizer(16));
                player.sendMessage(msg("cmd_give_fertilizer", Placeholder.unparsed("amount", "16")));
            }
            case "timer" -> {
                player.getInventory().addItem(im.createDryingTimer());
                player.sendMessage(msg("cmd_give_timer"));
            }
            case "lighter" -> {
                player.getInventory().addItem(im.createLighterOfStats());
                player.sendMessage(msg("cmd_give_lighter"));
            }
            case "teststrip" -> {
                player.getInventory().addItem(im.createTestStrip(16));
                player.sendMessage(msg("cmd_give_teststrip", Placeholder.unparsed("amount", "16")));
            }
            case "paper" -> {
                player.getInventory().addItem(im.createCigarettePaper(32));
                player.sendMessage(msg("cmd_give_paper", Placeholder.unparsed("amount", "32")));
            }
            case "cardboard" -> {
                player.getInventory().addItem(im.createCardboard(32));
                player.sendMessage(msg("cmd_give_cardboard", Placeholder.unparsed("amount", "32")));
            }
            case "fuelpellets" -> {
                player.getInventory().addItem(im.createFuelPellets(64));
                player.sendMessage(msg("cmd_give_fuelpellets", Placeholder.unparsed("amount", "64")));
            }
            case "autodryrack" -> {
                player.getInventory().addItem(im.createAutoDryingRack());
                player.sendMessage(msg("cmd_give_autodryrack"));
            }
            case "cuttingmachine" -> {
                player.getInventory().addItem(im.createCuttingMachine());
                player.sendMessage(msg("cmd_give_cuttingmachine"));
            }
            case "rollingpress" -> {
                player.getInventory().addItem(im.createRollingPress());
                player.sendMessage(msg("cmd_give_rollingpress"));
            }
            case "packingpress" -> {
                player.getInventory().addItem(im.createPackingPress());
                player.sendMessage(msg("cmd_give_packingpress"));
            }
            case "leaf" -> {
                if (args.length >= 3) {
                    try {
                        TobaccoType t = TobaccoType.valueOf(args[2].toUpperCase());
                        player.getInventory().addItem(im.createTobaccoLeaf(t, false));
                        player.getInventory().addItem(im.createTobaccoLeaf(t, true));
                        player.sendMessage(msg("cmd_give_leaf_type", Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(t))));
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", tobaccoTypeList())));
                    }
                } else {
                    for (TobaccoType t : TobaccoType.values()) player.getInventory().addItem(im.createTobaccoLeaf(t, false));
                    player.getInventory().addItem(im.createTobaccoLeaf(TobaccoType.VIRGINIA, true));
                    player.sendMessage(msg("cmd_give_leaf_all"));
                }
            }
            case "cut" -> {
                if (args.length >= 3) {
                    try {
                        TobaccoType t = TobaccoType.valueOf(args[2].toUpperCase());
                        player.getInventory().addItem(im.createCutTobacco(t, false, DryingLevel.PERFECT, 300, 1));
                        player.sendMessage(msg("cmd_give_cut_type", Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(t))));
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", tobaccoTypeList())));
                    }
                } else {
                    player.getInventory().addItem(im.createCutTobacco(TobaccoType.VIRGINIA, false, DryingLevel.PERFECT, 300, 2));
                    player.sendMessage(msg("cmd_give_cut_default"));
                }
            }
            case "flavor" -> {
                if (args.length < 3) {
                    player.sendMessage(msg("cmd_give_flavor_usage"));
                    player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                    return;
                }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    String variant = args.length >= 4 ? args[3].toLowerCase() : "both";
                    switch (variant) {
                        case "raw" -> {
                            player.getInventory().addItem(im.createRawIngredient(type));
                            player.sendMessage(msg("cmd_give_flavor_raw", Placeholder.unparsed("type", formatName(type))));
                        }
                        case "bottle" -> {
                            player.getInventory().addItem(im.createFlavorBottle(type));
                            player.sendMessage(msg("cmd_give_flavor_bottle", Placeholder.unparsed("type", formatName(type))));
                        }
                        default -> {
                            player.getInventory().addItem(im.createRawIngredient(type));
                            player.getInventory().addItem(im.createFlavorBottle(type));
                            player.sendMessage(msg("cmd_give_flavor_both", Placeholder.unparsed("type", formatName(type))));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                }
            }
            case "cigarette" -> {
                if (args.length < 4) {
                    player.sendMessage(msg("cmd_give_cigarette_usage"));
                    player.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                    player.sendMessage(msg("cmd_give_quality_list"));
                    return;
                }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    CigaretteQuality quality = CigaretteQuality.valueOf(args[3].toUpperCase());
                    int amount = 10;
                    if (args.length >= 5) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[4]))); } catch (NumberFormatException ignored) {}
                    player.getInventory().addItem(im.createCigarette(type, quality, amount));
                    player.sendMessage(msg("cmd_give_cigarette",
                            Placeholder.unparsed("amount", String.valueOf(amount)),
                            Placeholder.unparsed("type", formatName(type)),
                            Placeholder.unparsed("quality", formatName(quality))));
                } catch (IllegalArgumentException e) {
                    player.sendMessage(msg("cmd_give_unknown_type"));
                }
            }
            case "smokescreen" -> {
                player.getInventory().addItem(im.createSmokescreenCigarette(4));
                player.sendMessage(msg("cmd_give_smokescreen", Placeholder.unparsed("amount", "4")));
            }
            case "dryrack" -> {
                player.getInventory().addItem(im.createDryingRack());
                player.sendMessage(msg("cmd_give_dryrack"));
            }
            case "cuttable" -> {
                player.getInventory().addItem(im.createCuttingTable());
                player.sendMessage(msg("cmd_give_cuttable"));
            }
            case "roller" -> {
                player.getInventory().addItem(im.createRollingMachine());
                player.sendMessage(msg("cmd_give_roller"));
            }
            case "infuser" -> {
                player.getInventory().addItem(im.createInfuser());
                player.sendMessage(msg("cmd_give_infuser"));
            }
            case "cigar" -> {
                if (args.length < 4) { player.sendMessage(msg("cmd_give_cigar_usage")); return; }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    CigaretteQuality quality = CigaretteQuality.valueOf(args[3].toUpperCase());
                    int amount = args.length >= 5 ? Integer.parseInt(args[4]) : 4;
                    amount = Math.max(1, Math.min(64, amount));
                    player.getInventory().addItem(im.createCigar(type, quality, amount));
                    player.sendMessage(msg("cmd_give_cigar", Placeholder.unparsed("amount", String.valueOf(amount))));
                } catch (IllegalArgumentException e) { player.sendMessage(msg("cmd_give_unknown_type")); }
            }
            case "pipe" -> {
                player.getInventory().addItem(im.createPipe());
                player.sendMessage(msg("cmd_give_pipe"));
            }
            case "hookah" -> {
                player.getInventory().addItem(im.createHookah());
                player.sendMessage(msg("cmd_give_hookah"));
            }
            case "blendingtable" -> {
                player.getInventory().addItem(im.createBlendingTable());
                player.sendMessage(msg("cmd_give_blendingtable"));
            }
            case "patch" -> {
                player.getInventory().addItem(im.createNicotinePatch(4));
                player.sendMessage(msg("cmd_give_patch", Placeholder.unparsed("amount", "4")));
            }
            default -> player.sendMessage(msg("cmd_give_unknown_item"));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("cigaro.admin")) { sender.sendMessage(msg("cmd_no_permission")); return; }
        plugin.getConfigManager().reload();
        plugin.getLanguageManager().load();
        sender.sendMessage(msg("cmd_reload_success"));
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("cigaro.use")) { sender.sendMessage(msg("cmd_no_permission")); return; }

        long now = System.currentTimeMillis();
        if (now - cachedTopAt < TOP_CACHE_TTL_MS) {
            showLeaderboard(sender, cachedTop);
            return;
        }

        if (!topRefreshing.compareAndSet(false, true)) {
            showLeaderboard(sender, cachedTop);
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var entries = plugin.getDatabaseManager().getTopSmokers(10);
                List<ResolvedLeaderEntry> resolved = new ArrayList<>(entries.size());
                for (var entry : entries) {
                    String name = Bukkit.getOfflinePlayer(entry.getUuid()).getName();
                    if (name == null) name = "???";
                    resolved.add(new ResolvedLeaderEntry(
                            name, entry.getTotalSmoked(), entry.getAddictionStage(), entry.getRank()));
                }
                cachedTop = List.copyOf(resolved);
                cachedTopAt = System.currentTimeMillis();
            } finally {
                topRefreshing.set(false);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> showLeaderboard(sender, cachedTop));
        });
    }

    private void showLeaderboard(CommandSender sender, List<ResolvedLeaderEntry> entries) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg("cmd_top_header"));
        if (entries.isEmpty()) {
            sender.sendMessage(msg("cmd_top_empty"));
            sender.sendMessage(Component.empty());
            return;
        }
        for (var entry : entries) {
            TextColor rankColor = switch (entry.rank()) {
                case 1 -> CigaroColors.GOLD;
                case 2 -> CigaroColors.SMOKE;
                case 3 -> CigaroColors.EMBER;
                default -> CigaroColors.ASH;
            };
            String medal = switch (entry.rank()) {
                case 1 -> "🥇"; case 2 -> "🥈"; case 3 -> "🥉";
                default -> "#" + entry.rank();
            };
            String stageName = raw("cmd_top_stage_" + Math.min(entry.addictionStage(), 4));
            sender.sendMessage(msg("cmd_top_entry",
                    Placeholder.styling("rank_color", rankColor),
                    Placeholder.unparsed("medal", medal),
                    Placeholder.unparsed("name", entry.name()),
                    Placeholder.unparsed("total", String.valueOf(entry.totalSmoked())),
                    Placeholder.unparsed("stage", stageName)));
        }
        sender.sendMessage(Component.empty());
    }

    private void handleAchievements(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("cmd_players_only")); return; }
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        var am = plugin.getAchievementManager();
        int unlocked = am.count(data);
        int total = CigaroAchievement.values().length;

        player.sendMessage(Component.empty());
        player.sendMessage(msg("cmd_achievements_header",
                Placeholder.unparsed("unlocked", String.valueOf(unlocked)),
                Placeholder.unparsed("total", String.valueOf(total))));

        for (CigaroAchievement achievement : CigaroAchievement.values()) {
            boolean has = am.has(data, achievement);
            String key = has ? "cmd_achievement_unlocked" : "cmd_achievement_locked";

            String nameKey = "achievement." + achievement.name().toLowerCase() + ".name";
            String descKey = "achievement." + achievement.name().toLowerCase() + ".desc";
            String name = raw(nameKey).equals(nameKey) ? achievement.getDisplayName() : raw(nameKey);
            String desc = raw(descKey).equals(descKey) ? achievement.getDescription() : raw(descKey);

            player.sendMessage(msg(key,
                    Placeholder.styling("ach_color", has ? achievement.getColor() : CigaroColors.ASH),
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("desc", desc)));
        }
        player.sendMessage(Component.empty());
    }

    private void handleAnalytics(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("cmd_players_only")); return; }
        if (!player.hasPermission("cigaro.admin")) { player.sendMessage(msg("cmd_no_permission")); return; }
        plugin.getAnalyticsManager().printReportToConsole();
        player.sendMessage(msg("cmd_analytics_success"));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("cmd_players_only")); return; }
        if (!player.hasPermission("cigaro.admin")) { player.sendMessage(msg("cmd_no_permission")); return; }

        if (args.length < 2) {
            player.sendMessage(Component.empty());
            player.sendMessage(msg("cmd_debug_header"));
            for (Component line : msgList("cmd_debug_help")) player.sendMessage(line);
            player.sendMessage(Component.empty());
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        switch (args[1].toLowerCase()) {
            case "withdrawal" -> {
                if (args.length < 3) { player.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    int stage = Math.max(0, Math.min(4, Integer.parseInt(args[2])));
                    int threshold = (int) Math.ceil(PlayerData.stageThreshold(stage));
                    data.setAddictionStage(stage);
                    if (data.getTotalSmoked() < threshold) data.setTotalSmoked(threshold);
                    data.resetOnlineSoberSeconds();
                    data.setLastSmokeTime(System.currentTimeMillis() - 3600000);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    player.sendMessage(msg("cmd_debug_withdrawal_set",
                            Placeholder.unparsed("stage_name", getStageName(stage)),
                            Placeholder.unparsed("stage", String.valueOf(stage))));
                } catch (NumberFormatException e) { player.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "tolerance" -> {
                if (args.length < 3) { player.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    double val = Math.max(0.0, Math.min(1.0, Double.parseDouble(args[2])));
                    for (CigaretteType type : CigaretteType.values()) data.addTolerance(type, val - data.getTolerance(type));
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    player.sendMessage(msg("cmd_debug_tolerance_set",
                            Placeholder.unparsed("value", String.format("%.0f%%", val * 100))));
                } catch (NumberFormatException e) { player.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "smoked" -> {
                if (args.length < 3) { player.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    data.setTotalSmoked(Math.max(0, amount));
                    plugin.getLungDamageManager().updateLungHealth(player, data);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    player.sendMessage(msg("cmd_debug_smoked_set", Placeholder.unparsed("amount", String.valueOf(amount))));
                } catch (NumberFormatException e) { player.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "reset" -> {
                data.setAddictionStage(0); data.setTotalSmoked(0); data.setLastSmokeTime(0);
                data.resetOnlineSoberSeconds(); data.setPredispositionGenerated(false); data.setMastery(0);
                data.setWasAddicted(false);
                data.setLungRecoveredHearts(0);
                data.resetLungRecoveryProgress();
                for (CigaretteType type : CigaretteType.values()) data.addTolerance(type, -data.getTolerance(type));
                plugin.getLungDamageManager().updateLungHealth(player, data);
                plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                player.sendMessage(msg("cmd_debug_reset_done"));
            }
            case "predisposition" -> {
                data.setPredispositionGenerated(false);
                plugin.getPlayerDataManager().generatePredisposition(data);
                plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                player.sendMessage(msg("cmd_debug_pred_regen"));
                for (CigaretteType type : CigaretteType.values()) player.sendMessage(formatPredEntry(type, data));
            }
            case "blood" -> handleBloodDebug(player);
            case "sober" -> {
                if (args.length < 3) { player.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    double hours = Double.parseDouble(args[2]);
                    long seconds = (long) (hours * 3600);
                    data.setOnlineSoberSeconds(seconds);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    player.sendMessage(msg("cmd_debug_sober_set",
                            Placeholder.unparsed("hours", String.format("%.1f", hours)),
                            Placeholder.unparsed("seconds", String.valueOf(seconds))));
                } catch (NumberFormatException e) { player.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            default -> player.sendMessage(msg("cmd_debug_unknown"));
        }
    }

    private void handleBloodDebug(Player player) {
        player.sendMessage(Component.text("Spawning blood particle test...").color(NamedTextColor.GREEN));
        Location baseLoc = player.getEyeLocation().clone().add(player.getLocation().getDirection().multiply(2)).add(0, 2, 0);
        double spacing = 1.5;
        spawnLabeled(player, baseLoc.clone().add(-spacing * 2, 0, 0), "FALLING_DRIPSTONE_LAVA",
                () -> player.getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, baseLoc.clone().add(-spacing * 2, 0, 0), 10, 0.1, 0.05, 0.1, 0));
        spawnLabeled(player, baseLoc.clone().add(-spacing, 0, 0), "DRIPPING_LAVA",
                () -> player.getWorld().spawnParticle(Particle.DRIPPING_LAVA, baseLoc.clone().add(-spacing, 0, 0), 10, 0.1, 0.05, 0.1, 0));
        spawnLabeled(player, baseLoc.clone(), "FALLING_LAVA",
                () -> player.getWorld().spawnParticle(Particle.FALLING_LAVA, baseLoc.clone(), 10, 0.1, 0.05, 0.1, 0));
        spawnLabeled(player, baseLoc.clone().add(spacing, 0, 0), "DUST dark red",
                () -> player.getWorld().spawnParticle(Particle.DUST, baseLoc.clone().add(spacing, 0, 0), 10, 0.1, 0.3, 0.1, 0, new Particle.DustOptions(Color.fromRGB(100, 5, 5), 1.2f)));
        spawnLabeled(player, baseLoc.clone().add(spacing * 2, 0, 0), "DUST bright red",
                () -> player.getWorld().spawnParticle(Particle.DUST, baseLoc.clone().add(spacing * 2, 0, 0), 10, 0.1, 0.3, 0.1, 0, new Particle.DustOptions(Color.fromRGB(180, 20, 20), 0.6f)));
        spawnLabeled(player, baseLoc.clone().add(spacing * 3, 0, 0), "BLOCK redstone",
                () -> player.getWorld().spawnParticle(Particle.BLOCK, baseLoc.clone().add(spacing * 3, 0, 0), 15, 0.1, 0.3, 0.1, 0.5, Material.REDSTONE_BLOCK.createBlockData()));
        spawnLabeled(player, baseLoc.clone().add(spacing * 4, 0, 0), "BLOCK red_concrete",
                () -> player.getWorld().spawnParticle(Particle.BLOCK, baseLoc.clone().add(spacing * 4, 0, 0), 15, 0.1, 0.3, 0.1, 0.5, Material.RED_CONCRETE.createBlockData()));
        spawnLabeled(player, baseLoc.clone().add(spacing * 5, 0, 0), "FALLING_DRIP_WATER",
                () -> player.getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_WATER, baseLoc.clone().add(spacing * 5, 0, 0), 10, 0.1, 0.05, 0.1, 0));
        spawnLabeled(player, baseLoc.clone().add(spacing * 6, 0, 0), "DAMAGE_INDICATOR",
                () -> player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, baseLoc.clone().add(spacing * 6, 0, 0), 8, 0.1, 0.3, 0.1, 0.1));
    }

    private void spawnLabeled(Player player, Location loc, String label, Runnable spawner) {
        player.sendMessage(Component.text("  " + label + " at ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f %.0f %.0f", loc.getX(), loc.getY(), loc.getZ())).color(NamedTextColor.WHITE)));
        for (int i = 0; i < 3; i++) Bukkit.getScheduler().runTaskLater(plugin, spawner, i * 15L);
    }

    private String formatName(CigaretteType type) {
        return plugin.getItemManager().formatTypeName(type);
    }

    private String formatName(CigaretteQuality quality) {
        return plugin.getItemManager().formatQualityName(quality);
    }

    private String tobaccoTypeList() {
        StringBuilder sb = new StringBuilder();
        for (TobaccoType t : TobaccoType.values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(plugin.getItemManager().formatTobaccoTypeName(t));
        }
        return sb.toString();
    }

    private String cigaretteTypeList() {
        StringBuilder sb = new StringBuilder();
        for (CigaretteType t : CigaretteType.values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(plugin.getItemManager().formatTypeName(t));
        }
        return sb.toString();
    }
}


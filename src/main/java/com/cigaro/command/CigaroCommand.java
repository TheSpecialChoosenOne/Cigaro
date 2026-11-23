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
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CigaroCommand implements CommandExecutor {

    private final Cigaro plugin;

    private record ResolvedLeaderEntry(String name, int totalSmoked, int addictionStage, int rank) {}

    private volatile List<ResolvedLeaderEntry> cachedTop = List.of();
    private volatile long cachedTopAt = 0L;
    private final List<CommandSender> topWaiters = new ArrayList<>();
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { showHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "stats" -> handleStats(sender, args);
            case "give" -> dispatchTarget(sender, args, false);
            case "reload" -> handleReload(sender);
            case "debug" -> dispatchTarget(sender, args, true);
            case "top" -> handleTop(sender);
            case "achievements" -> handleAchievements(sender);
            case "analytics" -> handleAnalytics(sender);
            default -> sender.sendMessage(msg("cmd_unknown"));
        }
        return true;
    }

    private void dispatchTarget(CommandSender sender, String[] original, boolean debug) {
        if (!sender.hasPermission("cigaro.admin")) { sender.sendMessage(msg("cmd_no_permission")); return; }
        if (original.length == 1) {
            sender.sendMessage(msg(debug ? "cmd_debug_header" : "cmd_give_header"));
            for (Component line : msgList(debug ? "cmd_debug_help" : "cmd_give_help")) sender.sendMessage(line);
            return;
        }
        List<String> positional = new ArrayList<>();
        String targetName = null;
        Integer amount = null;
        for (int i = 0; i < original.length; i++) {
            if (original[i].equalsIgnoreCase("--player") || original[i].equalsIgnoreCase("--amount")) {
                boolean isPlayer = original[i].equalsIgnoreCase("--player");
                if (++i >= original.length || (isPlayer ? targetName != null : amount != null || debug)) {
                    sender.sendMessage(msg("cmd_invalid_argument")); return;
                }
                if (isPlayer) targetName = original[i];
                else try { amount = Integer.parseInt(original[i]); if (amount < 1 || amount > 64) throw new NumberFormatException(); }
                catch (NumberFormatException e) { sender.sendMessage(msg("cmd_invalid_amount")); return; }
            } else positional.add(original[i]);
        }
        Player target;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) { sender.sendMessage(msg("cmd_target_offline")); return; }
        } else if (sender instanceof Player player) target = player;
        else { sender.sendMessage(msg("cmd_target_required")); return; }
        String[] args = positional.toArray(String[]::new);
        if (debug) handleDebug(sender, target, args); else handleGive(sender, target, args, amount);
    }

    private void give(Player player, ItemStack item, Integer amount) {
        int remaining = amount == null ? item.getAmount() : amount;
        int max = item.getMaxStackSize();
        while (remaining > 0) {
            ItemStack batch = item.clone();
            batch.setAmount(Math.min(remaining, max));
            remaining -= batch.getAmount();
            for (ItemStack overflow : player.getInventory().addItem(batch).values())
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(msg("cmd_help_header",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion())));
        sender.sendMessage(msg("cmd_help_subtitle"));
        sender.sendMessage(Component.empty());
        for (Component line : msgList("cmd_help_player")) sender.sendMessage(line);
        if (sender.hasPermission("cigaro.admin"))
            for (Component line : msgList("cmd_help_admin")) sender.sendMessage(line);
        sender.sendMessage(Component.empty());
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cigaro.use")) { sender.sendMessage(msg("cmd_no_permission")); return; }
        Player player;
        if (args.length == 2 && sender.hasPermission("cigaro.admin")) {
            player = Bukkit.getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(msg("cmd_target_offline")); return; }
        } else if (args.length > 1) { sender.sendMessage(msg("cmd_no_permission")); return; }
        else if (sender instanceof Player self) player = self;
        else { sender.sendMessage(msg("cmd_target_required")); return; }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
        int stage = data.getAddictionStage();

        sender.sendMessage(Component.empty());
        sender.sendMessage(msg("cmd_stats_header"));
        sender.sendMessage(msg("cmd_stats_addiction",
                Placeholder.styling("stage_color", getStageColor(stage)),
                Placeholder.unparsed("stage_name", getStageName(stage))));
        sender.sendMessage(msg("cmd_stats_total",
                Placeholder.unparsed("total", String.valueOf(data.getTotalSmoked()))));

        long soberSec = data.getOnlineSoberSeconds();
        String soberStr = soberSec < 3600 ? (soberSec / 60) + "m" : String.format("%.1fh", soberSec / 3600.0);
        sender.sendMessage(msg("cmd_stats_sober", Placeholder.unparsed("time", soberStr)));

        CigaretteType[] types = CigaretteType.values().toArray(new CigaretteType[0]);

        sender.sendMessage(msg("cmd_stats_smoked_header"));
        for (int i = 0; i < types.length; i += 2) {
            Component line = formatSmokedEntry(types[i], data);
            if (i + 1 < types.length) line = line.append(Component.text("  ")).append(formatSmokedEntry(types[i + 1], data));
            sender.sendMessage(line);
        }

        sender.sendMessage(msg("cmd_stats_tolerance_header"));
        for (int i = 0; i < types.length; i += 2) {
            Component line = formatToleranceEntry(types[i], data);
            if (i + 1 < types.length) line = line.append(Component.text("  ")).append(formatToleranceEntry(types[i + 1], data));
            sender.sendMessage(line);
        }

        sender.sendMessage(msg("cmd_stats_mastery",
                Placeholder.unparsed("mastery", String.format("%.0f%%", data.getMastery() * 100))));

        if (data.isPredispositionGenerated()) {
            sender.sendMessage(msg("cmd_stats_pred_header"));
            for (CigaretteType type : types) {
                sender.sendMessage(formatPredEntry(type, data));
            }
        } else {
            sender.sendMessage(msg("cmd_stats_pred_unknown"));
        }
        sender.sendMessage(Component.empty());
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

    private void handleGive(CommandSender sender, Player player, String[] args, Integer overrideAmount) {

        if (args.length < 2) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(msg("cmd_give_header"));
            for (Component line : msgList("cmd_give_help")) sender.sendMessage(line);
            sender.sendMessage(Component.empty());
            return;
        }

        var im = plugin.getItemManager();
        String kind = args[1].toLowerCase(java.util.Locale.ROOT);
        int maxArgs = switch (kind) {
            case "cut" -> 6; case "leaf" -> 5; case "cigarette", "cigar" -> 5;
            case "flavor" -> 4; case "seeds", "pack" -> 3; default -> 2;
        };
        if (args.length > maxArgs || (overrideAmount != null && args.length == 5
                && (kind.equals("cigar") || kind.equals("cigarette")))) {
            sender.sendMessage(msg("cmd_invalid_argument")); return;
        }

        switch (args[1].toLowerCase()) {
            case "seeds" -> {
                if (args.length >= 3) {
                    try {
                        TobaccoType t = TobaccoType.valueOf(args[2].toUpperCase());
                        give(player, im.createTobaccoSeeds(t, 16), overrideAmount);
                        sender.sendMessage(msg("cmd_give_seeds_type",
                                Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 16)),
                                Placeholder.unparsed("type", plugin.getItemManager().formatTobaccoTypeName(t))));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", tobaccoTypeList())));
                    }
                } else {
                    for (TobaccoType t : TobaccoType.values()) give(player, im.createTobaccoSeeds(t, 4), overrideAmount);
                    sender.sendMessage(msg("cmd_give_seeds_all"));
                }
            }
            case "pack" -> {
                DyeColor color = DyeColor.RED;
                if (args.length >= 3) try { color = DyeColor.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException e) { sender.sendMessage(msg("cmd_invalid_argument")); return; }
                give(player, im.createCigarettePack(new ArrayList<>(), color), overrideAmount);
                sender.sendMessage(msg("cmd_give_pack"));
            }
            case "packer" -> {
                give(player, im.createPackingMachine(), overrideAmount);
                sender.sendMessage(msg("cmd_give_packer"));
            }
            case "fertilizer" -> {
                give(player, im.createTobaccoFertilizer(16), overrideAmount);
                sender.sendMessage(msg("cmd_give_fertilizer", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 16))));
            }
            case "timer" -> {
                give(player, im.createDryingTimer(), overrideAmount);
                sender.sendMessage(msg("cmd_give_timer"));
            }
            case "lighter" -> {
                give(player, im.createLighterOfStats(), overrideAmount);
                sender.sendMessage(msg("cmd_give_lighter"));
            }
            case "teststrip" -> {
                give(player, im.createTestStrip(16), overrideAmount);
                sender.sendMessage(msg("cmd_give_teststrip", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 16))));
            }
            case "paper" -> {
                give(player, im.createCigarettePaper(32), overrideAmount);
                sender.sendMessage(msg("cmd_give_paper", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 32))));
            }
            case "cardboard" -> {
                give(player, im.createCardboard(32), overrideAmount);
                sender.sendMessage(msg("cmd_give_cardboard", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 32))));
            }
            case "fuelpellets" -> {
                give(player, im.createFuelPellets(64), overrideAmount);
                sender.sendMessage(msg("cmd_give_fuelpellets", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 64))));
            }
            case "autodryrack" -> {
                give(player, im.createAutoDryingRack(), overrideAmount);
                sender.sendMessage(msg("cmd_give_autodryrack"));
            }
            case "cuttingmachine" -> {
                give(player, im.createCuttingMachine(), overrideAmount);
                sender.sendMessage(msg("cmd_give_cuttingmachine"));
            }
            case "rollingpress" -> {
                give(player, im.createRollingPress(), overrideAmount);
                sender.sendMessage(msg("cmd_give_rollingpress"));
            }
            case "packingpress" -> {
                give(player, im.createPackingPress(), overrideAmount);
                sender.sendMessage(msg("cmd_give_packingpress"));
            }
            case "leaf", "cut" -> {
                boolean cut = args[1].equalsIgnoreCase("cut");
                if (args.length < 3) {
                    if (cut) give(player, im.createCutTobacco(TobaccoType.VIRGINIA, false, DryingLevel.PERFECT, plugin.getConfigManager().getThresholdStart(DryingLevel.PERFECT), 2), overrideAmount);
                    else for (TobaccoType type : TobaccoType.values()) give(player, im.createTobaccoLeaf(type, false), overrideAmount);
                    sender.sendMessage(msg("cmd_give_simple_success")); return;
                }
                try {
                    TobaccoType type = TobaccoType.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                    int quality = 1;
                    int index = 3;
                    if (cut && args.length > index) {
                        quality = switch (args[index++].toLowerCase(java.util.Locale.ROOT)) {
                            case "bad" -> -1; case "rough" -> 0; case "good" -> 1; case "perfect" -> 2;
                            default -> throw new IllegalArgumentException();
                        };
                    }
                    DryingLevel drying = args.length > index ? DryingLevel.valueOf(args[index++].toUpperCase(java.util.Locale.ROOT)) : (cut ? DryingLevel.PERFECT : DryingLevel.RAW);
                    String grade = args.length > index ? args[index++].toLowerCase(java.util.Locale.ROOT) : "normal";
                    if (index != args.length || (!grade.equals("normal") && !grade.equals("elite"))) throw new IllegalArgumentException();
                    int seconds = plugin.getConfigManager().getThresholdStart(drying);
                    ItemStack item = cut ? im.createCutTobacco(type, grade.equals("elite"), drying, seconds, quality)
                            : im.createTobaccoLeaf(type, grade.equals("elite"), seconds);
                    give(player, item, overrideAmount);
                    sender.sendMessage(msg("cmd_give_simple_success"));
                } catch (IllegalArgumentException e) { sender.sendMessage(msg("cmd_give_tobacco_usage")); }
            }
            case "flavor" -> {
                if (args.length < 3) {
                    sender.sendMessage(msg("cmd_give_flavor_usage"));
                    sender.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                    return;
                }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    String variant = args.length >= 4 ? args[3].toLowerCase() : "both";
                    switch (variant) {
                        case "raw" -> {
                            give(player, im.createRawIngredient(type), overrideAmount);
                            sender.sendMessage(msg("cmd_give_flavor_raw", Placeholder.unparsed("type", formatName(type))));
                        }
                        case "bottle" -> {
                            give(player, im.createFlavorBottle(type), overrideAmount);
                            sender.sendMessage(msg("cmd_give_flavor_bottle", Placeholder.unparsed("type", formatName(type))));
                        }
                        case "both" -> {
                            give(player, im.createRawIngredient(type), overrideAmount);
                            give(player, im.createFlavorBottle(type), overrideAmount);
                            sender.sendMessage(msg("cmd_give_flavor_both", Placeholder.unparsed("type", formatName(type))));
                        }
                        default -> { sender.sendMessage(msg("cmd_invalid_argument")); return; }
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                }
            }
            case "cigarette" -> {
                if (args.length < 4) {
                    sender.sendMessage(msg("cmd_give_cigarette_usage"));
                    sender.sendMessage(msg("cmd_give_types", Placeholder.unparsed("types", cigaretteTypeList())));
                    sender.sendMessage(msg("cmd_give_quality_list"));
                    return;
                }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    CigaretteQuality quality = CigaretteQuality.valueOf(args[3].toUpperCase());
                    int amount = 10;
                    if (args.length >= 5) { amount = Integer.parseInt(args[4]); if (amount < 1 || amount > 64) throw new IllegalArgumentException(); }
                    give(player, im.createCigarette(type, quality, amount), overrideAmount);
                    sender.sendMessage(msg("cmd_give_cigarette",
                            Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : amount)),
                            Placeholder.unparsed("type", formatName(type)),
                            Placeholder.unparsed("quality", formatName(quality))));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(msg("cmd_invalid_argument"));
                }
            }
            case "smokescreen" -> {
                give(player, im.createSmokescreenCigarette(4), overrideAmount);
                sender.sendMessage(msg("cmd_give_smokescreen", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 4))));
            }
            case "dryrack" -> {
                give(player, im.createDryingRack(), overrideAmount);
                sender.sendMessage(msg("cmd_give_dryrack"));
            }
            case "cuttable" -> {
                give(player, im.createCuttingTable(), overrideAmount);
                sender.sendMessage(msg("cmd_give_cuttable"));
            }
            case "roller" -> {
                give(player, im.createRollingMachine(), overrideAmount);
                sender.sendMessage(msg("cmd_give_roller"));
            }
            case "infuser" -> {
                give(player, im.createInfuser(), overrideAmount);
                sender.sendMessage(msg("cmd_give_infuser"));
            }
            case "cigar" -> {
                if (args.length < 4) { sender.sendMessage(msg("cmd_give_cigar_usage")); return; }
                try {
                    CigaretteType type = CigaretteType.valueOf(args[2].toUpperCase());
                    CigaretteQuality quality = CigaretteQuality.valueOf(args[3].toUpperCase());
                    int amount = args.length >= 5 ? Integer.parseInt(args[4]) : 4;
                    if (amount < 1 || amount > 64) throw new IllegalArgumentException();
                    give(player, im.createCigar(type, quality, amount), overrideAmount);
                    sender.sendMessage(msg("cmd_give_cigar", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : amount))));
                } catch (IllegalArgumentException e) { sender.sendMessage(msg("cmd_invalid_argument")); }
            }
            case "pipe" -> {
                give(player, im.createPipe(), overrideAmount);
                sender.sendMessage(msg("cmd_give_pipe"));
            }
            case "hookah" -> {
                give(player, im.createHookah(), overrideAmount);
                sender.sendMessage(msg("cmd_give_hookah"));
            }
            case "blendingtable" -> {
                give(player, im.createBlendingTable(), overrideAmount);
                sender.sendMessage(msg("cmd_give_blendingtable"));
            }
            case "patch" -> {
                give(player, im.createNicotinePatch(4), overrideAmount);
                sender.sendMessage(msg("cmd_give_patch", Placeholder.unparsed("amount", String.valueOf(overrideAmount != null ? overrideAmount : 4))));
            }
            default -> sender.sendMessage(msg("cmd_give_unknown_item"));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("cigaro.admin")) { sender.sendMessage(msg("cmd_no_permission")); return; }
        int previousInterval = plugin.getConfigManager().getWithdrawalCheckSeconds();
        plugin.getConfigManager().reload();
        if (previousInterval != plugin.getConfigManager().getWithdrawalCheckSeconds())
            plugin.getWithdrawalTask().rescheduleWithdrawal();
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

        synchronized (topWaiters) { topWaiters.add(sender); }
        if (!topRefreshing.compareAndSet(false, true)) {
            sender.sendMessage(msg("cmd_top_loading"));
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
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<CommandSender> waiters;
                synchronized (topWaiters) { waiters = new ArrayList<>(topWaiters); topWaiters.clear(); }
                for (CommandSender waiter : waiters) showLeaderboard(waiter, cachedTop);
            });
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
        if (!player.hasPermission("cigaro.use")) { player.sendMessage(msg("cmd_no_permission")); return; }
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
        if (!sender.hasPermission("cigaro.admin")) { sender.sendMessage(msg("cmd_no_permission")); return; }
        plugin.getAnalyticsManager().printReportToConsole();
        sender.sendMessage(msg("cmd_analytics_success"));
    }

    private void handleDebug(CommandSender sender, Player player, String[] args) {
        if (!player.hasPermission("cigaro.admin")) { sender.sendMessage(msg("cmd_no_permission")); return; }

        if (args.length < 2) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(msg("cmd_debug_header"));
            for (Component line : msgList("cmd_debug_help")) sender.sendMessage(line);
            sender.sendMessage(Component.empty());
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        switch (args[1].toLowerCase()) {
            case "withdrawal" -> {
                if (args.length < 3) { sender.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    int stage = Integer.parseInt(args[2]);
                    if (stage < 0 || stage > 4) throw new NumberFormatException();
                    int threshold = (int) Math.ceil(PlayerData.stageThreshold(stage));
                    data.setAddictionStage(stage);
                    if (data.getTotalSmoked() < threshold) data.setTotalSmoked(threshold);
                    data.resetOnlineSoberSeconds();
                    data.setLastSmokeTime(System.currentTimeMillis() - 3600000);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    sender.sendMessage(msg("cmd_debug_withdrawal_set",
                            Placeholder.unparsed("stage_name", getStageName(stage)),
                            Placeholder.unparsed("stage", String.valueOf(stage))));
                } catch (NumberFormatException e) { sender.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "tolerance" -> {
                if (args.length < 3) { sender.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    double val = Double.parseDouble(args[2]);
                    if (!Double.isFinite(val) || val < 0 || val > 1) throw new NumberFormatException();
                    for (CigaretteType type : CigaretteType.values()) data.addTolerance(type, val - data.getTolerance(type));
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    sender.sendMessage(msg("cmd_debug_tolerance_set",
                            Placeholder.unparsed("value", String.format("%.0f%%", val * 100))));
                } catch (NumberFormatException e) { sender.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "smoked" -> {
                if (args.length < 3) { sender.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0) throw new NumberFormatException();
                    data.setTotalSmoked(amount);
                    plugin.getLungDamageManager().updateLungHealth(player, data);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    sender.sendMessage(msg("cmd_debug_smoked_set", Placeholder.unparsed("amount", String.valueOf(amount))));
                } catch (NumberFormatException e) { sender.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            case "reset" -> {
                data.resetAllProgress();
                plugin.getLungDamageManager().updateLungHealth(player, data);
                plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                sender.sendMessage(msg("cmd_debug_reset_done"));
            }
            case "predisposition" -> {
                data.setPredispositionGenerated(false);
                plugin.getPlayerDataManager().generatePredisposition(data);
                plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                sender.sendMessage(msg("cmd_debug_pred_regen"));
                for (CigaretteType type : CigaretteType.values()) sender.sendMessage(formatPredEntry(type, data));
            }
            case "blood" -> handleBloodDebug(player);
            case "sober" -> {
                if (args.length < 3) { sender.sendMessage(msg("cmd_debug_usage")); return; }
                try {
                    double hours = Double.parseDouble(args[2]);
                    if (!Double.isFinite(hours) || hours < 0 || hours > Long.MAX_VALUE / 3600.0) throw new NumberFormatException();
                    long seconds = (long) (hours * 3600);
                    data.setOnlineSoberSeconds(seconds);
                    plugin.getPlayerDataManager().saveNow(player.getUniqueId());
                    sender.sendMessage(msg("cmd_debug_sober_set",
                            Placeholder.unparsed("hours", String.format("%.1f", hours)),
                            Placeholder.unparsed("seconds", String.valueOf(seconds))));
                } catch (NumberFormatException e) { sender.sendMessage(msg("cmd_debug_invalid_number")); }
            }
            default -> sender.sendMessage(msg("cmd_debug_unknown"));
        }
    }

    private void handleBloodDebug(Player player) {
        player.sendMessage(msg("cmd_debug_blood_start"));
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
        player.sendMessage(msg("cmd_debug_blood_at", Placeholder.unparsed("label", label))
                .append(Component.text(String.format("%.0f %.0f %.0f", loc.getX(), loc.getY(), loc.getZ())).color(CigaroColors.GOLD)));
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


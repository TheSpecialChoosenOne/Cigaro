package com.cigaro.command;

import com.cigaro.data.CigaretteQuality;
import com.cigaro.data.CigaretteType;
import com.cigaro.data.TobaccoType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CigaroTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(
                    List.of("stats", "give", "reload", "top", "achievements")
            );
            if (sender.hasPermission("cigaro.admin")) {
                base.add("debug");
            }
            return filter(base, args[0]);
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(List.of(
                        "seeds", "leaf", "cut", "flavor",
                        "cigarette", "cigar", "pipe", "hookah",
                        "blendingtable", "patch", "smokescreen",
                        "dryrack", "cuttable",
                        "roller", "infuser", "pack", "packer",
                        "fertilizer", "timer", "lighter",
                        "teststrip", "paper", "cardboard",
                        "fuelpellets", "autodryrack",
                        "cuttingmachine", "rollingpress",
                        "packingpress"
                ), args[1]);
            }

            if (args.length == 3) {
                return switch (args[1].toLowerCase()) {
                    case "seeds", "leaf", "cut" ->
                            filter(tobaccoTypes(), args[2]);
                    case "flavor", "cigarette", "cigar" ->
                            filter(cigaretteTypes(), args[2]);
                    default -> List.of();
                };
            }

            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("flavor")) {
                    return filter(
                            List.of("raw", "bottle", "both"),
                            args[3]
                    );
                }
                if (args[1].equalsIgnoreCase("cigarette")
                        || args[1].equalsIgnoreCase("cigar")) {
                    return filter(qualities(), args[3]);
                }
            }

            if (args.length == 5) {
                if (args[1].equalsIgnoreCase("cigarette")
                        || args[1].equalsIgnoreCase("cigar")) {
                    return filter(
                            List.of("1", "5", "10", "32", "64"),
                            args[4]
                    );
                }
            }
        }

        if (args[0].equalsIgnoreCase("debug")
                && sender.hasPermission("cigaro.admin")) {

            if (args.length == 2) {
                return filter(List.of(
                        "withdrawal", "tolerance", "smoked",
                        "reset", "predisposition", "sober", "blood"
                ), args[1]);
            }

            if (args.length == 3) {
                return switch (args[1].toLowerCase()) {
                    case "withdrawal" -> filter(
                            List.of("0", "1", "2", "3", "4"),
                            args[2]
                    );
                    case "tolerance" -> filter(
                            List.of("0.0", "0.25", "0.5",
                                    "0.75", "1.0"),
                            args[2]
                    );
                    case "smoked" -> filter(
                            List.of("0", "5", "15", "35",
                                    "70", "100"),
                            args[2]
                    );
                    case "sober" -> filter(
                            List.of("0", "12", "24", "48", "96"),
                            args[2]
                    );
                    default -> List.of();
                };
            }
        }

        return List.of();
    }

    private List<String> tobaccoTypes() {
        return TobaccoType.values().stream()
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> cigaretteTypes() {
        return CigaretteType.values().stream()
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> qualities() {
        return Arrays.stream(CigaretteQuality.values())
                .map(q -> q.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(s -> s.startsWith(lower))
                .collect(Collectors.toList());
    }
}
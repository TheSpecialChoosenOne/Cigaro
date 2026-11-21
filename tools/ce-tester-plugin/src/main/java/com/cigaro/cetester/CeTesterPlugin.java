package com.cigaro.cetester;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CeTesterPlugin extends JavaPlugin {

    private static final String PERMISSION = "cetester.use";
    private static final int DEFAULT_SECONDS = 5;
    private static final int MAX_SECONDS = 3600;
    private static final List<Integer> SECOND_VALUES = List.of(1, 2, 3, 5, 10, 15, 30, 60);

    private static final SuggestionProvider<CommandSourceStack> EFFECT_SUGGESTIONS = (context, builder) -> {
        String remaining = normalize(builder.getRemaining());
        for (Effect effect : Effect.values()) {
            if (remaining.isEmpty() || effect.matchesPrefix(remaining)) {
                builder.suggest(effect.id, tooltip(effect.description));
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> SECOND_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining();
        for (int seconds : SECOND_VALUES) {
            if (Integer.toString(seconds).startsWith(remaining)) {
                builder.suggest(seconds, tooltip(seconds + " сек"));
            }
        }
        return builder.buildFuture();
    };

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildCommand().build(), "Cigaro effect tester", List.of())
        );
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ce")
                .requires(source -> source.getSender() instanceof Player player
                        && player.hasPermission(PERMISSION))
                .executes(context -> {
                    sendHelp(context.getSource().getSender());
                    return 1;
                });

        root.then(Commands.literal("remove")
                .executes(context -> removeAll(context.getSource()))
                .then(Commands.argument("effect", StringArgumentType.word())
                        .suggests(EFFECT_SUGGESTIONS)
                        .executes(context -> removeEffect(
                                context.getSource(),
                                StringArgumentType.getString(context, "effect")
                        ))));

        root.then(Commands.argument("effect", StringArgumentType.word())
                .suggests(EFFECT_SUGGESTIONS)
                .executes(context -> {
                    String raw = StringArgumentType.getString(context, "effect");
                    Effect effect = findEffect(raw);
                    if (effect == null) {
                        return unknownEffect(context.getSource(), raw);
                    }
                    return activate(context.getSource(), effect, DEFAULT_SECONDS);
                })
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_SECONDS))
                        .suggests(SECOND_SUGGESTIONS)
                        .executes(context -> {
                            String raw = StringArgumentType.getString(context, "effect");
                            Effect effect = findEffect(raw);
                            if (effect == null) {
                                return unknownEffect(context.getSource(), raw);
                            }
                            return activate(
                                    context.getSource(),
                                    effect,
                                    IntegerArgumentType.getInteger(context, "seconds")
                            );
                        })));

        return root;
    }

    private int activate(CommandSourceStack source, Effect effect, int seconds) {
        Player player = player(source);
        if (player == null) {
            return 0;
        }

        boolean ok = dispatch(player, effect.id, Integer.toString(seconds));
        if (!ok) {
            source.getSender().sendMessage(prefix()
                    .append(Component.text("Не удалось выполнить ce:dispatch. ", NamedTextColor.RED))
                    .append(Component.text("Датапак cigaro_effect_tester включён?", NamedTextColor.GRAY)));
            return 0;
        }

        source.getSender().sendMessage(prefix()
                .append(Component.text(effect.id, NamedTextColor.AQUA))
                .append(Component.text(" — " + seconds + " сек, затем плавное затухание.", NamedTextColor.GRAY)));
        return 1;
    }

    private int removeEffect(CommandSourceStack source, String raw) {
        if (normalize(raw).equals("all")) {
            return removeAll(source);
        }

        Effect effect = findEffect(raw);
        if (effect == null) {
            return unknownEffect(source, raw);
        }

        Player player = player(source);
        if (player == null) {
            return 0;
        }

        boolean ok = dispatch(player, "remove", effect.id);
        if (ok) {
            source.getSender().sendMessage(prefix()
                    .append(Component.text(effect.id, NamedTextColor.AQUA))
                    .append(Component.text(" снят.", NamedTextColor.GRAY)));
        }
        return ok ? 1 : 0;
    }

    private int removeAll(CommandSourceStack source) {
        Player player = player(source);
        if (player == null) {
            return 0;
        }

        boolean allOk = true;
        for (Effect effect : Effect.values()) {
            allOk &= dispatch(player, "remove", effect.id);
        }

        if (allOk) {
            source.getSender().sendMessage(prefix()
                    .append(Component.text("Все эффекты сняты.", NamedTextColor.GRAY)));
        }
        return allOk ? 1 : 0;
    }

    private boolean dispatch(Player player, String action, String value) {
        String command = "execute as " + player.getName()
                + " run function ce:dispatch {a:\"" + action + "\",b:\"" + value + "\"}";
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private int unknownEffect(CommandSourceStack source, String raw) {
        source.getSender().sendMessage(prefix()
                .append(Component.text("Неизвестный эффект: ", NamedTextColor.GRAY))
                .append(Component.text(raw, NamedTextColor.RED))
                .append(Component.text(". Нажми Tab после /ce.", NamedTextColor.GRAY)));
        return 0;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix()
                .append(Component.text("/ce <effect> [seconds]", NamedTextColor.YELLOW))
                .append(Component.text(" — включить (по умолчанию 5 сек)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                .append(Component.text("/ce remove [effect|all]", NamedTextColor.YELLOW))
                .append(Component.text(" — снять сразу", NamedTextColor.GRAY)));

        Component effects = Component.text("  Эффекты: ", NamedTextColor.GRAY);
        boolean first = true;
        for (Effect effect : Effect.values()) {
            if (!first) {
                effects = effects.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            }
            first = false;
            effects = effects.append(Component.text(effect.id, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand("/ce " + effect.id + " "))
                    .hoverEvent(HoverEvent.showText(Component.text(effect.description, NamedTextColor.GRAY))));
        }
        sender.sendMessage(effects);
        sender.sendMessage(Component.text("  Tab после /ce — подсказки эффектов и времени.", NamedTextColor.DARK_GRAY));
    }

    private static Player player(CommandSourceStack source) {
        if (source.getSender() instanceof Player player) {
            return player;
        }
        source.getSender().sendMessage(prefix()
                .append(Component.text("Команда доступна только игрокам.", NamedTextColor.RED)));
        return null;
    }

    private static Component prefix() {
        return Component.text("[Cigaro tester] ", NamedTextColor.GOLD);
    }

    private static com.mojang.brigadier.Message tooltip(String text) {
        return MessageComponentSerializer.message().serialize(Component.text(text, NamedTextColor.GRAY));
    }

    private static Effect findEffect(String raw) {
        String key = normalize(raw);
        if (key.isEmpty()) {
            return null;
        }
        for (Effect effect : Effect.values()) {
            if (effect.key.equals(key) || effect.aliases.contains(key)) {
                return effect;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private enum Effect {
        PUFF("puff", "Густой табачный дым"),
        MINT_CHILL("mint_chill", "Мятная прохлада", "mint", "mintchill"),
        SPICE_HEAT("spice_heat", "Пряный жар", "spice", "spiceheat"),
        SHADOW_NOIR("shadow_noir", "Тёмный нуар", "shadow", "shadownoir", "noir"),
        WITHDRAWAL("withdrawal", "Абстинентный синдром", "with", "withdraw"),
        KILLER_TRIP("killer_trip", "Fisheye и hue shift", "trip", "killer", "killertrip");

        private final String id;
        private final String key;
        private final String description;
        private final List<String> aliases;

        Effect(String id, String description, String... aliases) {
            this.id = id;
            this.key = normalize(id);
            this.description = description;
            this.aliases = new ArrayList<>(aliases.length);
            for (String alias : aliases) {
                this.aliases.add(normalize(alias));
            }
        }

        private boolean matchesPrefix(String normalizedPrefix) {
            if (key.startsWith(normalizedPrefix)) {
                return true;
            }
            for (String alias : aliases) {
                if (alias.startsWith(normalizedPrefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}

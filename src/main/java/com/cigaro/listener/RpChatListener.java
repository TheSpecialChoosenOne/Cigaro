package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RpChatListener implements Listener {
    private final Cigaro plugin;
    private final Random random = new Random();

    private static final Pattern QCHAT_REPLY_PATTERN = Pattern.compile("^([^\\s]?\\$GOS:\\d+:[a-zA-Z0-9_]+\\s+)(.*)$");

    private static final String[] LIGHT_INSERTS = {
            "chat_insert_light_1", "chat_insert_light_2", "chat_insert_light_3"
    };

    private static final String[] HEAVY_INSERTS = {
            "chat_insert_heavy_1", "chat_insert_heavy_2", "chat_insert_heavy_3", "chat_insert_heavy_4"
    };

    public RpChatListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        int stage = data.getAddictionStage();

        if (stage < 1 || plugin.getWithdrawalTask().isInGracePeriod(data)) return;

        String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());

        Matcher matcher = QCHAT_REPLY_PATTERN.matcher(plainText);
        String prefix = "";
        String content = plainText;

        if (matcher.find()) {
            prefix = matcher.group(1);
            content = matcher.group(2);
        }

        double insertChance = switch (stage) {
            case 1 -> 0.04;
            case 2 -> 0.09;
            case 3 -> 0.18;
            case 4 -> 0.30;
            default -> 0.0;
        };

        String[] words = content.split(" ");
        Component messageBody = Component.empty();

        String[] activePool = stage >= 3 ? HEAVY_INSERTS : LIGHT_INSERTS;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            if (stage >= 3 && word.length() >= 6 && random.nextDouble() < (insertChance * 0.4)) {
                int mid = word.length() / 2;
                String part1 = word.substring(0, mid);
                String part2 = word.substring(mid);

                String randomKey = activePool[random.nextInt(activePool.length)];
                String insertText = plugin.getLanguageManager().getRaw(randomKey);

                messageBody = messageBody.append(Component.text(part1).color(NamedTextColor.WHITE))
                        .append(Component.text("-" + insertText + "-")
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, true))
                        .append(Component.text(part2).color(NamedTextColor.WHITE));
            } else {
                messageBody = messageBody.append(Component.text(word).color(NamedTextColor.WHITE));
            }

            if (i < words.length - 1) {
                if (random.nextDouble() < insertChance) {
                    String randomKey = activePool[random.nextInt(activePool.length)];
                    String insertText = plugin.getLanguageManager().getRaw(randomKey);

                    messageBody = messageBody.append(
                            Component.text(" " + insertText + " ")
                                    .color(NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, true));
                } else {
                    messageBody = messageBody.append(Component.text(" "));
                }
            }
        }

        Component finalMessage = Component.empty();

        if (!prefix.isEmpty()) {
            finalMessage = finalMessage.append(Component.text(prefix));
        }

        finalMessage = finalMessage.append(messageBody);

        event.message(finalMessage);

        if (stage >= 4 && random.nextDouble() < 0.25) {
            String randomWord = words.length > 0 ? words[0].toLowerCase() : "";
            randomWord = randomWord.replaceAll("[^a-zA-Zа-яА-ЯёЁ]", "");

            if (randomWord.length() > 2) {
                String echoText = "*..." + randomWord + "...*";

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(
                                Component.text(echoText)
                                        .color(NamedTextColor.DARK_GRAY)
                                        .decoration(TextDecoration.ITALIC, true));
                    }
                }, 80L);
            }
        }
    }
}
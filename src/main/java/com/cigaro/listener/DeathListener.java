package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Random;

public class DeathListener implements Listener {
    private final Cigaro plugin;
    private final Random random = new Random();

    public DeathListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (player.getKiller() != null) return;

        if (player.getLastDamageCause() == null) return;
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause =
                player.getLastDamageCause().getCause();

        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON &&
                cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC &&
                cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.STARVATION &&
                cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM) {
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() == 4) {
            if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON ||
                    cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.MAGIC) {
                String[] keys = {
                        "death_catastrophic_1",
                        "death_catastrophic_2",
                        "death_catastrophic_3"
                };
                String key = keys[random.nextInt(keys.length)];
                event.deathMessage(plugin.getLanguageManager().get(key,
                        Placeholder.unparsed("player", player.getName())));
            } else {
                String[] keys = {
                        "death_severe_1",
                        "death_severe_2",
                        "death_severe_3",
                        "death_severe_4"
                };
                if (random.nextBoolean()) {
                    String key = keys[random.nextInt(keys.length)];
                    event.deathMessage(plugin.getLanguageManager().get(key,
                            Placeholder.unparsed("player", player.getName())));
                }
            }
        }
    }
}
package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.CigaroAchievement;
import com.cigaro.data.PlayerData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.Random;

public class InsomniaListener implements Listener {

    private final Cigaro plugin;
    private final Random random = new Random();

    private static final String[] INSOMNIA_4_KEYS = {
            "insomnia_severe_1", "insomnia_severe_2",
            "insomnia_severe_3", "insomnia_severe_4"
    };
    private static final String[] INSOMNIA_3_KEYS = {
            "insomnia_moderate_1", "insomnia_moderate_2",
            "insomnia_moderate_3"
    };

    public InsomniaListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager()
                .getData(player.getUniqueId());

        int stage = data.getPsychologicalStage();
        if (stage < 3) return;

        if (plugin.getWithdrawalTask().isInGracePeriod(data)) return;
        if (data.isPatchActive()) return;

        if (stage >= 4) {
            event.setCancelled(true);
            denyBed(player, stage);
            return;
        }

        if (random.nextDouble() < 0.5) {
            event.setCancelled(true);
            denyBed(player, stage);
        }
    }

    private void denyBed(Player player, int stage) {
        String[] pool = stage >= 4 ? INSOMNIA_4_KEYS : INSOMNIA_3_KEYS;
        String key = pool[random.nextInt(pool.length)];

        player.sendMessage(plugin.getLanguageManager().get(key));

        player.playSound(player.getLocation(),
                Sound.ENTITY_VILLAGER_NO, 0.3f, 0.5f);

        plugin.getAchievementManager()
                .grant(player, CigaroAchievement.INSOMNIA);

        if (stage >= 4) {
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        player.sendMessage(
                                plugin.getLanguageManager().get("insomnia_hint"));
                    }, 40L);
        }
    }
}
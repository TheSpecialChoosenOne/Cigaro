package com.cigaro.listener;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final Cigaro plugin;

    public PlayerListener(Cigaro plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        plugin.getPlayerDataManager().preload(event.getUniqueId());
    }

    @EventHandler
    public void onLoginDenied(PlayerConnectionValidateLoginEvent event) {
        if (event.isAllowed()) return;

        UUID uuid = null;
        if (event.getConnection() instanceof PlayerLoginConnection login) {
            uuid = login.getAuthenticatedProfile().getId();
        } else if (event.getConnection() instanceof PlayerConfigurationConnection configuration) {
            uuid = configuration.getProfile().getId();
        }

        if (uuid != null) {
            plugin.getPlayerDataManager().discardPreloaded(uuid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getData(
                event.getPlayer().getUniqueId()
        );
        plugin.getLungDamageManager().updateLungHealth(event.getPlayer(), data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        plugin.getSmokingListener().handleDisconnect(uuid);

        plugin.getWithdrawalEffectsManager().cleanup(uuid);

        plugin.getCoughManager().cleanup(uuid);

        plugin.getBreathingListener().cleanup(uuid);
        plugin.getPackingListener().cleanup(uuid);
        plugin.getHookahListener().cleanup(uuid);
        plugin.getPipeListener().cleanup(uuid);

        plugin.getWithdrawalTask().cleanup(uuid);

        plugin.getPlayerDataManager().unload(uuid);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() >= 2) {
            data.setLastSmokeTime(data.getLastSmokeTime() - 900000L);
            player.sendMessage(plugin.getLanguageManager().get("withdrawal_stress_death"));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);

            data.checkAndTriggerPhysicalStageUp(player);
            data.checkAndTriggerPsychologicalStageUp(player);

            plugin.getPlayerDataManager().save(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() >= 2) {
            if (player.getHealth() - event.getFinalDamage() <= 6.0 && player.getHealth() > 6.0) {
                data.setLastSmokeTime(data.getLastSmokeTime() - 300000L);
                player.sendMessage(plugin.getLanguageManager().get("withdrawal_stress_damage"));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.0f);

                data.checkAndTriggerPhysicalStageUp(player);
                data.checkAndTriggerPsychologicalStageUp(player);

                plugin.getPlayerDataManager().save(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player player = event.getEntity().getKiller();
        PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());

        if (data.getAddictionStage() >= 2) {
            org.bukkit.entity.Entity victim = event.getEntity();

            boolean isPeaceful = victim instanceof org.bukkit.entity.Animals
                    || victim instanceof org.bukkit.entity.Villager
                    || victim instanceof Player;

            if (isPeaceful) {
                data.setLastSmokeTime(data.getLastSmokeTime() - 300000L);
                player.sendMessage(plugin.getLanguageManager().get("withdrawal_stress_kill"));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.1f);

                data.checkAndTriggerPhysicalStageUp(player);
                data.checkAndTriggerPsychologicalStageUp(player);

                plugin.getPlayerDataManager().save(player.getUniqueId());
            }
        }
    }
}
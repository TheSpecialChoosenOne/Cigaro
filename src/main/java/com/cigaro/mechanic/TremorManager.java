package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

public class TremorManager {

    private final Cigaro plugin;
    private final Random random = new Random();
    private int taskId = -1;

    public TremorManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void start() {
        taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 40L, 40L)
                .getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerData data = plugin.getPlayerDataManager()
                    .getData(uuid);

            int stage = data.getAddictionStage();
            if (stage < 2) continue;

            if (plugin.getWithdrawalTask()
                    .isInGracePeriod(data)) continue;

            if (data.isPatchActive()) continue;

            if (plugin.getSmokingListener()
                    .isInAnimation(uuid)) continue;

            applyTremor(player, stage);
        }
    }

    private void applyTremor(Player player, int stage) {
        switch (stage) {
            case 2 -> {
                if (random.nextDouble() < 0.20) {
                    microShake(player, 0.3f, 0.2f);
                }
            }
            case 3 -> {
                if (random.nextDouble() < 0.35) {
                    microShake(player, 0.8f, 0.5f);
                }
                if (random.nextDouble() < 0.05) {
                    slowDrift(player, 2.0f, 1.5f);
                }
            }
            case 4 -> {
                if (random.nextDouble() < 0.50) {
                    microShake(player, 1.5f, 1.0f);
                }
                if (random.nextDouble() < 0.10) {
                    slowDrift(player, 4.0f, 3.0f);
                }
                if (random.nextDouble() < 0.03) {
                    jerk(player, 5.0f);
                }
            }
        }
    }

    private void rotateSmooth(Player player, float dYaw, float dPitch) {
        Location loc = player.getLocation();
        float newYaw = loc.getYaw() + dYaw;
        float newPitch = Math.max(-90f, Math.min(90f, loc.getPitch() + dPitch));
        player.setRotation(newYaw, newPitch);
    }

    private void microShake(Player player, float pitchRange, float yawRange) {
        float dp = (random.nextFloat() - 0.5f) * pitchRange;
        float dy = (random.nextFloat() - 0.5f) * yawRange;

        rotateSmooth(player, dy, dp);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    rotateSmooth(player, -dy * 0.8f, -dp * 0.8f);
                }, 2L);
    }

    private void slowDrift(Player player, float pitchMax, float yawMax) {
        float targetPitch = (random.nextFloat() - 0.5f) * pitchMax;
        float targetYaw = (random.nextFloat() - 0.5f) * yawMax;

        for (int i = 1; i <= 4; i++) {
            plugin.getServer().getScheduler()
                    .runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        rotateSmooth(player, targetYaw * 0.25f, targetPitch * 0.25f);
                    }, i * 2L);
        }
    }

    private void jerk(Player player, float intensity) {
        float dp = (random.nextFloat() - 0.5f) * intensity;
        float dy = (random.nextFloat() - 0.5f) * intensity;

        rotateSmooth(player, dy, dp);

        plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    rotateSmooth(player, -dy * 0.85f, -dp * 0.85f);
                }, 3L);
    }
}
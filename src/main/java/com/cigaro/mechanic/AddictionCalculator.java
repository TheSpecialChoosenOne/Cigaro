package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AddictionCalculator {

    private final Cigaro plugin;
    private final Random random = new Random();

    private static final long SESSION_MS = TimeUnit.HOURS.toMillis(2);

    public AddictionCalculator(Cigaro plugin) {
        this.plugin = plugin;
    }

    public void onSmoked(PlayerData data, CigaretteType type,
                         CigaretteQuality quality,
                         boolean wasInWithdrawal) {
        onSmoked(data, type, quality, wasInWithdrawal, 1.0);
    }

    public void onSmoked(PlayerData data, CigaretteType type,
                         CigaretteQuality quality,
                         boolean wasInWithdrawal,
                         double pointsMultiplier) {
        long now = System.currentTimeMillis();
        Player habitPlayer = Bukkit.getPlayer(data.getUuid());
        if (habitPlayer != null && habitPlayer.isOnline()) plugin.getHabitManager().recordSmoke(habitPlayer, data);

        if (now - data.getLastSessionStart() > SESSION_MS) {
            data.setLastSessionStart(now);
            data.setSmokedThisSession(1);
        } else {
            data.setSmokedThisSession(data.getSmokedThisSession() + 1);
        }

        java.time.LocalDate today = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();

        if (data.getLastDaySmoked() <= 0) {
            data.setConsecutiveDays(1);
        } else {
            java.time.LocalDate lastDay = java.time.Instant.ofEpochMilli(data.getLastDaySmoked())
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();

            if (today.equals(lastDay)) {
            } else if (today.equals(lastDay.plusDays(1))) {
                data.setConsecutiveDays(data.getConsecutiveDays() + 1);
            } else {
                data.setConsecutiveDays(1);
            }
        }
        data.setLastDaySmoked(now);

        String typeName = type.name();
        if (typeName.equals(data.getLastSmokedType())) {
            data.setSameTypeStreak(data.getSameTypeStreak() + 1);
        } else {
            data.setSameTypeStreak(1);
            data.setLastSmokedType(typeName);
        }

        double physicalPoints = calculatePhysicalPoints(data, quality) * pointsMultiplier;
        double psychologicalPoints = calculatePsychologicalPoints(data, type, wasInWithdrawal) * pointsMultiplier;

        double oldPhysical = data.getPhysicalScore(), oldPsychological = data.getPsychologicalScore();
        data.setPhysicalScore(data.getPhysicalScore() + physicalPoints);
        data.setPsychologicalScore(data.getPsychologicalScore() + psychologicalPoints);

        plugin.getServer().getPluginManager().callEvent(new com.cigaro.api.CigaroAddictionChangeEvent(
                data.getUuid(), oldPhysical, oldPsychological, data.getPhysicalScore(), data.getPsychologicalScore()));
        data.setPhysicalSoberSeconds(0);
        data.setPsychologicalSoberSeconds(0);

        Player player = Bukkit.getPlayer(data.getUuid());
        if (player != null && player.isOnline()) {
            data.checkAndTriggerPhysicalStageUp(player);
            data.checkAndTriggerPsychologicalStageUp(player);
        }
    }

    private double calculatePhysicalPoints(PlayerData data, CigaretteQuality quality) {
        double base = 1.0;

        double qualityMult = switch (quality) {
            case STANDARD -> 1.0;
            case BLUE -> 1.5;
            case GOLDEN -> 2.5;
            case VINTAGE -> 4.0;
        };

        int sessionCount = data.getSmokedThisSession();
        double frequencyMult = sessionCount <= 1 ? 1.0 : (sessionCount <= 3 ? 1.3 : 1.8);

        double randomVariance = 0.8 + random.nextDouble() * 0.4;

        return base * qualityMult * frequencyMult * randomVariance;
    }

    private double calculatePsychologicalPoints(PlayerData data, CigaretteType type, boolean wasInWithdrawal) {
        double base = 1.0;

        int pred = data.getPredisposition(type);
        double predMult = switch (pred) {
            case 2 -> 1.8;
            case 1 -> 1.3;
            case 0 -> 1.0;
            case -1 -> 0.6;
            case -2 -> 0.2;
            default -> 1.0;
        };

        double reinforcementMult = wasInWithdrawal ? 1.6 : 1.0;

        int sameStreak = data.getSameTypeStreak();
        double diversityMult = sameStreak >= 5 ? 1.2 : (sameStreak >= 3 ? 1.0 : 0.7);

        double randomVariance = 0.8 + random.nextDouble() * 0.4;

        return base * predMult * reinforcementMult * diversityMult * randomVariance;
    }
}
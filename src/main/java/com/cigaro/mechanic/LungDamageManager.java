package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.PlayerData;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

public class LungDamageManager {
    private final Cigaro plugin;
    private final NamespacedKey modifierKey;

    public LungDamageManager(Cigaro plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "lung_damage");
    }

    public double getBasePenalty(int totalSmoked) {
        if (totalSmoked >= plugin.getConfigManager().getLungDamageStage3()) return -6.0;
        if (totalSmoked >= plugin.getConfigManager().getLungDamageStage2()) return -4.0;
        if (totalSmoked >= plugin.getConfigManager().getLungDamageStage1()) return -2.0;
        return 0.0;
    }

    public double getPenalty(int totalSmoked, int recoveredHearts) {
        int hearts = Math.max(0, Math.min(3, recoveredHearts));
        return Math.min(0.0, getBasePenalty(totalSmoked) + hearts * 2.0);
    }

    public int getRecoverableHearts(int totalSmoked) {
        return (int) Math.max(0, -getBasePenalty(totalSmoked) / 2.0);
    }

    public void updateLungHealth(Player player, int totalSmoked) {
        updateLungHealth(player, totalSmoked, 0);
    }

    public void updateLungHealth(Player player, PlayerData data) {
        updateLungHealth(player, data.getTotalSmoked(), data.getLungRecoveredHearts());
    }

    public void updateLungHealth(Player player, int totalSmoked, int recoveredHearts) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double previousPenalty = maxHealth.getModifiers().stream()
                .filter(m -> modifierKey.equals(m.getKey()))
                .mapToDouble(AttributeModifier::getAmount)
                .findFirst().orElse(0.0);
        double previousMaxHealth = maxHealth.getValue();

        maxHealth.removeModifier(modifierKey);

        double healthPenalty = getPenalty(totalSmoked, recoveredHearts);
        if (healthPenalty < 0) {
            AttributeModifier modifier = new AttributeModifier(
                    modifierKey, healthPenalty, AttributeModifier.Operation.ADD_NUMBER
            );
            maxHealth.addModifier(modifier);
        }

        double newMaxHealth = maxHealth.getValue();
        if (newMaxHealth > previousMaxHealth && !player.isDead() && player.getHealth() > 0) {
            double restored = newMaxHealth - previousMaxHealth;
            player.setHealth(Math.min(newMaxHealth, player.getHealth() + restored));
        }

        if (healthPenalty < previousPenalty) {
            plugin.getAnalyticsManager().recordLungDamage();
        }
    }
}

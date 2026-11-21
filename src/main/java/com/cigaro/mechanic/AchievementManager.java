package com.cigaro.mechanic;

import com.cigaro.Cigaro;
import com.cigaro.data.CigaroAchievement;
import com.cigaro.data.PlayerData;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AchievementManager {

    private final Cigaro plugin;

    public AchievementManager(Cigaro plugin) {
        this.plugin = plugin;
    }

    public boolean grant(Player player, CigaroAchievement achievement) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getData(uuid);

        if (has(data, achievement)) return false;

        data.setAchievements(data.getAchievements() | achievement.getMask());
        plugin.getPlayerDataManager().save(uuid);

        notify(player, achievement);
        return true;
    }

    public boolean has(PlayerData data, CigaroAchievement achievement) {
        return (data.getAchievements() & achievement.getMask()) != 0;
    }

    public int count(PlayerData data) {
        int count = 0;
        for (CigaroAchievement a : CigaroAchievement.values()) {
            if (has(data, a)) count++;
        }
        return count;
    }

    public List<CigaroAchievement> getUnlocked(PlayerData data) {
        List<CigaroAchievement> list = new ArrayList<>();
        for (CigaroAchievement a : CigaroAchievement.values()) {
            if (has(data, a)) list.add(a);
        }
        return list;
    }

    private void notify(Player player, CigaroAchievement achievement) {
        String nameKey = "achievement." + achievement.name().toLowerCase() + ".name";
        String descKey = "achievement." + achievement.name().toLowerCase() + ".desc";

        String name = plugin.getLanguageManager().getRaw(nameKey);
        String desc = plugin.getLanguageManager().getRaw(descKey);

        if (name.equals(nameKey)) name = achievement.getDisplayName();
        if (desc.equals(descKey)) desc = achievement.getDescription();

        player.sendMessage(plugin.getLanguageManager().get("achievement_blank"));
        player.sendMessage(plugin.getLanguageManager().get("achievement_unlocked_header"));
        player.sendMessage(plugin.getLanguageManager().get("achievement_unlocked_name",
                Placeholder.unparsed("name", name)));
        player.sendMessage(plugin.getLanguageManager().get("achievement_unlocked_desc",
                Placeholder.unparsed("desc", desc)));
        player.sendMessage(plugin.getLanguageManager().get("achievement_blank"));

        player.playSound(player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
    }

    public void checkSmokingAchievements(Player player, PlayerData data) {
        if (data.getTotalSmoked() >= 1) {
            grant(player, CigaroAchievement.FIRST_DRAG);
        }
        if (data.getTotalSmoked() >= 50) {
            grant(player, CigaroAchievement.CHAIN_SMOKER);
        }
        if (data.getTotalSmoked() >= 100) {
            grant(player, CigaroAchievement.CHIMNEY);
        }
        if (data.getTotalSmoked() >= 500) {
            grant(player, CigaroAchievement.ASHTRAY);
        }

        boolean allTypes = true;
        for (com.cigaro.data.CigaretteType type : com.cigaro.data.CigaretteType.values()) {
            if (data.getSmoked(type) <= 0) {
                allTypes = false;
                break;
            }
        }
        if (allTypes) {
            grant(player, CigaroAchievement.CONNOISSEUR);
        }

        if (data.getMastery() >= 0.5) {
            grant(player, CigaroAchievement.MASTER_ROLLER);
        }
    }

    public void checkPredispositionAchievements(Player player, PlayerData data,
                                                com.cigaro.data.CigaretteType smokedType) {
        int pred = data.getPredisposition(smokedType);
        if (pred >= 2) {
            grant(player, CigaroAchievement.FAVORITE_FOUND);
        }
        if (pred <= -2) {
            grant(player, CigaroAchievement.BAD_CHOICE);
        }
    }

    public void checkAddictionAchievements(Player player, PlayerData data) {
        if (data.getAddictionStage() >= 2) {
            grant(player, CigaroAchievement.HOOKED);
        }
        if (data.getAddictionStage() >= 4) {
            grant(player, CigaroAchievement.ROCK_BOTTOM);
        }
        if (data.getAddictionStage() == 0 && data.wasAddicted()) {
            grant(player, CigaroAchievement.CLEAN);
        }
    }
}
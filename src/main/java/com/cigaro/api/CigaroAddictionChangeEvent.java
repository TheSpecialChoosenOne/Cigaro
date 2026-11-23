package com.cigaro.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public final class CigaroAddictionChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final double oldPhysical, oldPsychological, physical, psychological;
    public CigaroAddictionChangeEvent(UUID playerId, double oldPhysical, double oldPsychological,
                                      double physical, double psychological) {
        this.playerId = playerId; this.oldPhysical = oldPhysical; this.oldPsychological = oldPsychological;
        this.physical = physical; this.psychological = psychological;
    }
    public UUID getPlayerId() { return playerId; }
    public double getOldPhysical() { return oldPhysical; }
    public double getOldPsychological() { return oldPsychological; }
    public double getPhysical() { return physical; }
    public double getPsychological() { return psychological; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

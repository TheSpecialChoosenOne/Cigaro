package com.cigaro.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CigaroTobaccoEvent extends Event {
    public enum Action { PLANTED, GROWN, HARVESTED, CUT, BLENDED, CIGARETTES_CREATED, CIGAR_CREATED, CIGAR_SMOKED, PIPE_PUFF, HOOKAH_PUFF }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Action action;
    private final Player player;
    private final Location location;
    private final ItemStack result;
    public CigaroTobaccoEvent(Action action, @Nullable Player player, @Nullable Location location, @Nullable ItemStack result) {
        this.action = java.util.Objects.requireNonNull(action);
        this.player = player;
        this.location = location == null ? null : location.clone();
        this.result = result == null ? null : result.clone();
    }
    public Action getAction() { return action; }
    public @Nullable Player getPlayer() { return player; }
    public @Nullable Location getLocation() { return location == null ? null : location.clone(); }
    public @Nullable ItemStack getResult() { return result == null ? null : result.clone(); }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

package com.cigaro.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CigaroPuffEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ItemStack cigaretteItem;
    private final int puffsLeft;

    public CigaroPuffEvent(Player player, ItemStack cigaretteItem, int puffsLeft) {
        this.player = player;
        this.cigaretteItem = cigaretteItem;
        this.puffsLeft = puffsLeft;
    }

    public Player getPlayer() { return player; }
    public ItemStack getCigaretteItem() { return cigaretteItem; }
    public int getPuffsLeft() { return puffsLeft; }

    @NotNull
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

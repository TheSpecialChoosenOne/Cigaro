package com.cigaro.station;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

public final class StationBreakCheckEvent extends BlockBreakEvent {
    public StationBreakCheckEvent(Block block, Player player) {
        super(block, player);
    }
}

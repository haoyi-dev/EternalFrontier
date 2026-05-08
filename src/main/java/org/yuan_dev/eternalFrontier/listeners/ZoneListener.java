package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class ZoneListener implements Listener {

    private final EternalFrontier plugin;

    public ZoneListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
            && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        plugin.getZoneManager().onPlayerMove(e.getPlayer(), e.getTo());
    }
}

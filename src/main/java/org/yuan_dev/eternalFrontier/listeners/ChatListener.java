package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final EternalFrontier plugin;

    public ChatListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        var player = e.getPlayer();
        var uuid = player.getUniqueId();

        if (!plugin.getClanManager().isInClanChat(uuid)) return;
        if (!plugin.getClanManager().isInClan(uuid)) {
            plugin.getClanManager().toggleClanChat(uuid);
            return;
        }

        e.setCancelled(true);
        plugin.getClanManager().broadcastClanChat(uuid, e.getMessage());
    }
}

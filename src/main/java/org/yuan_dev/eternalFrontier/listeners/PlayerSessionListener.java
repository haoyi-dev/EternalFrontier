package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.quest.QuestManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerSessionListener implements Listener {

    private final EternalFrontier plugin;

    public PlayerSessionListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();

        plugin.getEconomyManager().loadPlayer(player).thenRun(() -> {

            plugin.getZoneManager().loadPlayer(player);

            plugin.getQuestManager().initPlayerQuests(player);

            plugin.getZoneManager().startBossbar(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        var uuid = e.getPlayer().getUniqueId();
        plugin.getZoneManager().stopBossbar(e.getPlayer());
        plugin.getZoneManager().savePlayer(uuid);
        plugin.getEconomyManager().unloadPlayer(uuid);
    }
}

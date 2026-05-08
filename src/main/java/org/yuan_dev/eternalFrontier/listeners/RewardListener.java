package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.yuan_dev.eternalFrontier.zone.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class RewardListener implements Listener {

    private final EternalFrontier plugin;

    public RewardListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) return;

        Zone zone = plugin.getZoneManager().getZone(killer.getLocation());
        long reward = switch (zone) {
            case ZONE1 -> plugin.getConfig().getLong("economy.mob-rewards.zone1", 1);
            case ZONE2 -> plugin.getConfig().getLong("economy.mob-rewards.zone2", 3);
            case ZONE3 -> plugin.getConfig().getLong("economy.mob-rewards.zone3", 5);
            default    -> 0;
        };

        if (reward <= 0) return;

        plugin.getEconomyManager().addCoins(killer.getUniqueId(), reward, "Giet mob tai " + zone.name())
            .thenRun(() -> {

                String msg = plugin.getMessageManager().get("economy.mob-kill-reward",
                    "amount", String.valueOf(reward));
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    killer.sendActionBar(MessageManager.component(msg))
                );
            });

        plugin.getQuestManager().trackKill(killer);
    }
}

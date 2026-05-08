package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.core.CoreData;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class ProtectionListener implements Listener {

    private final EternalFrontier plugin;

    public ProtectionListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        if (player.hasPermission("eternalfrontier.bypass.protection")) return;

        CoreData core = plugin.getCoreManager().getCoreAtLocation(e.getBlock().getLocation());
        if (core == null) return;

        if (plugin.getCoreManager().isFriendlyZone(player, e.getBlock().getLocation())) return;

        e.setCancelled(true);
        plugin.getMessageManager().send(player, "core.no-destroy");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player player = e.getPlayer();

        if (e.getBlock().getType() == Material.BEACON
            && plugin.getClanManager().isInClan(player.getUniqueId())
            && plugin.getCoreManager().getClanCore(player).isEmpty()) {
            plugin.getCoreManager().createCore(player, e.getBlock().getLocation())
                .thenAccept(success -> {
                    if (success) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getMessageManager().send(player, "core.created")
                        );
                    }
                });
            return;
        }

        if (player.hasPermission("eternalfrontier.bypass.protection")) return;

        CoreData core = plugin.getCoreManager().getCoreAtLocation(e.getBlock().getLocation());
        if (core == null) return;

        if (plugin.getCoreManager().isFriendlyZone(player, e.getBlock().getLocation())) return;

        e.setCancelled(true);
        plugin.getMessageManager().send(player, "core.no-build");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;

        if (attacker.hasPermission("eternalfrontier.bypass.pvp")) return;

        CoreData core = plugin.getCoreManager().getCoreAtLocation(victim.getLocation());
        if (core == null) {

            core = plugin.getCoreManager().getCoreAtLocation(attacker.getLocation());
        }
        if (core == null) return;

        boolean attackerFriendly = plugin.getCoreManager().isFriendlyZone(attacker, victim.getLocation());
        boolean victimFriendly   = plugin.getCoreManager().isFriendlyZone(victim, victim.getLocation());

        if (victimFriendly && attackerFriendly) {
            e.setCancelled(true);
            plugin.getMessageManager().send(attacker, "core.no-pvp");
            return;
        }

        if (!victimFriendly) {
            plugin.getCoreManager().damageCore(core.clanName(), plugin.getCoreManager().getPvpDamagePerHit(), attacker);
            attacker.sendActionBar(MessageManager.component(
                plugin.getMessageManager().get("core.damage-dealt",
                    "damage", String.valueOf(plugin.getCoreManager().getPvpDamagePerHit()),
                    "remaining", String.valueOf(Math.max(0, core.hp() - plugin.getCoreManager().getPvpDamagePerHit())))
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(block ->
            plugin.getCoreManager().getCoreAtLocation(block.getLocation()) != null
        );
    }

}

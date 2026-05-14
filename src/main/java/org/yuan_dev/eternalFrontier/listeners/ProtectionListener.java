package org.yuan_dev.eternalFrontier.listeners;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.core.CoreData;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionListener implements Listener {

    private final EternalFrontier plugin;
    private final Map<UUID, BossBar> coreBars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> coreBarTasks = new ConcurrentHashMap<>();

    public ProtectionListener(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();

        if (e.getBlock().getType() == Material.BEACON) {
            CoreData exactCore = plugin.getCoreManager().getCoreByExactBlock(e.getBlock().getLocation());
            if (exactCore != null) {
                e.setCancelled(true);

                if (player.hasPermission("eternalfrontier.bypass.protection")) return;

                if (plugin.getCoreManager().isFriendlyZone(player, e.getBlock().getLocation())) {
                    plugin.getMessageManager().send(player, "core.no-destroy");
                    return;
                }

                int damage = plugin.getCoreManager().getPvpDamagePerHit();
                plugin.getCoreManager().damageCore(exactCore.clanName(), damage, player)
                    .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!success) return;

                        CoreData updated = plugin.getCoreManager().getCoreByClan(exactCore.clanName());
                        if (updated == null) return;

                        showCoreHpBar(player, updated);

                        var clan = plugin.getClanManager().getClanByName(updated.clanName());
                        if (clan != null) {
                            clan.members().keySet().forEach(uuid -> {
                                Player member = plugin.getServer().getPlayer(uuid);
                                if (member != null && member.isOnline()) {
                                    showCoreHpBar(member, updated);
                                }
                            });
                        }

                        player.sendActionBar(MessageManager.component(
                            "&cCore -" + damage + " HP &7(" + updated.hp() + "/" + updated.maxHp() + ")"
                        ));
                    }));
                return;
            }
        }

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
            && plugin.getClanManager().isInClan(player.getUniqueId())) {

            if (plugin.getCoreManager().getClanCore(player).isEmpty()) {
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

            var oldCore = plugin.getCoreManager().getClanCore(player).orElse(null);
            plugin.getCoreManager().relocateCore(player, e.getBlock().getLocation())
                .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!success) return;

                    if (oldCore != null) {
                        var oldLoc = oldCore.toLocation();
                        if (oldLoc != null && oldLoc.getBlock().getType() == Material.BEACON) {
                            oldLoc.getBlock().setType(Material.AIR);
                        }
                    }

                    player.sendMessage(org.yuan_dev.eternalFrontier.utils.MessageManager.colorize(
                        "&aDa di doi Clan Core den vi tri moi: &f"
                            + e.getBlock().getX() + ", " + e.getBlock().getY() + ", " + e.getBlock().getZ()
                    ));
                }));
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
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(block ->
            plugin.getCoreManager().getCoreAtLocation(block.getLocation()) != null
        );
    }

    private void showCoreHpBar(Player viewer, CoreData core) {
        String leaderName = "Unknown";
        var clan = plugin.getClanManager().getClanByName(core.clanName());
        if (clan != null) {
            String name = plugin.getServer().getOfflinePlayer(clan.leaderUuid()).getName();
            if (name != null && !name.isBlank()) leaderName = name;
        }

        double progress = Math.max(0.0, Math.min(1.0, core.maxHp() <= 0 ? 0.0 : (double) core.hp() / core.maxHp()));
        String title = MessageManager.colorize(
            "&c❤ Core &7| &6" + core.clanName() + " &7| &fChu clan: &e" + leaderName +
            " &7| &c" + core.hp() + "/" + core.maxHp()
        );

        BossBar bar = coreBars.computeIfAbsent(viewer.getUniqueId(),
            id -> Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID));
        bar.setTitle(title);
        bar.setProgress(progress);
        bar.addPlayer(viewer);
        bar.setVisible(true);

        Integer oldTask = coreBarTasks.remove(viewer.getUniqueId());
        if (oldTask != null) Bukkit.getScheduler().cancelTask(oldTask);

        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (!plugin.isEnabled()) return;
            BossBar existing = coreBars.remove(viewer.getUniqueId());
            if (existing != null) {
                existing.removeAll();
                existing.setVisible(false);
            }
            coreBarTasks.remove(viewer.getUniqueId());
        }, 60L);
        coreBarTasks.put(viewer.getUniqueId(), taskId);
    }

}

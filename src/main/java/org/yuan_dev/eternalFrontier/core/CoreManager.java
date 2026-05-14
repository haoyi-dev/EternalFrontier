package org.yuan_dev.eternalFrontier.core;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.clan.ClanData;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CoreManager {

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<String, CoreData> coreCache = new ConcurrentHashMap<>();

    private final int protectionRadius;
    private final int maxHp;
    private final int pvpDamage;

    private BukkitTask particleTask;
    private BukkitTask repairTask;

    public CoreManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.protectionRadius = plugin.getConfig().getInt("core.protection-radius", 75);
        this.maxHp = plugin.getConfig().getInt("core.max-hp", 25000);
        this.pvpDamage = plugin.getConfig().getInt("core.pvp-damage-per-hit", 35);
        loadAllCores();
        startParticleTask();
        startRepairTask();
    }

    public void reload() {
        loadAllCores();
    }

    public void shutdown() {
        if (particleTask != null) particleTask.cancel();
        if (repairTask != null) repairTask.cancel();
    }

    private void loadAllCores() {
        db.runAsync(() -> {
            try {
                String sql = "SELECT * FROM cores";
                try (PreparedStatement ps = db.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        CoreData core = new CoreData(
                            rs.getInt("id"),
                            rs.getString("clan_name"),
                            rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getInt("hp"), rs.getInt("max_hp"),
                            rs.getInt("upgrade_iron"), rs.getInt("upgrade_drone"),
                            rs.getInt("upgrade_repair"), rs.getInt("upgrade_beacon")
                        );
                        coreCache.put(core.clanName().toLowerCase(), core);
                    }
                }
                plugin.getLogger().info("Da tai " + coreCache.size() + " clan core.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tai danh sach core!", e);
            }
        });
    }

    public CompletableFuture<Boolean> createCore(Player player, Location loc) {
        return db.supplyAsync(() -> {
            ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null) {
                plugin.getMessageManager().send(player, "clan.not-in-clan");
                return false;
            }
            if (coreCache.containsKey(clan.name().toLowerCase())) {
                plugin.getMessageManager().send(player, "core.already-exists");
                return false;
            }

            try {
                String sql = """
                    INSERT INTO cores (clan_name, world, x, y, z, hp, max_hp)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
                try (PreparedStatement ps = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, clan.name());
                    ps.setString(2, loc.getWorld().getName());
                    ps.setDouble(3, loc.getX());
                    ps.setDouble(4, loc.getY());
                    ps.setDouble(5, loc.getZ());
                    ps.setInt(6, maxHp);
                    ps.setInt(7, maxHp);
                    ps.executeUpdate();

                    ResultSet keys = ps.getGeneratedKeys();
                    if (!keys.next()) return false;
                    int coreId = keys.getInt(1);

                    CoreData core = new CoreData(coreId, clan.name(),
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                        maxHp, maxHp, 0, 0, 0, 0);
                    coreCache.put(clan.name().toLowerCase(), core);
                }
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tao core", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> relocateCore(Player player, Location newLoc) {
        return db.supplyAsync(() -> {
            ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null) return false;

            CoreData current = coreCache.get(clan.name().toLowerCase());
            if (current == null) return false;

            try {
                String sql = "UPDATE cores SET world = ?, x = ?, y = ?, z = ? WHERE clan_name = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setString(1, newLoc.getWorld().getName());
                    ps.setDouble(2, newLoc.getX());
                    ps.setDouble(3, newLoc.getY());
                    ps.setDouble(4, newLoc.getZ());
                    ps.setString(5, clan.name());
                    int updatedRows = ps.executeUpdate();
                    if (updatedRows <= 0) return false;
                }

                CoreData updated = new CoreData(
                    current.id(),
                    current.clanName(),
                    newLoc.getWorld().getName(),
                    newLoc.getX(), newLoc.getY(), newLoc.getZ(),
                    current.hp(), current.maxHp(),
                    current.upgradeIron(), current.upgradeDrone(),
                    current.upgradeRepair(), current.upgradeBeacon()
                );
                coreCache.put(clan.name().toLowerCase(), updated);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the di doi core cua clan " + clan.name(), e);
                return false;
            }
        });
    }

    public void removeCoreForClan(String clanName) {
        coreCache.remove(clanName.toLowerCase());
        db.runAsync(() -> {
            try {
                String sql = "DELETE FROM cores WHERE clan_name = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setString(1, clanName);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the xoa core cua clan " + clanName, e);
            }
        });
    }

    public CompletableFuture<Boolean> damageCore(String clanName, int damage, Player attacker) {
        return db.supplyAsync(() -> {
            CoreData core = coreCache.get(clanName.toLowerCase());
            if (core == null || !core.isAlive()) return false;

            int actualDamage = (int) (damage * (1.0 - core.upgradeIron() * 0.2));
            int newHp = Math.max(0, core.hp() - actualDamage);
            CoreData updated = core.withHp(newHp);
            coreCache.put(clanName.toLowerCase(), updated);

            try {
                String sql = "UPDATE cores SET hp = ? WHERE clan_name = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setInt(1, newHp);
                    ps.setString(2, clanName);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the cap nhat HP core", e);
            }

            ClanData clan = plugin.getClanManager().getClanByName(clanName);
            if (clan != null) {
                String msg = MessageManager.colorize(
                    plugin.getMessageManager().get("core.under-attack",
                        "attacker", attacker.getName())
                );
                clan.members().keySet().forEach(uuid -> {
                    Player m = plugin.getServer().getPlayer(uuid);
                    if (m != null) m.sendMessage(msg);
                });
            }

            if (newHp <= 0) {
                onCoreDestroyed(clanName, attacker);
            }

            return true;
        });
    }

    private void onCoreDestroyed(String clanName, Player destroyer) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            String msg = MessageManager.colorize(
                plugin.getMessageManager().get("core.destroyed",
                    "clan", clanName, "attacker", destroyer.getName())
            );
            plugin.getServer().broadcast(MessageManager.component(msg));
            removeCoreForClan(clanName);
        });
    }

    public boolean isInsideProtection(Location loc) {
        for (CoreData core : coreCache.values()) {
            if (!core.isAlive()) continue;
            Location coreLoc = core.toLocation();
            if (coreLoc == null) continue;
            if (!coreLoc.getWorld().equals(loc.getWorld())) continue;
            if (coreLoc.distance(loc) <= protectionRadius) return true;
        }
        return false;
    }

    public CoreData getCoreAtLocation(Location loc) {
        for (CoreData core : coreCache.values()) {
            if (!core.isAlive()) continue;
            Location coreLoc = core.toLocation();
            if (coreLoc == null) continue;
            if (!coreLoc.getWorld().equals(loc.getWorld())) continue;
            if (coreLoc.distance(loc) <= protectionRadius) return core;
        }
        return null;
    }

    public CoreData getCoreByExactBlock(Location loc) {
        for (CoreData core : coreCache.values()) {
            if (!core.isAlive()) continue;
            Location coreLoc = core.toLocation();
            if (coreLoc == null) continue;
            if (!coreLoc.getWorld().equals(loc.getWorld())) continue;
            if (coreLoc.getBlockX() == loc.getBlockX()
                && coreLoc.getBlockY() == loc.getBlockY()
                && coreLoc.getBlockZ() == loc.getBlockZ()) {
                return core;
            }
        }
        return null;
    }

    public boolean isFriendlyZone(Player player, Location loc) {
        CoreData core = getCoreAtLocation(loc);
        if (core == null) return false;
        ClanData playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (playerClan == null) return false;
        return playerClan.name().equalsIgnoreCase(core.clanName());
    }

    private void startParticleTask() {
        long interval = plugin.getConfig().getLong("core.particle-interval", 60);
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                coreCache.values().forEach(core -> {
                    if (!core.isAlive()) return;
                    Location center = core.toLocation();
                    if (center == null) return;

                    int radius = protectionRadius;
                    for (double angle = 0; angle < Math.PI * 2; angle += 0.3) {
                        double px = center.getX() + radius * Math.cos(angle);
                        double pz = center.getZ() + radius * Math.sin(angle);
                        Location pLoc = new Location(center.getWorld(), px, center.getY() + 1, pz);
                        center.getWorld().spawnParticle(Particle.DUST,
                            pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(
                                core.getHpPercent() > 0.5
                                    ? org.bukkit.Color.fromRGB(0, 255, 100)
                                    : org.bukkit.Color.fromRGB(255, 50, 50),
                                1.5f
                            )
                        );
                    }
                });
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void startRepairTask() {
        repairTask = new BukkitRunnable() {
            @Override
            public void run() {
                coreCache.forEach((name, core) -> {
                    if (!core.isAlive() || core.upgradeRepair() <= 0) return;
                    int repairAmount = 100 * core.upgradeRepair();
                    int newHp = Math.min(core.maxHp(), core.hp() + repairAmount);
                    if (newHp == core.hp()) return;

                    CoreData updated = core.withHp(newHp);
                    coreCache.put(name, updated);
                    db.runAsync(() -> {
                        try {
                            String sql = "UPDATE cores SET hp = ? WHERE clan_name = ?";
                            try (PreparedStatement ps = db.prepareStatement(sql)) {
                                ps.setInt(1, newHp);
                                ps.setString(2, core.clanName());
                                ps.executeUpdate();
                            }
                        } catch (SQLException ignored) {}
                    });
                });
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    public CoreData getCoreByClan(String clanName) {
        return coreCache.get(clanName.toLowerCase());
    }

    public Optional<CoreData> getClanCore(Player player) {
        ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return Optional.empty();
        return Optional.ofNullable(coreCache.get(clan.name().toLowerCase()));
    }

    public Collection<CoreData> getAllCores() {
        return Collections.unmodifiableCollection(coreCache.values());
    }

    public int getProtectionRadius() { return protectionRadius; }
    public int getPvpDamagePerHit()  { return pvpDamage; }
}

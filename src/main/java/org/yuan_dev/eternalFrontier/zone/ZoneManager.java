package org.yuan_dev.eternalFrontier.zone;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ZoneManager {

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<UUID, Zone> playerZones   = new ConcurrentHashMap<>();

    private final Map<UUID, ZoneStats> statsCache = new ConcurrentHashMap<>();

    private final Map<UUID, BossBar> bossbars    = new ConcurrentHashMap<>();

    private final Map<UUID, Long> zoneEntryTimes = new ConcurrentHashMap<>();

    private double zone1MaxRadius;
    private double zone2MaxRadius;
    private List<String> activeWorlds;

    private BukkitTask hourlyTask;
    private BukkitTask bossbarTask;

    public ZoneManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        loadConfig();
        startHourlyRewards();
        startBossbarUpdater();
    }

    public void reload() {
        loadConfig();
    }

    public void shutdown() {
        if (hourlyTask != null) hourlyTask.cancel();
        if (bossbarTask != null) bossbarTask.cancel();

        plugin.getServer().getOnlinePlayers().forEach(p -> savePlayer(p.getUniqueId()));
    }

    private void loadConfig() {
        zone1MaxRadius = plugin.getConfig().getDouble("zone.zone1.max-radius", 2000);
        zone2MaxRadius = plugin.getConfig().getDouble("zone.zone2.max-radius", 4000);
        activeWorlds   = plugin.getConfig().getStringList("zone.worlds");
    }

    public Zone getZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return Zone.SPAWN;
        if (!activeWorlds.contains(loc.getWorld().getName())) return Zone.SPAWN;

        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());

        if (dist <= 50)             return Zone.SPAWN;
        if (dist <= zone1MaxRadius) return Zone.ZONE1;
        if (dist <= zone2MaxRadius) return Zone.ZONE2;
        return Zone.ZONE3;
    }

    public double getDistanceToNextZone(Location loc) {
        if (loc == null) return 0;
        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        Zone current = getZone(loc);
        return switch (current) {
            case SPAWN -> zone1MaxRadius - dist;
            case ZONE1 -> zone2MaxRadius - dist;
            case ZONE2, ZONE3 -> 0;
        };
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        db.runAsync(() -> {
            try {
                String upsert = """
                    MERGE INTO zone_stats (uuid)
                    KEY (uuid)
                    VALUES (?)
                """;
                try (PreparedStatement ps = db.prepareStatement(upsert)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }

                String select = """
                    SELECT zone1_playtime, zone2_playtime, zone3_playtime, highest_zone
                    FROM zone_stats WHERE uuid = ?
                """;
                try (PreparedStatement ps = db.prepareStatement(select)) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        statsCache.put(uuid, new ZoneStats(
                            rs.getLong("zone1_playtime"),
                            rs.getLong("zone2_playtime"),
                            rs.getLong("zone3_playtime"),
                            rs.getInt("highest_zone")
                        ));
                    }
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Zone zone = getZone(player.getLocation());
                    playerZones.put(uuid, zone);
                    zoneEntryTimes.put(uuid, System.currentTimeMillis());
                });

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the tai thong ke zone: " + player.getName(), e);
            }
        });
    }

    public void savePlayer(UUID uuid) {
        ZoneStats stats = statsCache.get(uuid);
        if (stats == null) return;

        Long entryTime = zoneEntryTimes.get(uuid);
        Zone currentZone = playerZones.getOrDefault(uuid, Zone.SPAWN);
        if (entryTime != null) {
            long elapsed = (System.currentTimeMillis() - entryTime) / 1000;
            stats = stats.withAdded(currentZone, elapsed);
        }

        final ZoneStats finalStats = stats;
        db.runAsync(() -> {
            try {
                String sql = """
                    UPDATE zone_stats
                    SET zone1_playtime = ?, zone2_playtime = ?, zone3_playtime = ?,
                        highest_zone = ?
                    WHERE uuid = ?
                """;
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setLong(1, finalStats.zone1Time());
                    ps.setLong(2, finalStats.zone2Time());
                    ps.setLong(3, finalStats.zone3Time());
                    ps.setInt(4, finalStats.highestZone());
                    ps.setString(5, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the luu thong ke zone: " + uuid, e);
            }
        });
    }

    public void onPlayerMove(Player player, Location newLoc) {
        UUID uuid = player.getUniqueId();
        Zone oldZone = playerZones.getOrDefault(uuid, Zone.SPAWN);
        Zone newZone = getZone(newLoc);

        if (oldZone == newZone) return;

        Long entryTime = zoneEntryTimes.get(uuid);
        if (entryTime != null) {
            long elapsed = (System.currentTimeMillis() - entryTime) / 1000;
            statsCache.computeIfPresent(uuid, (k, s) -> s.withAdded(oldZone, elapsed));
        }

        playerZones.put(uuid, newZone);
        zoneEntryTimes.put(uuid, System.currentTimeMillis());

        statsCache.computeIfPresent(uuid, (k, s) ->
            newZone.getNumber() > s.highestZone() ? s.withHighest(newZone.getNumber()) : s
        );

        String msg = plugin.getMessageManager().get("zone.entered",
            "color", newZone.getColor(),
            "zone", plugin.getMessageManager().get("zone." + newZone.name().toLowerCase() + "-name"),
            "reward", String.valueOf(getHourlyReward(newZone))
        );
        player.sendMessage(MessageManager.colorize(msg));
    }

    private void startHourlyRewards() {
        long intervalTicks = 72000L;
        hourlyTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getOnlinePlayers().forEach(player -> {
                    Zone zone = playerZones.getOrDefault(player.getUniqueId(), Zone.SPAWN);
                    long reward = getHourlyReward(zone);
                    if (reward <= 0) return;

                    reward = applyBeaconBonus(player, reward);

                    final long finalReward = reward;
                    plugin.getEconomyManager().addCoins(player.getUniqueId(), finalReward, "Thuong zone hang gio")
                        .thenRun(() -> {
                            String msg = plugin.getMessageManager().get("economy.hourly-reward",
                                "amount", String.valueOf(finalReward));
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                player.sendMessage(MessageManager.colorize(msg))
                            );
                        });
                });
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private long getHourlyReward(Zone zone) {
        return switch (zone) {
            case ZONE1 -> plugin.getConfig().getLong("economy.hourly-rewards.zone1", 1);
            case ZONE2 -> plugin.getConfig().getLong("economy.hourly-rewards.zone2", 3);
            case ZONE3 -> plugin.getConfig().getLong("economy.hourly-rewards.zone3", 5);
            default    -> 0;
        };
    }

    private long applyBeaconBonus(Player player, long base) {

        var coreOpt = plugin.getCoreManager().getClanCore(player);
        if (coreOpt.isEmpty()) return base;
        int beaconLevel = coreOpt.get().upgradeBeacon();
        return (long) (base * (1.0 + beaconLevel * 0.1));
    }

    public void startBossbar(Player player) {
        BossBar bar = BossBar.bossBar(
            Component.text(""),
            1.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bar);
        bossbars.put(player.getUniqueId(), bar);
        updateBossbar(player);
    }

    public void stopBossbar(Player player) {
        BossBar bar = bossbars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void startBossbarUpdater() {
        long interval = plugin.getConfig().getLong("zone.bossbar-update-interval", 20);
        bossbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getOnlinePlayers().forEach(ZoneManager.this::updateBossbar);
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void updateBossbar(Player player) {
        BossBar bar = bossbars.get(player.getUniqueId());
        if (bar == null) return;

        Zone zone = getZone(player.getLocation());
        double dist = Math.sqrt(
            player.getLocation().getX() * player.getLocation().getX() +
            player.getLocation().getZ() * player.getLocation().getZ()
        );

        String text;
        float progress;
        BossBar.Color color;

        switch (zone) {
            case SPAWN -> {
                text = plugin.getMessageManager().get("zone.bossbar-spawn",
                    "dist", String.format("%.0f", Math.max(0, zone1MaxRadius - dist)));
                progress = (float) Math.max(0, Math.min(1, 1 - (dist / zone1MaxRadius)));
                color = BossBar.Color.WHITE;
            }
            case ZONE1 -> {
                text = plugin.getMessageManager().get("zone.bossbar-zone1",
                    "reward", String.valueOf(getHourlyReward(zone)));
                progress = (float) Math.max(0, Math.min(1, 1 - ((dist - 50) / (zone1MaxRadius - 50))));
                color = BossBar.Color.GREEN;
            }
            case ZONE2 -> {
                text = plugin.getMessageManager().get("zone.bossbar-zone2",
                    "dist", String.format("%.0f", Math.max(0, zone2MaxRadius - dist)),
                    "reward", String.valueOf(getHourlyReward(zone)));
                progress = (float) Math.max(0, Math.min(1, 1 - ((dist - zone1MaxRadius) / (zone2MaxRadius - zone1MaxRadius))));
                color = BossBar.Color.YELLOW;
            }
            default -> {
                text = plugin.getMessageManager().get("zone.bossbar-zone3",
                    "reward", String.valueOf(getHourlyReward(zone)));
                progress = 1.0f;
                color = BossBar.Color.RED;
            }
        }

        bar.name(MessageManager.component(text));
        bar.progress(progress);
        bar.color(color);
    }

    public Zone getPlayerZone(UUID uuid) {
        return playerZones.getOrDefault(uuid, Zone.SPAWN);
    }

    public ZoneStats getStats(UUID uuid) {
        return statsCache.getOrDefault(uuid, new ZoneStats(0, 0, 0, 1));
    }

    public record ZoneStats(long zone1Time, long zone2Time, long zone3Time, int highestZone) {

        public ZoneStats withAdded(Zone zone, long seconds) {
            return switch (zone) {
                case ZONE1 -> new ZoneStats(zone1Time + seconds, zone2Time, zone3Time, highestZone);
                case ZONE2 -> new ZoneStats(zone1Time, zone2Time + seconds, zone3Time, highestZone);
                case ZONE3 -> new ZoneStats(zone1Time, zone2Time, zone3Time + seconds, highestZone);
                default    -> this;
            };
        }

        public ZoneStats withHighest(int zone) {
            return zone > highestZone ? new ZoneStats(zone1Time, zone2Time, zone3Time, zone) : this;
        }

        public long getTime(Zone zone) {
            return switch (zone) {
                case ZONE1 -> zone1Time;
                case ZONE2 -> zone2Time;
                case ZONE3 -> zone3Time;
                default    -> 0;
            };
        }
    }
}

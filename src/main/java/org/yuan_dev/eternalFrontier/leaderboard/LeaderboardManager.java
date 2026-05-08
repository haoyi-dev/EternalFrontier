package org.yuan_dev.eternalFrontier.leaderboard;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LeaderboardManager {

    public enum LeaderboardType { ECO, ZONE, CLAN, CORE }

    public record LeaderboardEntry(int rank, String uuid, String name, long value) {}

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<LeaderboardType, List<LeaderboardEntry>> cache = new ConcurrentHashMap<>();
    private BukkitTask updateTask;

    public LeaderboardManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        for (LeaderboardType t : LeaderboardType.values()) cache.put(t, new ArrayList<>());
        startUpdateTask();
        refreshAll();
    }

    public void reload() {
        refreshAll();
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
    }

    private void startUpdateTask() {
        long interval = plugin.getConfig().getLong("leaderboard.update-interval", 6000);
        updateTask = new BukkitRunnable() {
            @Override public void run() { refreshAll(); }
        }.runTaskTimerAsynchronously(plugin, interval, interval);
    }

    public void refreshAll() {
        db.runAsync(() -> {
            refreshEco();
            refreshZone();
            refreshClan();
        });
    }

    private void refreshEco() {
        try {
            int limit = plugin.getConfig().getInt("leaderboard.top-size", 10);
            String sql = "SELECT uuid, username, total_earned FROM players ORDER BY total_earned DESC LIMIT ?";
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                int rank = 1;
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(rank++,
                        rs.getString("uuid"), rs.getString("username"), rs.getLong("total_earned")));
                }
            }
            cache.put(LeaderboardType.ECO, entries);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Khong the lam moi bang xep hang Kinh Te", e);
        }
    }

    private void refreshZone() {
        try {
            int limit = plugin.getConfig().getInt("leaderboard.top-size", 10);
            String sql = """
                SELECT zs.uuid, p.username,
                       (zs.zone1_playtime + zs.zone2_playtime + zs.zone3_playtime) AS total_time
                FROM zone_stats zs
                JOIN players p ON p.uuid = zs.uuid
                ORDER BY total_time DESC LIMIT ?
            """;
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                int rank = 1;
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(rank++,
                        rs.getString("uuid"), rs.getString("username"), rs.getLong("total_time")));
                }
            }
            cache.put(LeaderboardType.ZONE, entries);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Khong the lam moi bang xep hang Zone", e);
        }
    }

    private void refreshClan() {
        try {
            int limit = plugin.getConfig().getInt("leaderboard.top-size", 10);
            String sql = "SELECT name, power FROM clans ORDER BY power DESC LIMIT ?";
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                int rank = 1;
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(rank++,
                        "", rs.getString("name"), rs.getLong("power")));
                }
            }
            cache.put(LeaderboardType.CLAN, entries);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Khong the lam moi bang xep hang Clan", e);
        }
    }

    public List<LeaderboardEntry> getTop(LeaderboardType type) {
        return Collections.unmodifiableList(cache.getOrDefault(type, new ArrayList<>()));
    }

    public int getRank(LeaderboardType type, String uuid) {
        List<LeaderboardEntry> entries = cache.getOrDefault(type, new ArrayList<>());
        for (LeaderboardEntry e : entries) {
            if (e.uuid().equals(uuid)) return e.rank();
        }
        return -1;
    }
}

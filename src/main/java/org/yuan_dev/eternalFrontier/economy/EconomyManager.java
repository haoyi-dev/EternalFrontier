package org.yuan_dev.eternalFrontier.economy;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class EconomyManager {

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();

    private final Map<UUID, List<TransactionRecord>> txCache = new ConcurrentHashMap<>();

    private final long startingBalance;
    private final long maxBalance;
    private final int historySize;

    public EconomyManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.startingBalance = plugin.getConfig().getLong("economy.starting-balance", 100);
        this.maxBalance = plugin.getConfig().getLong("economy.max-balance", 999_999_999L);
        this.historySize = plugin.getConfig().getInt("economy.transaction-history-size", 5);
    }

    public CompletableFuture<Void> loadPlayer(Player player) {
        return db.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            try {

                String upsert = """
                    MERGE INTO players (uuid, username, coins, total_earned)
                    KEY (uuid)
                    VALUES (?, ?, ?, ?)
                """;
                try (PreparedStatement ps = db.prepareStatement(upsert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, player.getName());
                    ps.setLong(3, startingBalance);
                    ps.setLong(4, 0);
                    ps.executeUpdate();
                }

                String select = "SELECT coins FROM players WHERE uuid = ?";
                try (PreparedStatement ps = db.prepareStatement(select)) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        balanceCache.put(uuid, rs.getLong("coins"));
                    }
                }

                String update = "UPDATE players SET username = ?, last_seen = CURRENT_TIMESTAMP WHERE uuid = ?";
                try (PreparedStatement ps = db.prepareStatement(update)) {
                    ps.setString(1, player.getName());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

                loadTransactionHistory(uuid);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tai du lieu kinh te nguoi choi: " + player.getName(), e);
            }
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        return db.runAsync(() -> {
            Long balance = balanceCache.get(uuid);
            if (balance == null) return;
            try {
                String sql = "UPDATE players SET coins = ?, last_seen = CURRENT_TIMESTAMP WHERE uuid = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setLong(1, balance);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the luu so du nguoi choi: " + uuid, e);
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        balanceCache.remove(uuid);
        txCache.remove(uuid);
    }

    public long getBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0L);
    }

    public CompletableFuture<Long> getBalanceAsync(UUID uuid) {
        if (balanceCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(balanceCache.get(uuid));
        }
        return db.supplyAsync(() -> {
            try {
                String sql = "SELECT coins FROM players WHERE uuid = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) return rs.getLong("coins");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the lay so du bat dong bo", e);
            }
            return 0L;
        });
    }

    public boolean hasEnough(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public CompletableFuture<Boolean> addCoins(UUID uuid, long amount, String reason) {
        return db.supplyAsync(() -> {
            try {
                long current = balanceCache.getOrDefault(uuid, fetchFromDB(uuid));
                long newBalance = Math.min(current + amount, maxBalance);
                long actualAdded = newBalance - current;
                if (actualAdded <= 0) return false;

                balanceCache.put(uuid, newBalance);
                persistBalance(uuid, newBalance);
                recordTransaction(uuid, actualAdded, "CREDIT", reason);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "addCoins failed for " + uuid, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> removeCoins(UUID uuid, long amount, String reason) {
        return db.supplyAsync(() -> {
            try {
                long current = balanceCache.getOrDefault(uuid, fetchFromDB(uuid));
                if (current < amount) return false;

                long newBalance = current - amount;
                balanceCache.put(uuid, newBalance);
                persistBalance(uuid, newBalance);
                recordTransaction(uuid, amount, "DEBIT", reason);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "removeCoins failed for " + uuid, e);
                return false;
            }
        });
    }

    public CompletableFuture<Void> setCoins(UUID uuid, long amount) {
        return db.runAsync(() -> {
            try {
                long clamped = Math.min(Math.max(0, amount), maxBalance);
                balanceCache.put(uuid, clamped);
                persistBalance(uuid, clamped);
                recordTransaction(uuid, clamped, "SET", "Admin dat so du");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "setCoins failed for " + uuid, e);
            }
        });
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount, String reason) {
        return db.supplyAsync(() -> {
            try {
                long fromBal = balanceCache.getOrDefault(from, fetchFromDB(from));
                if (fromBal < amount) return false;

                long toBal = balanceCache.getOrDefault(to, fetchFromDB(to));

                balanceCache.put(from, fromBal - amount);
                balanceCache.put(to, Math.min(toBal + amount, maxBalance));

                persistBalance(from, fromBal - amount);
                persistBalance(to, Math.min(toBal + amount, maxBalance));

                recordTransaction(from, amount, "DEBIT", reason);
                recordTransaction(to, amount, "CREDIT", reason);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "transfer failed", e);
                return false;
            }
        });
    }

    public List<TransactionRecord> getRecentTransactions(UUID uuid) {
        return txCache.getOrDefault(uuid, new ArrayList<>());
    }

    private void loadTransactionHistory(UUID uuid) throws SQLException {
        String sql = """
            SELECT amount, type, reason, created_at
            FROM transactions
            WHERE uuid = ?
            ORDER BY created_at DESC
            LIMIT ?
        """;
        List<TransactionRecord> history = new ArrayList<>();
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, historySize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                history.add(new TransactionRecord(
                    rs.getLong("amount"),
                    rs.getString("type"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
        }
        txCache.put(uuid, history);
    }

    private void recordTransaction(UUID uuid, long amount, String type, String reason) throws SQLException {
        String sql = "INSERT INTO transactions (uuid, amount, type, reason) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, amount);
            ps.setString(3, type);
            ps.setString(4, reason.length() > 128 ? reason.substring(0, 128) : reason);
            ps.executeUpdate();
        }

        if ("CREDIT".equals(type)) {
            String upd = "UPDATE players SET total_earned = total_earned + ? WHERE uuid = ?";
            try (PreparedStatement ps = db.prepareStatement(upd)) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        }

        List<TransactionRecord> cached = txCache.computeIfAbsent(uuid, k -> new ArrayList<>());
        cached.add(0, new TransactionRecord(amount, type, reason, java.time.LocalDateTime.now()));
        if (cached.size() > historySize) cached.subList(historySize, cached.size()).clear();
    }

    private long fetchFromDB(UUID uuid) throws SQLException {
        String sql = "SELECT coins FROM players WHERE uuid = ?";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("coins");
        }
        return 0L;
    }

    private void persistBalance(UUID uuid, long balance) throws SQLException {
        String sql = "UPDATE players SET coins = ? WHERE uuid = ?";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setLong(1, balance);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public long getTotalEarned(UUID uuid) {
        try {
            String sql = "SELECT total_earned FROM players WHERE uuid = ?";
            try (PreparedStatement ps = db.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getLong("total_earned");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Khong the lay tong tien da kiem", e);
        }
        return 0L;
    }

    public long getStartingBalance()  { return startingBalance; }
    public long getMaxBalance()       { return maxBalance; }

    public record TransactionRecord(
        long amount,
        String type,
        String reason,
        java.time.LocalDateTime timestamp
    ) {}
}

package org.yuan_dev.eternalFrontier.database;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class DatabaseManager {

    private final EternalFrontier plugin;
    private Connection connection;
    private final ExecutorService asyncExecutor;
    private final String dbPath;

    public DatabaseManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newFixedThreadPool(
            plugin.getConfig().getInt("database.pool-size", 10)
        );
        String fileName = plugin.getConfig().getString("database.file", "eternalfrontier");
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + fileName;
    }

    public void connect() throws SQLException {
        try {
            Class.forName("org.yuan_dev.eternalFrontier.libs.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Khong tim thay H2 driver!", e);
        }

        String url = "jdbc:h2:" + dbPath + ";MODE=MySQL;AUTO_SERVER=TRUE";
        connection = DriverManager.getConnection(url, "sa", "");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET WRITE_DELAY 0");
        }

        plugin.getLogger().info("Ket noi database thanh cong.");
        initTables();
    }

    public void disconnect() {
        asyncExecutor.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Dong ket noi database thanh cong.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Loi khi dong ket noi database", e);
        }
    }

    public void awaitAsyncTasks(long timeoutMs) {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                plugin.getLogger().warning("DB async tasks chua ket thuc truoc timeout, dang force shutdown...");
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Bi ngat khi cho DB async tasks ket thuc.");
            asyncExecutor.shutdownNow();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor)
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Loi tac vu DB bat dong bo", ex);
                return null;
            });
    }

    public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, asyncExecutor)
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Loi supply DB bat dong bo", ex);
                return null;
            });
    }

    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private void initTables() {
        try (Statement stmt = connection.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid         VARCHAR(36)  PRIMARY KEY,
                    username     VARCHAR(16)  NOT NULL,
                    coins        BIGINT       NOT NULL DEFAULT 100,
                    total_earned BIGINT       NOT NULL DEFAULT 0,
                    first_join   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    last_seen    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id          INT          AUTO_INCREMENT PRIMARY KEY,
                    uuid        VARCHAR(36)  NOT NULL,
                    amount      BIGINT       NOT NULL,
                    type        VARCHAR(10)  NOT NULL,
                    reason      VARCHAR(128) NOT NULL,
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_tx_uuid (uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS zone_stats (
                    uuid            VARCHAR(36) PRIMARY KEY,
                    zone1_playtime  BIGINT NOT NULL DEFAULT 0,
                    zone2_playtime  BIGINT NOT NULL DEFAULT 0,
                    zone3_playtime  BIGINT NOT NULL DEFAULT 0,
                    highest_zone    INT    NOT NULL DEFAULT 1,
                    last_reward     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clans (
                    id          INT         AUTO_INCREMENT PRIMARY KEY,
                    name        VARCHAR(16) NOT NULL UNIQUE,
                    tag         VARCHAR(5)  NOT NULL UNIQUE,
                    tier        INT         NOT NULL DEFAULT 1,
                    power       INT         NOT NULL DEFAULT 0,
                    balance     BIGINT      NOT NULL DEFAULT 0,
                    leader_uuid VARCHAR(36) NOT NULL,
                    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_members (
                    uuid        VARCHAR(36) PRIMARY KEY,
                    clan_id     INT         NOT NULL,
                    rank        VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
                    joined_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clan_homes (
                    id          INT         AUTO_INCREMENT PRIMARY KEY,
                    clan_id     INT         NOT NULL,
                    name        VARCHAR(32) NOT NULL,
                    world       VARCHAR(64) NOT NULL,
                    x           DOUBLE      NOT NULL,
                    y           DOUBLE      NOT NULL,
                    z           DOUBLE      NOT NULL,
                    yaw         FLOAT       NOT NULL DEFAULT 0,
                    pitch       FLOAT       NOT NULL DEFAULT 0,
                    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                    UNIQUE (clan_id, name)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cores (
                    id              INT         AUTO_INCREMENT PRIMARY KEY,
                    clan_name       VARCHAR(16) NOT NULL UNIQUE,
                    world           VARCHAR(64) NOT NULL,
                    x               DOUBLE      NOT NULL,
                    y               DOUBLE      NOT NULL,
                    z               DOUBLE      NOT NULL,
                    hp              INT         NOT NULL DEFAULT 25000,
                    max_hp          INT         NOT NULL DEFAULT 25000,
                    upgrade_iron    INT         NOT NULL DEFAULT 0,
                    upgrade_drone   INT         NOT NULL DEFAULT 0,
                    upgrade_repair  INT         NOT NULL DEFAULT 0,
                    upgrade_beacon  INT         NOT NULL DEFAULT 0,
                    created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_quests (
                    id          INT         AUTO_INCREMENT PRIMARY KEY,
                    uuid        VARCHAR(36) NOT NULL,
                    quest_date  DATE        NOT NULL,
                    quest_type  VARCHAR(32) NOT NULL,
                    target      INT         NOT NULL,
                    progress    INT         NOT NULL DEFAULT 0,
                    reward      INT         NOT NULL,
                    claimed     BOOLEAN     NOT NULL DEFAULT FALSE,
                    UNIQUE (uuid, quest_date, quest_type)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS leaderboards (
                    id          INT         AUTO_INCREMENT PRIMARY KEY,
                    type        VARCHAR(16) NOT NULL,
                    uuid        VARCHAR(36) NOT NULL,
                    name        VARCHAR(64) NOT NULL,
                    score_value BIGINT      NOT NULL DEFAULT 0,
                    updated_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (type, uuid)
                )
            """);

            plugin.getLogger().info("Tat ca bang du lieu da khoi tao.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Khong the khoi tao bang du lieu!", e);
        }
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return connection.prepareStatement(sql, autoGeneratedKeys);
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public void reconnectIfNeeded() {
        if (!isConnected()) {
            plugin.getLogger().warning("Mat ket noi database. Dang ket noi lai...");
            try {
                connect();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the ket noi lai database!", e);
            }
        }
    }
}

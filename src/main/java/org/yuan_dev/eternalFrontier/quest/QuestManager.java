package org.yuan_dev.eternalFrontier.quest;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class QuestManager {

    public record Quest(int id, String type, int target, int progress, int reward, boolean claimed) {
        public boolean isComplete() { return progress >= target; }
        public double getPercent()  { return Math.min(1.0, (double) progress / target); }
        public Quest withProgress(int p) { return new Quest(id, type, target, p, reward, claimed); }
        public Quest withClaimed()       { return new Quest(id, type, target, progress, reward, true); }
    }

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<UUID, List<Quest>> questCache = new ConcurrentHashMap<>();

    private static final String[] QUEST_TYPES = {"kill-mobs", "mine-blocks", "sell-items", "login"};

    public QuestManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        scheduleMidnightReset();
    }

    public void reload() {}
    public void shutdown() {}

    public void initPlayerQuests(Player player) {
        db.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            LocalDate today = LocalDate.now();
            List<Quest> quests = new ArrayList<>();

            try {

                String checkSql = """
                    SELECT id, quest_type, target, progress, reward, claimed
                    FROM daily_quests
                    WHERE uuid = ? AND quest_date = ?
                """;
                try (PreparedStatement ps = db.prepareStatement(checkSql)) {
                    ps.setString(1, uuid.toString());
                    ps.setDate(2, Date.valueOf(today));
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        quests.add(new Quest(
                            rs.getInt("id"),
                            rs.getString("quest_type"),
                            rs.getInt("target"),
                            rs.getInt("progress"),
                            rs.getInt("reward"),
                            rs.getBoolean("claimed")
                        ));
                    }
                }

                if (quests.isEmpty()) {
                    quests = generateQuests(uuid, today);
                }

                questCache.put(uuid, quests);

                trackQuestProgress(uuid, "login", 1);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the khoi tao quest cho " + player.getName(), e);
            }
        });
    }

    private List<Quest> generateQuests(UUID uuid, LocalDate date) throws SQLException {
        List<Quest> quests = new ArrayList<>();
        Random rng = new Random(uuid.hashCode() + date.toEpochDay());

        int[] kills  = {20, 50, 100};
        int[] mine   = {32, 64, 128};
        int[] sell   = {5, 15, 30};
        int[] killR  = {50, 150, 300};
        int[] mineR  = {30, 80, 200};
        int[] sellR  = {40, 100, 250};

        int killIdx = rng.nextInt(3);
        int mineIdx = rng.nextInt(3);
        int sellIdx = rng.nextInt(3);

        Object[][] defs = {
            {"kill-mobs",   kills[killIdx], killR[killIdx]},
            {"mine-blocks", mine[mineIdx],  mineR[mineIdx]},
            {"sell-items",  sell[sellIdx],  sellR[sellIdx]},
            {"login",       1,              25}
        };

        String sql = """
            INSERT INTO daily_quests (uuid, quest_date, quest_type, target, reward)
            VALUES (?, ?, ?, ?, ?)
        """;
        for (Object[] def : defs) {
            try (PreparedStatement ps = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, uuid.toString());
                ps.setDate(2, Date.valueOf(date));
                ps.setString(3, (String) def[0]);
                ps.setInt(4, (int) def[1]);
                ps.setInt(5, (int) def[2]);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    quests.add(new Quest(keys.getInt(1), (String) def[0], (int) def[1], 0, (int) def[2], false));
                }
            }
        }
        return quests;
    }

    public void trackKill(Player player) {
        trackQuestProgress(player.getUniqueId(), "kill-mobs", 1);
    }

    public void trackMine(Player player, int count) {
        trackQuestProgress(player.getUniqueId(), "mine-blocks", count);
    }

    public void trackSell(Player player, int count) {
        trackQuestProgress(player.getUniqueId(), "sell-items", count);
    }

    private void trackQuestProgress(UUID uuid, String type, int amount) {
        List<Quest> quests = questCache.get(uuid);
        if (quests == null) return;

        for (int i = 0; i < quests.size(); i++) {
            Quest q = quests.get(i);
            if (!q.type().equals(type)) continue;
            if (q.isComplete()) continue;

            int newProgress = Math.min(q.target(), q.progress() + amount);
            Quest updated = q.withProgress(newProgress);
            quests.set(i, updated);

            final int qId = q.id();
            final int np = newProgress;
            db.runAsync(() -> {
                try {
                    String sql = "UPDATE daily_quests SET progress = ? WHERE id = ?";
                    try (PreparedStatement ps = db.prepareStatement(sql)) {
                        ps.setInt(1, np);
                        ps.setInt(2, qId);
                        ps.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Khong the cap nhat tien do quest", e);
                }
            });

            if (!q.isComplete() && updated.isComplete()) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        String questName = plugin.getMessageManager().get("quest.quest-" + type);
                        plugin.getMessageManager().send(player, "quest.completed",
                            "quest", questName, "reward", String.valueOf(q.reward()));
                    });
                }
            }
            break;
        }
    }

    public boolean claimReward(Player player, int questIndex) {
        List<Quest> quests = questCache.get(player.getUniqueId());
        if (quests == null || questIndex < 0 || questIndex >= quests.size()) return false;

        Quest q = quests.get(questIndex);
        if (!q.isComplete()) {
            plugin.getMessageManager().send(player, "quest.not-complete",
                "current", String.valueOf(q.progress()), "target", String.valueOf(q.target()));
            return false;
        }
        if (q.claimed()) {
            plugin.getMessageManager().send(player, "quest.already-claimed");
            return false;
        }

        quests.set(questIndex, q.withClaimed());

        plugin.getEconomyManager().addCoins(player.getUniqueId(), q.reward(), "Thuong quest: " + q.type());

        db.runAsync(() -> {
            try {
                String sql = "UPDATE daily_quests SET claimed = TRUE WHERE id = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setInt(1, q.id());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Khong the nhan thuong quest", e);
            }
        });

        return true;
    }

    private void scheduleMidnightReset() {

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long delay = java.time.Duration.between(now, midnight).getSeconds() * 20L;

        new BukkitRunnable() {
            @Override public void run() {
                questCache.clear();
                plugin.getServer().getOnlinePlayers().forEach(p -> initPlayerQuests(p));
                plugin.getServer().broadcast(
                    org.yuan_dev.eternalFrontier.utils.MessageManager.component(
                        plugin.getMessageManager().get("quest.new-quests"))
                );
                scheduleMidnightReset();
            }
        }.runTaskLater(plugin, delay);
    }

    public List<Quest> getQuests(UUID uuid) {
        return Collections.unmodifiableList(questCache.getOrDefault(uuid, new ArrayList<>()));
    }
}

package org.yuan_dev.eternalFrontier.clan;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.database.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ClanManager {

    private final EternalFrontier plugin;
    private final DatabaseManager db;

    private final Map<String, ClanData> clanCache = new ConcurrentHashMap<>();

    private final Map<UUID, String> playerClanMap = new ConcurrentHashMap<>();

    private final Map<UUID, String> pendingInvites = new ConcurrentHashMap<>();

    private final Set<UUID> clanChatEnabled = ConcurrentHashMap.newKeySet();

    private final int nameMinLen;
    private final int nameMaxLen;

    public ClanManager(EternalFrontier plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.nameMinLen = plugin.getConfig().getInt("clan.name-min-length", 3);
        this.nameMaxLen = plugin.getConfig().getInt("clan.name-max-length", 16);
        loadAllClans();
    }

    public void reload() {
        clanCache.clear();
        playerClanMap.clear();
        loadAllClans();
    }

    public void shutdown() {

    }

    private void loadAllClans() {
        db.runAsync(() -> {
            try {

                String sql = "SELECT * FROM clans";
                try (PreparedStatement ps = db.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ClanData clan = ClanData.fromResultSet(rs);
                        clanCache.put(clan.name().toLowerCase(), clan);
                    }
                }

                String memberSql = "SELECT cm.uuid, c.name FROM clan_members cm JOIN clans c ON cm.clan_id = c.id";
                try (PreparedStatement ps = db.prepareStatement(memberSql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        playerClanMap.put(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                    }
                }
                plugin.getLogger().info("Da tai " + clanCache.size() + " clan.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tai danh sach clan!", e);
            }
        });
    }

    public CompletableFuture<ClanResult> createClan(UUID leaderUuid, String name, String tag) {
        return db.supplyAsync(() -> {

            if (playerClanMap.containsKey(leaderUuid))
                return ClanResult.fail("clan.already-in-clan");

            if (name.length() < nameMinLen)
                return ClanResult.fail("clan.name-too-short", "min", String.valueOf(nameMinLen));

            if (name.length() > nameMaxLen)
                return ClanResult.fail("clan.name-too-long", "max", String.valueOf(nameMaxLen));

            if (!name.matches("^[a-zA-Z0-9_]+$"))
                return ClanResult.fail("clan.name-invalid");

            if (clanCache.containsKey(name.toLowerCase()))
                return ClanResult.fail("clan.name-taken", "name", name);

            try {

                String sql = "INSERT INTO clans (name, tag, tier, leader_uuid) VALUES (?, ?, 1, ?)";
                int clanId;
                try (PreparedStatement ps = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, tag.toUpperCase());
                    ps.setString(3, leaderUuid.toString());
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    if (!keys.next()) return ClanResult.fail("general.error");
                    clanId = keys.getInt(1);
                }

                String memberSql = "INSERT INTO clan_members (uuid, clan_id, rank) VALUES (?, ?, 'LEADER')";
                try (PreparedStatement ps = db.prepareStatement(memberSql)) {
                    ps.setString(1, leaderUuid.toString());
                    ps.setInt(2, clanId);
                    ps.executeUpdate();
                }

                ClanData clan = new ClanData(clanId, name, tag.toUpperCase(), 1, 0, 0, leaderUuid,
                    new HashMap<>(Map.of(leaderUuid, ClanRank.LEADER)), new ArrayList<>());
                clanCache.put(name.toLowerCase(), clan);
                playerClanMap.put(leaderUuid, name);

                return ClanResult.success(clan);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tao clan: " + name, e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public CompletableFuture<ClanResult> disbandClan(UUID leaderUuid) {
        return db.supplyAsync(() -> {
            String clanName = playerClanMap.get(leaderUuid);
            if (clanName == null) return ClanResult.fail("clan.not-in-clan");

            ClanData clan = clanCache.get(clanName.toLowerCase());
            if (clan == null) return ClanResult.fail("clan.not-in-clan");

            if (!clan.leaderUuid().equals(leaderUuid)) return ClanResult.fail("clan.not-leader");

            try {
                String sql = "DELETE FROM clans WHERE id = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setInt(1, clan.id());
                    ps.executeUpdate();
                }

                clan.members().keySet().forEach(playerClanMap::remove);
                clanCache.remove(clanName.toLowerCase());

                plugin.getCoreManager().removeCoreForClan(clanName);

                return ClanResult.success(clan);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the giai tan clan: " + clanName, e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public CompletableFuture<ClanResult> joinClan(UUID playerUuid, String clanName) {
        return db.supplyAsync(() -> {
            if (playerClanMap.containsKey(playerUuid)) return ClanResult.fail("clan.already-in-clan");

            ClanData clan = clanCache.get(clanName.toLowerCase());
            if (clan == null) return ClanResult.fail("clan.not-found");

            int maxMembers = plugin.getConfig().getInt("clan.tiers." + clan.tier() + ".max-members", 8);
            if (maxMembers != -1 && clan.members().size() >= maxMembers)
                return ClanResult.fail("clan.clan-full", "max", String.valueOf(maxMembers), "tier", String.valueOf(clan.tier()));

            try {
                String sql = "INSERT INTO clan_members (uuid, clan_id, rank) VALUES (?, ?, 'MEMBER')";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.setInt(2, clan.id());
                    ps.executeUpdate();
                }

                clan.members().put(playerUuid, ClanRank.MEMBER);
                playerClanMap.put(playerUuid, clanName);
                return ClanResult.success(clan);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the tham gia clan", e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public CompletableFuture<ClanResult> leaveClan(UUID playerUuid) {
        return db.supplyAsync(() -> {
            String clanName = playerClanMap.get(playerUuid);
            if (clanName == null) return ClanResult.fail("clan.not-in-clan");

            ClanData clan = clanCache.get(clanName.toLowerCase());
            if (clan == null) return ClanResult.fail("clan.not-in-clan");

            if (clan.leaderUuid().equals(playerUuid))
                return ClanResult.fail("clan.leader-cannot-leave");

            try {
                String sql = "DELETE FROM clan_members WHERE uuid = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setString(1, playerUuid.toString());
                    ps.executeUpdate();
                }

                clan.members().remove(playerUuid);
                playerClanMap.remove(playerUuid);
                return ClanResult.success(clan);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the roi clan", e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public CompletableFuture<ClanResult> upgradeTier(UUID leaderUuid) {
        return db.supplyAsync(() -> {
            ClanData clan = getClanByPlayer(leaderUuid);
            if (clan == null) return ClanResult.fail("clan.not-in-clan");
            if (!clan.leaderUuid().equals(leaderUuid) && clan.members().get(leaderUuid) != ClanRank.OFFICER)
                return ClanResult.fail("clan.not-officer");

            int currentTier = clan.tier();
            if (currentTier >= 3) return ClanResult.fail("clan.upgrade-max");

            int newTier = currentTier + 1;
            long cost = plugin.getConfig().getLong("clan.tiers." + newTier + ".upgrade-cost", 5000);

            if (clan.balance() < cost)
                return ClanResult.fail("clan.upgrade-insufficient", "cost", String.valueOf(cost));

            try {
                long newBalance = clan.balance() - cost;
                String sql = "UPDATE clans SET tier = ?, balance = ? WHERE id = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setInt(1, newTier);
                    ps.setLong(2, newBalance);
                    ps.setInt(3, clan.id());
                    ps.executeUpdate();
                }

                ClanData updated = clan.withTier(newTier).withBalance(newBalance);
                clanCache.put(clan.name().toLowerCase(), updated);
                return ClanResult.success(updated, "tier", String.valueOf(newTier));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the nang cap tier clan", e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public CompletableFuture<ClanResult> depositToClan(UUID playerUuid, long amount) {
        return db.supplyAsync(() -> {
            ClanData clan = getClanByPlayer(playerUuid);
            if (clan == null) return ClanResult.fail("clan.not-in-clan");

            boolean success = plugin.getEconomyManager()
                .removeCoins(playerUuid, amount, "Nap vao quy clan").join();
            if (!success) return ClanResult.fail("economy.insufficient-funds",
                "balance", String.valueOf(plugin.getEconomyManager().getBalance(playerUuid)),
                "required", String.valueOf(amount));

            try {
                long newBalance = clan.balance() + amount;
                String sql = "UPDATE clans SET balance = ? WHERE id = ?";
                try (PreparedStatement ps = db.prepareStatement(sql)) {
                    ps.setLong(1, newBalance);
                    ps.setInt(2, clan.id());
                    ps.executeUpdate();
                }

                ClanData updated = clan.withBalance(newBalance);
                clanCache.put(clan.name().toLowerCase(), updated);
                return ClanResult.success(updated, "amount", String.valueOf(amount), "balance", String.valueOf(newBalance));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Khong the nap tien vao clan", e);
                return ClanResult.fail("general.error");
            }
        });
    }

    public ClanResult sendInvite(UUID senderUuid, UUID targetUuid) {
        ClanData clan = getClanByPlayer(senderUuid);
        if (clan == null) return ClanResult.fail("clan.not-in-clan");

        ClanRank rank = clan.members().get(senderUuid);
        if (rank != ClanRank.LEADER && rank != ClanRank.OFFICER)
            return ClanResult.fail("clan.not-officer");

        if (playerClanMap.containsKey(targetUuid))
            return ClanResult.fail("clan.already-in-clan");

        if (pendingInvites.containsKey(targetUuid))
            return ClanResult.fail("clan.invite-already-sent", "player", targetUuid.toString());

        pendingInvites.put(targetUuid, clan.name());

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
            () -> pendingInvites.remove(targetUuid), 1200L);

        return ClanResult.success(clan);
    }

    public CompletableFuture<ClanResult> acceptInvite(UUID playerUuid) {
        String clanName = pendingInvites.remove(playerUuid);
        if (clanName == null) return CompletableFuture.completedFuture(ClanResult.fail("clan.not-invited"));
        return joinClan(playerUuid, clanName);
    }

    public boolean toggleClanChat(UUID uuid) {
        if (clanChatEnabled.contains(uuid)) {
            clanChatEnabled.remove(uuid);
            return false;
        } else {
            clanChatEnabled.add(uuid);
            return true;
        }
    }

    public boolean isInClanChat(UUID uuid) {
        return clanChatEnabled.contains(uuid);
    }

    public void broadcastClanChat(UUID senderUuid, String message) {
        ClanData clan = getClanByPlayer(senderUuid);
        if (clan == null) return;

        String senderName = plugin.getServer().getOfflinePlayer(senderUuid).getName();
        String formatted = plugin.getConfig().getString("clan.chat-format",
                "&8[&6C&8] &7{player}&8: &f{message}")
            .replace("{player}", senderName != null ? senderName : "Khong xac dinh")
            .replace("{message}", message);

        clan.members().keySet().forEach(uuid -> {
            var member = plugin.getServer().getPlayer(uuid);
            if (member != null) member.sendMessage(org.yuan_dev.eternalFrontier.utils.MessageManager.colorize(formatted));
        });
    }

    public ClanData getClanByName(String name) {
        return clanCache.get(name.toLowerCase());
    }

    public ClanData getClanByPlayer(UUID uuid) {
        String name = playerClanMap.get(uuid);
        return name != null ? clanCache.get(name.toLowerCase()) : null;
    }

    public boolean isInClan(UUID uuid) {
        return playerClanMap.containsKey(uuid);
    }

    public String getPendingInvite(UUID uuid) {
        return pendingInvites.get(uuid);
    }

    public Collection<ClanData> getAllClans() {
        return Collections.unmodifiableCollection(clanCache.values());
    }
}

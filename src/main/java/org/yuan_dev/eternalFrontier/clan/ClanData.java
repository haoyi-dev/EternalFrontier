package org.yuan_dev.eternalFrontier.clan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public record ClanData(
    int id,
    String name,
    String tag,
    int tier,
    int power,
    long balance,
    UUID leaderUuid,
    Map<UUID, ClanRank> members,
    List<ClanHome> homes
) {
    public static ClanData fromResultSet(ResultSet rs) throws SQLException {
        return new ClanData(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("tag"),
            rs.getInt("tier"),
            rs.getInt("power"),
            rs.getLong("balance"),
            UUID.fromString(rs.getString("leader_uuid")),
            new HashMap<>(),
            new ArrayList<>()
        );
    }

    public ClanData withTier(int tier) {
        return new ClanData(id, name, tag, tier, power, balance, leaderUuid, members, homes);
    }

    public ClanData withBalance(long balance) {
        return new ClanData(id, name, tag, tier, power, balance, leaderUuid, members, homes);
    }

    public ClanData withPower(int power) {
        return new ClanData(id, name, tag, tier, power, balance, leaderUuid, members, homes);
    }

    public String getFormattedPrefix() {
        return "§8[§6" + tag + "§8] ";
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public ClanRank getRank(UUID uuid) {
        return members.getOrDefault(uuid, null);
    }
}

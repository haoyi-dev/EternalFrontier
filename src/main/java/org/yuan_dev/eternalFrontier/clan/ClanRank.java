package org.yuan_dev.eternalFrontier.clan;

public enum ClanRank {
    LEADER,
    OFFICER,
    MEMBER;

    public String getDisplayName() {
        return switch (this) {
            case LEADER  -> "§c👑 Leader";
            case OFFICER -> "§6⚔ Officer";
            case MEMBER  -> "§7👤 Member";
        };
    }

    public int getLevel() {
        return switch (this) {
            case LEADER  -> 3;
            case OFFICER -> 2;
            case MEMBER  -> 1;
        };
    }

    public boolean isAtLeast(ClanRank rank) {
        return this.getLevel() >= rank.getLevel();
    }
}

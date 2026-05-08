package org.yuan_dev.eternalFrontier.zone;

public enum Zone {
    SPAWN,
    ZONE1,
    ZONE2,
    ZONE3;

    public String getDisplayName() {
        return switch (this) {
            case SPAWN -> "&7Spawn";
            case ZONE1 -> "&aSafe Zone";
            case ZONE2 -> "&eWild Zone";
            case ZONE3 -> "&cDeath Zone";
        };
    }

    public String getColor() {
        return switch (this) {
            case SPAWN -> "7";
            case ZONE1 -> "a";
            case ZONE2 -> "e";
            case ZONE3 -> "c";
        };
    }

    public int getNumber() {
        return switch (this) {
            case SPAWN -> 0;
            case ZONE1 -> 1;
            case ZONE2 -> 2;
            case ZONE3 -> 3;
        };
    }
}

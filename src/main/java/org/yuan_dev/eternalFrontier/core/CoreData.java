package org.yuan_dev.eternalFrontier.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public record CoreData(
    int id,
    String clanName,
    String world,
    double x, double y, double z,
    int hp,
    int maxHp,
    int upgradeIron,
    int upgradeDrone,
    int upgradeRepair,
    int upgradeBeacon
) {
    public Location toLocation() {
        var w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public boolean isAlive() { return hp > 0; }

    public double getHpPercent() { return (double) hp / maxHp; }

    public CoreData withHp(int hp) {
        return new CoreData(id, clanName, world, x, y, z, hp, maxHp,
            upgradeIron, upgradeDrone, upgradeRepair, upgradeBeacon);
    }

    public CoreData withUpgrade(String type, int level) {
        return switch (type) {
            case "iron-plate" -> new CoreData(id, clanName, world, x, y, z, hp, maxHp,
                level, upgradeDrone, upgradeRepair, upgradeBeacon);
            case "drone" -> new CoreData(id, clanName, world, x, y, z, hp, maxHp,
                upgradeIron, level, upgradeRepair, upgradeBeacon);
            case "repair" -> new CoreData(id, clanName, world, x, y, z, hp, maxHp,
                upgradeIron, upgradeDrone, level, upgradeBeacon);
            case "beacon" -> new CoreData(id, clanName, world, x, y, z, hp, maxHp,
                upgradeIron, upgradeDrone, upgradeRepair, level);
            default -> this;
        };
    }

    public int getUpgradeLevel(String type) {
        return switch (type) {
            case "iron-plate" -> upgradeIron;
            case "drone"      -> upgradeDrone;
            case "repair"     -> upgradeRepair;
            case "beacon"     -> upgradeBeacon;
            default           -> 0;
        };
    }
}

package org.yuan_dev.eternalFrontier.clan;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public record ClanHome(int id, int clanId, String name, String world, double x, double y, double z, float yaw, float pitch) {

    public Location toLocation() {
        var w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}

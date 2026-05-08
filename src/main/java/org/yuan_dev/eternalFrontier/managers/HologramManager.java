package org.yuan_dev.eternalFrontier.managers;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.leaderboard.LeaderboardManager;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

public class HologramManager {

    private final EternalFrontier plugin;

    private final Map<String, List<UUID>> holograms = new LinkedHashMap<>();
    private BukkitTask updateTask;

    private static final Object[][] HOLO_CONFIGS = {
        {"eco",  "&6&l💰 TOP KINH TE",  LeaderboardManager.LeaderboardType.ECO,   0},
        {"zone", "&a&l🗺 TOP ZONE",      LeaderboardManager.LeaderboardType.ZONE,  11},
        {"clan", "&b&l👥 TOP CLAN",      LeaderboardManager.LeaderboardType.CLAN,  22},

        {"core", "&c&l⚔ TOP CORE",      LeaderboardManager.LeaderboardType.CLAN,  33},
    };

    public HologramManager(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    public void spawnAll() {
        String worldName = plugin.getConfig().getString("leaderboard.hologram-world", "world");
        double baseX = plugin.getConfig().getDouble("leaderboard.hologram-x", 0.5);
        double baseY = plugin.getConfig().getDouble("leaderboard.hologram-y", 64.0);
        double baseZ = plugin.getConfig().getDouble("leaderboard.hologram-z", 0.5);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World hologram '" + worldName + "' not found! Holograms skipped.");
            return;
        }

        removeAll();

        for (Object[] cfg : HOLO_CONFIGS) {
            String id   = (String) cfg[0];
            String title = (String) cfg[1];
            int xOffset = (int) cfg[3];
            double x = baseX + xOffset;

            List<UUID> stands = spawnHologram(world, x, baseY, baseZ, title, 7);
            holograms.put(id, stands);
        }

        long interval = plugin.getConfig().getLong("leaderboard.update-interval", 6000);
        updateTask = new BukkitRunnable() {
            @Override public void run() { updateAll(); }
        }.runTaskTimer(plugin, 100L, interval);

        updateAll();
        plugin.getLogger().info("Da tao 4 hologram bang xep hang.");
    }

    public void removeAll() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        holograms.forEach((id, uuids) -> {
            for (UUID uuid : uuids) {
                var entity = Bukkit.getEntity(uuid);
                if (entity != null) entity.remove();
            }
        });
        holograms.clear();
    }

    private void updateAll() {
        for (int i = 0; i < HOLO_CONFIGS.length; i++) {
            String id = (String) HOLO_CONFIGS[i][0];
            LeaderboardManager.LeaderboardType type = (LeaderboardManager.LeaderboardType) HOLO_CONFIGS[i][2];

            List<UUID> stands = holograms.get(id);
            if (stands == null || stands.isEmpty()) continue;

            List<LeaderboardManager.LeaderboardEntry> top = plugin.getLeaderboardManager().getTop(type);

            for (int line = 0; line < Math.min(stands.size(), 7); line++) {
                UUID uid = stands.get(line);
                var entity = Bukkit.getEntity(uid);
                if (!(entity instanceof ArmorStand stand)) continue;

                if (line == 0) continue;

                int entryIdx = line - 1;
                String text;
                if (entryIdx >= top.size()) {
                    text = "&8---";
                } else {
                    var e = top.get(entryIdx);
                    String[] rankColors = {"&6", "&7", "&c", "&f", "&f"};
                    String color = rankColors[Math.min(entryIdx, rankColors.length - 1)];
                    String value = type == LeaderboardManager.LeaderboardType.ZONE
                        ? GuiUtils.formatTime(e.value())
                        : GuiUtils.formatCoins(e.value());
                    text = color + "#" + e.rank() + " &f" + e.name() + " &8| " + color + value;
                }
                stand.customName(MessageManager.component(text));
            }
        }
    }

    private List<UUID> spawnHologram(World world, double x, double y, double z, String title, int lines) {
        List<UUID> stands = new ArrayList<>();
        double lineSpacing = 0.28;

        for (int i = 0; i < lines; i++) {
            double hy = y + (lines - i) * lineSpacing;
            Location loc = new Location(world, x, hy, z);

            try {
                ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setVisible(false);
                stand.setSmall(true);
                stand.setMarker(true);
                stand.setCustomNameVisible(true);
                stand.setInvulnerable(true);
                stand.setPersistent(true);

                String text = (i == 0) ? title : "&8---";
                stand.customName(MessageManager.component(text));

                stands.add(stand.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Khong the tao hologram ArmorStand", e);
            }
        }
        return stands;
    }
}

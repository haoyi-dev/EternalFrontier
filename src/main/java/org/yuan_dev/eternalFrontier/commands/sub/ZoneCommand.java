package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.yuan_dev.eternalFrontier.zone.Zone;
import org.yuan_dev.eternalFrontier.zone.ZoneManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ZoneCommand implements SubCommand {
    private final EternalFrontier plugin;
    public ZoneCommand(EternalFrontier plugin) { this.plugin = plugin; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        Zone zone = plugin.getZoneManager().getZone(p.getLocation());
        ZoneManager.ZoneStats stats = plugin.getZoneManager().getStats(p.getUniqueId());
        double dist = plugin.getZoneManager().getDistanceToNextZone(p.getLocation());

        Inventory inv = GuiUtils.createGui("&8🗺 &aZone Info", 3);
        GuiUtils.fillBorder(inv, Material.GREEN_STAINED_GLASS_PANE);

        Material zoneMat = switch (zone) {
            case ZONE1 -> Material.GRASS_BLOCK;
            case ZONE2 -> Material.SAND;
            case ZONE3 -> Material.NETHERRACK;
            default    -> Material.COBBLESTONE;
        };
        inv.setItem(11, GuiUtils.buildItem(zoneMat,
            zone.getDisplayName() + " &7(Hien tai)",
            "&8&m──────────────────",
            "&7Zone cua ban: " + zone.getDisplayName(),
            "&7Toa do: &f" + (int)p.getLocation().getX() + ", " + (int)p.getLocation().getZ(),
            dist > 0 ? "&7Den zone tiep: &e" + (int)dist + "m" : "&7Day la zone cao nhat!",
            "&8&m──────────────────",
            "&7Phan thuong/gio: &6" + getHourlyReward(zone) + " Coins"
        ));

        inv.setItem(13, GuiUtils.buildItem(Material.BOOK,
            "&eThong ke Zone",
            "&8&m──────────────────",
            "&aZone 1: &f" + GuiUtils.formatTime(stats.zone1Time()),
            "&eZone 2: &f" + GuiUtils.formatTime(stats.zone2Time()),
            "&cZone 3: &f" + GuiUtils.formatTime(stats.zone3Time()),
            "&8&m──────────────────",
            "&7Zone cao nhat: &6Zone " + stats.highestZone()
        ));

        String bar = GuiUtils.progressBar(zone.getNumber(), 3, 10, "&a█", "&8░");
        inv.setItem(15, GuiUtils.buildItem(Material.COMPASS,
            "&6Tien do Zone",
            "&8&m──────────────────",
            "&7" + bar,
            "&7Zone " + zone.getNumber() + " / 3",
            "&8&m──────────────────",
            zone.getNumber() < 3 ? "&7Tiep tuc di xa de vao zone cao hon!" : "&c☠ Ban dang o vung nguy hiem nhat!"
        ));

        inv.setItem(26, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    private long getHourlyReward(Zone zone) {
        return switch (zone) {
            case ZONE1 -> plugin.getConfig().getLong("economy.hourly-rewards.zone1", 1);
            case ZONE2 -> plugin.getConfig().getLong("economy.hourly-rewards.zone2", 3);
            case ZONE3 -> plugin.getConfig().getLong("economy.hourly-rewards.zone3", 5);
            default    -> 0;
        };
    }

    @Override public String getPermission() { return "eternalfrontier.zone.info"; }
}

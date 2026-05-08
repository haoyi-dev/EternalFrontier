package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.leaderboard.LeaderboardManager;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class TopCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;
    public TopCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        openTop((Player) sender, LeaderboardManager.LeaderboardType.ECO);
    }

    private void openTop(Player p, LeaderboardManager.LeaderboardType type) {
        String typeName = switch (type) {
            case ECO  -> "&6💰 Kinh Te";
            case ZONE -> "&a🗺 Zone";
            case CLAN -> "&b👥 Clan";
            default   -> "&7Leaderboard";
        };
        Inventory inv = GuiUtils.createGui("&8📊 " + typeName, 4);
        GuiUtils.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(10, GuiUtils.buildItem(
            type == LeaderboardManager.LeaderboardType.ECO ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
            type == LeaderboardManager.LeaderboardType.ECO ? "&6💰 Kinh Te &7(Dang xem)" : "&7💰 Kinh Te",
            "&7Top nguoi choi giau nhat"
        ));
        inv.setItem(13, GuiUtils.buildItem(
            type == LeaderboardManager.LeaderboardType.ZONE ? Material.GRASS_BLOCK : Material.DIRT,
            type == LeaderboardManager.LeaderboardType.ZONE ? "&a🗺 Zone &7(Dang xem)" : "&7🗺 Zone",
            "&7Top thoi gian trong zone"
        ));
        inv.setItem(16, GuiUtils.buildItem(
            type == LeaderboardManager.LeaderboardType.CLAN ? Material.DIAMOND : Material.COAL,
            type == LeaderboardManager.LeaderboardType.CLAN ? "&b👥 Clan &7(Dang xem)" : "&7👥 Clan",
            "&7Top clan manh nhat"
        ));

        List<LeaderboardManager.LeaderboardEntry> entries = plugin.getLeaderboardManager().getTop(type);
        Material[] rankMats = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT,
            Material.STONE, Material.STONE, Material.STONE, Material.STONE, Material.STONE, Material.STONE, Material.STONE};
        String[] rankColors = {"&6", "&7", "&c", "&f", "&f", "&f", "&f", "&f", "&f", "&f"};
        int[] entrySlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

        for (int i = 0; i < Math.min(entries.size(), 10); i++) {
            var e = entries.get(i);
            String value = type == LeaderboardManager.LeaderboardType.ZONE
                ? GuiUtils.formatTime(e.value())
                : GuiUtils.formatCoins(e.value());
            inv.setItem(entrySlots[i], GuiUtils.buildItem(rankMats[i],
                rankColors[i] + "#" + e.rank() + " " + e.name(),
                "&8&m──────────────────",
                type == LeaderboardManager.LeaderboardType.ECO ? "&7Kiem duoc: &6" + value + " Coins" :
                type == LeaderboardManager.LeaderboardType.ZONE ? "&7Zone time: &a" + value :
                "&7Power: &c" + value
            ));
        }

        if (entries.isEmpty()) {
            inv.setItem(22, GuiUtils.buildItem(Material.BARRIER, "&7Chua co du lieu", "&7Hay choi de len bang xep hang!"));
        }

        inv.setItem(35, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(e.getView().title());
        if (!title.contains("Kinh Te") && !title.contains("Zone") && !title.contains("Clan")) return;

        if (!title.startsWith("📊") && !title.contains("📊")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (slot == 10) openTop(p, LeaderboardManager.LeaderboardType.ECO);
            else if (slot == 13) openTop(p, LeaderboardManager.LeaderboardType.ZONE);
            else if (slot == 16) openTop(p, LeaderboardManager.LeaderboardType.CLAN);
            else if (slot == 35) p.closeInventory();
        });
    }

    @Override public String getPermission() { return "eternalfrontier.top"; }
}

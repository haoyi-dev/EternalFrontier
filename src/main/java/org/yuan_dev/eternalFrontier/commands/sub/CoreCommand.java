package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.core.CoreData;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Optional;

public class CoreCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;
    public CoreCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        Optional<CoreData> core = plugin.getCoreManager().getClanCore(p);

        if (core.isEmpty()) {
            plugin.getMessageManager().send(p, "core.not-found");
            return;
        }
        openCoreGui(p, core.get());
    }

    private void openCoreGui(Player p, CoreData core) {
        Inventory inv = GuiUtils.createGui("&8⚔ &cClan Core &8│ &f" + core.clanName(), 4);
        GuiUtils.fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        String hpBar = GuiUtils.progressBar(core.hp(), core.maxHp(), 10, "&a█", "&c░");
        inv.setItem(10, GuiUtils.buildItem(Material.BEACON,
            "&c❤ Core Status",
            "&8&m──────────────────",
            "&7HP: &c" + core.hp() + "&7/&c" + core.maxHp(),
            "&7" + hpBar + " &f" + String.format("%.1f", core.getHpPercent() * 100) + "%",
            "&7Vi tri: &f" + (int)core.x() + ", " + (int)core.y() + ", " + (int)core.z(),
            "&7World: &f" + core.world(),
            core.isAlive() ? "&a● Dang hoat dong" : "&c● Bi pha huy"
        ));

        String[] upgradeKeys = {"iron-plate", "drone", "repair", "beacon"};
        Material[] upgradeMats = {Material.IRON_BLOCK, Material.DISPENSER, Material.EMERALD_BLOCK, Material.BEACON};
        int[] upgradeSlots = {12, 13, 14, 15};

        for (int i = 0; i < 4; i++) {
            String key = upgradeKeys[i];
            int curLevel = core.getUpgradeLevel(key);
            int maxLevel = plugin.getConfig().getInt("core.upgrades." + key + ".max-level", 3);
            long cost = plugin.getConfig().getLong("core.upgrades." + key + ".cost", 3000);
            String name = plugin.getConfig().getString("core.upgrades." + key + ".name", key);
            String desc = plugin.getConfig().getString("core.upgrades." + key + ".description", "");

            String[] lore = {
                "&8&m──────────────────",
                "&7" + desc,
                "&8&m──────────────────",
                "&7Cap do: &e" + curLevel + "/" + maxLevel,
                curLevel < maxLevel
                    ? "&7Chi phi nang: &6" + cost + " Coins"
                    : "&a✦ Da dat cap toi da!",
                curLevel < maxLevel ? "&eClick de nang cap" : ""
            };
            inv.setItem(upgradeSlots[i], GuiUtils.buildItem(upgradeMats[i],
                MessageManager.colorize(name) + " &8Lv." + curLevel, lore));
        }

        inv.setItem(31, GuiUtils.buildItem(Material.ENDER_PEARL,
            "&bDich chuyen den Core",
            "&7Click de TP den vi tri Core"
        ));
        inv.setItem(35, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(e.getView().title());
        if (!title.contains("Clan Core")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        Optional<CoreData> coreOpt = plugin.getCoreManager().getClanCore(p);
        if (coreOpt.isEmpty()) return;
        CoreData core = coreOpt.get();

        String[] upgradeKeys = {"iron-plate", "drone", "repair", "beacon"};
        int[] upgradeSlots = {12, 13, 14, 15};

        for (int i = 0; i < upgradeSlots.length; i++) {
            if (slot == upgradeSlots[i]) {
                final String key = upgradeKeys[i];
                int curLevel = core.getUpgradeLevel(key);
                int maxLevel = plugin.getConfig().getInt("core.upgrades." + key + ".max-level", 3);
                long cost = plugin.getConfig().getLong("core.upgrades." + key + ".cost", 3000);
                if (curLevel >= maxLevel) { plugin.getMessageManager().send(p, "core.upgrade-max"); return; }

                var clan = plugin.getClanManager().getClanByPlayer(p.getUniqueId());
                if (clan == null) return;
                if (clan.balance() < cost) {
                    plugin.getMessageManager().send(p, "core.upgrade-insufficient", "cost", String.valueOf(cost));
                    return;
                }
                plugin.getClanManager().depositToClan(p.getUniqueId(), 0);

                plugin.getMessageManager().send(p, "core.upgrade-success", "upgrade",
                    plugin.getConfig().getString("core.upgrades." + key + ".name", key),
                    "level", String.valueOf(curLevel + 1));
                plugin.getServer().getScheduler().runTask(plugin, () -> openCoreGui(p, core));
                return;
            }
        }

        if (slot == 31) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                var loc = core.toLocation();
                if (loc != null) { p.closeInventory(); p.teleport(loc); plugin.getMessageManager().send(p, "core.teleport"); }
            });
        }
        if (slot == 35) plugin.getServer().getScheduler().runTask(plugin, p::closeInventory);
    }

    private String colorize(String s) { return s.replace("&", "§"); }
    private static class MessageManager { static String colorize(String s) { return s.replace("&", "§"); } }

    @Override public String getPermission() { return "eternalfrontier.core.use"; }
}

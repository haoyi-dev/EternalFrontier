package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.clan.*;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class ClanCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;
    public ClanCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;

        if (args.length == 0) {

            ClanData clan = plugin.getClanManager().getClanByPlayer(p.getUniqueId());
            if (clan == null) {
                showNoClanMenu(p);
            } else {
                openClanMain(p, clan);
            }
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    plugin.getMessageManager().send(p, "general.invalid-args", "usage", "/clan create <name> <tag>");
                    return;
                }
                plugin.getClanManager().createClan(p.getUniqueId(), args[1], args[2])
                    .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (r.isSuccess()) plugin.getMessageManager().send(p, "clan.created", "name", args[1]);
                        else plugin.getMessageManager().send(p, r.getMessageKey(), flattenMap(r.getPlaceholders()));
                    }));
            }
            case "disband" -> {
                plugin.getClanManager().disbandClan(p.getUniqueId())
                    .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (r.isSuccess()) plugin.getMessageManager().send(p, "clan.disbanded", "name", r.getClan().name());
                        else plugin.getMessageManager().send(p, r.getMessageKey());
                    }));
            }
            case "invite" -> {
                if (args.length < 2) { plugin.getMessageManager().send(p, "general.invalid-args", "usage", "/clan invite <player>"); return; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { plugin.getMessageManager().send(p, "general.player-not-found", "player", args[1]); return; }
                ClanResult r = plugin.getClanManager().sendInvite(p.getUniqueId(), target.getUniqueId());
                if (r.isSuccess()) {
                    plugin.getMessageManager().send(p, "clan.invite-sent", "player", target.getName());
                    plugin.getMessageManager().send(target, "clan.invited", "player", p.getName(), "clan", r.getClan().name());
                } else {
                    plugin.getMessageManager().send(p, r.getMessageKey(), flattenMap(r.getPlaceholders()));
                }
            }
            case "accept" -> {
                plugin.getClanManager().acceptInvite(p.getUniqueId())
                    .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (r.isSuccess()) {
                            plugin.getMessageManager().send(p, "clan.you-joined", "clan", r.getClan().name());

                            r.getClan().members().keySet().forEach(uuid -> {
                                Player m = plugin.getServer().getPlayer(uuid);
                                if (m != null && !m.equals(p))
                                    plugin.getMessageManager().send(m, "clan.joined", "player", p.getName(), "clan", r.getClan().name());
                            });
                        } else {
                            plugin.getMessageManager().send(p, r.getMessageKey());
                        }
                    }));
            }
            case "leave" -> {
                plugin.getClanManager().leaveClan(p.getUniqueId())
                    .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (r.isSuccess()) plugin.getMessageManager().send(p, "clan.you-left", "clan", r.getClan().name());
                        else plugin.getMessageManager().send(p, r.getMessageKey());
                    }));
            }
            case "chat", "c" -> {
                boolean on = plugin.getClanManager().toggleClanChat(p.getUniqueId());
                plugin.getMessageManager().send(p, on ? "clan.chat-toggle-on" : "clan.chat-toggle-off");
            }
            case "deposit" -> {
                if (args.length < 2) { plugin.getMessageManager().send(p, "general.invalid-args", "usage", "/clan deposit <amount>"); return; }
                try {
                    long amt = Long.parseLong(args[1]);
                    plugin.getClanManager().depositToClan(p.getUniqueId(), amt)
                        .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (r.isSuccess()) plugin.getMessageManager().send(p, "clan.deposit", flattenMap(r.getPlaceholders()));
                            else plugin.getMessageManager().send(p, r.getMessageKey(), flattenMap(r.getPlaceholders()));
                        }));
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(p, "general.invalid-number", "input", args[1]);
                }
            }
            case "upgrade" -> {
                plugin.getClanManager().upgradeTier(p.getUniqueId())
                    .thenAccept(r -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (r.isSuccess()) {
                            String tier = r.getPlaceholders().getOrDefault("tier", "?");
                            r.getClan().members().keySet().forEach(uuid -> {
                                Player m = plugin.getServer().getPlayer(uuid);
                                if (m != null) plugin.getMessageManager().send(m, "clan.upgrade-success", "tier", tier);
                            });
                        } else {
                            plugin.getMessageManager().send(p, r.getMessageKey(), flattenMap(r.getPlaceholders()));
                        }
                    }));
            }
            default -> {
                p.sendMessage(MessageManager.colorize("&6[Clan] &7Lenh: create, disband, invite, accept, leave, chat, deposit, upgrade"));
            }
        }
    }

    private void showNoClanMenu(Player p) {
        Inventory inv = GuiUtils.createGui("&8👥 &6Clan Menu", 3);
        GuiUtils.fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(11, GuiUtils.buildItem(Material.PAPER,
            "&aThanh lap Clan",
            "&7Dung: &e/clan create <ten> <tag>",
            "&7Phi: &6Mien phi",
            "&7Tier 1: toi da &f8 thanh vien"
        ));
        inv.setItem(13, GuiUtils.buildItem(Material.BOOK,
            "&eThong tin Clan System",
            "&8&m──────────────────",
            "&7Tier 1 &f→ &aMaximum 8 members",
            "&7Tier 2 &f→ &aMaximum 25 members (&65000⭐&7)",
            "&7Tier 3 &f→ &aUnlimited (&625000⭐&7)",
            "&8&m──────────────────",
            "&7Clan co Clan Chat, Chest & Core!"
        ));
        inv.setItem(15, GuiUtils.buildItem(Material.OAK_SIGN,
            "&bTham gia Clan",
            "&7Nho leader invite ban",
            "&7Sau do dung: &e/clan accept"
        ));
        inv.setItem(26, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    private void openClanMain(Player p, ClanData clan) {
        int maxMembers = plugin.getConfig().getInt("clan.tiers." + clan.tier() + ".max-members", 8);
        String maxStr = maxMembers == -1 ? "∞" : String.valueOf(maxMembers);

        Inventory inv = GuiUtils.createGui("&8👥 &6" + clan.name() + " &8│ Tier " + clan.tier(), 4);
        GuiUtils.fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(10, GuiUtils.buildItem(Material.GOLD_BLOCK,
            "&6⚔ " + clan.name() + " &8[" + clan.tag() + "]",
            "&8&m──────────────────",
            "&7Tier: &e" + clan.tier(),
            "&7Thanh vien: &f" + clan.getMemberCount() + "/" + maxStr,
            "&7Quy: &6" + GuiUtils.formatCoins(clan.balance()) + " Coins",
            "&7Power: &c" + clan.power()
        ));

        List<String> memberLore = new ArrayList<>();
        memberLore.add("&8&m──────────────────");
        clan.members().forEach((uuid, rank) -> {
            Player m = plugin.getServer().getPlayer(uuid);
            String online = m != null ? "&a● " : "&7● ";
            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            memberLore.add(online + name + " &8- " + rank.getDisplayName());
        });
        inv.setItem(12, GuiUtils.buildItem(Material.PLAYER_HEAD,
            "&eThanh vien (" + clan.getMemberCount() + ")",
            memberLore.toArray(new String[0])
        ));

        if (clan.tier() < 3) {
            int nextTier = clan.tier() + 1;
            long cost = plugin.getConfig().getLong("clan.tiers." + nextTier + ".upgrade-cost", 5000);
            int nextMax = plugin.getConfig().getInt("clan.tiers." + nextTier + ".max-members", 25);
            String nextMaxStr = nextMax == -1 ? "∞" : String.valueOf(nextMax);
            inv.setItem(14, GuiUtils.buildItem(Material.EXPERIENCE_BOTTLE,
                "&bNang cap Tier " + nextTier,
                "&8&m──────────────────",
                "&7Chi phi: &6" + cost + " Coins &7(quy clan)",
                "&7Quy hien tai: &6" + GuiUtils.formatCoins(clan.balance()),
                "&7Max members: &f" + clan.getMemberCount() + " → " + nextMaxStr,
                clan.balance() >= cost ? "&aClick de nang cap!" : "&cKhong du tien trong quy!"
            ));
        } else {
            inv.setItem(14, GuiUtils.buildItem(Material.DIAMOND,
                "&6✦ Tier 3 MAX",
                "&7Clan cua ban da dat cap toi da!"
            ));
        }

        var core = plugin.getCoreManager().getClanCore(p);
        if (core.isPresent()) {
            var c = core.get();
            inv.setItem(16, GuiUtils.buildItem(Material.BEACON,
                "&cClan Core",
                "&8&m──────────────────",
                "&7HP: &c" + c.hp() + "/" + c.maxHp(),
                "&7Vi tri: &f" + (int)c.x() + ", " + (int)c.z(),
                "&7Iron: Lv" + c.upgradeIron() + " | Repair: Lv" + c.upgradeRepair()
            ));
        } else {
            inv.setItem(16, GuiUtils.buildItem(Material.BARRIER,
                "&7Clan Core",
                "&7Clan chua co Core!",
                "&7Dat Beacon de tao Core."
            ));
        }

        inv.setItem(35, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(e.getView().title());
        if (!title.contains("Clan")) return;
        if (e.getCurrentItem() == null) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();

        if (slot == 14 && title.contains("│ Tier")) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                new ClanCommand(plugin).execute(p, new String[]{"upgrade"})
            );
        }
        if (slot == 26 || slot == 35) {
            plugin.getServer().getScheduler().runTask(plugin, p::closeInventory);
        }
    }

    private String[] flattenMap(Map<String, String> map) {
        String[] arr = new String[map.size() * 2];
        int i = 0;
        for (var entry : map.entrySet()) { arr[i++] = entry.getKey(); arr[i++] = entry.getValue(); }
        return arr;
    }

    @Override public String getPermission() { return "eternalfrontier.clan.use"; }
}

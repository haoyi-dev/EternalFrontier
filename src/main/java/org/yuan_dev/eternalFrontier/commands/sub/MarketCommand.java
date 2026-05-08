package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MarketCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;

    private static final Object[][][] ALL_TABS = {
        {
            {"Sword Rune",Material.DIAMOND_SWORD,200},{"Armor Shard",Material.IRON_CHESTPLATE,150},
            {"Zone Token",Material.EMERALD,50},{"Speed Potion",Material.POTION,30},
            {"Strength Gem",Material.AMETHYST_SHARD,80},{"Zone Map",Material.MAP,20},
            {"Wolf Fang",Material.BONE,15},{"Blood Crystal",Material.NETHER_STAR,500},
            {"Elder Stone",Material.ANCIENT_DEBRIS,999}},
        {
            {"Health Potion",Material.RED_DYE,30},{"Mana Brew",Material.BLUE_DYE,35},
            {"XP Boost",Material.EXPERIENCE_BOTTLE,80},{"Fire Resist",Material.ORANGE_DYE,25},
            {"Night Vision",Material.PURPLE_DYE,20},{"Water Breath",Material.CYAN_DYE,22},
            {"Jump Boost",Material.LIME_DYE,18},{"Haste Brew",Material.YELLOW_DYE,40},
            {"Invisibility",Material.GRAY_DYE,60}},
        {
            {"Sharpness V",Material.ENCHANTED_BOOK,300},{"Protection IV",Material.ENCHANTED_BOOK,280},
            {"Unbreaking III",Material.ENCHANTED_BOOK,150},{"Fortune III",Material.ENCHANTED_BOOK,200},
            {"Silk Touch",Material.ENCHANTED_BOOK,180},{"Looting III",Material.ENCHANTED_BOOK,250},
            {"Power V",Material.ENCHANTED_BOOK,220},{"Mending",Material.ENCHANTED_BOOK,500},
            {"Infinity",Material.ENCHANTED_BOOK,400}},
        {
            {"Common Crate",Material.CHEST,50},{"Rare Crate",Material.TRAPPED_CHEST,200},
            {"Epic Crate",Material.BARREL,500},{"Legendary Crate",Material.SHULKER_BOX,1500},
            {"Zone Crate",Material.ENDER_CHEST,800},{"Clan Crate",Material.CHEST,300},
            {"Boss Crate",Material.TRAPPED_CHEST,1000},{"Ancient Crate",Material.BARREL,2500},
            {"Eternal Crate",Material.SHULKER_BOX,5000}},
        {
            {"Boss Token",Material.NETHERITE_SCRAP,400},{"Dragon Scale",Material.SCULK,600},
            {"Wither Bone",Material.BONE,350},{"Guardian Eye",Material.PRISMARINE_CRYSTALS,450},
            {"Blaze Core",Material.BLAZE_POWDER,250},{"Ender Pearl",Material.ENDER_PEARL,120},
            {"Ghast Tear",Material.GHAST_TEAR,180},{"Magma Cream",Material.MAGMA_CREAM,90},
            {"Nether Star",Material.NETHER_STAR,2000}},
        {
            {"Auction - Coming Soon",Material.GOLD_INGOT,0}}
    };

    private static final String[] TAB_NAMES = {
        "&a⚔ Zone", "&e☕ Consumables", "&b✨ Enchants",
        "&d📦 Crates", "&c💀 Boss", "&6🔨 Auction"
    };

    public MarketCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        openTab((Player) sender, 0);
    }

    public void openTab(Player p, int tab) {
        Inventory inv = GuiUtils.createGui("&8🛒 &6Eternal Market &8│ " + TAB_NAMES[tab], 6);
        GuiUtils.fillAll(inv, Material.BLACK_STAINED_GLASS_PANE);

        for (int i = 0; i < 6; i++) {
            boolean active = i == tab;
            inv.setItem(i, GuiUtils.buildItem(
                active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                TAB_NAMES[i] + (active ? " &7◀" : ""),
                active ? "&aDang xem" : "&7Click de mo"
            ));
        }

        inv.setItem(8, GuiUtils.buildItem(Material.COMPASS, "&0§" + tab));

        Object[][] items = ALL_TABS[Math.min(tab, ALL_TABS.length - 1)];
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        long balance = plugin.getEconomyManager().getBalance(p.getUniqueId());

        for (int i = 0; i < Math.min(items.length, 9); i++) {
            String name  = (String) items[i][0];
            Material mat = (Material) items[i][1];
            long price   = ((Number) items[i][2]).longValue();

            inv.setItem(slots[i], GuiUtils.buildItem(mat,
                "&f" + name,
                "&8&m──────────────────",
                "&7Gia: &6⭐ " + price + " Coins",
                "&7Ban co: &6" + GuiUtils.formatCoins(balance),
                price == 0 ? "&7(Chua mo)" : (balance >= price ? "&a✓ Du tien" : "&c✗ Thieu " + (price - balance)),
                "&8&m──────────────────",
                price > 0 ? "&eClick chuot trai de mua" : "&7---"
            ));
        }

        inv.setItem(49, GuiUtils.closeButton());
        if (tab > 0) inv.setItem(45, GuiUtils.buildItem(Material.ARROW, "&c← Tab truoc"));
        if (tab < 5) inv.setItem(53, GuiUtils.buildItem(Material.ARROW, "&aTab sau →"));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(e.getView().title());
        if (!title.contains("Eternal Market")) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = e.getRawSlot();

        ItemStack compass = e.getInventory().getItem(8);
        int currentTab = 0;
        if (compass != null && compass.getItemMeta() != null) {
            try {
                String dn = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(compass.getItemMeta().displayName() != null
                        ? compass.getItemMeta().displayName() : net.kyori.adventure.text.Component.empty());
                currentTab = Integer.parseInt(dn.replace("§", "").trim());
            } catch (NumberFormatException ignored) {}
        }

        final int tab = currentTab;

        if (slot >= 0 && slot <= 5) {
            plugin.getServer().getScheduler().runTask(plugin, () -> openTab(p, slot));
            return;
        }
        if (slot == 45 && tab > 0) { plugin.getServer().getScheduler().runTask(plugin, () -> openTab(p, tab-1)); return; }
        if (slot == 53 && tab < 5) { plugin.getServer().getScheduler().runTask(plugin, () -> openTab(p, tab+1)); return; }
        if (slot == 49) { plugin.getServer().getScheduler().runTask(plugin, () -> p.closeInventory()); return; }

        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int itemIdx = -1;
        for (int i = 0; i < slots.length; i++) { if (slots[i] == slot) { itemIdx = i; break; } }
        if (itemIdx < 0) return;

        Object[][] items = ALL_TABS[Math.min(tab, ALL_TABS.length - 1)];
        if (itemIdx >= items.length) return;

        String itemName = (String) items[itemIdx][0];
        Material mat    = (Material) items[itemIdx][1];
        long price      = ((Number) items[itemIdx][2]).longValue();
        if (price <= 0) return;

        plugin.getEconomyManager().removeCoins(p.getUniqueId(), price, "Mua cho: " + itemName)
            .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ok) {
                    p.getInventory().addItem(new ItemStack(mat));
                    plugin.getMessageManager().send(p, "market.bought", "item", itemName, "price", String.valueOf(price));
                    plugin.getQuestManager().trackSell(p, 1);
                    openTab(p, tab);
                } else {
                    plugin.getMessageManager().send(p, "economy.insufficient-funds",
                        "balance", String.valueOf(plugin.getEconomyManager().getBalance(p.getUniqueId())),
                        "required", String.valueOf(price));
                }
            }));
    }

    @Override public String getPermission() { return "eternalfrontier.economy.market"; }
}

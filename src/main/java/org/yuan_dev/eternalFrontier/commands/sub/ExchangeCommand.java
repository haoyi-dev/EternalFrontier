package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ExchangeCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;

    private static final Object[][] EXCHANGES = {
        {"64x Diamonds → 500 Coins",   Material.DIAMOND,        64,  500},
        {"32x Gold Ingot → 200 Coins",  Material.GOLD_INGOT,     32,  200},
        {"64x Iron Ingot → 100 Coins",  Material.IRON_INGOT,     64,  100},
        {"16x Emerald → 300 Coins",     Material.EMERALD,        16,  300},
        {"32x Lapis → 50 Coins",        Material.LAPIS_LAZULI,   32,   50},
        {"8x Nether Star → 2000 Coins", Material.NETHER_STAR,     8, 2000},
        {"16x Blaze Rod → 150 Coins",   Material.BLAZE_ROD,      16,  150},
        {"32x Ender Pearl → 120 Coins", Material.ENDER_PEARL,    32,  120},
        {"1x Beacon → 5000 Coins",      Material.BEACON,          1, 5000},
    };

    public ExchangeCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        Inventory inv = GuiUtils.createGui("&8🔄 &6Exchange Shop", 4);
        GuiUtils.fillBorder(inv, Material.CYAN_STAINED_GLASS_PANE);

        int[] slots = {10,11,12,13,14,15,16, 20,21};
        for (int i = 0; i < EXCHANGES.length; i++) {
            Object[] ex = EXCHANGES[i];
            String label  = (String) ex[0];
            Material mat  = (Material) ex[1];
            int qty        = (int) ex[2];
            int reward     = (int) ex[3];
            int have = p.getInventory().all(mat).values().stream().mapToInt(ItemStack::getAmount).sum();

            inv.setItem(slots[i], GuiUtils.buildItem(mat,
                "&f" + label,
                "&8&m──────────────────",
                "&7Can: &e" + qty + "x " + mat.name(),
                "&7Ban co: &f" + have + "x",
                "&7Nhan: &6+" + reward + " Coins",
                "&8&m──────────────────",
                have >= qty ? "&aClick de doi!" : "&cKhong du vat pham!"
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
        if (!title.contains("Exchange Shop")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int[] slots = {10,11,12,13,14,15,16, 20,21};
        int slot = e.getRawSlot();
        if (slot == 35) { plugin.getServer().getScheduler().runTask(plugin, p::closeInventory); return; }

        for (int i = 0; i < slots.length; i++) {
            if (slot != slots[i]) continue;
            final int idx = i;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Object[] ex = EXCHANGES[idx];
                Material mat = (Material) ex[1];
                int qty = (int) ex[2];
                int reward = (int) ex[3];

                int have = p.getInventory().all(mat).values().stream().mapToInt(ItemStack::getAmount).sum();
                if (have < qty) {
                    plugin.getMessageManager().send(p, "market.not-enough-items", "item", mat.name());
                    return;
                }

                p.getInventory().removeItem(new ItemStack(mat, qty));
                plugin.getEconomyManager().addCoins(p.getUniqueId(), reward, "Doi vat pham: " + mat.name());
                plugin.getMessageManager().send(p, "exchange.success",
                    "cost", qty + "x " + mat.name(), "item", mat.name());
                plugin.getQuestManager().trackSell(p, 1);
                execute(p, new String[0]);
            });
            return;
        }
    }

    @Override public String getPermission() { return "eternalfrontier.exchange"; }
}

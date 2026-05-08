package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.economy.EconomyManager;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class WalletCommand implements SubCommand {
    private final EternalFrontier plugin;
    public WalletCommand(EternalFrontier plugin) { this.plugin = plugin; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        long balance = plugin.getEconomyManager().getBalance(p.getUniqueId());
        long totalEarned = plugin.getEconomyManager().getTotalEarned(p.getUniqueId());
        List<EconomyManager.TransactionRecord> history = plugin.getEconomyManager().getRecentTransactions(p.getUniqueId());

        Inventory inv = GuiUtils.createGui(plugin.getMessageManager().get("wallet.gui-title"), 3);
        GuiUtils.fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        ItemStack head = GuiUtils.buildSkull(p,
            "&6⭐ " + GuiUtils.formatCoins(balance) + " Coins",
            "&7Total earned: &f" + GuiUtils.formatCoins(totalEarned),
            "",
            "&7" + plugin.getMessageManager().get("wallet.your-balance",
                "balance", String.valueOf(balance))
        );
        inv.setItem(13, head);

        List<String> histLore = new ArrayList<>();
        histLore.add(MessageManager.colorize("&8&m------------------"));
        if (history.isEmpty()) {
            histLore.add(MessageManager.colorize(plugin.getMessageManager().get("wallet.history-empty")));
        } else {
            for (var tx : history) {
                String sign = "CREDIT".equals(tx.type()) ? "&a+" : "&c-";
                histLore.add(MessageManager.colorize(sign + tx.amount() + " &7- " + tx.reason()));
            }
        }

        ItemStack histBook = GuiUtils.buildItem(Material.BOOK,
            plugin.getMessageManager().get("wallet.history-title"),
            histLore.toArray(new String[0])
        );
        inv.setItem(15, histBook);

        inv.setItem(26, GuiUtils.closeButton());

        p.openInventory(inv);
    }

    @Override public String getPermission() { return "eternalfrontier.economy.wallet"; }
}

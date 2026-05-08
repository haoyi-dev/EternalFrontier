package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements SubCommand {
    private final EternalFrontier plugin;
    public BalanceCommand(EternalFrontier plugin) { this.plugin = plugin; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {

            Player p = (Player) sender;
            long bal = plugin.getEconomyManager().getBalance(p.getUniqueId());
            plugin.getMessageManager().send(sender, "economy.balance", "balance", String.valueOf(bal));
        } else {

            if (!sender.hasPermission("eternalfrontier.economy.balance")) {
                plugin.getMessageManager().send(sender, "general.no-permission");
                return;
            }
            var target = Bukkit.getOfflinePlayer(args[0]);
            plugin.getEconomyManager().getBalanceAsync(target.getUniqueId()).thenAccept(bal ->
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().send(sender, "economy.balance-other",
                        "player", args[0], "balance", String.valueOf(bal))
                )
            );
        }
    }

    @Override public String getPermission() { return "eternalfrontier.economy.balance"; }
    @Override public boolean requiresPlayer() { return false; }
}

package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements SubCommand {
    private final EternalFrontier plugin;
    public PayCommand(EternalFrontier plugin) { this.plugin = plugin; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player from = (Player) sender;
        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "general.invalid-args", "usage", "/pay <player> <amount>");
            return;
        }
        Player to = Bukkit.getPlayer(args[0]);
        if (to == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[0]);
            return;
        }
        if (to.equals(from)) {
            plugin.getMessageManager().send(sender, "economy.pay-self");
            return;
        }
        long amount;
        try { amount = Long.parseLong(args[1]); } catch (NumberFormatException e) {
            plugin.getMessageManager().send(sender, "general.invalid-number", "input", args[1]);
            return;
        }
        if (amount <= 0) {
            plugin.getMessageManager().send(sender, "economy.pay-invalid-amount");
            return;
        }
        plugin.getEconomyManager().transfer(from.getUniqueId(), to.getUniqueId(), amount, "Nguoi choi chuyen tien")
            .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    plugin.getMessageManager().send(from, "economy.pay-sent",
                        "amount", String.valueOf(amount), "player", to.getName());
                    plugin.getMessageManager().send(to, "economy.pay-received",
                        "amount", String.valueOf(amount), "player", from.getName());
                } else {
                    plugin.getMessageManager().send(from, "economy.insufficient-funds",
                        "balance", String.valueOf(plugin.getEconomyManager().getBalance(from.getUniqueId())),
                        "required", String.valueOf(amount));
                }
            }));
    }

    @Override public String getPermission() { return "eternalfrontier.economy.pay"; }
}

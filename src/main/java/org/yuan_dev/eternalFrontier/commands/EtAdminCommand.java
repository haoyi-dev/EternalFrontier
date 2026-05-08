package org.yuan_dev.eternalFrontier.commands;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public class EtAdminCommand implements CommandExecutor, TabCompleter {
    private final EternalFrontier plugin;
    public EtAdminCommand(EternalFrontier plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("eternalfrontier.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                plugin.getMessageManager().send(sender, "general.reloaded");
            }
            case "give" -> {
                if (args.length < 3) { sender.sendMessage(MessageManager.colorize("&cUsage: /etadmin give <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[1]); return true; }
                try {
                    long amt = Long.parseLong(args[2]);
                    plugin.getEconomyManager().addCoins(target.getUniqueId(), amt, "Admin them tien").thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getMessageManager().send(sender, "economy.admin-give",
                                "player", target.getName(), "amount", String.valueOf(amt))
                        )
                    );
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(sender, "general.invalid-number", "input", args[2]);
                }
            }
            case "take" -> {
                if (args.length < 3) { sender.sendMessage(MessageManager.colorize("&cUsage: /etadmin take <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[1]); return true; }
                try {
                    long amt = Long.parseLong(args[2]);
                    plugin.getEconomyManager().removeCoins(target.getUniqueId(), amt, "Admin tru tien").thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getMessageManager().send(sender, "economy.admin-take",
                                "player", target.getName(), "amount", String.valueOf(amt))
                        )
                    );
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(sender, "general.invalid-number", "input", args[2]);
                }
            }
            case "set" -> {
                if (args.length < 3) { sender.sendMessage(MessageManager.colorize("&cUsage: /etadmin set <player> <amount>")); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { plugin.getMessageManager().send(sender, "general.player-not-found", "player", args[1]); return true; }
                try {
                    long amt = Long.parseLong(args[2]);
                    plugin.getEconomyManager().setCoins(target.getUniqueId(), amt).thenRun(() ->
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getMessageManager().send(sender, "economy.admin-set",
                                "player", target.getName(), "amount", String.valueOf(amt))
                        )
                    );
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(sender, "general.invalid-number", "input", args[2]);
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(MessageManager.colorize("&6[EF Admin] &7Commands:"));
        s.sendMessage(MessageManager.colorize(" &e/etadmin reload &7- Reload plugin"));
        s.sendMessage(MessageManager.colorize(" &e/etadmin give <p> <amount> &7- Give coins"));
        s.sendMessage(MessageManager.colorize(" &e/etadmin take <p> <amount> &7- Take coins"));
        s.sendMessage(MessageManager.colorize(" &e/etadmin set <p> <amount> &7- Set coins"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("reload", "give", "take", "set");
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return new ArrayList<>();
    }
}

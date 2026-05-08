package org.yuan_dev.eternalFrontier.commands;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.commands.sub.*;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class EtCommand implements CommandExecutor, TabCompleter {

    private final EternalFrontier plugin;

    private final WalletCommand walletHandler;
    private final BalanceCommand balanceHandler;
    private final PayCommand payHandler;
    private final MarketCommand marketHandler;
    private final ZoneCommand zoneHandler;
    private final ClanCommand clanHandler;
    private final CoreCommand coreHandler;
    private final TopCommand topHandler;
    private final QuestCommand questHandler;
    private final ExchangeCommand exchangeHandler;

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public EtCommand(EternalFrontier plugin) {
        this.plugin = plugin;

        walletHandler   = new WalletCommand(plugin);
        balanceHandler  = new BalanceCommand(plugin);
        payHandler      = new PayCommand(plugin);
        marketHandler   = new MarketCommand(plugin);
        zoneHandler     = new ZoneCommand(plugin);
        clanHandler     = new ClanCommand(plugin);
        coreHandler     = new CoreCommand(plugin);
        topHandler      = new TopCommand(plugin);
        questHandler    = new QuestCommand(plugin);
        exchangeHandler = new ExchangeCommand(plugin);

        subCommands.put("wallet",   walletHandler);
        subCommands.put("bal",      balanceHandler);
        subCommands.put("balance",  balanceHandler);
        subCommands.put("pay",      payHandler);
        subCommands.put("market",   marketHandler);
        subCommands.put("zone",     zoneHandler);
        subCommands.put("clan",     clanHandler);
        subCommands.put("core",     coreHandler);
        subCommands.put("top",      topHandler);
        subCommands.put("quest",    questHandler);
        subCommands.put("exchange", exchangeHandler);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        SubCommand handler = subCommands.get(sub);

        if (handler == null) {
            sendHelp(sender);
            return true;
        }

        if (!sender.hasPermission(handler.getPermission())) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return true;
        }

        if (handler.requiresPlayer() && !(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        handler.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
                if (sender.hasPermission(entry.getValue().getPermission())
                    && entry.getKey().startsWith(args[0].toLowerCase())) {
                    subs.add(entry.getKey());
                }
            }

            return new ArrayList<>(new LinkedHashSet<>(subs));
        }

        if (args.length >= 2) {
            SubCommand handler = subCommands.get(args[0].toLowerCase());
            if (handler != null) {
                return handler.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender) {
        String[] help = {
            "&8&m──────────────────────",
            "&6⚔ EternalFrontier &7v" + plugin.getDescription().getVersion(),
            "&8&m──────────────────────",
            " &e/et wallet &7- Xem vi coins",
            " &e/et market &7- Mo cho",
            " &e/et zone &7- Thong tin zone",
            " &e/et clan &7- Quan ly clan",
            " &e/et core &7- Quan ly core",
            " &e/et top &7- Bang xep hang",
            " &e/et quest &7- Daily quests",
            " &e/et exchange &7- Doi vat pham",
            " &e/et pay <player> <amount> &7- Gui coins",
            "&8&m──────────────────────",
        };
        for (String line : help) sender.sendMessage(MessageManager.colorize(line));
    }

    public WalletCommand   getWalletHandler()   { return walletHandler; }
    public BalanceCommand  getBalanceHandler()  { return balanceHandler; }
    public PayCommand      getPayHandler()      { return payHandler; }
    public MarketCommand   getMarketHandler()   { return marketHandler; }
    public ZoneCommand     getZoneHandler()     { return zoneHandler; }
    public ClanCommand     getClanHandler()     { return clanHandler; }
    public CoreCommand     getCoreHandler()     { return coreHandler; }
    public TopCommand      getTopHandler()      { return topHandler; }
    public QuestCommand    getQuestHandler()    { return questHandler; }
    public ExchangeCommand getExchangeHandler() { return exchangeHandler; }
}

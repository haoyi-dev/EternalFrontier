package org.yuan_dev.eternalFrontier;

import org.yuan_dev.eternalFrontier.database.DatabaseManager;
import org.yuan_dev.eternalFrontier.economy.EconomyManager;
import org.yuan_dev.eternalFrontier.utils.MessageManager;
import org.yuan_dev.eternalFrontier.zone.ZoneManager;
import org.yuan_dev.eternalFrontier.clan.ClanManager;
import org.yuan_dev.eternalFrontier.core.CoreManager;
import org.yuan_dev.eternalFrontier.leaderboard.LeaderboardManager;
import org.yuan_dev.eternalFrontier.quest.QuestManager;
import org.yuan_dev.eternalFrontier.commands.EtCommand;
import org.yuan_dev.eternalFrontier.commands.EtAdminCommand;
import org.yuan_dev.eternalFrontier.listeners.RewardListener;
import org.yuan_dev.eternalFrontier.listeners.ZoneListener;
import org.yuan_dev.eternalFrontier.listeners.ChatListener;
import org.yuan_dev.eternalFrontier.listeners.ProtectionListener;
import org.yuan_dev.eternalFrontier.listeners.BlockBreakListener;
import org.yuan_dev.eternalFrontier.listeners.PlayerSessionListener;
import org.yuan_dev.eternalFrontier.managers.EFPlaceholderExpansion;
import org.yuan_dev.eternalFrontier.managers.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class EternalFrontier extends JavaPlugin {

    private static EternalFrontier instance;

    private DatabaseManager databaseManager;
    private MessageManager messageManager;
    private EconomyManager economyManager;
    private ZoneManager zoneManager;
    private ClanManager clanManager;
    private CoreManager coreManager;
    private LeaderboardManager leaderboardManager;
    private QuestManager questManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);

        messageManager = new MessageManager(this);

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
            log("&a  [DB] &fKet noi database thanh cong.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Khong the ket noi database! Tat plugin.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        economyManager    = new EconomyManager(this);
        zoneManager       = new ZoneManager(this);
        clanManager       = new ClanManager(this);
        coreManager       = new CoreManager(this);
        leaderboardManager = new LeaderboardManager(this);
        questManager      = new QuestManager(this);
        hologramManager   = new HologramManager(this);
        hologramManager.spawnAll();

        log("&a  [OK] &fDa khoi tao &e7 managers&f.");

        registerCommands();
        log("&a  [OK] &fDa dang ky &e12 lenh&f.");

        registerListeners();
        log("&a  [OK] &fDa dang ky &e6 listeners&f.");

        hookPlaceholderAPI();

        printFooter();
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        if (zoneManager != null)        zoneManager.shutdown();
        if (coreManager != null)        coreManager.shutdown();
        if (leaderboardManager != null) leaderboardManager.shutdown();
        if (questManager != null)       questManager.shutdown();
        if (hologramManager != null)    hologramManager.removeAll();

        if (databaseManager != null) {
            databaseManager.awaitAsyncTasks(3000);
            databaseManager.disconnect();
        }

        String[] bye = {
            "&8╔════════════════════════════════════╗",
            "&8║  &c⚔  &6EternalFrontier &cDISABLED  &c⚔  &8║",
            "&8║  &7Tam biet! Hen gap lai...          &8║",
            "&8╚════════════════════════════════════╝"
        };
        for (String line : bye) log(line);
    }

    public void reload() {
        reloadConfig();
        messageManager.reload();
        zoneManager.reload();
        clanManager.reload();
        coreManager.reload();
        leaderboardManager.reload();
        questManager.reload();
        log("&a[EF] &fPlugin da duoc reload.");
    }

    private void printFooter() {
        log("&8  ╔══════════════════════════════════════╗");
        log("&8  ║  &a✔ &fEternalFrontier &ev" + getDescription().getVersion() + " &fda bat!       &8║");
        log("&8  ║  &a✔ &fTat ca he thong san sang.         &8║");
        log("&8  ║  &7» &fDung &e/et help &fde bat dau         &8║");
        log("&8  ╚══════════════════════════════════════╝");
    }

    private void log(String msg) {
        getServer().getConsoleSender().sendMessage(MessageManager.colorize(msg));
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private String spaces(int n) {
        return " ".repeat(n);
    }

    private void registerCommands() {
        EtCommand etCmd = new EtCommand(this);
        getCommand("et").setExecutor(etCmd);
        getCommand("et").setTabCompleter(etCmd);

        EtAdminCommand adminCmd = new EtAdminCommand(this);
        getCommand("etadmin").setExecutor(adminCmd);
        getCommand("etadmin").setTabCompleter(adminCmd);

        var walletCmd = etCmd.getWalletHandler();
        getCommand("wallet").setExecutor((sender, cmd, label, args) -> { walletCmd.execute(sender, args); return true; });

        var balCmd = etCmd.getBalanceHandler();
        getCommand("balance").setExecutor((sender, cmd, label, args) -> { balCmd.execute(sender, args); return true; });

        var payCmd = etCmd.getPayHandler();
        getCommand("pay").setExecutor((sender, cmd, label, args) -> { payCmd.execute(sender, args); return true; });

        var marketCmd = etCmd.getMarketHandler();
        getCommand("market").setExecutor((sender, cmd, label, args) -> { marketCmd.execute(sender, args); return true; });

        var zoneCmd = etCmd.getZoneHandler();
        getCommand("zone").setExecutor((sender, cmd, label, args) -> { zoneCmd.execute(sender, args); return true; });

        var clanCmd = etCmd.getClanHandler();
        getCommand("clan").setExecutor((sender, cmd, label, args) -> { clanCmd.execute(sender, args); return true; });

        var coreCmd = etCmd.getCoreHandler();
        getCommand("core").setExecutor((sender, cmd, label, args) -> { coreCmd.execute(sender, args); return true; });

        var topCmd = etCmd.getTopHandler();
        getCommand("top").setExecutor((sender, cmd, label, args) -> { topCmd.execute(sender, args); return true; });

        var questCmd = etCmd.getQuestHandler();
        getCommand("quest").setExecutor((sender, cmd, label, args) -> { questCmd.execute(sender, args); return true; });

        var exchangeCmd = etCmd.getExchangeHandler();
        getCommand("exchange").setExecutor((sender, cmd, label, args) -> { exchangeCmd.execute(sender, args); return true; });
    }

    private void registerListeners() {
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerSessionListener(this), this);
        pm.registerEvents(new RewardListener(this), this);
        pm.registerEvents(new ZoneListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new BlockBreakListener(this), this);
    }

    private void hookPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EFPlaceholderExpansion(this).register();
            log("&a  [OK] &fPlaceholderAPI da ket noi.");
        } else {
            log("&e  [!!] &fPlaceholderAPI khong tim thay. Placeholder se khong hoat dong.");
        }
    }

    public static EternalFrontier getInstance()           { return instance; }
    public DatabaseManager getDatabaseManager()           { return databaseManager; }
    public MessageManager getMessageManager()             { return messageManager; }
    public EconomyManager getEconomyManager()             { return economyManager; }
    public ZoneManager getZoneManager()                   { return zoneManager; }
    public ClanManager getClanManager()                   { return clanManager; }
    public CoreManager getCoreManager()                   { return coreManager; }
    public LeaderboardManager getLeaderboardManager()     { return leaderboardManager; }
    public QuestManager getQuestManager()                 { return questManager; }
    public HologramManager getHologramManager()           { return hologramManager; }
}

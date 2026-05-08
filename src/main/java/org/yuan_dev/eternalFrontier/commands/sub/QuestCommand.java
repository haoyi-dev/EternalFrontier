package org.yuan_dev.eternalFrontier.commands.sub;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.quest.QuestManager;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class QuestCommand implements SubCommand, Listener {
    private final EternalFrontier plugin;
    public QuestCommand(EternalFrontier plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        openQuestGui(p);
    }

    private void openQuestGui(Player p) {
        List<QuestManager.Quest> quests = plugin.getQuestManager().getQuests(p.getUniqueId());
        Inventory inv = GuiUtils.createGui("&8📋 &eDaily Quests", 3);
        GuiUtils.fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        int[] slots = {10, 12, 14, 16};
        Material[] mats = {Material.BONE, Material.DIAMOND_PICKAXE, Material.GOLD_NUGGET, Material.CLOCK};
        String[] typeNames = {"kill-mobs", "mine-blocks", "sell-items", "login"};

        for (int i = 0; i < Math.min(quests.size(), 4); i++) {
            QuestManager.Quest q = quests.get(i);
            String bar = GuiUtils.progressBar(q.progress(), q.target(), 10,
                q.claimed() ? "&7█" : "&a█", "&8░");
            String statusLine;
            if (q.claimed()) statusLine = "&a✔ Da nhan thuong";
            else if (q.isComplete()) statusLine = "&e► Click de nhan &6" + q.reward() + " Coins";
            else statusLine = "&7Tien do: &f" + q.progress() + "/" + q.target();

            Material mat = q.claimed() ? Material.LIME_CONCRETE : (q.isComplete() ? Material.GOLD_INGOT : mats[i]);
            String questName = plugin.getMessageManager().get("quest.quest-" + q.type());

            inv.setItem(slots[i], GuiUtils.buildItem(mat,
                (q.claimed() ? "&7✔ " : q.isComplete() ? "&e✦ " : "&f") + questName,
                "&8&m──────────────────",
                "&7" + bar,
                statusLine,
                "&8&m──────────────────",
                "&7Phan thuong: &6+" + q.reward() + " Coins"
            ));
        }

        if (quests.isEmpty()) {
            inv.setItem(13, GuiUtils.buildItem(Material.CLOCK, "&7Dang tai quest...", "&7Thu lai sau 1 giay"));
        }

        inv.setItem(22, GuiUtils.buildItem(Material.CLOCK,
            "&7Reset luc nua dem",
            "&7Quest moi moi ngay luc 00:00"
        ));
        inv.setItem(26, GuiUtils.closeButton());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(e.getView().title());
        if (!title.contains("Daily Quests")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int[] slots = {10, 12, 14, 16};
        int slot = e.getRawSlot();
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                final int idx = i;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean claimed = plugin.getQuestManager().claimReward(p, idx);
                    if (claimed) openQuestGui(p);
                });
                return;
            }
        }
        if (slot == 26) plugin.getServer().getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    @Override public String getPermission() { return "eternalfrontier.quest"; }
}

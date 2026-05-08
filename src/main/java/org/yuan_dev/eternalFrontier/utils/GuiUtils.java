package org.yuan_dev.eternalFrontier.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GuiUtils {

    public static ItemStack buildItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageManager.component(name));

        if (lore.length > 0) {
            List<Component> loreList = Arrays.stream(lore)
                .map(MessageManager::component)
                .collect(Collectors.toList());
            meta.lore(loreList);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildItem(Material material, String name, List<String> lore) {
        return buildItem(material, name, lore.toArray(new String[0]));
    }

    public static ItemStack buildGlowing(Material material, String name, String... lore) {
        ItemStack item = buildItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack buildSkull(Player player, String name, String... lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(MessageManager.component(name));
            if (lore.length > 0) {
                meta.lore(Arrays.stream(lore)
                    .map(MessageManager::component)
                    .collect(Collectors.toList()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    public static ItemStack buildFiller(Material material) {
        return buildItem(material, "&0", "");
    }

    public static void fillBorder(Inventory inv, Material material) {
        ItemStack filler = buildFiller(material);
        int size = inv.getSize();
        int rows = size / 9;

        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = size - 9; i < size; i++) inv.setItem(i, filler);
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler);
            inv.setItem(row * 9 + 8, filler);
        }
    }

    public static void fillAll(Inventory inv, Material material) {
        ItemStack filler = buildFiller(material);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    public static Inventory createGui(String title, int rows) {
        return Bukkit.createInventory(null, rows * 9,
            MessageManager.component(title));
    }

    public static ItemStack backButton() {
        return buildItem(Material.ARROW, "&c« Quay lai", "&7Click de quay lai");
    }

    public static ItemStack closeButton() {
        return buildItem(Material.BARRIER, "&cDong", "&7Click de dong");
    }

    public static ItemStack nextPageButton() {
        return buildItem(Material.ARROW, "&aPage sau »", "&7Click de xem page tiep theo");
    }

    public static ItemStack prevPageButton() {
        return buildItem(Material.ARROW, "&c« Page truoc", "&7Click de xem page truoc");
    }

    public static ItemStack infoButton(String... lore) {
        return buildItem(Material.BOOK, "&eThong tin", lore);
    }

    public static String formatCoins(long amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    public static String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    public static List<String> wrapLore(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder("&7");
        for (String word : words) {
            if (current.length() + word.length() > maxWidth) {
                lines.add(current.toString().trim());
                current = new StringBuilder("&7");
            }
            current.append(word).append(" ");
        }
        if (!current.toString().trim().isEmpty()) lines.add(current.toString().trim());
        return lines;
    }

    public static String progressBar(int current, int max, int bars, String filled, String empty) {
        int filledBars = (int) ((double) current / max * bars);
        return filled.repeat(Math.max(0, filledBars)) +
               empty.repeat(Math.max(0, bars - filledBars));
    }
}

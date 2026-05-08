package org.yuan_dev.eternalFrontier.utils;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class MessageManager {

    private final EternalFrontier plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(EternalFrontier plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = colorize(plugin.getConfig().getString("prefix", "&8[&6⚔ EF&8] "));
    }

    public String get(String key) {
        String raw = messages.getString(key, "&cMissing message: " + key);
        return colorize(raw).replace("{prefix}", prefix);
    }

    public String get(String key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    public String get(String key, Map<String, String> placeholders) {
        String msg = get(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(colorize(get(key)));
    }

    public void send(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(colorize(get(key, replacements)));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(colorize(get(key, placeholders)));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    public Component toComponent(String key) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(get(key));
    }

    public Component toComponent(String key, String... replacements) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(get(key, replacements));
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    public static Component component(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public String getPrefix() {
        return prefix;
    }

    public FileConfiguration getMessages() {
        return messages;
    }
}

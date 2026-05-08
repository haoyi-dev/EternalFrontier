package org.yuan_dev.eternalFrontier.commands.sub;

import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;

public interface SubCommand {
    void execute(CommandSender sender, String[] args);
    default List<String> tabComplete(CommandSender sender, String[] args) { return new ArrayList<>(); }
    String getPermission();
    default boolean requiresPlayer() { return true; }
}

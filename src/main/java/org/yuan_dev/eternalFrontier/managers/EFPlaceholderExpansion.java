package org.yuan_dev.eternalFrontier.managers;

import org.yuan_dev.eternalFrontier.EternalFrontier;
import org.yuan_dev.eternalFrontier.clan.ClanData;
import org.yuan_dev.eternalFrontier.core.CoreData;
import org.yuan_dev.eternalFrontier.utils.GuiUtils;
import org.yuan_dev.eternalFrontier.zone.Zone;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class EFPlaceholderExpansion extends PlaceholderExpansion {

    private final EternalFrontier plugin;

    public EFPlaceholderExpansion(EternalFrontier plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "eternal"; }
    @Override public @NotNull String getAuthor()     { return "yuan_dev"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        return switch (params.toLowerCase()) {
            case "balance" -> {
                long bal = plugin.getEconomyManager().getBalance(player.getUniqueId());
                yield GuiUtils.formatCoins(bal);
            }
            case "balance_raw" -> {
                yield String.valueOf(plugin.getEconomyManager().getBalance(player.getUniqueId()));
            }
            case "zone" -> {
                if (!player.isOnline()) yield "?";
                Zone zone = plugin.getZoneManager().getPlayerZone(player.getUniqueId());
                yield zone.getDisplayName().replace("&", "§");
            }
            case "zone_number" -> {
                if (!player.isOnline()) yield "0";
                yield String.valueOf(plugin.getZoneManager().getPlayerZone(player.getUniqueId()).getNumber());
            }
            case "clan_name" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                yield clan != null ? clan.name() : plugin.getMessageManager().get("placeholder.clan-name");
            }
            case "clan_tag" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                yield clan != null ? clan.tag() : plugin.getMessageManager().get("placeholder.clan-tag");
            }
            case "clan_prefix" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                yield clan != null ? clan.getFormattedPrefix() : "";
            }
            case "clan_tier" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                yield clan != null ? String.valueOf(clan.tier()) : "0";
            }
            case "clan_rank" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan == null) yield plugin.getMessageManager().get("placeholder.clan-rank");
                var rank = clan.getRank(player.getUniqueId());
                yield rank != null ? rank.getDisplayName().replace("&", "§") : "?";
            }
            case "core_hp" -> {
                ClanData clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
                if (clan == null) yield "N/A";
                CoreData core = plugin.getCoreManager().getCoreByClan(clan.name());
                yield core != null ? String.valueOf(core.hp()) : "N/A";
            }
            case "eco_rank" -> {
                int rank = plugin.getLeaderboardManager().getRank(
                    org.yuan_dev.eternalFrontier.leaderboard.LeaderboardManager.LeaderboardType.ECO,
                    player.getUniqueId().toString());
                yield rank > 0 ? "#" + rank : "Chua xep hang";
            }
            default -> null;
        };
    }
}

package com.nemonicorp.loui;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Expansao PlaceholderAPI.
 *
 * ATENCAO: esta classe so pode ser referenciada quando a PlaceholderAPI esta
 * instalada. Toca-la sem ela lanca NoClassDefFoundError e derruba o onEnable.
 */
public class LouiPlaceholders extends PlaceholderExpansion {

    private final LouiPlugin plugin;
    private final PrisonManager manager;

    public LouiPlaceholders(LouiPlugin plugin, PrisonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "loui";
    }

    @Override
    public String getAuthor() {
        return "GEPR";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // Nao depende de jogador: vale ate no console.
        if ("count".equalsIgnoreCase(params)) {
            return String.valueOf(manager.getPrisonerCount());
        }

        if (player == null) return "";

        boolean jailed = manager.isImprisoned(player.getUniqueId());

        if ("jailed".equalsIgnoreCase(params)) {
            return jailed
                    ? plugin.getConfig().getString("messages.placeholder-yes", "sim")
                    : plugin.getConfig().getString("messages.placeholder-no", "nao");
        }

        if ("tag".equalsIgnoreCase(params)) {
            if (!jailed) return "";
            return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.tab-tag", "&c[APRISIONADO] "));
        }

        // Vazio (e nao "00:00") pra quem nao esta contido: permite esconder o
        // campo no scoreboard em vez de obrigar o admin a tratar o zero.
        if (!jailed) return "";

        if ("time_left".equalsIgnoreCase(params)) {
            return TimeParser.formatClock(manager.getRemainingMillis(player.getUniqueId()));
        }
        if ("reason".equalsIgnoreCase(params)) {
            return manager.getReason(player.getUniqueId());
        }

        // null faz a PlaceholderAPI exibir o placeholder cru, sinalizando erro de digitacao.
        return null;
    }
}

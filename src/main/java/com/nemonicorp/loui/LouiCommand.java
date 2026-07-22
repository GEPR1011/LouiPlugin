package com.nemonicorp.loui;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Parsing de /loui, tab-complete e checagem de imunidade. */
public class LouiCommand implements CommandExecutor, TabCompleter {

    private final PrisonManager manager;

    public LouiCommand(PrisonManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loui.use")) {
            sender.sendMessage(manager.msg("no-permission", "&cSem permissao."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m, 2h ou 2d> <motivo...>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            manager.sendList(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("free")) {
            if (args.length < 2) {
                sender.sendMessage(manager.msg("usage", "&7Uso: /loui free <nick>"));
                return true;
            }
            manager.freeByName(sender, args[1]);
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m, 2h ou 2d> <motivo...>"));
            return true;
        }

        long minutes = TimeParser.parse(args[1]);
        if (minutes <= 0) {
            sender.sendMessage(manager.msg("usage", "&7Tempo invalido. Use minutos, ou sufixo m/h/d. Ex: 30m, 2h, 2d."));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String reason = sb.toString();

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            if (target.hasPermission("loui.exempt")) {
                sender.sendMessage(manager.msg("is-exempt", "&cEsse jogador e imune ao castigo."));
                return true;
            }
            if (!manager.imprison(target, minutes, reason, sender.getName())) {
                sender.sendMessage(manager.msg("imprison-vetoed",
                        "&cA punicao foi impedida por outro plugin."));
            }
            return true;
        }

        // Alvo offline: consulta o usercache local. NUNCA usar getOfflinePlayer(String),
        // que dispara HTTP a Mojang na thread principal em servidor online-mode.
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (offline == null || !offline.hasPlayedBefore()) {
            sender.sendMessage(manager.msg("never-joined", "&cEsse jogador nunca entrou no servidor."));
            return true;
        }

        if (!manager.imprisonOffline(offline, minutes, reason, sender.getName())) {
            sender.sendMessage(manager.msg("imprison-vetoed",
                    "&cA punicao foi impedida por outro plugin."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("loui.use")) return out;

        if (args.length == 1) {
            String a = args[0].toLowerCase();
            if ("free".startsWith(a)) out.add("free");
            if ("list".startsWith(a)) out.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a)) out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("free")) {
            String a = args[1].toLowerCase();
            for (String name : manager.getPrisonerNames()) {
                if (name.toLowerCase().startsWith(a)) out.add(name);
            }
        } else if (args.length == 2) {
            out.add("30m");
            out.add("2h");
            out.add("1d");
        }
        return out;
    }
}

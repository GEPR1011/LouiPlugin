package com.nemonicorp.loui;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * LouiPlugin — castigo void.
 *
 * /loui <nick> <tempo> <motivo...>    → joga o jogador num vazio escuro em queda
 *   tempo: minutos puros (30), sufixo de minutos (30m) ou de dias (2d).
 *                                       constante, invulneravel, sem drops e sem
 *                                       comandos, com bossbar (motivo + tempo).
 * /loui free <nick>                   → liberta antes da hora.
 * /loui list                          → lista os contidos.
 *
 * O jogador volta EXATAMENTE pro lugar (e gamemode) em que estava.
 */
public class LouiPlugin extends JavaPlugin {

    private PrisonManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new PrisonManager(this);
        manager.load();

        getServer().getPluginManager().registerEvents(new LouiListener(this, manager), this);

        // Tick de 1s: bossbar, expiracao e loop de queda
        Bukkit.getScheduler().runTaskTimer(this, () -> manager.tick(), 20L, 20L);

        // Reaplicar castigo em quem ja esta online (reload/restart)
        for (Player p : Bukkit.getOnlinePlayers()) {
            manager.handleJoin(p);
        }

        getLogger().info("LouiPlugin habilitado.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public PrisonManager getManager() {
        return manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loui.use")) {
            sender.sendMessage(manager.msg("no-permission", "&cSem permissao."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m ou 2d> <motivo...>"));
            return true;
        }

        // /loui list
        if (args[0].equalsIgnoreCase("list")) {
            manager.sendList(sender);
            return true;
        }

        // /loui free <nick>
        if (args[0].equalsIgnoreCase("free")) {
            if (args.length < 2) {
                sender.sendMessage(manager.msg("usage", "&7Uso: /loui free <nick>"));
                return true;
            }
            manager.freeByName(sender, args[1]);
            return true;
        }

        // /loui <nick> <minutos> <motivo...>
        if (args.length < 3) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m ou 2d> <motivo...>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(manager.msg("not-online", "&cJogador nao esta online."));
            return true;
        }

        long minutes = parseMinutes(args[1]);
        if (minutes <= 0) {
            sender.sendMessage(manager.msg("usage", "&7Tempo invalido. Use minutos, ou sufixo m/d. Ex: 30m, 2d."));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String reason = sb.toString();

        manager.imprison(target, minutes, reason, sender.getName());
        return true;
    }

    /**
     * Converte o argumento de tempo em minutos.
     * Aceita minutos puros ("30"), sufixo de minutos ("30m") ou de dias ("2d").
     * Retorna -1 quando o formato e invalido ou o valor nao e positivo.
     */
    static long parseMinutes(String input) {
        if (input == null || input.isEmpty()) return -1;
        String s = input.toLowerCase();
        long multiplier = 1L; // minutos por unidade
        char last = s.charAt(s.length() - 1);
        if (last == 'd') {
            multiplier = 1440L; // 1 dia = 1440 min
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            multiplier = 1L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1;
        try {
            long value = Long.parseLong(s);
            if (value <= 0) return -1;
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return -1;
        }
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
            out.add("5");
            out.add("10");
            out.add("30");
        }
        return out;
    }
}

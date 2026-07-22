package com.nemonicorp.loui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

        LouiCommand executor = new LouiCommand(manager);
        getCommand("loui").setExecutor(executor);
        getCommand("loui").setTabCompleter(executor);

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
}

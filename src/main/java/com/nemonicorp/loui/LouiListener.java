package com.nemonicorp.loui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Bloqueios enquanto o jogador esta contido: dano (recebido e causado), drops,
 * pickup, fome, comandos e teleportes externos.
 */
public class LouiListener implements Listener {

    private final LouiPlugin plugin;
    private final PrisonManager manager;

    public LouiListener(LouiPlugin plugin, PrisonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // Invulneravel: nao recebe dano nenhum (inclui void, queda, ataques)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && manager.isImprisoned(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Tambem nao causa dano em ninguem
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && manager.isImprisoned(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Sem dropar itens
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isImprisoned(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Sem pegar itens
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && manager.isImprisoned(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Sem fome
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && manager.isImprisoned(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Nenhum comando durante o castigo
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (manager.isImprisoned(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(manager.msg("no-commands", "&cVoce nao pode usar comandos enquanto estiver contido."));
        }
    }

    // Bloquear teleportes de OUTRAS fontes (ender pearl, outros plugins, /spawn etc.)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (!manager.isImprisoned(p.getUniqueId())) return;
        if (manager.isInternalTeleport(p.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }
}

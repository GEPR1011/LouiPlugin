package com.nemonicorp.loui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Disparado quando um castigo termina, seja qual for o motivo.
 *
 * NAO e cancelavel de proposito: vetar uma soltura prenderia o jogador para
 * sempre e, durante o desligamento, o deixaria cego e invulneravel sem plugin
 * carregado para desfazer.
 */
public class LouiReleaseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String reason;
    private final ReleaseCause cause;

    public LouiReleaseEvent(Player player, String reason, ReleaseCause cause) {
        this.player = player;
        this.reason = reason;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    /** O motivo original do castigo. */
    public String getReason() {
        return reason;
    }

    public ReleaseCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

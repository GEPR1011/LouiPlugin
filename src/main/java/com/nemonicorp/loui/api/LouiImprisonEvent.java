package com.nemonicorp.loui.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Disparado antes de um castigo ser aplicado.
 *
 * Cancelar impede a punicao por completo: nenhum estado e alterado, nenhum
 * teleporte acontece, e o moderador recebe aviso de que outro plugin impediu.
 */
public class LouiImprisonEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer target;
    private final long minutes;
    private final String reason;
    private final String byWhom;
    private final boolean offline;
    private boolean cancelled;

    public LouiImprisonEvent(OfflinePlayer target, long minutes, String reason,
                             String byWhom, boolean offline) {
        this.target = target;
        this.minutes = minutes;
        this.reason = reason;
        this.byWhom = byWhom;
        this.offline = offline;
    }

    /** O alvo. E um Player quando isOffline() e false. */
    public OfflinePlayer getTarget() {
        return target;
    }

    /** Duracao do castigo em minutos. */
    public long getMinutes() {
        return minutes;
    }

    public String getReason() {
        return reason;
    }

    /** Nome de quem executou o comando, ou do console. */
    public String getByWhom() {
        return byWhom;
    }

    /** true quando o alvo nao estava online no momento do comando. */
    public boolean isOffline() {
        return offline;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

package com.nemonicorp.loui.api;

import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LouiEventsTest {

    @Test
    void imprisonEventGuardaOsCampos() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 120L, "griefing", "Steve", true);
        assertNull(event.getTarget());
        assertEquals(120L, event.getMinutes());
        assertEquals("griefing", event.getReason());
        assertEquals("Steve", event.getByWhom());
        assertTrue(event.isOffline());
    }

    @Test
    void imprisonEventComecaNaoCancelado() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 30L, "teste", "Alex", false);
        assertFalse(event.isCancelled());
        assertFalse(event.isOffline());
    }

    @Test
    void imprisonEventPodeSerCanceladoEDescancelado() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 30L, "teste", "Alex", false);
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    void releaseEventGuardaOsCampos() {
        LouiReleaseEvent event = new LouiReleaseEvent(null, "griefing", ReleaseCause.EXPIRED);
        assertNull(event.getPlayer());
        assertEquals("griefing", event.getReason());
        assertEquals(ReleaseCause.EXPIRED, event.getCause());
    }

    @Test
    void releaseEventNaoEhCancelavel() {
        assertFalse(Cancellable.class.isAssignableFrom(LouiReleaseEvent.class),
                "LouiReleaseEvent nao pode ser cancelavel: vetar uma soltura prenderia o "
                        + "jogador para sempre e, no desligamento, o deixaria cego e "
                        + "invulneravel sem plugin para desfazer");
    }

    @Test
    void releaseCauseTemExatamenteQuatroValores() {
        assertArrayEquals(
                new ReleaseCause[] {
                        ReleaseCause.MANUAL, ReleaseCause.EXPIRED,
                        ReleaseCause.SHUTDOWN, ReleaseCause.EXEMPT },
                ReleaseCause.values());
    }

    @Test
    void handlerListEstaticaEhAMesmaDaInstancia() {
        LouiImprisonEvent imprison = new LouiImprisonEvent(null, 1L, "x", "y", false);
        assertSame(LouiImprisonEvent.getHandlerList(), imprison.getHandlers());

        LouiReleaseEvent release = new LouiReleaseEvent(null, "x", ReleaseCause.MANUAL);
        assertSame(LouiReleaseEvent.getHandlerList(), release.getHandlers());
    }
}

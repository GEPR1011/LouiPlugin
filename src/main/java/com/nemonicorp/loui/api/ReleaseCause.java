package com.nemonicorp.loui.api;

/** Por que um castigo terminou. */
public enum ReleaseCause {

    /** Soltura manual via /loui free. */
    MANUAL,

    /** A pena chegou ao fim. */
    EXPIRED,

    /** Release em massa ao desabilitar o plugin. */
    SHUTDOWN,

    /** Punicao descartada no login por o jogador ter loui.exempt. */
    EXEMPT
}

package com.nemonicorp.loui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRootTest {

    @Test
    void extraiRaizComBarra() {
        assertEquals("msg", LouiListener.commandRoot("/msg alguem oi"));
    }

    @Test
    void extraiRaizSemBarra() {
        assertEquals("msg", LouiListener.commandRoot("msg"));
    }

    @Test
    void normalizaCaixaEEspacos() {
        assertEquals("msg", LouiListener.commandRoot("  /MSG  alguem "));
    }

    @Test
    void comandoSemArgumentos() {
        assertEquals("home", LouiListener.commandRoot("/home"));
    }

    @Test
    void entradasVazias() {
        assertEquals("", LouiListener.commandRoot(null));
        assertEquals("", LouiListener.commandRoot(""));
        assertEquals("", LouiListener.commandRoot("/"));
    }
}

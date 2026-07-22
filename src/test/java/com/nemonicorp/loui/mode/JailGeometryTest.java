package com.nemonicorp.loui.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JailGeometryTest {

    @Test
    void dentroDoRaioNaoEstaFora() {
        assertFalse(JailMode.foraDoRaio(3.0, 4.0, 10.0));
    }

    @Test
    void exatamenteNoRaioNaoEstaFora() {
        // 3-4-5: a distancia e exatamente 5
        assertFalse(JailMode.foraDoRaio(3.0, 4.0, 5.0));
    }

    @Test
    void alemDoRaioEstaFora() {
        assertTrue(JailMode.foraDoRaio(3.0, 4.0, 4.9));
    }

    @Test
    void centroExatoNaoEstaFora() {
        assertFalse(JailMode.foraDoRaio(0.0, 0.0, 1.0));
    }

    @Test
    void distanciaNegativaContaComoPositiva() {
        assertTrue(JailMode.foraDoRaio(-10.0, 0.0, 5.0));
        assertFalse(JailMode.foraDoRaio(-3.0, -4.0, 5.0));
    }

    @Test
    void raioZeroDeixaTudoFora() {
        assertTrue(JailMode.foraDoRaio(0.1, 0.0, 0.0));
    }
}

package com.nemonicorp.loui.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BandPoolTest {

    @Test
    void allocateDistribuiFaixasEmOrdem() {
        BandPool pool = new BandPool();
        assertEquals(0, pool.allocate(3));
        assertEquals(1, pool.allocate(3));
        assertEquals(2, pool.allocate(3));
    }

    @Test
    void allocateComPoolEsgotadoDevolveSempreAUltima() {
        BandPool pool = new BandPool();
        pool.allocate(2);
        pool.allocate(2);

        assertEquals(1, pool.allocate(2));
        assertEquals(1, pool.allocate(2));
        assertEquals(1, pool.allocate(2));
    }

    @Test
    void releaseDeUmDivisorMantemFaixaOcupada() {
        BandPool pool = new BandPool();
        pool.allocate(1);
        pool.allocate(1);
        pool.allocate(1);
        assertEquals(3, pool.occupantsOf(0));

        pool.release(0);

        assertEquals(2, pool.occupantsOf(0));
    }

    @Test
    void releaseDoUltimoDivisorLiberaEFaixaEReaproveitada() {
        BandPool pool = new BandPool();
        pool.allocate(1);
        pool.allocate(1);
        assertEquals(2, pool.occupantsOf(0));

        pool.release(0);
        pool.release(0);
        assertEquals(0, pool.occupantsOf(0));

        assertEquals(0, pool.allocate(1));
    }

    @Test
    void faixaDoMeioLiberadaEReaproveitadaAntesDaUltima() {
        BandPool pool = new BandPool();
        pool.allocate(3); // 0
        pool.allocate(3); // 1
        pool.allocate(3); // 2

        pool.release(1);

        assertEquals(1, pool.allocate(3));
    }

    @Test
    void reserveMarcaFaixaOcupadaEExigeReleasesEmpilhados() {
        BandPool pool = new BandPool();
        pool.reserve(2);
        assertEquals(1, pool.occupantsOf(2));

        pool.reserve(2);
        assertEquals(2, pool.occupantsOf(2));

        pool.release(2);
        assertEquals(1, pool.occupantsOf(2));

        pool.release(2);
        assertEquals(0, pool.occupantsOf(2));
    }

    @Test
    void releaseEmFaixaNegativaOuNuncaAlocadaNaoFazNada() {
        BandPool pool = new BandPool();

        pool.release(-1);
        pool.release(5);

        assertEquals(0, pool.occupantsOf(-1));
        assertEquals(0, pool.occupantsOf(5));
    }

    @Test
    void allocateComMaxZeroOuNegativoLimitaAUmaFaixa() {
        BandPool poolZero = new BandPool();
        assertEquals(0, poolZero.allocate(0));
        assertEquals(0, poolZero.allocate(0));

        BandPool poolNegativo = new BandPool();
        assertEquals(0, poolNegativo.allocate(-1));
        assertEquals(0, poolNegativo.allocate(-1));
    }
}

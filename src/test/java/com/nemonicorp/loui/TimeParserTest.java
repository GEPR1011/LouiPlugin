package com.nemonicorp.loui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeParserTest {

    @Test
    void parseAceitaMinutosPuros() {
        assertEquals(30L, TimeParser.parse("30"));
    }

    @Test
    void parseAceitaSufixoDeMinutos() {
        assertEquals(30L, TimeParser.parse("30m"));
    }

    @Test
    void parseAceitaSufixoDeHoras() {
        assertEquals(120L, TimeParser.parse("2h"));
    }

    @Test
    void parseAceitaSufixoDeDias() {
        assertEquals(2880L, TimeParser.parse("2d"));
    }

    @Test
    void parseIgnoraCaixa() {
        assertEquals(120L, TimeParser.parse("2H"));
        assertEquals(2880L, TimeParser.parse("2D"));
    }

    @Test
    void parseRejeitaEntradasInvalidas() {
        assertEquals(-1L, TimeParser.parse("0"));
        assertEquals(-1L, TimeParser.parse("-5"));
        assertEquals(-1L, TimeParser.parse("abc"));
        assertEquals(-1L, TimeParser.parse("h"));
        assertEquals(-1L, TimeParser.parse(""));
        assertEquals(-1L, TimeParser.parse(null));
    }

    @Test
    void parseRejeitaOverflow() {
        assertEquals(-1L, TimeParser.parse("9223372036854775807d"));
    }

    @Test
    void formatClockUsaMinutosESegundos() {
        assertEquals("00:00", TimeParser.formatClock(0L));
        assertEquals("00:59", TimeParser.formatClock(59_000L));
    }

    @Test
    void formatClockUsaHorasQuandoNecessario() {
        assertEquals("1:00:00", TimeParser.formatClock(3_600_000L));
    }

    @Test
    void formatClockUsaDiasQuandoNecessario() {
        assertEquals("1d 00:00:00", TimeParser.formatClock(86_400_000L));
    }

    @Test
    void formatDurationEscreveTextoAmigavel() {
        assertEquals("0min", TimeParser.formatDuration(0L));
        assertEquals("30min", TimeParser.formatDuration(30L));
        assertEquals("1h", TimeParser.formatDuration(60L));
        assertEquals("1h 30min", TimeParser.formatDuration(90L));
        assertEquals("1d", TimeParser.formatDuration(1440L));
        assertEquals("2d 5h 30min", TimeParser.formatDuration(3210L));
    }
}

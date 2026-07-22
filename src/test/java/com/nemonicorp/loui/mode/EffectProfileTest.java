package com.nemonicorp.loui.mode;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectProfileTest {

    private static YamlConfiguration secao(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return cfg;
    }

    @Test
    void leOsQuatroFlagsDaSecao() {
        EffectProfile p = EffectProfile.fromConfig(
                secao("darkness: true\nblindness: false\ninvulnerable: true\nadventure-mode: false"),
                false, false);
        assertTrue(p.isDarkness());
        assertFalse(p.isBlindness());
        assertTrue(p.isInvulnerable());
        assertFalse(p.isAdventureMode());
    }

    @Test
    void secaoNulaUsaOsDefaults() {
        EffectProfile p = EffectProfile.fromConfig(null, true, true);
        assertTrue(p.isDarkness());
        assertTrue(p.isBlindness());
        assertTrue(p.isInvulnerable());
        assertTrue(p.isAdventureMode());
    }

    @Test
    void chavesAusentesCaemNosDefaults() {
        EffectProfile p = EffectProfile.fromConfig(secao("darkness: false"), true, true);
        assertFalse(p.isDarkness());
        assertTrue(p.isBlindness());
    }

    @Test
    void defaultDoVazioMantemEscuridao() {
        EffectProfile p = EffectProfile.fromConfig(null, true, true);
        assertTrue(p.isDarkness());
        assertTrue(p.isBlindness());
    }

    @Test
    void defaultDaJailNaoCega() {
        EffectProfile p = EffectProfile.fromConfig(null, false, false);
        assertFalse(p.isDarkness());
        assertFalse(p.isBlindness());
        assertTrue(p.isInvulnerable());
        assertTrue(p.isAdventureMode());
    }
}

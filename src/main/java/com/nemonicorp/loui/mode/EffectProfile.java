package com.nemonicorp.loui.mode;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Quais efeitos um modo de castigo aplica.
 *
 * Desfaz apenas o que aplicou: um perfil com cegueira desligada nao remove
 * cegueira de ninguem, porque ela pode ter vindo de outro plugin ou de pocao.
 */
public class EffectProfile {

    private final boolean darkness;
    private final boolean blindness;
    private final boolean invulnerable;
    private final boolean adventureMode;

    private EffectProfile(boolean darkness, boolean blindness,
                          boolean invulnerable, boolean adventureMode) {
        this.darkness = darkness;
        this.blindness = blindness;
        this.invulnerable = invulnerable;
        this.adventureMode = adventureMode;
    }

    /**
     * Le a secao de config. Secao nula ou chave ausente cai no default.
     * invulnerable e adventure-mode tem default true nos dois modos.
     */
    public static EffectProfile fromConfig(ConfigurationSection section,
                                           boolean defaultDarkness, boolean defaultBlindness) {
        if (section == null) {
            return new EffectProfile(defaultDarkness, defaultBlindness, true, true);
        }
        return new EffectProfile(
                section.getBoolean("darkness", defaultDarkness),
                section.getBoolean("blindness", defaultBlindness),
                section.getBoolean("invulnerable", true),
                section.getBoolean("adventure-mode", true));
    }

    public boolean isDarkness() {
        return darkness;
    }

    public boolean isBlindness() {
        return blindness;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public boolean isAdventureMode() {
        return adventureMode;
    }

    /** Aplica o estado inicial. */
    public void apply(Player player) {
        if (adventureMode) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
        }
        player.setInvulnerable(invulnerable);
        reassert(player);
    }

    /** Reaplica o que outro plugin possa ter removido. Chamado a cada tick de 1s. */
    public void reassert(Player player) {
        if (invulnerable && !player.isInvulnerable()) {
            player.setInvulnerable(true);
        }
        if (darkness && !player.hasPotionEffect(PotionEffectType.DARKNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                    PotionEffect.INFINITE_DURATION, 0, true, false));
        }
        if (blindness && !player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    PotionEffect.INFINITE_DURATION, 0, true, false));
        }
    }

    /** Desfaz somente o que este perfil aplicou. */
    public void undo(Player player) {
        if (darkness) player.removePotionEffect(PotionEffectType.DARKNESS);
        if (blindness) player.removePotionEffect(PotionEffectType.BLINDNESS);
        if (invulnerable) player.setInvulnerable(false);
    }
}

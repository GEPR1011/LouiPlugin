package com.nemonicorp.loui.mode;

import com.nemonicorp.loui.PrisonManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Como o castigo se manifesta no mundo.
 *
 * O PrisonManager orquestra; a implementacao decide se o preso cai num vazio
 * ou fica contido numa regiao. Vocabulario especifico de cada modo — faixa de
 * altura, raio — nao vaza para fora daqui.
 */
public interface PunishmentMode {

    /** Identificador gravado no prisons.yml. Nao renomear: e dado em disco. */
    String id();

    /** false quando a config esta incompleta e aplicar o castigo quebraria. */
    boolean isConfigured();

    /** O perfil de efeitos deste modo. */
    EffectProfile effects();

    /** Coloca o jogador sob castigo. */
    void apply(Player player, PrisonManager.Prison prison);

    /** Desfaz o castigo e devolve o jogador ao lugar de origem. */
    void restore(Player player, Location returnLocation, GameMode gameMode, boolean allowFlight);

    /** Mantem o preso onde deve estar e reafirma os efeitos. Chamado a cada 1s. */
    void contain(Player player, PrisonManager.Prison prison);

    /** true quando o teleporte em curso foi feito pelo proprio plugin. */
    boolean isInternalTeleport(UUID uuid);
}

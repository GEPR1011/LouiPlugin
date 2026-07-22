package com.nemonicorp.loui;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado fisico do castigo: teleporte, efeitos e faixas de altura.
 *
 * Cada preso ocupa uma faixa de Y propria, para que dois presos simultaneos nao
 * ocupem o mesmo ponto e se empurrem por colisao. Como o X/Z e compartilhado,
 * apenas UM chunk fica carregado independentemente de quantos presos existam.
 */
public class VoidState {

    private final LouiPlugin plugin;
    private final Set<Integer> usedBands = new HashSet<>();
    /** Teleportes feitos pelo proprio plugin (pra nao serem cancelados pelo listener). */
    private final Set<UUID> internalTeleport = new HashSet<>();

    public VoidState(LouiPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Faixas ──

    /** Menor faixa livre. Com o pool esgotado, devolve a ultima (presos passam a dividi-la). */
    public int allocateBand() {
        int max = Math.max(1, plugin.getConfig().getInt("void.max-bands", 8));
        for (int i = 0; i < max; i++) {
            if (usedBands.add(i)) return i;
        }
        return max - 1;
    }

    public void reserveBand(int band) {
        if (band >= 0) usedBands.add(band);
    }

    public void releaseBand(int band) {
        usedBands.remove(band);
    }

    /** Distancia entre o topo de uma faixa e o topo da seguinte. */
    private double bandStep() {
        double topY = plugin.getConfig().getDouble("void.top-y", 5000.0);
        double minY = plugin.getConfig().getDouble("void.min-y", 1500.0);
        double gap = plugin.getConfig().getDouble("void.band-gap", 100.0);
        return (topY - minY) + gap;
    }

    private double bandTop(int band) {
        double topY = plugin.getConfig().getDouble("void.top-y", 5000.0);
        return topY - Math.max(0, band) * bandStep();
    }

    /** Altura em que o jogador e reposicionado no topo da sua faixa. */
    public double bandFloor(int band) {
        double minY = plugin.getConfig().getDouble("void.min-y", 1500.0);
        return minY - Math.max(0, band) * bandStep();
    }

    // ── Estado ──

    /** Aplica o estado de vazio: gamemode, invulnerabilidade, teleporte e efeitos. */
    public void apply(Player player, PrisonManager.Prison prison) {
        if (prison.band < 0) prison.band = allocateBand();

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setInvulnerable(true);
        player.setFallDistance(0f);

        teleportToTop(player, prison.band);
        applyEffects(player);
    }

    /** Desfaz o estado de vazio e devolve o jogador ao lugar de origem. */
    public void restore(Player player, Location returnLocation, GameMode gameMode, boolean allowFlight) {
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setInvulnerable(false);
        player.setFallDistance(0f);

        internalTeleport.add(player.getUniqueId());
        try {
            if (returnLocation != null && returnLocation.getWorld() != null) {
                player.teleport(returnLocation);
            } else {
                player.teleport(player.getWorld().getSpawnLocation());
            }
        } finally {
            internalTeleport.remove(player.getUniqueId());
        }

        player.setFallDistance(0f);
        if (gameMode != null) player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
    }

    public void teleportToTop(Player player, int band) {
        double x = plugin.getConfig().getDouble("void.x", 250000.5);
        double z = plugin.getConfig().getDouble("void.z", 250000.5);

        Location loc = new Location(voidWorld(), x, bandTop(band), z);
        internalTeleport.add(player.getUniqueId());
        try {
            player.teleport(loc);
        } finally {
            internalTeleport.remove(player.getUniqueId());
        }
        player.setFallDistance(0f);
    }

    /** Reaplica efeitos que outro plugin possa ter removido. Chamado a cada tick de 1s. */
    public void applyEffects(Player player) {
        if (!player.hasPotionEffect(PotionEffectType.DARKNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                    PotionEffect.INFINITE_DURATION, 0, true, false));
        }
        if (!player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    PotionEffect.INFINITE_DURATION, 0, true, false));
        }
    }

    public boolean isInternalTeleport(UUID uuid) {
        return internalTeleport.contains(uuid);
    }

    private World voidWorld() {
        String name = plugin.getConfig().getString("void.world", "");
        World w = (name == null || name.isEmpty()) ? null : Bukkit.getWorld(name);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return w;
    }
}

package com.nemonicorp.loui.mode;

import com.nemonicorp.loui.LouiPlugin;
import com.nemonicorp.loui.PrisonManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Castigo por queda infinita num vazio escuro.
 *
 * Cada preso ocupa uma faixa de Y propria, para que dois presos simultaneos nao
 * ocupem o mesmo ponto e se empurrem por colisao. Como o X/Z e compartilhado,
 * apenas UM chunk fica carregado independentemente de quantos presos existam.
 */
public class VoidMode implements PunishmentMode {

    private final LouiPlugin plugin;
    private final BandPool bands = new BandPool();
    /** Teleportes feitos pelo proprio plugin (pra nao serem cancelados pelo listener). */
    private final Set<UUID> internalTeleport = new HashSet<>();

    public VoidMode(LouiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "void";
    }

    /** O vazio tem default pra tudo, entao esta sempre configurado. */
    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public EffectProfile effects() {
        return EffectProfile.fromConfig(
                plugin.getConfig().getConfigurationSection("void.effects"), true, true);
    }

    @Override
    public void apply(Player player, PrisonManager.Prison prison) {
        if (prison.getBand() < 0) prison.setBand(allocateBand());

        effects().apply(player);
        player.setFallDistance(0f);
        teleportToTop(player, prison.getBand());
    }

    @Override
    public void restore(Player player, Location returnLocation, GameMode gameMode, boolean allowFlight) {
        effects().undo(player);
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

    @Override
    public void contain(Player player, PrisonManager.Prison prison) {
        if (player.getLocation().getY() < bandFloor(prison.getBand())) {
            teleportToTop(player, prison.getBand());
        }
        effects().reassert(player);
    }

    @Override
    public boolean isInternalTeleport(UUID uuid) {
        return internalTeleport.contains(uuid);
    }

    // ── Faixas ──

    public int allocateBand() {
        return bands.allocate(Math.max(1, plugin.getConfig().getInt("void.max-bands", 8)));
    }

    public void reserveBand(int band) {
        bands.reserve(band);
    }

    public void releaseBand(int band) {
        bands.release(band);
    }

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

    private double bandFloor(int band) {
        double minY = plugin.getConfig().getDouble("void.min-y", 1500.0);
        return minY - Math.max(0, band) * bandStep();
    }

    private void teleportToTop(Player player, int band) {
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

    private World voidWorld() {
        String name = plugin.getConfig().getString("void.world", "");
        World w = (name == null || name.isEmpty()) ? null : Bukkit.getWorld(name);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return w;
    }
}

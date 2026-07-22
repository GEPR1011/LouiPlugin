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
 * Castigo numa regiao fisica: o preso anda dentro de um raio e e puxado de
 * volta ao centro se tentar sair.
 *
 * A distancia e medida so na horizontal. Se o Y contasse, uma jail rasa
 * expulsaria quem pulasse — teto e piso sao responsabilidade de quem constroi.
 */
public class JailMode implements PunishmentMode {

    private final LouiPlugin plugin;
    /** Teleportes feitos pelo proprio plugin (pra nao serem cancelados pelo listener). */
    private final Set<UUID> internalTeleport = new HashSet<>();

    public JailMode(LouiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "jail";
    }

    @Override
    public boolean isConfigured() {
        return plugin.getConfig().getDouble("jail.radius", 0.0) > 0.0 && jailWorld() != null;
    }

    @Override
    public EffectProfile effects() {
        return EffectProfile.fromConfig(
                plugin.getConfig().getConfigurationSection("jail.effects"), false, false);
    }

    @Override
    public void apply(Player player, PrisonManager.Prison prison) {
        effects().apply(player);
        player.setFallDistance(0f);
        teleportToCenter(player);
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
        World world = jailWorld();
        if (world == null) return;

        Location loc = player.getLocation();
        if (!world.equals(loc.getWorld())) {
            teleportToCenter(player);
        } else {
            double dx = loc.getX() - plugin.getConfig().getDouble("jail.x", 0.0);
            double dz = loc.getZ() - plugin.getConfig().getDouble("jail.z", 0.0);
            if (foraDoRaio(dx, dz, plugin.getConfig().getDouble("jail.radius", 0.0))) {
                teleportToCenter(player);
            }
        }
        effects().reassert(player);
    }

    @Override
    public boolean isInternalTeleport(UUID uuid) {
        return internalTeleport.contains(uuid);
    }

    /**
     * Compara distancia ao quadrado com raio ao quadrado, evitando uma raiz
     * quadrada por preso a cada segundo. Estar exatamente no raio conta como
     * dentro.
     */
    static boolean foraDoRaio(double dx, double dz, double radius) {
        return (dx * dx + dz * dz) > (radius * radius);
    }

    private void teleportToCenter(Player player) {
        World world = jailWorld();
        if (world == null) return;

        Location center = new Location(world,
                plugin.getConfig().getDouble("jail.x", 0.0),
                plugin.getConfig().getDouble("jail.y", 64.0),
                plugin.getConfig().getDouble("jail.z", 0.0));

        internalTeleport.add(player.getUniqueId());
        try {
            player.teleport(center);
        } finally {
            internalTeleport.remove(player.getUniqueId());
        }
        player.setFallDistance(0f);
    }

    private World jailWorld() {
        String name = plugin.getConfig().getString("jail.world", "");
        if (name == null || name.isEmpty()) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        return Bukkit.getWorld(name);
    }
}

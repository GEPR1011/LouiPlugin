package com.nemonicorp.loui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gerencia os castigos: dados, persistencia, bossbar, loop de queda e expiracao.
 */
public class PrisonManager {

    /** Dados de um jogador contido. */
    public static class Prison {
        String playerName;
        Location returnLocation;
        GameMode returnGameMode;
        boolean returnAllowFlight;
        long endTime;     // epoch ms
        long totalMs;
        String reason;
        int band = -1;    // faixa de altura ocupada; -1 = nao atribuida
        transient BossBar bar;
    }

    private final LouiPlugin plugin;
    private final Map<UUID, Prison> prisons = new HashMap<>();
    private final VoidState voidState;

    public PrisonManager(LouiPlugin plugin) {
        this.plugin = plugin;
        this.voidState = new VoidState(plugin);
    }

    // ── Mensagens ──

    public String msg(String key, String def) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&cLoui&8] &7");
        String raw = plugin.getConfig().getString("messages." + key, def);
        return ChatColor.translateAlternateColorCodes('&', prefix + raw);
    }

    private String msgRaw(String key, String def) {
        String raw = plugin.getConfig().getString("messages." + key, def);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    // ── API consultada pelo listener ──

    public boolean isImprisoned(UUID uuid) {
        return prisons.containsKey(uuid);
    }

    public boolean isInternalTeleport(UUID uuid) {
        return voidState.isInternalTeleport(uuid);
    }

    public List<String> getPrisonerNames() {
        List<String> names = new ArrayList<>();
        for (Prison p : prisons.values()) names.add(p.playerName);
        return names;
    }

    // ── Acoes principais ──

    public void imprison(Player target, long minutes, String reason, String byWhom) {
        UUID uuid = target.getUniqueId();
        Prison existing = prisons.get(uuid);

        Prison prison;
        if (existing != null) {
            // Ja contido: atualiza tempo/motivo, mantem o local de retorno original
            prison = existing;
            if (prison.bar != null) prison.bar.removeAll();
        } else {
            prison = new Prison();
            prison.returnLocation = target.getLocation().clone();
            prison.returnGameMode = target.getGameMode();
            prison.returnAllowFlight = target.getAllowFlight();
        }

        prison.playerName = target.getName();
        prison.reason = reason;
        prison.totalMs = minutes * 60_000L;
        prison.endTime = System.currentTimeMillis() + prison.totalMs;

        prisons.put(uuid, prison);
        save();

        applyVoidState(target, prison);

        String pretty = TimeParser.formatDuration(minutes);
        String m1 = msg("jailed-target", "&cVoce foi contido pelo motivo: &f%reason% &7(%time%)")
                .replace("%reason%", reason)
                .replace("%time%", pretty)
                .replace("%minutes%", String.valueOf(minutes));
        target.sendMessage(m1);

        String m2 = msg("jailed-broadcast-staff", "&7%player% foi contido por %time%: &f%reason%")
                .replace("%player%", target.getName())
                .replace("%time%", pretty)
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%reason%", reason);
        Player executor = Bukkit.getPlayerExact(byWhom);
        if (executor != null) executor.sendMessage(m2);
        Bukkit.getConsoleSender().sendMessage(m2 + ChatColor.DARK_GRAY + " (por " + byWhom + ")");

        plugin.getLogger().info("[LOUI] " + target.getName() + " contido por " + pretty
                + ". Motivo: " + reason + " (por " + byWhom + ")");
    }

    public void freeByName(CommandSender sender, String name) {
        UUID found = null;
        for (Map.Entry<UUID, Prison> e : prisons.entrySet()) {
            if (e.getValue().playerName.equalsIgnoreCase(name)) {
                found = e.getKey();
                break;
            }
        }
        if (found == null) {
            sender.sendMessage(msg("not-jailed", "&cEsse jogador nao esta contido."));
            return;
        }

        Player online = Bukkit.getPlayer(found);
        if (online != null) {
            release(online);
        } else {
            // Offline: marca como expirado; sera restaurado quando entrar
            Prison prison = prisons.get(found);
            prison.endTime = 0L;
            save();
        }
        sender.sendMessage(msg("already-free", "&aJogador libertado."));
    }

    /** Restaura o jogador: local original, gamemode, efeitos, bossbar. */
    public void release(Player player) {
        Prison prison = prisons.remove(player.getUniqueId());
        if (prison == null) return;

        if (prison.bar != null) prison.bar.removeAll();

        voidState.restore(player, prison.returnLocation, prison.returnGameMode, prison.returnAllowFlight);
        voidState.releaseBand(prison.band);

        player.sendMessage(msg("released", "&aVoce foi libertado. Comporte-se."));
        save();

        plugin.getLogger().info("[LOUI] " + player.getName() + " libertado.");
    }

    /** Aplica o estado de vazio e monta a bossbar. */
    private void applyVoidState(Player player, Prison prison) {
        voidState.apply(player, prison);

        if (prison.bar == null) {
            BarColor color;
            try {
                color = BarColor.valueOf(plugin.getConfig().getString("bossbar.color", "RED").toUpperCase());
            } catch (IllegalArgumentException ex) {
                color = BarColor.RED;
            }
            prison.bar = Bukkit.createBossBar("", color, BarStyle.SOLID);
        }
        prison.bar.addPlayer(player);
        updateBar(prison);
    }

    private void updateBar(Prison prison) {
        if (prison.bar == null) return;
        long remaining = Math.max(0L, prison.endTime - System.currentTimeMillis());
        double progress = prison.totalMs <= 0 ? 0.0
                : Math.max(0.0, Math.min(1.0, remaining / (double) prison.totalMs));

        prison.bar.setTitle(ChatColor.RED + "" + ChatColor.BOLD + prison.reason
                + ChatColor.GRAY + " — " + ChatColor.WHITE + TimeParser.formatClock(remaining));
        prison.bar.setProgress(progress);
    }

// ── Tick (1s) ──

    public void tick() {
        if (prisons.isEmpty()) return;

        List<Player> toRelease = new ArrayList<>();

        for (Map.Entry<UUID, Prison> e : prisons.entrySet()) {
            Prison prison = e.getValue();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) continue;

            if (System.currentTimeMillis() >= prison.endTime) {
                toRelease.add(player);
                continue;
            }

            // Loop de queda infinita, dentro da faixa do preso
            if (player.getLocation().getY() < voidState.bandFloor(prison.band)) {
                voidState.teleportToTop(player, prison.band);
            }

            // Garantias (caso outro plugin tenha mexido)
            if (!player.isInvulnerable()) player.setInvulnerable(true);
            voidState.applyEffects(player);

            updateBar(prison);
        }

        for (Player p : toRelease) {
            release(p);
        }
    }

    // ── Join/Quit ──

    public void handleJoin(Player player) {
        Prison prison = prisons.get(player.getUniqueId());
        if (prison == null) return;

        if (System.currentTimeMillis() >= prison.endTime) {
            // Expirou (ou foi libertado) enquanto estava offline
            release(player);
        } else {
            prison.playerName = player.getName();
            applyVoidState(player, prison);
        }
    }

    public void handleQuit(Player player) {
        Prison prison = prisons.get(player.getUniqueId());
        if (prison != null && prison.bar != null) {
            prison.bar.removePlayer(player);
        }
    }

    public void sendList(CommandSender sender) {
        if (prisons.isEmpty()) {
            sender.sendMessage(msg("not-jailed", "&7Ninguem contido no momento."));
            return;
        }
        for (Prison prison : prisons.values()) {
            long remaining = Math.max(0L, prison.endTime - System.currentTimeMillis()) / 60000L;
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + prison.playerName
                    + ChatColor.GRAY + " (" + TimeParser.formatDuration(remaining) + " restantes): "
                    + ChatColor.YELLOW + prison.reason);
        }
    }

    // ── Persistencia ──

    private File dataFile() {
        return new File(plugin.getDataFolder(), "prisons.yml");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Prison> e : prisons.entrySet()) {
            Prison p = e.getValue();
            String base = e.getKey().toString() + ".";
            yaml.set(base + "name", p.playerName);
            yaml.set(base + "end-time", p.endTime);
            yaml.set(base + "total-ms", p.totalMs);
            yaml.set(base + "reason", p.reason);
            yaml.set(base + "gamemode", p.returnGameMode == null ? "SURVIVAL" : p.returnGameMode.name());
            yaml.set(base + "allow-flight", p.returnAllowFlight);
            yaml.set(base + "band", p.band);
            Location l = p.returnLocation;
            if (l != null && l.getWorld() != null) {
                yaml.set(base + "loc.world", l.getWorld().getName());
                yaml.set(base + "loc.x", l.getX());
                yaml.set(base + "loc.y", l.getY());
                yaml.set(base + "loc.z", l.getZ());
                yaml.set(base + "loc.yaw", l.getYaw());
                yaml.set(base + "loc.pitch", l.getPitch());
            }
        }
        try {
            yaml.save(dataFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("[LOUI] Falha ao salvar prisons.yml: " + ex.getMessage());
        }
    }

    public void load() {
        prisons.clear();
        File file = dataFile();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection sec = yaml.getConfigurationSection(key);
                if (sec == null) continue;

                Prison p = new Prison();
                p.playerName = sec.getString("name", "?");
                p.endTime = sec.getLong("end-time", 0L);
                p.totalMs = sec.getLong("total-ms", 60_000L);
                p.reason = sec.getString("reason", "Comportamento inadequado");
                try {
                    p.returnGameMode = GameMode.valueOf(sec.getString("gamemode", "SURVIVAL"));
                } catch (IllegalArgumentException ex) {
                    p.returnGameMode = GameMode.SURVIVAL;
                }
                p.returnAllowFlight = sec.getBoolean("allow-flight", false);
                p.band = sec.getInt("band", -1);

                String worldName = sec.getString("loc.world", null);
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world != null) {
                    p.returnLocation = new Location(world,
                            sec.getDouble("loc.x"), sec.getDouble("loc.y"), sec.getDouble("loc.z"),
                            (float) sec.getDouble("loc.yaw"), (float) sec.getDouble("loc.pitch"));
                }

                prisons.put(uuid, p);
                voidState.reserveBand(p.band);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("[LOUI] " + prisons.size() + " castigo(s) carregado(s).");
    }

    public void shutdown() {
        for (Prison p : prisons.values()) {
            if (p.bar != null) p.bar.removeAll();
        }
        save();
    }
}

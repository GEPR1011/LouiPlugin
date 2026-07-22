package com.nemonicorp.loui;

import com.nemonicorp.loui.api.LouiImprisonEvent;
import com.nemonicorp.loui.api.LouiReleaseEvent;
import com.nemonicorp.loui.api.ReleaseCause;
import com.nemonicorp.loui.mode.JailMode;
import com.nemonicorp.loui.mode.PunishmentMode;
import com.nemonicorp.loui.mode.VoidMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredListener;

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
        String mode = "void";   // em qual modo o castigo foi aplicado
        transient BossBar bar;

        /** Acessores para o pacote mode, que vive fora deste pacote. */
        public int getBand() {
            return band;
        }

        public void setBand(int band) {
            this.band = band;
        }
    }

    private final LouiPlugin plugin;
    private final Map<UUID, Prison> prisons = new HashMap<>();
    private final PunishmentMode voidMode;
    private final PunishmentMode jailMode;

    public PrisonManager(LouiPlugin plugin) {
        this.plugin = plugin;
        this.voidMode = new VoidMode(plugin);
        this.jailMode = new JailMode(plugin);
    }

    /** O modo que o servidor usa agora, conforme o config. */
    public PunishmentMode activeMode() {
        return "jail".equalsIgnoreCase(plugin.getConfig().getString("mode", "void"))
                ? jailMode : voidMode;
    }

    /**
     * O modo em que ESTE castigo foi aplicado — nao o do config atual.
     * E o que impede o desastre de trocar o modo com gente presa.
     */
    private PunishmentMode modeFor(Prison prison) {
        return "jail".equalsIgnoreCase(prison.mode) ? jailMode : voidMode;
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
        // Consulta os dois: o listener nao sabe de qual modo veio o teleporte.
        return voidMode.isInternalTeleport(uuid) || jailMode.isInternalTeleport(uuid);
    }

    /**
     * Whitelist de comandos permitidos durante o castigo. Lista vazia bloqueia tudo.
     * Aliases nao sao resolvidos: liberar /msg nao libera /tell.
     */
    public boolean isCommandAllowed(String rawMessage) {
        List<String> allowed = plugin.getConfig().getStringList("restrictions.allowed-commands");
        if (allowed.isEmpty()) return false;

        String root = LouiListener.commandRoot(rawMessage);
        for (String entry : allowed) {
            if (LouiListener.commandRoot(entry).equals(root)) return true;
        }
        return false;
    }

    public List<String> getPrisonerNames() {
        List<String> names = new ArrayList<>();
        for (Prison p : prisons.values()) names.add(p.playerName);
        return names;
    }

    /** true se o castigo deste jogador o torna invulneravel. */
    public boolean isInvulnerableFor(UUID uuid) {
        Prison prison = prisons.get(uuid);
        if (prison == null) return false;
        return modeFor(prison).effects().isInvulnerable();
    }

    /**
     * Recoloca o jogador sob castigo depois de morrer.
     *
     * Sem isto, com invulnerable desligado, morrer seria fuga: o respawn joga o
     * jogador no spawn do mundo e handleJoin nao roda, porque respawn nao e login.
     */
    public void reapplyAfterRespawn(Player player) {
        Prison prison = prisons.get(player.getUniqueId());
        if (prison == null) return;
        applyPunishment(player, prison);
    }

    /** Grava a posicao atual do jogador como centro da jail. Nao mexe no raio. */
    public void setJailHere(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set("jail.world", loc.getWorld().getName());
        plugin.getConfig().set("jail.x", loc.getX());
        plugin.getConfig().set("jail.y", loc.getY());
        plugin.getConfig().set("jail.z", loc.getZ());
        plugin.saveConfig();

        plugin.getLogger().info("[LOUI] Jail definida por " + player.getName()
                + " em " + loc.getWorld().getName()
                + " " + String.format("%.1f %.1f %.1f", loc.getX(), loc.getY(), loc.getZ()));
    }

    /** Quantos castigos existem, incluindo os de jogadores offline. */
    public int getPrisonerCount() {
        return prisons.size();
    }

    /** Milissegundos restantes do castigo, ou 0 se o jogador nao estiver contido. */
    public long getRemainingMillis(UUID uuid) {
        Prison prison = prisons.get(uuid);
        if (prison == null) return 0L;
        return Math.max(0L, prison.endTime - System.currentTimeMillis());
    }

    /** Motivo do castigo, ou string vazia se o jogador nao estiver contido. */
    public String getReason(UUID uuid) {
        Prison prison = prisons.get(uuid);
        return prison == null ? "" : prison.reason;
    }

    // ── Acoes principais ──

    /** Devolve false quando outro plugin vetou a punicao. */
    public boolean imprison(Player target, long minutes, String reason, String byWhom) {
        if (imprisonVetoed(target, target.getName(), minutes, reason, byWhom, false)) {
            return false;
        }

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
            prison.mode = activeMode().id();
        }

        prison.playerName = target.getName();
        prison.reason = reason;
        prison.totalMs = minutes * 60_000L;
        prison.endTime = System.currentTimeMillis() + prison.totalMs;

        prisons.put(uuid, prison);
        save();

        applyPunishment(target, prison);

        String m1 = msg("jailed-target", "&cVoce foi contido pelo motivo: &f%reason% &7(%time%)")
                .replace("%reason%", reason)
                .replace("%time%", TimeParser.formatDuration(minutes))
                .replace("%minutes%", String.valueOf(minutes));
        target.sendMessage(m1);

        announceImprison(target.getName(), minutes, reason, byWhom, target.getUniqueId(), false);
        return true;
    }

    /**
     * Pune um jogador que nao esta online. O castigo fica registrado sem localizacao
     * de retorno; handleJoin captura a posicao de login e aplica o estado de vazio.
     *
     * Devolve false quando outro plugin vetou a punicao.
     */
    public boolean imprisonOffline(OfflinePlayer target, long minutes, String reason, String byWhom) {
        String name = target.getName() == null ? "?" : target.getName();
        if (imprisonVetoed(target, name, minutes, reason, byWhom, true)) {
            return false;
        }

        UUID uuid = target.getUniqueId();
        Prison prison = prisons.get(uuid);
        if (prison == null) {
            prison = new Prison();
            prison.returnLocation = null;      // capturada no primeiro login
            prison.returnGameMode = null;      // idem
            prison.returnAllowFlight = false;
            prison.mode = activeMode().id();
        }

        prison.playerName = name;
        prison.reason = reason;
        prison.totalMs = minutes * 60_000L;
        prison.endTime = System.currentTimeMillis() + prison.totalMs;

        prisons.put(uuid, prison);
        save();

        announceImprison(name, minutes, reason, byWhom, null, true);
        return true;
    }

    /** Envia uma mensagem a todo jogador online com a permissao de notificacao. */
    public void notifyStaff(String message, UUID excluded) {
        String permission = plugin.getConfig().getString("notify.permission", "loui.notify");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (excluded != null && p.getUniqueId().equals(excluded)) continue;
            if (p.hasPermission(permission)) p.sendMessage(message);
        }
    }

    /**
     * Dispara o LouiImprisonEvent e devolve true se algum plugin vetou.
     *
     * O log lista os plugins que ESCUTAM o evento, nao o que o cancelou — o
     * Bukkit nao expoe essa informacao. Afirmar culpa que nao se pode provar
     * mandaria o admin investigar o plugin errado.
     */
    private boolean imprisonVetoed(OfflinePlayer target, String targetName, long minutes,
                                   String reason, String byWhom, boolean offline) {
        LouiImprisonEvent event = new LouiImprisonEvent(target, minutes, reason, byWhom, offline);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) return false;

        StringBuilder listeners = new StringBuilder();
        for (RegisteredListener rl : LouiImprisonEvent.getHandlerList().getRegisteredListeners()) {
            if (listeners.length() > 0) listeners.append(", ");
            listeners.append(rl.getPlugin().getName());
        }
        String who = listeners.length() == 0 ? "nenhum" : listeners.toString();

        plugin.getLogger().info("[LOUI] Punicao de " + targetName + " cancelada por um plugin. "
                + "Plugins escutando este evento: " + who);
        return true;
    }

    /** Monta e distribui o aviso de punicao para staff, console e log. */
    private void announceImprison(String playerName, long minutes, String reason,
                                  String byWhom, UUID excluded, boolean offline) {
        String pretty = TimeParser.formatDuration(minutes);
        String m = msg("jailed-broadcast-staff", "&7%player% foi contido por %time%: &f%reason%")
                .replace("%player%", playerName)
                .replace("%time%", pretty)
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%reason%", reason);

        notifyStaff(m, excluded);
        Bukkit.getConsoleSender().sendMessage(m + ChatColor.DARK_GRAY
                + " (por " + byWhom + (offline ? ", offline" : "") + ")");

        plugin.getLogger().info("[LOUI] " + playerName + " contido" + (offline ? " offline" : "")
                + " por " + pretty + ". Motivo: " + reason + " (por " + byWhom + ")");
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
        releaseInternal(player, true, ReleaseCause.MANUAL);
    }

    private void releaseInternal(Player player, boolean persist, ReleaseCause cause) {
        Prison prison = prisons.remove(player.getUniqueId());
        if (prison == null) return;

        Bukkit.getPluginManager().callEvent(new LouiReleaseEvent(player, prison.reason, cause));

        if (prison.bar != null) prison.bar.removeAll();

        PunishmentMode mode = modeFor(prison);
        mode.restore(player, prison.returnLocation, prison.returnGameMode, prison.returnAllowFlight);
        if (mode instanceof VoidMode vm) vm.releaseBand(prison.band);

        player.sendMessage(msg("released", "&aVoce foi libertado. Comporte-se."));
        if (persist) save();

        plugin.getLogger().info("[LOUI] " + player.getName() + " libertado.");
    }

    /** Aplica o estado de vazio e monta a bossbar. */
    private void applyPunishment(Player player, Prison prison) {
        modeFor(prison).apply(player, prison);

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

            // Mantem o preso onde deve estar e reafirma os efeitos
            modeFor(prison).contain(player, prison);

            updateBar(prison);
        }

        for (Player p : toRelease) {
            releaseInternal(p, true, ReleaseCause.EXPIRED);
        }
    }

    // ── Join/Quit ──

    public void handleJoin(Player player) {
        Prison prison = prisons.get(player.getUniqueId());
        if (prison == null) return;

        // Imunidade so e consultavel com o jogador online. Sem esta checagem, daria
        // pra contornar loui.exempt punindo um admin enquanto ele estivesse fora.
        if (player.hasPermission("loui.exempt")) {
            prisons.remove(player.getUniqueId());
            if (modeFor(prison) instanceof VoidMode vm) vm.releaseBand(prison.band);
            save();
            Bukkit.getPluginManager().callEvent(
                    new LouiReleaseEvent(player, prison.reason, ReleaseCause.EXEMPT));
            notifyStaff(msg("exempt-discarded", "&7%player% e imune ao castigo — punicao descartada.")
                    .replace("%player%", player.getName()), player.getUniqueId());
            return;
        }

        if (System.currentTimeMillis() >= prison.endTime) {
            if (prison.returnLocation == null) {
                // Punicao offline que expirou antes do primeiro login: nunca chegou a
                // ser aplicada, entao nao ha nada a restaurar nem para onde teleportar.
                prisons.remove(player.getUniqueId());
                if (modeFor(prison) instanceof VoidMode vm) vm.releaseBand(prison.band);
                save();
                Bukkit.getPluginManager().callEvent(
                        new LouiReleaseEvent(player, prison.reason, ReleaseCause.EXPIRED));
            } else {
                releaseInternal(player, true, ReleaseCause.EXPIRED);
            }
            return;
        }

        if (prison.returnLocation == null) {
            // Primeiro login apos punicao offline: o ponto de retorno e onde ele entrou.
            prison.returnLocation = player.getLocation().clone();
            prison.returnGameMode = player.getGameMode();
            prison.returnAllowFlight = player.getAllowFlight();
            save();
        }

        prison.playerName = player.getName();
        applyPunishment(player, prison);
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
            yaml.set(base + "mode", p.mode);
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
                // Default "void": era o unico modo antes da v1.3.0.
                p.mode = sec.getString("mode", "void");

                String worldName = sec.getString("loc.world", null);
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world != null) {
                    p.returnLocation = new Location(world,
                            sec.getDouble("loc.x"), sec.getDouble("loc.y"), sec.getDouble("loc.z"),
                            (float) sec.getDouble("loc.yaw"), (float) sec.getDouble("loc.pitch"));
                }

                prisons.put(uuid, p);
                if (modeFor(p) instanceof VoidMode vm) vm.reserveBand(p.band);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("[LOUI] " + prisons.size() + " castigo(s) carregado(s).");
    }

    public void shutdown() {
        if (plugin.getConfig().getBoolean("safety.release-all-on-disable", true)) {
            // Coletar antes de soltar: releaseInternal modifica o mapa.
            List<Player> online = new ArrayList<>();
            for (UUID uuid : prisons.keySet()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) online.add(p);
            }
            for (Player p : online) {
                releaseInternal(p, false, ReleaseCause.SHUTDOWN);
            }
            if (!online.isEmpty()) {
                plugin.getLogger().info("[LOUI] " + online.size() + " preso(s) solto(s) no desligamento.");
            }
        }

        for (Prison p : prisons.values()) {
            if (p.bar != null) p.bar.removeAll();
        }
        save();
    }
}

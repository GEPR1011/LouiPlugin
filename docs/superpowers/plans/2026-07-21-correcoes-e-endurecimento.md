# LouiPlugin — Correções e Endurecimento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir sete lacunas do LouiPlugin v1.0.0 que qualquer servidor encontraria em produção, entregando a v1.1.0.

**Architecture:** Extração dirigida — três classes novas (`TimeParser`, `VoidState`, `LouiCommand`) criadas apenas onde o trabalho novo aterrissa. `PrisonManager` fica com estado, persistência e orquestração. Dependências fluem numa direção só: `LouiCommand` → `PrisonManager` → `VoidState`, com `TimeParser` como folha sem dependência de Bukkit.

**Tech Stack:** Java 21, Maven, Paper API 1.21.4-R0.1-SNAPSHOT (scope `provided`), JUnit 5.

## Global Constraints

- Java 21. `maven.compiler.source` e `target` já em `${java.version}` = `21`.
- Paper API em scope `provided` — nunca empacotar no jar.
- Sem dependências de runtime novas. O plugin permanece com zero `depend`/`softdepend`.
- **Nunca usar `Bukkit.getOfflinePlayer(String)`** — em servidor online-mode dispara HTTP à Mojang na thread principal. Usar `Bukkit.getOfflinePlayerIfCached(String)`, confirmado presente na paper-api 1.21.4.
- Arquivos-fonte sem acentos nos comentários e identificadores, seguindo o padrão do código existente. Mensagens ao jogador em `config.yml` também sem acentos (o padrão atual).
- `finalName` do jar é `${project.name}` = `LouiPlugin`. Não alterar.
- Toda chave de config nova precisa de default no código — um `config.yml` da v1.0.0 tem de continuar funcionando sem edição.
- Faixa 0 deve ser byte a byte o comportamento atual (queda de `top-y` até `min-y`).
- Commits em português, sem trailer de atribuição.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade | Ação |
|---|---|---|
| `src/main/java/com/nemonicorp/loui/TimeParser.java` | Parse e formatação de durações. Sem Bukkit. | Criar |
| `src/main/java/com/nemonicorp/loui/VoidState.java` | Estado físico do castigo: teleporte, efeitos, faixas | Criar |
| `src/main/java/com/nemonicorp/loui/LouiCommand.java` | `/loui`, tab-complete, exempt, resolução de alvo | Criar |
| `src/test/java/com/nemonicorp/loui/TimeParserTest.java` | Testes de `TimeParser` | Criar |
| `src/test/java/com/nemonicorp/loui/CommandRootTest.java` | Testes de `LouiListener.commandRoot` | Criar |
| `src/main/java/com/nemonicorp/loui/PrisonManager.java` | Mapa, persistência, orquestração, tick, bossbar | Modificar |
| `src/main/java/com/nemonicorp/loui/LouiListener.java` | Bloqueios + whitelist | Modificar |
| `src/main/java/com/nemonicorp/loui/LouiPlugin.java` | Wiring e scheduler apenas | Modificar |
| `src/main/resources/config.yml` | Chaves novas | Modificar |
| `src/main/resources/plugin.yml` | Versão + permissões | Modificar |
| `pom.xml` | Versão + JUnit + surefire | Modificar |
| `README.md` | Config, permissões, limitações | Modificar |

---

### Task 1: TimeParser e infraestrutura de testes

Entrega o **item 3** (horas no parse) e cria a única base de testes automatizados do projeto.

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/nemonicorp/loui/TimeParser.java`
- Create: `src/test/java/com/nemonicorp/loui/TimeParserTest.java`
- Modify: `src/main/java/com/nemonicorp/loui/LouiPlugin.java` (remover `parseMinutes`)
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java` (remover `formatClock`, `formatDuration`)

**Interfaces:**
- Consumes: nada.
- Produces: `TimeParser.parse(String) -> long` (minutos, ou `-1` inválido); `TimeParser.formatClock(long millis) -> String`; `TimeParser.formatDuration(long minutes) -> String`. Todos `public static`.

- [ ] **Step 1: Adicionar JUnit 5 e surefire ao pom, bumpar versão**

Em `pom.xml`, trocar `<version>1.0.0</version>` (a do projeto, linha ~11, **não** as das dependências) por `<version>1.1.0</version>`.

Adicionar dentro de `<dependencies>`, depois da dependência do paper-api:

```xml
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
        </dependency>
```

Adicionar dentro de `<build><plugins>`, depois do `maven-compiler-plugin`:

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
```

- [ ] **Step 2: Escrever o teste que falha**

Criar `src/test/java/com/nemonicorp/loui/TimeParserTest.java`:

```java
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
```

- [ ] **Step 3: Rodar o teste e confirmar que falha**

Run: `.\mvnw.cmd test`
Expected: FAIL na compilação — `cannot find symbol: class TimeParser`.

- [ ] **Step 4: Implementar TimeParser**

Criar `src/main/java/com/nemonicorp/loui/TimeParser.java`:

```java
package com.nemonicorp.loui;

/**
 * Conversao e formatacao de duracoes.
 *
 * Nao depende de Bukkit — e a unica peca do plugin testavel sem servidor.
 */
public final class TimeParser {

    private TimeParser() {
    }

    /**
     * Converte o argumento de tempo em minutos.
     * Aceita minutos puros ("30") ou sufixo de minutos ("30m"), horas ("2h") ou dias ("2d").
     * Retorna -1 quando o formato e invalido, o valor nao e positivo, ou a
     * multiplicacao estouraria o long.
     */
    public static long parse(String input) {
        if (input == null || input.isEmpty()) return -1;
        String s = input.toLowerCase();
        long multiplier = 1L; // minutos por unidade
        char last = s.charAt(s.length() - 1);
        if (last == 'd') {
            multiplier = 1440L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'h') {
            multiplier = 60L;
            s = s.substring(0, s.length() - 1);
        } else if (last == 'm') {
            multiplier = 1L;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) return -1;
        try {
            long value = Long.parseLong(s);
            if (value <= 0) return -1;
            if (value > Long.MAX_VALUE / multiplier) return -1;
            return value * multiplier;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Relogio da bossbar: "Dd HH:MM:SS", "H:MM:SS" ou "MM:SS" conforme a duracao restante. */
    public static String formatClock(long remainingMs) {
        long totalSec = Math.max(0L, remainingMs) / 1000L;
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long min = (totalSec % 3600L) / 60L;
        long sec = totalSec % 60L;
        if (days > 0) return String.format("%dd %02d:%02d:%02d", days, hours, min, sec);
        if (hours > 0) return String.format("%d:%02d:%02d", hours, min, sec);
        return String.format("%02d:%02d", min, sec);
    }

    /** Texto amigavel de duracao em minutos: "2d 5h", "5h 30min", "30min". */
    public static String formatDuration(long minutes) {
        if (minutes <= 0) return "0min";
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append('d');
        if (hours > 0) sb.append(sb.length() > 0 ? " " : "").append(hours).append('h');
        if (mins > 0 || sb.length() == 0) sb.append(sb.length() > 0 ? " " : "").append(mins).append("min");
        return sb.toString();
    }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `.\mvnw.cmd test`
Expected: PASS — `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Remover os métodos antigos e apontar as chamadas para TimeParser**

Em `LouiPlugin.java`: apagar o método `static long parseMinutes(String input)` inteiro (incluindo o javadoc acima dele) e trocar a chamada em `onCommand`:

```java
        long minutes = TimeParser.parse(args[1]);
```

Trocar também a mensagem de uso, que agora deve citar horas:

```java
            sender.sendMessage(manager.msg("usage", "&7Tempo invalido. Use minutos, ou sufixo m/h/d. Ex: 30m, 2h, 2d."));
```

Em `PrisonManager.java`: apagar `static String formatClock(long)` e `static String formatDuration(long)` (com seus javadocs) e trocar as três chamadas restantes:

- em `imprison`: `String pretty = TimeParser.formatDuration(minutes);`
- em `updateBar`: `+ ChatColor.GRAY + " — " + ChatColor.WHITE + TimeParser.formatClock(remaining));`
- em `sendList`: `+ ChatColor.GRAY + " (" + TimeParser.formatDuration(remaining) + " restantes): "`

- [ ] **Step 7: Compilar e rodar tudo**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 11, Failures: 0`, jar gerado em `target/LouiPlugin.jar`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/nemonicorp/loui/TimeParser.java src/test/java/com/nemonicorp/loui/TimeParserTest.java src/main/java/com/nemonicorp/loui/LouiPlugin.java src/main/java/com/nemonicorp/loui/PrisonManager.java
git commit -m "feat: aceita horas no tempo de castigo e extrai TimeParser

Adiciona o sufixo h (/loui X 2h motivo), que antes era rejeitado embora a
bossbar ja exibisse horas. Consolida parse e formatacao numa classe sem
dependencia de Bukkit, com testes de unidade.

Adiciona JUnit 5 e surefire ao pom, que nao tinha nenhuma infraestrutura
de teste. Versao 1.1.0."
```

---

### Task 2: VoidState e faixas de altura

Entrega o **item 6** (separação de prisioneiros).

**Files:**
- Create: `src/main/java/com/nemonicorp/loui/VoidState.java`
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: nada da Task 1.
- Produces: `VoidState(LouiPlugin)`; `void apply(Player, PrisonManager.Prison)`; `void restore(Player, Location returnLocation, GameMode, boolean allowFlight)`; `void teleportToTop(Player, int band)`; `double bandFloor(int band)`; `int allocateBand()`; `void reserveBand(int)`; `void releaseBand(int)`; `boolean isInternalTeleport(UUID)`. Campo novo `int band` em `PrisonManager.Prison`.

- [ ] **Step 1: Criar VoidState**

Criar `src/main/java/com/nemonicorp/loui/VoidState.java`:

```java
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
```

- [ ] **Step 2: Adicionar o campo `band` a Prison e persistir**

Em `PrisonManager.java`, dentro da classe `Prison`, depois de `String reason;`:

```java
        int band = -1;    // faixa de altura ocupada; -1 = nao atribuida
```

Em `save()`, dentro do laço, depois da linha de `allow-flight`:

```java
            yaml.set(base + "band", p.band);
```

Em `load()`, depois da linha de `returnAllowFlight`:

```java
                p.band = sec.getInt("band", -1);
```

E logo após `prisons.put(uuid, p);`, reservar a faixa lida para que um restart não coloque dois presos na mesma:

```java
                voidState.reserveBand(p.band);
```

- [ ] **Step 3: Ligar o VoidState no PrisonManager**

Em `PrisonManager.java`, adicionar o campo e inicializar no construtor:

```java
    private final VoidState voidState;

    public PrisonManager(LouiPlugin plugin) {
        this.plugin = plugin;
        this.voidState = new VoidState(plugin);
    }
```

Apagar o campo `internalTeleport`, o método `voidWorld()` e o método `teleportToVoidTop(Player)` — todos migraram para `VoidState`.

Trocar `isInternalTeleport` por uma delegação:

```java
    public boolean isInternalTeleport(UUID uuid) {
        return voidState.isInternalTeleport(uuid);
    }
```

Substituir o corpo de `applyVoidState` — ele agora só delega e cuida da bossbar:

```java
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
```

- [ ] **Step 4: Usar a faixa no release e no tick**

Em `release(Player)`, substituir todo o bloco de efeitos/teleporte/gamemode (das linhas de `removePotionEffect` até `setAllowFlight`) por:

```java
        voidState.restore(player, prison.returnLocation, prison.returnGameMode, prison.returnAllowFlight);
        voidState.releaseBand(prison.band);
```

Em `tick()`, trocar o bloco de queda e garantias por versões que respeitam a faixa:

```java
            // Loop de queda infinita, dentro da faixa do preso
            if (player.getLocation().getY() < voidState.bandFloor(prison.band)) {
                voidState.teleportToTop(player, prison.band);
            }

            // Garantias (caso outro plugin tenha mexido)
            if (!player.isInvulnerable()) player.setInvulnerable(true);
            voidState.applyEffects(player);
```

Remover a leitura de `minY` no topo de `tick()` — ela agora vem de `voidState.bandFloor`.

- [ ] **Step 5: Adicionar as chaves de config**

Em `src/main/resources/config.yml`, dentro do bloco `void:`, depois de `min-y`:

```yaml
  # Cada preso ocupa uma faixa de altura propria, pra dois presos simultaneos nao
  # ocuparem o mesmo ponto. A altura de queda de cada faixa e (top-y - min-y).
  band-gap: 100.0
  max-bands: 8
```

- [ ] **Step 6: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 11, Failures: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/VoidState.java src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/resources/config.yml
git commit -m "feat: separa presos simultaneos em faixas de altura

Antes todos caiam no mesmo X/Z e se empurravam por colisao. Cada preso
agora ocupa uma faixa de Y propria; como o X/Z segue compartilhado, apenas
um chunk fica carregado independentemente da quantidade de presos.

A faixa 0 reproduz exatamente o comportamento anterior. Extrai o estado
fisico do castigo para VoidState."
```

---

### Task 3: Release em massa no disable

Entrega o **item 1**.

**Files:**
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `VoidState.restore(...)` e `releaseBand(int)` da Task 2.
- Produces: `PrisonManager.release(Player)` mantém a assinatura pública; novo privado `releaseInternal(Player, boolean persist)`.

- [ ] **Step 1: Separar o release da persistência**

Em `PrisonManager.java`, renomear o método atual e adicionar um invólucro. O `release` público continua salvando; a variante interna permite soltar em lote com um único `save()`:

```java
    /** Restaura o jogador: local original, gamemode, efeitos, bossbar. */
    public void release(Player player) {
        releaseInternal(player, true);
    }

    private void releaseInternal(Player player, boolean persist) {
        Prison prison = prisons.remove(player.getUniqueId());
        if (prison == null) return;

        if (prison.bar != null) prison.bar.removeAll();

        voidState.restore(player, prison.returnLocation, prison.returnGameMode, prison.returnAllowFlight);
        voidState.releaseBand(prison.band);

        player.sendMessage(msg("released", "&aVoce foi libertado. Comporte-se."));
        if (persist) save();

        plugin.getLogger().info("[LOUI] " + player.getName() + " libertado.");
    }
```

- [ ] **Step 2: Soltar todo mundo no shutdown**

Substituir `shutdown()` por:

```java
    public void shutdown() {
        if (plugin.getConfig().getBoolean("safety.release-all-on-disable", true)) {
            // Coletar antes de soltar: releaseInternal modifica o mapa.
            List<Player> online = new ArrayList<>();
            for (UUID uuid : prisons.keySet()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) online.add(p);
            }
            for (Player p : online) {
                releaseInternal(p, false);
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
```

- [ ] **Step 3: Adicionar a chave de config**

Em `src/main/resources/config.yml`, no fim do arquivo:

```yaml

safety:
  # Solta todos os presos ONLINE quando o plugin e desabilitado. Sem isso, remover
  # o plugin deixa o jogador cego e invulneravel no vazio, sem comando que resolva.
  # Presos OFFLINE nao sao alcancados — solte-os com /loui free antes de desinstalar.
  release-all-on-disable: true
```

- [ ] **Step 4: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/resources/config.yml
git commit -m "fix: solta presos online ao desabilitar o plugin

Sem isso, remover o plugin deixava quem estivesse contido cego,
invulneravel e a 250000 blocos de casa, sem nenhum comando capaz de
desfazer o estado — que fica gravado no playerdata.

Presos offline seguem inalcancaveis: sem o plugin carregado nao ha codigo
para rodar. A limitacao esta documentada no README."
```

---

### Task 4: LouiCommand, imunidade e broadcast

Entrega os **itens 4 e 5**.

**Files:**
- Create: `src/main/java/com/nemonicorp/loui/LouiCommand.java`
- Modify: `src/main/java/com/nemonicorp/loui/LouiPlugin.java`
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `TimeParser.parse` (Task 1); `PrisonManager.imprison/freeByName/sendList/msg/getPrisonerNames`.
- Produces: `LouiCommand implements CommandExecutor, TabCompleter`, construtor `LouiCommand(PrisonManager)`; `PrisonManager.notifyStaff(String message, UUID excluded)`.

- [ ] **Step 1: Extrair o comando para a classe nova**

Criar `src/main/java/com/nemonicorp/loui/LouiCommand.java`:

```java
package com.nemonicorp.loui;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Parsing de /loui, tab-complete e checagem de imunidade. */
public class LouiCommand implements CommandExecutor, TabCompleter {

    private final PrisonManager manager;

    public LouiCommand(PrisonManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loui.use")) {
            sender.sendMessage(manager.msg("no-permission", "&cSem permissao."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m, 2h ou 2d> <motivo...>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            manager.sendList(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("free")) {
            if (args.length < 2) {
                sender.sendMessage(manager.msg("usage", "&7Uso: /loui free <nick>"));
                return true;
            }
            manager.freeByName(sender, args[1]);
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(manager.msg("usage", "&7Uso: /loui <nick> <tempo: 30m, 2h ou 2d> <motivo...>"));
            return true;
        }

        long minutes = TimeParser.parse(args[1]);
        if (minutes <= 0) {
            sender.sendMessage(manager.msg("usage", "&7Tempo invalido. Use minutos, ou sufixo m/h/d. Ex: 30m, 2h, 2d."));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String reason = sb.toString();

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(manager.msg("not-online", "&cJogador nao esta online."));
            return true;
        }

        if (target.hasPermission("loui.exempt")) {
            sender.sendMessage(manager.msg("is-exempt", "&cEsse jogador e imune ao castigo."));
            return true;
        }

        manager.imprison(target, minutes, reason, sender.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("loui.use")) return out;

        if (args.length == 1) {
            String a = args[0].toLowerCase();
            if ("free".startsWith(a)) out.add("free");
            if ("list".startsWith(a)) out.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a)) out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("free")) {
            String a = args[1].toLowerCase();
            for (String name : manager.getPrisonerNames()) {
                if (name.toLowerCase().startsWith(a)) out.add(name);
            }
        } else if (args.length == 2) {
            out.add("30m");
            out.add("2h");
            out.add("1d");
        }
        return out;
    }
}
```

- [ ] **Step 2: Enxugar o LouiPlugin**

Em `LouiPlugin.java`: apagar os métodos `onCommand` e `onTabComplete` inteiros e os imports que ficarem sem uso (`Command`, `CommandSender`, `ArrayList`, `List`). Registrar o executor no `onEnable`, logo após `manager.load();`:

```java
        LouiCommand executor = new LouiCommand(manager);
        getCommand("loui").setExecutor(executor);
        getCommand("loui").setTabCompleter(executor);
```

- [ ] **Step 3: Fazer o broadcast alcançar o staff**

Em `PrisonManager.java`, adicionar:

```java
    /** Envia uma mensagem a todo jogador online com a permissao de notificacao. */
    public void notifyStaff(String message, UUID excluded) {
        String permission = plugin.getConfig().getString("notify.permission", "loui.notify");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (excluded != null && p.getUniqueId().equals(excluded)) continue;
            if (p.hasPermission(permission)) p.sendMessage(message);
        }
    }
```

Em `imprison`, substituir o bloco que envia `m2` apenas ao executor:

```java
        notifyStaff(m2, target.getUniqueId());
        Bukkit.getConsoleSender().sendMessage(m2 + ChatColor.DARK_GRAY + " (por " + byWhom + ")");
```

(as linhas `Player executor = Bukkit.getPlayerExact(byWhom);` e `if (executor != null) executor.sendMessage(m2);` saem.)

- [ ] **Step 4: Declarar as permissões novas**

Em `src/main/resources/plugin.yml`, trocar `version: 1.0.0` por `version: 1.1.0`, atualizar o `usage` do comando e acrescentar as permissões:

```yaml
    usage: |
      /loui <nick> <tempo: 30m, 2h ou 2d> <motivo...>
      /loui free <nick>
      /loui list

permissions:
  loui.use:
    description: Permite usar o /loui.
    default: op
  loui.exempt:
    description: Torna o jogador imune ao castigo.
    default: false
  loui.notify:
    description: Recebe aviso quando alguem e contido.
    default: op
```

- [ ] **Step 5: Adicionar config e mensagem**

Em `config.yml`, adicionar o bloco de notificação no fim:

```yaml

notify:
  # Quem recebe o aviso quando alguem e contido.
  permission: loui.notify
```

E dentro de `messages:`, junto das outras de erro:

```yaml
  is-exempt: '&cEsse jogador e imune ao castigo.'
```

- [ ] **Step 6: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/LouiCommand.java src/main/java/com/nemonicorp/loui/LouiPlugin.java src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/resources/plugin.yml src/main/resources/config.yml
git commit -m "feat: imunidade loui.exempt e aviso real para o staff

A chave jailed-broadcast-staff so alcancava quem executou o comando e o
console, apesar do nome — os demais moderadores online nao ficavam
sabendo. Agora vai para todos com loui.notify.

Adiciona loui.exempt, que faltava por completo: qualquer um com loui.use
podia conter o dono do servidor.

Extrai o comando de LouiPlugin para LouiCommand."
```

---

### Task 5: Punição de jogador offline

Entrega o **item 2**, mais a segunda checagem de imunidade.

**Files:**
- Modify: `src/main/java/com/nemonicorp/loui/LouiCommand.java`
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `PrisonManager.notifyStaff` (Task 4); `TimeParser.formatDuration` (Task 1).
- Produces: `PrisonManager.imprisonOffline(OfflinePlayer, long minutes, String reason, String byWhom)`.

- [ ] **Step 1: Resolver o alvo offline no comando**

Em `LouiCommand.onCommand`, substituir todo o trecho final do método — da linha
`Player target = Bukkit.getPlayerExact(args[0]);` até o `return true;` que fecha
o método — por:

```java
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target != null) {
            if (target.hasPermission("loui.exempt")) {
                sender.sendMessage(manager.msg("is-exempt", "&cEsse jogador e imune ao castigo."));
                return true;
            }
            manager.imprison(target, minutes, reason, sender.getName());
            return true;
        }

        // Alvo offline: consulta o usercache local. NUNCA usar getOfflinePlayer(String),
        // que dispara HTTP a Mojang na thread principal em servidor online-mode.
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (offline == null || !offline.hasPlayedBefore()) {
            sender.sendMessage(manager.msg("never-joined", "&cEsse jogador nunca entrou no servidor."));
            return true;
        }

        manager.imprisonOffline(offline, minutes, reason, sender.getName());
        return true;
```

Adicionar o import `org.bukkit.OfflinePlayer`.

- [ ] **Step 2: Registrar a punição offline**

Em `PrisonManager.java`, adicionar:

```java
    /**
     * Pune um jogador que nao esta online. O castigo fica registrado sem localizacao
     * de retorno; handleJoin captura a posicao de login e aplica o estado de vazio.
     */
    public void imprisonOffline(OfflinePlayer target, long minutes, String reason, String byWhom) {
        UUID uuid = target.getUniqueId();
        Prison prison = prisons.get(uuid);
        if (prison == null) {
            prison = new Prison();
            prison.returnLocation = null;      // capturada no primeiro login
            prison.returnGameMode = null;      // idem
            prison.returnAllowFlight = false;
        }

        prison.playerName = target.getName() == null ? "?" : target.getName();
        prison.reason = reason;
        prison.totalMs = minutes * 60_000L;
        prison.endTime = System.currentTimeMillis() + prison.totalMs;

        prisons.put(uuid, prison);
        save();

        String pretty = TimeParser.formatDuration(minutes);
        String m = msg("jailed-broadcast-staff", "&7%player% foi contido por %time%: &f%reason%")
                .replace("%player%", prison.playerName)
                .replace("%time%", pretty)
                .replace("%minutes%", String.valueOf(minutes))
                .replace("%reason%", reason);

        notifyStaff(m, null);
        Bukkit.getConsoleSender().sendMessage(m + ChatColor.DARK_GRAY + " (por " + byWhom + ", offline)");

        plugin.getLogger().info("[LOUI] " + prison.playerName + " contido offline por " + pretty
                + ". Motivo: " + reason + " (por " + byWhom + ")");
    }
```

Adicionar o import `org.bukkit.OfflinePlayer`.

- [ ] **Step 3: Tratar tudo no login**

Substituir `handleJoin` por:

```java
    public void handleJoin(Player player) {
        Prison prison = prisons.get(player.getUniqueId());
        if (prison == null) return;

        // Imunidade so e consultavel com o jogador online. Sem esta checagem, daria
        // pra contornar loui.exempt punindo um admin enquanto ele estivesse fora.
        if (player.hasPermission("loui.exempt")) {
            prisons.remove(player.getUniqueId());
            voidState.releaseBand(prison.band);
            save();
            notifyStaff(msg("exempt-discarded", "&7%player% e imune ao castigo — punicao descartada.")
                    .replace("%player%", player.getName()), player.getUniqueId());
            return;
        }

        if (System.currentTimeMillis() >= prison.endTime) {
            if (prison.returnLocation == null) {
                // Punicao offline que expirou antes do primeiro login: nunca chegou a
                // ser aplicada, entao nao ha nada a restaurar nem para onde teleportar.
                prisons.remove(player.getUniqueId());
                voidState.releaseBand(prison.band);
                save();
            } else {
                release(player);
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
        applyVoidState(player, prison);
    }
```

- [ ] **Step 4: Adicionar a mensagem**

Em `config.yml`, dentro de `messages:`:

```yaml
  never-joined: '&cEsse jogador nunca entrou no servidor.'
  exempt-discarded: '&7%player% e imune ao castigo — punicao descartada.'
```

- [ ] **Step 5: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/LouiCommand.java src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/resources/config.yml
git commit -m "feat: permite punir jogadores offline

O caso mais comum de moderacao e o griefer que destroi e desloga antes de
alguem reagir; antes disso o staff simplesmente nao conseguia agir.

Usa o usercache local via getOfflinePlayerIfCached, sem consulta a Mojang
na thread principal. O ponto de retorno e capturado no primeiro login.

Verifica loui.exempt tambem no login, fechando a brecha de punir um admin
offline para contornar a imunidade."
```

---

### Task 6: Whitelist de comandos

Entrega o **item 10**.

**Files:**
- Modify: `src/main/java/com/nemonicorp/loui/LouiListener.java`
- Create: `src/test/java/com/nemonicorp/loui/CommandRootTest.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `PrisonManager.isImprisoned/msg`.
- Produces: `static String LouiListener.commandRoot(String)`; `boolean PrisonManager.isCommandAllowed(String rawMessage)`.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/com/nemonicorp/loui/CommandRootTest.java`:

```java
package com.nemonicorp.loui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRootTest {

    @Test
    void extraiRaizComBarra() {
        assertEquals("msg", LouiListener.commandRoot("/msg alguem oi"));
    }

    @Test
    void extraiRaizSemBarra() {
        assertEquals("msg", LouiListener.commandRoot("msg"));
    }

    @Test
    void normalizaCaixaEEspacos() {
        assertEquals("msg", LouiListener.commandRoot("  /MSG  alguem "));
    }

    @Test
    void comandoSemArgumentos() {
        assertEquals("home", LouiListener.commandRoot("/home"));
    }

    @Test
    void entradasVazias() {
        assertEquals("", LouiListener.commandRoot(null));
        assertEquals("", LouiListener.commandRoot(""));
        assertEquals("", LouiListener.commandRoot("/"));
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `.\mvnw.cmd test`
Expected: FAIL na compilação — `cannot find symbol: method commandRoot(String)`.

- [ ] **Step 3: Implementar a extração da raiz e o filtro**

Em `LouiListener.java`, adicionar o método estático:

```java
    /** Raiz do comando digitado: sem barra, sem argumentos, em minusculas. */
    static String commandRoot(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int space = s.indexOf(' ');
        if (space >= 0) s = s.substring(0, space);
        return s.toLowerCase();
    }
```

Substituir o handler `onCommand`:

```java
    // Comandos bloqueados durante o castigo, exceto os liberados na whitelist
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!manager.isImprisoned(p.getUniqueId())) return;
        if (manager.isCommandAllowed(event.getMessage())) return;

        event.setCancelled(true);
        p.sendMessage(manager.msg("no-commands", "&cVoce nao pode usar comandos enquanto estiver contido."));
    }
```

Em `PrisonManager.java`, adicionar:

```java
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
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `.\mvnw.cmd test`
Expected: PASS — `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Adicionar a config**

Em `config.yml`, no fim:

```yaml

restrictions:
  # Comandos que o preso ainda pode usar — util pra dar um canal de recurso.
  # Vazio bloqueia tudo, que e o comportamento padrao.
  # Aliases nao sao resolvidos: liberar /msg nao libera /tell.
  allowed-commands: []
```

- [ ] **Step 6: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 16, Failures: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/LouiListener.java src/test/java/com/nemonicorp/loui/CommandRootTest.java src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/resources/config.yml
git commit -m "feat: whitelist de comandos durante o castigo

O bloqueio total impedia o punido de recorrer. Servidores agora podem
liberar comandos especificos, tipo /discord ou /msg.

Sai vazia de fabrica, preservando exatamente o comportamento atual de quem
atualizar."
```

---

### Task 7: Documentação e verificação final

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: tudo das tasks 1–6.
- Produces: nada.

- [ ] **Step 1: Atualizar o badge e os comandos no README**

Trocar `v1.0.0` por `v1.1.0` no badge de versão. Na tabela "Formatos de tempo aceitos", inserir a linha de horas entre `30m` e `2d`:

```markdown
| `2h` | 2 horas |
```

- [ ] **Step 2: Documentar as permissões**

Substituir a frase "Todos exigem a permissão `loui.use` (padrão: **op**)." por:

```markdown
Todos exigem a permissão `loui.use` (padrão: **op**).

| Permissão | Padrão | Para quê |
|---|---|---|
| `loui.use` | op | Usar `/loui` |
| `loui.exempt` | — | Imunidade: o jogador não pode ser contido |
| `loui.notify` | op | Recebe aviso quando alguém é contido |
```

- [ ] **Step 3: Documentar a config nova**

No bloco de exemplo do `config.yml` no README, dentro de `void:`, acrescentar
depois de `min-y`:

```yaml
  band-gap: 100.0    # espaco morto entre faixas de presos
  max-bands: 8       # teto de faixas distintas
```

E, depois do bloco `messages:`, acrescentar os três blocos novos:

```yaml
safety:
  release-all-on-disable: true   # solta presos ONLINE ao desabilitar o plugin

restrictions:
  allowed-commands: []           # comandos liberados ao preso; vazio bloqueia tudo

notify:
  permission: loui.notify        # quem recebe o aviso de punicao
```

- [ ] **Step 4: Documentar as duas limitações**

Acrescentar ao fim da seção "O que acontece com o jogador contido":

```markdown
> ⚠️ **Antes de desinstalar o plugin**, rode `/loui list` e solte todos. O
> `release-all-on-disable` alcança apenas quem está **online** — o estado de um
> preso offline (cegueira, invulnerabilidade, posição) fica gravado no playerdata
> e, sem o plugin carregado, não há código capaz de desfazê-lo.

> ⚠️ A whitelist de comandos não resolve aliases. Liberar `/msg` não libera
> `/tell` — ambos precisam constar na lista.
```

- [ ] **Step 5: Build completo e verificação**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 16, Failures: 0, Errors: 0`, jar em `target/LouiPlugin.jar`.

Conferir que a versão empacotada saiu certa:

Run: `unzip -p target/LouiPlugin.jar plugin.yml | grep -E "^(name|version)"`
Expected: `name: LouiPlugin` e `version: 1.1.0`.

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "docs: atualiza README para a v1.1.0

Documenta o sufixo de horas, as permissoes loui.exempt e loui.notify, as
chaves de config novas e as duas limitacoes conhecidas (presos offline no
disable, aliases na whitelist)."
```

---

## Verificação manual (requer servidor)

Os testes automatizados cobrem apenas `TimeParser` e `commandRoot`. O resto depende de servidor vivo. Rodar antes de publicar a release:

1. Punir jogador online → cai na faixa 0, bossbar com motivo e relógio.
2. Punir dois jogadores → faixas distintas, sem se empurrarem.
3. `/loui X 2h motivo` → aceito, bossbar mostra `2:00:00`.
4. Punir alguém offline → ao entrar, é contido; ao ser solto, volta à posição de login.
5. Punir offline por 1 minuto e entrar depois de 2 → nada acontece, sem teleporte para o spawn.
6. Punir offline alguém com `loui.exempt` → ao entrar, castigo descartado, staff avisado.
7. `/loui <admin com exempt> 30m teste` com o admin online → recusado com `is-exempt`.
8. Com `allowed-commands: [/msg]` → `/msg` passa, `/home` é bloqueado.
9. Parar o servidor com um preso online → é solto e volta ao lugar certo.
10. Subir com um `prisons.yml` da v1.0.0 → carrega sem erro, faixa realocada.
11. Confirmar que um segundo moderador com `loui.notify` recebe o aviso.

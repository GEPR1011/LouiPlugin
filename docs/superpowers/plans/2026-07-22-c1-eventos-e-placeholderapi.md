# LouiPlugin — Eventos de API e PlaceholderAPI (C1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Abrir o LouiPlugin para integração — eventos que outros plugins podem observar e vetar, e placeholders que scoreboards e tab podem exibir.

**Architecture:** Pacote público `com.nemonicorp.loui.api` com dois eventos e um enum de causa. `PrisonManager` dispara os eventos nos seis caminhos por onde um castigo começa ou termina. A expansão PlaceholderAPI fica numa classe isolada, referenciada apenas dentro do `if` que detecta a plugin — para que a JVM nunca a carregue num servidor sem ela.

**Tech Stack:** Java 21, Maven, Paper API 1.21.4 (`provided`), PlaceholderAPI 2.11.6 (`provided`), JUnit 5.

## Global Constraints

- Java 21. Paper API e PlaceholderAPI ambos em scope `provided` — **nenhum dos dois vai para o jar**. A promessa de "roda sem instalar nada além do Paper" continua valendo.
- Nenhuma dependência de runtime nova. `depend` continua vazio; PlaceholderAPI entra apenas como `softdepend`.
- Comentários e identificadores **sem acentos**, seguindo o código existente ("nao", "punicao", "castigo", "invulneravel"). Mensagens de `config.yml` também sem acentos. O README é prosa normal, com acentos.
- Toda chave de config nova precisa de default no código — um `config.yml` da v1.1.0 tem de continuar funcionando sem edição.
- `finalName` do jar é `${project.name}` = `LouiPlugin`. Não alterar.
- Versão `1.2.0` em `pom.xml` e `plugin.yml`.
- Commits em português, **sem trailer de atribuição**.
- Os 24 testes existentes devem continuar passando.
- **`LouiReleaseEvent` não pode implementar `Cancellable`.** Vetar uma soltura prenderia o jogador para sempre e, durante o desligamento, o deixaria cego e invulnerável sem plugin carregado para desfazer.

---

## Estrutura de arquivos

| Arquivo | Responsabilidade | Ação |
|---|---|---|
| `src/main/java/com/nemonicorp/loui/api/ReleaseCause.java` | Enum das quatro causas de soltura | Criar |
| `src/main/java/com/nemonicorp/loui/api/LouiImprisonEvent.java` | Evento cancelável de punição | Criar |
| `src/main/java/com/nemonicorp/loui/api/LouiReleaseEvent.java` | Notificação de soltura | Criar |
| `src/test/java/com/nemonicorp/loui/api/LouiEventsTest.java` | Testes dos eventos | Criar |
| `src/main/java/com/nemonicorp/loui/LouiPlaceholders.java` | Expansão PlaceholderAPI | Criar |
| `src/main/java/com/nemonicorp/loui/PrisonManager.java` | Dispara eventos; acessores para placeholders; extrai helper de broadcast | Modificar |
| `src/main/java/com/nemonicorp/loui/LouiCommand.java` | Reage ao veto | Modificar |
| `src/main/java/com/nemonicorp/loui/LouiPlugin.java` | Registro condicional da expansão | Modificar |
| `pom.xml` | Repositório e dependência do PlaceholderAPI; versão | Modificar |
| `src/main/resources/plugin.yml` | `softdepend`; versão | Modificar |
| `src/main/resources/config.yml` | Mensagens novas | Modificar |
| `README.md` | Seção de integração | Modificar |

---

### Task 1: Classes de evento

Entrega a superfície pública da API, com testes. Nada ainda dispara os eventos.

**Files:**
- Create: `src/main/java/com/nemonicorp/loui/api/ReleaseCause.java`
- Create: `src/main/java/com/nemonicorp/loui/api/LouiImprisonEvent.java`
- Create: `src/main/java/com/nemonicorp/loui/api/LouiReleaseEvent.java`
- Create: `src/test/java/com/nemonicorp/loui/api/LouiEventsTest.java`
- Modify: `pom.xml` (só a versão do projeto)

**Interfaces:**
- Consumes: nada.
- Produces: `ReleaseCause` com valores `MANUAL, EXPIRED, SHUTDOWN, EXEMPT` nessa ordem; `LouiImprisonEvent(OfflinePlayer target, long minutes, String reason, String byWhom, boolean offline)` com getters `getTarget/getMinutes/getReason/getByWhom/isOffline` mais `isCancelled/setCancelled` e `static HandlerList getHandlerList()`; `LouiReleaseEvent(Player player, String reason, ReleaseCause cause)` com getters `getPlayer/getReason/getCause` e `static HandlerList getHandlerList()`.

- [ ] **Step 1: Bumpar a versão do projeto**

Em `pom.xml`, trocar `<version>1.1.0</version>` (a do projeto, logo abaixo de `<artifactId>LouiPlugin</artifactId>`) por `<version>1.2.0</version>`. **Não** tocar nas versões de `paper-api`, `junit-jupiter`, `maven-compiler-plugin` ou `maven-surefire-plugin`.

- [ ] **Step 2: Escrever o teste que falha**

Criar `src/test/java/com/nemonicorp/loui/api/LouiEventsTest.java`:

```java
package com.nemonicorp.loui.api;

import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LouiEventsTest {

    @Test
    void imprisonEventGuardaOsCampos() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 120L, "griefing", "Steve", true);
        assertNull(event.getTarget());
        assertEquals(120L, event.getMinutes());
        assertEquals("griefing", event.getReason());
        assertEquals("Steve", event.getByWhom());
        assertTrue(event.isOffline());
    }

    @Test
    void imprisonEventComecaNaoCancelado() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 30L, "teste", "Alex", false);
        assertFalse(event.isCancelled());
        assertFalse(event.isOffline());
    }

    @Test
    void imprisonEventPodeSerCanceladoEDescancelado() {
        LouiImprisonEvent event = new LouiImprisonEvent(null, 30L, "teste", "Alex", false);
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    void releaseEventGuardaOsCampos() {
        LouiReleaseEvent event = new LouiReleaseEvent(null, "griefing", ReleaseCause.EXPIRED);
        assertNull(event.getPlayer());
        assertEquals("griefing", event.getReason());
        assertEquals(ReleaseCause.EXPIRED, event.getCause());
    }

    @Test
    void releaseEventNaoEhCancelavel() {
        assertFalse(Cancellable.class.isAssignableFrom(LouiReleaseEvent.class),
                "LouiReleaseEvent nao pode ser cancelavel: vetar uma soltura prenderia o "
                        + "jogador para sempre e, no desligamento, o deixaria cego e "
                        + "invulneravel sem plugin para desfazer");
    }

    @Test
    void releaseCauseTemExatamenteQuatroValores() {
        assertArrayEquals(
                new ReleaseCause[] {
                        ReleaseCause.MANUAL, ReleaseCause.EXPIRED,
                        ReleaseCause.SHUTDOWN, ReleaseCause.EXEMPT },
                ReleaseCause.values());
    }

    @Test
    void handlerListEstaticaEhAMesmaDaInstancia() {
        LouiImprisonEvent imprison = new LouiImprisonEvent(null, 1L, "x", "y", false);
        assertSame(LouiImprisonEvent.getHandlerList(), imprison.getHandlers());

        LouiReleaseEvent release = new LouiReleaseEvent(null, "x", ReleaseCause.MANUAL);
        assertSame(LouiReleaseEvent.getHandlerList(), release.getHandlers());
    }
}
```

- [ ] **Step 3: Rodar e confirmar que falha**

Run: `.\mvnw.cmd test`
Expected: FAIL na compilação — `package com.nemonicorp.loui.api does not exist` / `cannot find symbol: class LouiImprisonEvent`.

- [ ] **Step 4: Criar o enum**

Criar `src/main/java/com/nemonicorp/loui/api/ReleaseCause.java`:

```java
package com.nemonicorp.loui.api;

/** Por que um castigo terminou. */
public enum ReleaseCause {

    /** Soltura manual via /loui free. */
    MANUAL,

    /** A pena chegou ao fim. */
    EXPIRED,

    /** Release em massa ao desabilitar o plugin. */
    SHUTDOWN,

    /** Punicao descartada no login por o jogador ter loui.exempt. */
    EXEMPT
}
```

- [ ] **Step 5: Criar o evento de punição**

Criar `src/main/java/com/nemonicorp/loui/api/LouiImprisonEvent.java`:

```java
package com.nemonicorp.loui.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Disparado antes de um castigo ser aplicado.
 *
 * Cancelar impede a punicao por completo: nenhum estado e alterado, nenhum
 * teleporte acontece, e o moderador recebe aviso de que outro plugin impediu.
 */
public class LouiImprisonEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer target;
    private final long minutes;
    private final String reason;
    private final String byWhom;
    private final boolean offline;
    private boolean cancelled;

    public LouiImprisonEvent(OfflinePlayer target, long minutes, String reason,
                             String byWhom, boolean offline) {
        this.target = target;
        this.minutes = minutes;
        this.reason = reason;
        this.byWhom = byWhom;
        this.offline = offline;
    }

    /** O alvo. E um Player quando isOffline() e false. */
    public OfflinePlayer getTarget() {
        return target;
    }

    /** Duracao do castigo em minutos. */
    public long getMinutes() {
        return minutes;
    }

    public String getReason() {
        return reason;
    }

    /** Nome de quem executou o comando, ou do console. */
    public String getByWhom() {
        return byWhom;
    }

    /** true quando o alvo nao estava online no momento do comando. */
    public boolean isOffline() {
        return offline;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

- [ ] **Step 6: Criar o evento de soltura**

Criar `src/main/java/com/nemonicorp/loui/api/LouiReleaseEvent.java`:

```java
package com.nemonicorp.loui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Disparado quando um castigo termina, seja qual for o motivo.
 *
 * NAO e cancelavel de proposito: vetar uma soltura prenderia o jogador para
 * sempre e, durante o desligamento, o deixaria cego e invulneravel sem plugin
 * carregado para desfazer.
 */
public class LouiReleaseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String reason;
    private final ReleaseCause cause;

    public LouiReleaseEvent(Player player, String reason, ReleaseCause cause) {
        this.player = player;
        this.reason = reason;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    /** O motivo original do castigo. */
    public String getReason() {
        return reason;
    }

    public ReleaseCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

- [ ] **Step 7: Rodar e confirmar que passa**

Run: `.\mvnw.cmd test`
Expected: PASS — `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` (24 existentes + 7 novos).

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/nemonicorp/loui/api src/test/java/com/nemonicorp/loui/api
git commit -m "feat: adiciona os eventos de API do castigo

Cria o pacote publico com.nemonicorp.loui.api com LouiImprisonEvent
(cancelavel), LouiReleaseEvent e o enum ReleaseCause.

A soltura nao e cancelavel de proposito: vetar prenderia o jogador para
sempre e, no desligamento, reintroduziria o estado que a v1.1.0 consertou.
Um teste estrutural protege essa decisao contra regressao.

Nada dispara os eventos ainda. Versao 1.2.0."
```

---

### Task 2: Disparar os eventos

Liga os eventos aos seis caminhos por onde um castigo começa ou termina, e corrige duas causas erradas herdadas do código atual.

**Files:**
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/java/com/nemonicorp/loui/LouiCommand.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `LouiImprisonEvent`, `LouiReleaseEvent`, `ReleaseCause` da Task 1.
- Produces: `PrisonManager.imprison(...)` e `imprisonOffline(...)` passam a devolver `boolean` (`false` = vetado); `releaseInternal(Player, boolean persist, ReleaseCause cause)`.

- [ ] **Step 1: Adicionar os imports em PrisonManager**

No topo de `PrisonManager.java`, junto dos outros imports:

```java
import com.nemonicorp.loui.api.LouiImprisonEvent;
import com.nemonicorp.loui.api.LouiReleaseEvent;
import com.nemonicorp.loui.api.ReleaseCause;
import org.bukkit.plugin.RegisteredListener;
```

- [ ] **Step 2: Adicionar o helper de veto e o de broadcast**

Em `PrisonManager.java`, logo antes de `public void freeByName(...)`:

```java
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
```

- [ ] **Step 3: Ligar o veto e o helper em `imprison`**

Substituir a assinatura e o miolo de `imprison`. O método passa a devolver `boolean`, e todo o bloco que montava `m2`/console/log some, substituído pela chamada ao helper:

```java
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
        }

        prison.playerName = target.getName();
        prison.reason = reason;
        prison.totalMs = minutes * 60_000L;
        prison.endTime = System.currentTimeMillis() + prison.totalMs;

        prisons.put(uuid, prison);
        save();

        applyVoidState(target, prison);

        String m1 = msg("jailed-target", "&cVoce foi contido pelo motivo: &f%reason% &7(%time%)")
                .replace("%reason%", reason)
                .replace("%time%", TimeParser.formatDuration(minutes))
                .replace("%minutes%", String.valueOf(minutes));
        target.sendMessage(m1);

        announceImprison(target.getName(), minutes, reason, byWhom, target.getUniqueId(), false);
        return true;
    }
```

- [ ] **Step 4: Ligar o veto e o helper em `imprisonOffline`**

```java
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
```

- [ ] **Step 5: Disparar o evento de soltura em `releaseInternal`**

```java
    /** Restaura o jogador: local original, gamemode, efeitos, bossbar. */
    public void release(Player player) {
        releaseInternal(player, true, ReleaseCause.MANUAL);
    }

    private void releaseInternal(Player player, boolean persist, ReleaseCause cause) {
        Prison prison = prisons.remove(player.getUniqueId());
        if (prison == null) return;

        Bukkit.getPluginManager().callEvent(new LouiReleaseEvent(player, prison.reason, cause));

        if (prison.bar != null) prison.bar.removeAll();

        voidState.restore(player, prison.returnLocation, prison.returnGameMode, prison.returnAllowFlight);
        voidState.releaseBand(prison.band);

        player.sendMessage(msg("released", "&aVoce foi libertado. Comporte-se."));
        if (persist) save();

        plugin.getLogger().info("[LOUI] " + player.getName() + " libertado.");
    }
```

- [ ] **Step 6: Corrigir a causa no tick**

No fim de `tick()`, o laço hoje chama `release(p)`, que passaria `MANUAL` — mas essa soltura é por expiração. Trocar:

```java
        for (Player p : toRelease) {
            releaseInternal(p, true, ReleaseCause.EXPIRED);
        }
```

- [ ] **Step 7: Corrigir a causa no shutdown**

Em `shutdown()`, trocar a chamada dentro do laço:

```java
            for (Player p : online) {
                releaseInternal(p, false, ReleaseCause.SHUTDOWN);
            }
```

- [ ] **Step 8: Disparar os dois eventos que não passam por `releaseInternal`**

Em `handleJoin`, o ramo da imunidade e o da punição offline expirada removem o registro direto. Ambos precisam disparar o evento explicitamente. Substituir os dois blocos:

```java
        if (player.hasPermission("loui.exempt")) {
            prisons.remove(player.getUniqueId());
            voidState.releaseBand(prison.band);
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
                voidState.releaseBand(prison.band);
                save();
                Bukkit.getPluginManager().callEvent(
                        new LouiReleaseEvent(player, prison.reason, ReleaseCause.EXPIRED));
            } else {
                releaseInternal(player, true, ReleaseCause.EXPIRED);
            }
            return;
        }
```

- [ ] **Step 9: Fazer o comando reagir ao veto**

Em `LouiCommand.onCommand`, as duas chamadas de punição passam a checar o retorno. No ramo do alvo online:

```java
            if (!manager.imprison(target, minutes, reason, sender.getName())) {
                sender.sendMessage(manager.msg("imprison-vetoed",
                        "&cA punicao foi impedida por outro plugin."));
            }
            return true;
```

E no ramo offline, no fim do método:

```java
        if (!manager.imprisonOffline(offline, minutes, reason, sender.getName())) {
            sender.sendMessage(manager.msg("imprison-vetoed",
                    "&cA punicao foi impedida por outro plugin."));
        }
        return true;
```

- [ ] **Step 10: Adicionar a mensagem no config**

Em `src/main/resources/config.yml`, dentro de `messages:`, depois de `exempt-discarded`:

```yaml
  imprison-vetoed: '&cA punicao foi impedida por outro plugin.'
```

- [ ] **Step 11: Compilar e rodar os testes**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/java/com/nemonicorp/loui/LouiCommand.java src/main/resources/config.yml
git commit -m "feat: dispara os eventos de castigo

Liga LouiImprisonEvent e LouiReleaseEvent aos seis caminhos por onde um
castigo comeca ou termina. O veto e checado antes de qualquer mutacao: se
cancelado, nenhum estado muda e o moderador e avisado.

Corrige duas causas erradas: o tick de expiracao e o ramo de expiracao do
handleJoin chamavam release(), que reportaria MANUAL em vez de EXPIRED.

Extrai o bloco de broadcast duplicado entre imprison e imprisonOffline,
divida apontada na revisao do sub-projeto A."
```

---

### Task 3: PlaceholderAPI

**Files:**
- Create: `src/main/java/com/nemonicorp/loui/LouiPlaceholders.java`
- Modify: `pom.xml`
- Modify: `src/main/java/com/nemonicorp/loui/PrisonManager.java`
- Modify: `src/main/java/com/nemonicorp/loui/LouiPlugin.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `TimeParser.formatClock(long)`; `PrisonManager.isImprisoned(UUID)`.
- Produces: `PrisonManager.getPrisonerCount() -> int`, `getRemainingMillis(UUID) -> long`, `getReason(UUID) -> String`.

- [ ] **Step 1: Adicionar repositório e dependência do PlaceholderAPI**

Em `pom.xml`, dentro de `<repositories>`, depois do repositório `papermc`:

```xml
        <repository>
            <id>placeholderapi</id>
            <url>https://repo.extendedclip.com/content/repositories/placeholderapi/</url>
        </repository>
```

E dentro de `<dependencies>`, depois da dependência do `paper-api`:

```xml
        <dependency>
            <groupId>me.clip</groupId>
            <artifactId>placeholderapi</artifactId>
            <version>2.11.6</version>
            <scope>provided</scope>
        </dependency>
```

Rodar `.\mvnw.cmd dependency:resolve` para confirmar que a versão resolve. Se `2.11.6` não existir no repositório, abrir `https://repo.extendedclip.com/content/repositories/placeholderapi/me/clip/placeholderapi/` e usar a versão estável mais recente listada, ajustando o `<version>`. **Não** trocar o `<scope>provided</scope>`.

- [ ] **Step 2: Adicionar os acessores no PrisonManager**

Em `PrisonManager.java`, na seção `// ── API consultada pelo listener ──`, depois de `getPrisonerNames()`:

```java
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
```

- [ ] **Step 3: Criar a expansão**

Criar `src/main/java/com/nemonicorp/loui/LouiPlaceholders.java`:

```java
package com.nemonicorp.loui;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Expansao PlaceholderAPI.
 *
 * ATENCAO: esta classe so pode ser referenciada quando a PlaceholderAPI esta
 * instalada. Toca-la sem ela lanca NoClassDefFoundError e derruba o onEnable.
 */
public class LouiPlaceholders extends PlaceholderExpansion {

    private final LouiPlugin plugin;
    private final PrisonManager manager;

    public LouiPlaceholders(LouiPlugin plugin, PrisonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "loui";
    }

    @Override
    public String getAuthor() {
        return "GEPR";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // Nao depende de jogador: vale ate no console.
        if ("count".equalsIgnoreCase(params)) {
            return String.valueOf(manager.getPrisonerCount());
        }

        if (player == null) return "";

        boolean jailed = manager.isImprisoned(player.getUniqueId());

        if ("jailed".equalsIgnoreCase(params)) {
            return jailed
                    ? plugin.getConfig().getString("messages.placeholder-yes", "sim")
                    : plugin.getConfig().getString("messages.placeholder-no", "nao");
        }

        // Vazio (e nao "00:00") pra quem nao esta contido: permite esconder o
        // campo no scoreboard em vez de obrigar o admin a tratar o zero.
        if (!jailed) return "";

        if ("time_left".equalsIgnoreCase(params)) {
            return TimeParser.formatClock(manager.getRemainingMillis(player.getUniqueId()));
        }
        if ("reason".equalsIgnoreCase(params)) {
            return manager.getReason(player.getUniqueId());
        }

        // null faz a PlaceholderAPI exibir o placeholder cru, sinalizando erro de digitacao.
        return null;
    }
}
```

- [ ] **Step 4: Registrar condicionalmente**

Em `LouiPlugin.onEnable`, depois do bloco que registra o listener e antes do agendamento do tick:

```java
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LouiPlaceholders(this, manager).register();
            getLogger().info("PlaceholderAPI detectada: placeholders registrados.");
        }
```

- [ ] **Step 5: Declarar o softdepend e bumpar a versão**

Em `src/main/resources/plugin.yml`, trocar `version: 1.1.0` por `version: 1.2.0` e acrescentar, depois da linha `author: GEPR`:

```yaml
softdepend: [PlaceholderAPI]
```

- [ ] **Step 6: Adicionar as mensagens dos placeholders**

Em `src/main/resources/config.yml`, dentro de `messages:`, depois de `imprison-vetoed`:

```yaml
  placeholder-yes: 'sim'
  placeholder-no: 'nao'
```

- [ ] **Step 7: Compilar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`.

Confirmar que a PlaceholderAPI **não** foi empacotada:

Run: `.\mvnw.cmd dependency:list -DincludeScope=runtime`
Expected: a saída não contém `me.clip:placeholderapi`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/nemonicorp/loui/LouiPlaceholders.java src/main/java/com/nemonicorp/loui/PrisonManager.java src/main/java/com/nemonicorp/loui/LouiPlugin.java src/main/resources/plugin.yml src/main/resources/config.yml
git commit -m "feat: expoe quatro placeholders via PlaceholderAPI

Adiciona %loui_jailed%, %loui_time_left%, %loui_reason% e %loui_count%.

A expansao so e registrada quando a PlaceholderAPI esta presente, e a
classe e referenciada apenas dentro desse if — toca-la sem a plugin
lancaria NoClassDefFoundError e derrubaria o onEnable.

A dependencia entra em escopo provided e nao vai para o jar: o plugin
continua rodando sem nada alem do Paper."
```

---

### Task 4: Documentação e verificação final

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: tudo das Tasks 1-3.
- Produces: nada.

- [ ] **Step 1: Atualizar o badge de versão**

Em `README.md`, trocar `v1.1.0` por `v1.2.0` no badge de versão.

- [ ] **Step 2: Trocar o badge de dependências**

O badge atual diz `dependências-nenhuma`. Continua verdadeiro para execução, mas agora existe uma integração opcional. Trocar a linha do badge por:

```markdown
[![Deps](https://img.shields.io/badge/dependências-nenhuma-lightgrey?style=for-the-badge)](.)
[![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-opcional-blue?style=for-the-badge)](.)
```

- [ ] **Step 3: Adicionar a seção de integração**

Inserir uma seção nova logo antes de `## ⚙️ Requisitos`:

```markdown
---

## 🔌 Integração com outros plugins

### Eventos

O plugin dispara dois eventos em `com.nemonicorp.loui.api`.

**`LouiImprisonEvent`** — cancelável, disparado **antes** de o castigo ser
aplicado. Cancelar impede a punição por completo: nenhum estado muda, nenhum
teleporte acontece, e o moderador é avisado.

| Método | Devolve |
|---|---|
| `getTarget()` | `OfflinePlayer` — é um `Player` quando `isOffline()` é `false` |
| `getMinutes()` | Duração em minutos |
| `getReason()` | Motivo informado |
| `getByWhom()` | Quem executou o comando |
| `isOffline()` | `true` se o alvo não estava online |

**`LouiReleaseEvent`** — **não** cancelável, disparado quando um castigo
termina. Vetar uma soltura prenderia o jogador para sempre, então a API não
permite.

| Método | Devolve |
|---|---|
| `getPlayer()` | O jogador solto |
| `getReason()` | Motivo original do castigo |
| `getCause()` | `MANUAL`, `EXPIRED`, `SHUTDOWN` ou `EXEMPT` |

| Causa | Quando |
|---|---|
| `MANUAL` | `/loui free` |
| `EXPIRED` | A pena chegou ao fim |
| `SHUTDOWN` | Release em massa ao desabilitar o plugin |
| `EXEMPT` | Punição descartada no login por imunidade |

### PlaceholderAPI

Opcional. Se a PlaceholderAPI estiver instalada, quatro placeholders ficam
disponíveis; sem ela, o plugin funciona normalmente e nada é registrado.

| Placeholder | Devolve | Sem castigo ativo |
|---|---|---|
| `%loui_jailed%` | `sim` / `nao` (configurável) | `nao` |
| `%loui_time_left%` | `2:00:00` | vazio |
| `%loui_reason%` | O motivo | vazio |
| `%loui_count%` | Total de presos | o número real |

> `%loui_time_left%` devolve string vazia — e não `00:00` — para quem não está
> contido, permitindo esconder o campo no scoreboard.
```

- [ ] **Step 4: Atualizar a estrutura do projeto**

No bloco "Estrutura do projeto", acrescentar as duas entradas novas, entre `PrisonManager.java` e `VoidState.java` para manter a ordem lógica:

```
│   ├── LouiPlaceholders.java                # expansao PlaceholderAPI (opcional)
│   ├── api/                                 # superficie publica: eventos e enum
```

- [ ] **Step 5: Build final e verificação do jar**

Run: `.\mvnw.cmd clean package`
Expected: BUILD SUCCESS, `Tests run: 31, Failures: 0, Errors: 0`.

Verificar a versão empacotada. Se `unzip` não existir, usar PowerShell:

```powershell
Copy-Item target\LouiPlugin.jar "$env:TEMP\loui.zip" -Force
Expand-Archive "$env:TEMP\loui.zip" -DestinationPath "$env:TEMP\loui_x" -Force
Get-Content "$env:TEMP\loui_x\plugin.yml" | Select-Object -First 6
```

Expected: `name: LouiPlugin`, `version: 1.2.0`, `softdepend: [PlaceholderAPI]`.

Confirmar também que as classes da API foram empacotadas:

```powershell
Get-ChildItem "$env:TEMP\loui_x\com\nemonicorp\loui\api"
```

Expected: `LouiImprisonEvent.class`, `LouiReleaseEvent.class`, `ReleaseCause.class`.

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "docs: documenta os eventos e os placeholders

Acrescenta a secao de integracao com a tabela dos dois eventos, o enum de
causas e os quatro placeholders. Deixa explicito que PlaceholderAPI e
opcional e que o jar nao a embute."
```

---

## Verificação manual (requer servidor)

Os testes automatizados cobrem só os eventos como objetos. O disparo real e a expansão precisam de servidor vivo:

1. Servidor sobe **sem** PlaceholderAPI instalada → plugin habilita sem erro. **É o teste mais importante**: prova que o registro condicional protege quem não tem a plugin.
2. Servidor sobe **com** PlaceholderAPI → log mostra "placeholders registrados".
3. `/papi parse me %loui_jailed%` fora de castigo → `nao`.
4. Punir e repetir → `sim`; `%loui_time_left%` mostra o relógio; `%loui_reason%` mostra o motivo.
5. `/papi parse me %loui_count%` reflete o número de presos.
6. Plugin de teste escutando `LouiImprisonEvent` recebe o evento ao punir.
7. Plugin de teste cancela o evento → nada muda no servidor, moderador vê `imprison-vetoed`, console lista os ouvintes.
8. `/loui free` → `LouiReleaseEvent` com causa `MANUAL`.
9. Pena expira sozinha → causa `EXPIRED`.
10. Parar o servidor com preso online → causa `SHUTDOWN`.
11. Punir offline alguém com `loui.exempt` e fazê-lo entrar → causa `EXEMPT`.
12. Punir offline por 1 minuto, entrar depois de 2 → causa `EXPIRED`, sem teleporte.

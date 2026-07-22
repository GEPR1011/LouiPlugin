# LouiPlugin — Eventos de API e PlaceholderAPI (sub-projeto C1)

**Data:** 2026-07-22
**Versão alvo:** 1.2.0
**Status:** design aprovado, aguardando plano de implementação
**Pré-requisito:** v1.1.0 validada em servidor e mesclada

---

## 1. Contexto e escopo

O LouiPlugin hoje é uma ilha: nenhum outro plugin consegue reagir a uma punição
nem ler o estado de um preso. Este sub-projeto abre as duas portas mais baratas
— eventos de API e PlaceholderAPI — sem tocar na camada de persistência.

O roteiro `2026-07-22-roadmap-subprojetos-b-c-d.md` separou o sub-projeto C em
duas etapas. Esta é a **C1**, que não depende de storage. O histórico e o
`/loui info` ficam para a **C2**, que precisa da decisão de onde os dados moram.

### No escopo

| Item | Entrega |
|---|---|
| 11 | `LouiImprisonEvent` (cancelável) e `LouiReleaseEvent` (notificação) |
| 8 | Expansão PlaceholderAPI com quatro placeholders |

### Fora do escopo

Histórico de punições e `/loui info` (C2); MySQL e sincronização de rede (B);
efeitos configuráveis e modo jail (D).

---

## 2. Decisões tomadas

**`LouiReleaseEvent` não é cancelável.** Só notifica, carregando a causa. Um
plugin mal escrito que vetasse a soltura prenderia alguém para sempre — e, se
vetasse durante o desligamento, reintroduziria exatamente o bug que a v1.1.0
consertou: jogador cego e invulnerável, sem plugin carregado para desfazer.
O veto existe apenas na aplicação do castigo, onde impedir é uma decisão
legítima.

**Quatro placeholders, não mais.** Cada placeholder é superfície pública que se
mantém para sempre. É fácil crescer o conjunto depois e impossível encolher.

**Veto produz mensagem ao moderador e log no console.** Sem isso, quem digitou
`/loui` vê o comando não fazer nada e conclui que o plugin está quebrado.

---

## 3. Arquitetura

### Pacote novo: `com.nemonicorp.loui.api`

Superfície pública separada do interno. A separação por pacote deixa explícito
o que é contrato com terceiros — e o que pode mudar sem aviso.

| Arquivo | Responsabilidade |
|---|---|
| `api/LouiImprisonEvent.java` | Evento cancelável, disparado antes de aplicar o castigo |
| `api/LouiReleaseEvent.java` | Notificação de soltura, com a causa |
| `api/ReleaseCause.java` | Enum: `MANUAL`, `EXPIRED`, `SHUTDOWN`, `EXEMPT` |

### Classe nova: `LouiPlaceholders`

Fica no pacote raiz (`com.nemonicorp.loui`), não em `api` — não é contrato para
terceiros, é integração com um plugin específico.

### Arquivos modificados

| Arquivo | Mudança |
|---|---|
| `PrisonManager.java` | Dispara os eventos; `releaseInternal` ganha `ReleaseCause`; extrai o helper de broadcast |
| `LouiPlugin.java` | Registra a expansão se PlaceholderAPI estiver presente |
| `plugin.yml` | `softdepend: [PlaceholderAPI]`, versão `1.2.0` |
| `pom.xml` | Repositório e dependência do PlaceholderAPI em escopo `provided`, versão `1.2.0` |
| `config.yml` | Mensagens novas |

---

## 4. Eventos

### `LouiImprisonEvent`

Cancelável. Disparado por `imprison` e por `imprisonOffline`.

```
OfflinePlayer getTarget()
long getMinutes()
String getReason()
String getByWhom()
boolean isOffline()
```

O alvo é `OfflinePlayer`, não `Player`, para que o mesmo evento sirva aos dois
caminhos de punição. `Player` estende `OfflinePlayer`, então um listener pode
fazer o cast quando `isOffline()` for `false`.

**O veto é checado antes de qualquer mutação.** Se o evento voltar cancelado,
nada acontece: nenhum `prisons.put`, nenhum `save()`, nenhum teleporte, nenhum
efeito. O estado do servidor fica idêntico ao de antes do comando.

### `LouiReleaseEvent`

Não cancelável, não implementa `Cancellable`.

```
Player getPlayer()
String getReason()      // o motivo original do castigo
ReleaseCause getCause()
```

Quando há restauração a fazer, o evento é disparado **antes** dela, para que o
listener ainda enxergue o jogador no estado de castigo se precisar.

### `ReleaseCause` e onde cada causa é disparada

`releaseInternal` **não** é o ponto único por onde toda soltura passa. Dois
caminhos do `handleJoin` removem o registro diretamente, sem chamá-lo — porque
nesses casos não existe estado físico a desfazer. O evento precisa ser
disparado explicitamente neles.

| Valor | Quando | Disparado em |
|---|---|---|
| `MANUAL` | `/loui free` | `releaseInternal` |
| `EXPIRED` | A pena chegou ao fim no tick de 1s | `releaseInternal` |
| `SHUTDOWN` | Release em massa ao desabilitar o plugin | `releaseInternal` |
| `EXEMPT` | Punição descartada no login por o jogador ter `loui.exempt` | `handleJoin`, ramo da imunidade |

Além dessas quatro, há um quinto caminho: a **punição offline que expirou antes
do primeiro login**. O registro é descartado sem teleportar ninguém, porque o
castigo nunca chegou a ser aplicado. Esse caminho dispara `EXPIRED` a partir do
`handleJoin`, não uma causa própria — do ponto de vista de quem observa, a pena
acabou pelo tempo, e inventar um quinto valor de enum para uma distinção que
nenhum consumidor precisa é superfície pública desperdiçada.

`EXEMPT` e o caso acima compartilham a característica de não terem estado
físico a restaurar. O evento é disparado assim mesmo, porque um plugin de logs
quer registrar que a punição terminou — inclusive quando terminou sem nunca ter
começado.

### Diagnóstico do veto

Quando `LouiImprisonEvent` volta cancelado, o console registra os plugins que
**escutam** o evento, obtidos via `getHandlers().getRegisteredListeners()`.

**Isso não identifica o culpado.** O Bukkit não expõe qual listener marcou o
evento como cancelado — só quais estão registrados. O log deve dizer
"plugins escutando este evento", nunca "cancelado por X". Afirmar culpa que
não se pode provar manda o admin investigar o plugin errado.

---

## 5. PlaceholderAPI

### Registro condicional

A expansão só é registrada quando `Bukkit.getPluginManager().getPlugin("PlaceholderAPI")`
não for nulo. A classe `LouiPlaceholders` é referenciada **apenas** dentro
desse `if`, para que a JVM nunca a carregue num servidor sem PlaceholderAPI —
carregá-la ali lançaria `NoClassDefFoundError` e derrubaria o `onEnable`.

Identificador da expansão: `loui`. `persist()` devolve `true`, porque o
registro acontece uma vez no enable.

### Os quatro placeholders

| Placeholder | Devolve | Quando não está contido |
|---|---|---|
| `%loui_jailed%` | `sim` / `nao` | `nao` |
| `%loui_time_left%` | `2:00:00` | string vazia |
| `%loui_reason%` | O motivo do castigo | string vazia |
| `%loui_count%` | Total de presos registrados | o número real, sempre |

`%loui_time_left%` usa `TimeParser.formatClock`, que já existe e já é testado.

Devolver **string vazia** em vez de `00:00` para quem não está contido é
deliberado: vazio permite esconder o campo no scoreboard, enquanto um zero
literal obriga o admin a tratar o caso na configuração dele.

Os textos `sim` e `nao` são configuráveis em `messages`, porque servidores em
outros idiomas vão querer trocá-los e mudar isso depois quebraria layouts.

`%loui_count%` conta o mapa inteiro de presos, incluindo os offline — é a
contagem que um painel de staff quer ver.

---

## 6. Configuração

Mensagens novas em `messages:`:

| Chave | Uso |
|---|---|
| `imprison-vetoed` | Mostrada ao moderador quando um plugin cancela a punição |
| `placeholder-yes` | Texto de `%loui_jailed%` quando contido (padrão `sim`) |
| `placeholder-no` | Texto de `%loui_jailed%` quando livre (padrão `nao`) |

Todas com default no código, mantendo a regra de que um `config.yml` antigo
continua funcionando sem edição.

Nenhuma permissão nova.

---

## 7. Mudanças no código existente

**`releaseInternal` ganha um terceiro parâmetro `ReleaseCause`.** Os três
chamadores passam a causa correspondente: `release(Player)` → `MANUAL`, o tick
de expiração → `EXPIRED`, `shutdown()` → `SHUTDOWN`. O método público
`release(Player)` mantém a assinatura atual, para não quebrar nada que já o
chame.

**`handleJoin` dispara o evento nos seus dois ramos de descarte**, que não
passam por `releaseInternal`: o da imunidade (`EXEMPT`) e o da punição offline
expirada antes do primeiro login (`EXPIRED`). Sem isso, duas formas de um
castigo terminar ficariam invisíveis para quem integra.

**O bloco de broadcast duplicado entre `imprison` e `imprisonOffline` vira um
helper privado.** São cerca de dez linhas quase idênticas montando a mensagem
de staff e as linhas de console e log. A revisão da Task 5 do sub-projeto A já
tinha apontado a duplicação; como os dois métodos agora também compartilham o
disparo do evento, extrair é o caminho natural.

Nenhuma outra mudança de comportamento. Este sub-projeto não altera o que o
plugin faz — só permite que outros plugins observem e reajam.

---

## 8. Dependências

PlaceholderAPI entra no `pom.xml` em escopo **`provided`**, do repositório
`https://repo.extendedclip.com/content/repositories/placeholderapi/`.

Escopo `provided` significa que ela **não é empacotada no jar**. A badge
"dependências: nenhuma" do README continua verdadeira no sentido que importa:
o plugin roda sem nada instalado além do Paper. O README deve deixar claro que
PlaceholderAPI é opcional e habilita apenas os placeholders.

Isso contrasta com o sub-projeto B, onde o driver MySQL seria uma dependência
de runtime de verdade — a distinção está registrada no roteiro.

---

## 9. Testes

**Automatizados** — os eventos são objetos simples e testáveis sem servidor:

- `LouiImprisonEvent` guarda e devolve os campos passados no construtor;
- `setCancelled(true)` reflete em `isCancelled()`;
- `LouiReleaseEvent` **não** implementa `Cancellable` (assertiva estrutural, via
  reflexão — protege contra alguém adicionar cancelamento por engano no futuro);
- `ReleaseCause` tem exatamente os quatro valores esperados.

Os testes existentes (24) devem continuar passando.

**Manuais** — dependem de servidor vivo:

1. Punir alguém e confirmar que um plugin de teste recebe `LouiImprisonEvent`.
2. Cancelar o evento no plugin de teste: nenhum estado muda, moderador recebe
   `imprison-vetoed`, console lista os ouvintes.
3. Soltar com `/loui free` → `LouiReleaseEvent` com causa `MANUAL`.
4. Deixar a pena expirar → causa `EXPIRED`.
5. Parar o servidor com preso online → causa `SHUTDOWN`.
6. Punir offline alguém com `loui.exempt` e fazê-lo entrar → causa `EXEMPT`.
7. Punir offline por 1 minuto, entrar depois de 2 → causa `EXPIRED`, sem teleporte.
8. Com PlaceholderAPI instalada, `/papi parse me %loui_jailed%` e os outros três.
9. **Sem** PlaceholderAPI instalada, o servidor sobe sem erro e o plugin habilita.

O item 9 é o mais importante: é o que prova que o registro condicional protege
os servidores que não têm a plugin.

---

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Carregar `LouiPlaceholders` sem PlaceholderAPI derruba o `onEnable` | Classe referenciada só dentro do `if` de detecção; teste manual 8 cobre |
| Listener lento no `LouiReleaseEvent` durante o shutdown atrasa o desligamento | Evento é notificação; nada a fazer no plugin, mas fica documentado |
| Assinatura de `releaseInternal` muda | Método privado; os quatro chamadores estão no mesmo arquivo |
| A versão do PlaceholderAPI no pom não bater com a do servidor | Escopo `provided` e API estável há anos; expansão usa só `onRequest` |

---

## 11. Entregável

Versão `1.2.0` em `pom.xml` e `plugin.yml`. README com uma seção nova
documentando os dois eventos, o enum de causas e os quatro placeholders, além
de deixar explícito que PlaceholderAPI é opcional.

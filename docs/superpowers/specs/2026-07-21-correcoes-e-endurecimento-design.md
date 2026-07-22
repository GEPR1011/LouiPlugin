# LouiPlugin — Correções e Endurecimento (sub-projeto A)

**Data:** 2026-07-21
**Versão alvo:** 1.1.0
**Status:** design aprovado, aguardando plano de implementação

---

## 1. Contexto

O LouiPlugin v1.0.0 envia jogadores para um vazio escuro por tempo determinado e
os devolve ao ponto exato de origem. O núcleo funciona, mas a leitura do código
revelou sete lacunas que qualquer servidor encontraria em produção.

Este documento cobre **apenas o sub-projeto A**. Os itens de MySQL, histórico,
PlaceholderAPI, eventos de API e modo jail ficam para os sub-projetos B, C e D,
cada um com seu próprio ciclo de spec → plano → implementação.

### Itens no escopo

| # | Item | Problema hoje |
|---|---|---|
| 1 | `release-all-on-disable` | Desinstalar o plugin deixa presos cegos e invulneráveis para sempre |
| 2 | Punição de jogador offline | `Bukkit.getPlayerExact()` exige o alvo online |
| 3 | Suporte a horas no parse | `/loui X 2h motivo` falha; só `m` e `d` são aceitos |
| 4 | Broadcast real para staff | A mensagem vai só para quem executou e para o console |
| 5 | Permissão `loui.exempt` | Qualquer um com `loui.use` pode conter o dono |
| 6 | Separação de prisioneiros | Todos caem no mesmo X/Z e se empurram por colisão |
| 10 | Whitelist de comandos | Bloqueio total impede o punido de recorrer |

### Fora do escopo

MySQL e sincronização de rede (B); histórico, `/loui info`, PlaceholderAPI e
eventos de API (C); efeitos configuráveis e modo jail (D).

---

## 2. Decisões tomadas

Registradas aqui porque moldam tudo o que vem depois.

**O cronômetro continua sendo relógio de parede.** `endTime` permanece um
instante absoluto e o tempo corre com o jogador offline. Zero mudança de
semântica.

**Punições offline começam a contar no momento do comando**, não no primeiro
login. Consequência aceita conscientemente: uma pena curta aplicada a alguém
offline pode expirar antes de o jogador voltar, e ele nunca saberá que foi
punido. Para punição offline, prefira durações longas.

**A separação usa faixas de altura, não grade em X/Z.** Todos os presos
compartilham o mesmo X/Z e ocupam intervalos de Y distintos. Isso mantém **um
único chunk carregado** independentemente de quantos presos existam — a
alternativa em grade forçaria a geração de um chunk por preso.

**A whitelist de comandos sai vazia de fábrica**, preservando exatamente o
comportamento atual para quem atualizar.

---

## 3. Arquitetura

Três classes novas, extraídas de onde o trabalho novo aterrissa. Nenhuma
reestruturação além disso.

| Arquivo | Responsabilidade | Estado |
|---|---|---|
| `LouiPlugin` | `onEnable`/`onDisable`, wiring, scheduler | existente, encolhe |
| `LouiCommand` | parsing de `/loui`, tab-complete, exempt, broadcast | **novo** |
| `PrisonManager` | mapa de castigos, persistência, orquestração, tick | existente, encolhe |
| `VoidState` | teleporte, efeitos, faixas de altura, restauração | **novo** |
| `TimeParser` | `parse`, `formatDuration`, `formatClock` | **novo** |
| `LouiListener` | bloqueios de evento + whitelist | existente, +1 método |

Dependências fluem numa direção só:

```
LouiCommand ──→ PrisonManager ──→ VoidState
                      │
                      └──────────→ TimeParser ←── LouiCommand
```

`TimeParser` não importa nada de Bukkit. É função pura e, por isso, a única peça
testável sem servidor.

### Onde cada item aterrissa

- **(1)** `PrisonManager.shutdown()`
- **(2)** `LouiCommand` (resolução do alvo) + `PrisonManager.handleJoin()` (captura tardia do retorno)
- **(3)** `TimeParser.parse()`
- **(4)** `LouiCommand`
- **(5)** `LouiCommand` + `PrisonManager.handleJoin()`
- **(6)** `VoidState`
- **(10)** `LouiListener.onCommand()`

---

## 4. Modelo de dados

Dois campos entram em `Prison`:

```java
Location returnLocation;   // agora pode ser null
int band;                  // faixa de altura; -1 = não atribuída
```

### `returnLocation` nulo

É o mecanismo da punição offline. Ao punir alguém que não está no servidor não
existe posição a capturar, então o campo nasce nulo. `handleJoin()` o preenche
com a posição de login **antes** de aplicar o estado de vazio — caso contrário o
jogador retornaria para dentro do próprio vazio.

`release()` já trata o caso nulo hoje, caindo no spawn do mundo. O caminho de
código existe e não precisa ser criado.

### `band`

Índice da faixa ocupada. Alocado na primeira aplicação do estado de vazio,
devolvido a um pool no release. É persistido para que um restart não coloque
dois presos na mesma faixa.

Um `prisons.yml` da v1.0.0 carrega sem alteração: `band` ausente vira `-1` e é
alocado no próximo `applyVoidState`.

### Geometria das faixas

A altura de queda **não** ganha config própria — ela já é `top-y − min-y`. Criar
uma chave `band-height` separada produziria duas fontes de verdade que podem
divergir. As faixas são cópias deslocadas do intervalo já existente:

```
span      = top-y − min-y                        (padrão: 5000 − 1500 = 3500)
passo     = span + band-gap                      (padrão: 3500 + 100 = 3600)
topo(i)   = top-y − i × passo
piso(i)   = min-y − i × passo
```

A faixa 0 é exatamente o comportamento atual (5000 → 1500), o que garante que
nada muda para um servidor com um preso só.

Faixas abaixo do fundo do mundo são legítimas: o jogador está invulnerável e o
tick o reposiciona ao cruzar o piso da sua faixa. Não há dano de void a temer.

---

## 5. Configuração

Chaves novas, todas com default no código — um `config.yml` da v1.0.0 continua
funcionando sem edição:

```yaml
void:
  band-gap: 100.0            # espaço morto entre faixas
  max-bands: 8               # teto de faixas distintas

safety:
  release-all-on-disable: true

restrictions:
  allowed-commands: []       # vazio = bloqueia tudo (comportamento atual)

notify:
  permission: loui.notify    # quem recebe o broadcast
```

Mensagens novas em `messages:`

| Chave | Quando aparece |
|---|---|
| `is-exempt` | Alvo tem `loui.exempt` |
| `never-joined` | Nome não está no usercache do servidor |

A mensagem de comando bloqueado continua sendo a `no-commands` já existente,
com ou sem whitelist configurada. Do ponto de vista do jogador a situação é a
mesma — ele tentou um comando e foi barrado —, e uma segunda chave para isso só
criaria duas mensagens a manter em sincronia.

### Permissões

| Permissão | Default | Para quê |
|---|---|---|
| `loui.exempt` | `false` | Imunidade a ser punido |
| `loui.notify` | `op` | Recebe o broadcast de punição |

`loui.free` e `loui.list` **não** são criadas. Continuam sob `loui.use`, porque
separá-las não foi pedido e pertence a um escopo futuro.

---

## 6. Comportamento detalhado

### (1) Release em massa no disable

`shutdown()` passa a soltar todos os presos **online** quando
`safety.release-all-on-disable` é `true`.

Implementação: coletar os jogadores online numa lista, depois soltar — nunca
iterar o mapa `prisons` enquanto `release()` o modifica. Um único `save()` ao
final, em vez de um por jogador.

**Limitação documentada:** presos offline não são alcançados. O estado deles
(cegueira, invulnerabilidade, posição no vazio) vive no playerdata do servidor,
e sem o plugin carregado não há código para desfazê-lo. Isso vai no README, não
escondido. O procedimento seguro antes de desinstalar é rodar `/loui list` e
soltar todo mundo.

### (2) Punição offline

`LouiCommand` resolve o alvo em duas etapas:

1. `Bukkit.getPlayerExact(nome)` — se online, fluxo atual, sem mudança.
2. Se nulo, consulta o **usercache do servidor**, sem chamada de rede.

A consulta ao usercache não pode bloquear a thread principal. A API usada é
`Bukkit.getOfflinePlayerIfCached(String)`, que devolve `null` sem consultar a
Mojang.

**Confirmado em 2026-07-21** por inspeção do jar
`paper-api-1.21.4-R0.1-20250925.065901-231.jar` com `javap`: o método existe em
`org.bukkit.Bukkit` e em `org.bukkit.Server` com a assinatura
`OfflinePlayer getOfflinePlayerIfCached(String)`.

Nunca usar `Bukkit.getOfflinePlayer(String)`: em servidor online-mode ele
dispara uma consulta HTTP à Mojang na thread principal e trava o servidor.

Consequência: só é possível punir quem já entrou no servidor alguma vez. Para
nome desconhecido, o comando recusa com `never-joined`.

O `Prison` criado tem `returnLocation = null` e nenhum efeito é aplicado — o
jogador não está lá. Tudo acontece no `handleJoin()`.

### (3) Horas no parse

`TimeParser.parse(String)` devolve minutos, ou `-1` para entrada inválida.

| Entrada | Resultado |
|---|---|
| `30` | 30 min |
| `30m` | 30 min |
| `2h` | 120 min |
| `2d` | 2880 min |
| `0`, `-5`, `abc`, `h`, `""` | `-1` |

Sufixo é case-insensitive. Multiplicação verificada contra overflow de `long`:
se `valor > Long.MAX_VALUE / multiplicador`, devolve `-1` em vez de estourar
silenciosamente para negativo.

Não haverá formatos compostos (`1d2h`). YAGNI — ninguém pediu, e cada formato
extra é mais superfície de teste.

### (4) Broadcast para staff

Ao punir, a mensagem vai para:

- o alvo (mensagem própria, `jailed-target`);
- **todos os jogadores online com `notify.permission`**, exceto o alvo;
- o console, sempre, com o autor anexado.

Quem executou recebe por ser staff com a permissão. Se um admin remover
`loui.notify` de si mesmo, deixa de receber — comportamento correto e previsível.

### (5) Imunidade

`loui.exempt` vale **também para o console**. Automação não deve furar imunidade;
para punir um isento, tira-se a permissão primeiro.

Permissões de jogadores offline não são consultáveis pelo Bukkit puro — depende
do plugin de permissões. Por isso a checagem acontece em dois pontos:

- **Alvo online:** `LouiCommand` recusa na hora, com `is-exempt`.
- **Alvo offline:** a punição é registrada; `handleJoin()` verifica no login e,
  se o jogador for isento, descarta o castigo sem aplicar efeito algum e avisa
  o staff online.

Isso fecha a brecha de punir um admin offline para contornar a imunidade.

### (6) Faixas de altura

`VoidState` mantém o pool de faixas. Alocação: menor índice livre. Liberação: no
release, devolve ao pool.

**Pool esgotado** (mais presos que `max-bands`): o excedente compartilha a última
faixa. O castigo continua funcionando; volta a haver colisão apenas entre esses.
Recusar a punição por falta de faixa seria pior — a moderação não pode falhar
porque a prisão está cheia.

### (10) Whitelist de comandos

`LouiListener.onCommand()` extrai a raiz do comando digitado: do início da
mensagem até o primeiro espaço, sem a barra, em minúsculas.

Se `restrictions.allowed-commands` estiver vazio, bloqueia tudo — comportamento
atual. Se tiver entradas, libera só as que casarem exatamente com a raiz.

Entradas do config são normalizadas do mesmo jeito, então `/msg` e `msg`
funcionam igual.

**Aliases não são resolvidos.** Liberar `/msg` não libera `/tell`; ambos precisam
constar na lista. Resolver aliases exigiria consultar o `CommandMap` e lidar com
plugins que registram dinamicamente — complexidade que não se paga aqui. Isso
vai documentado.

---

## 7. Testes

**Automatizados** — `TimeParser` é a única peça sem dependência de Bukkit:

- todos os formatos válidos (`30`, `30m`, `2h`, `2d`, maiúsculas);
- entradas inválidas (`0`, `-5`, `abc`, `h`, `""`, `null`);
- guarda de overflow;
- `formatDuration` e `formatClock` nas fronteiras (0, exatamente 1h, exatamente
  1 dia, mais de 1 dia).

Isso exige adicionar JUnit 5 e o `maven-surefire-plugin` ao `pom.xml`, que hoje
não tem nenhuma dependência de teste.

**Manuais** — o resto depende de servidor vivo e será verificado à mão. O spec
não vai fingir cobertura automatizada que não existe:

1. Punir jogador online → cai na faixa 0, bossbar correta.
2. Dois presos simultâneos → faixas distintas, sem colisão.
3. Punir offline → ao entrar, é contido e o retorno é a posição de login.
4. Punir offline alguém com `loui.exempt` → ao entrar, nada acontece.
5. `/loui X 2h motivo` → aceito, bossbar mostra `2:00:00`.
6. Whitelist com `/msg` → `/msg` passa, `/home` é bloqueado.
7. Parar o servidor com um preso online → é solto e volta ao lugar certo.
8. Carregar `prisons.yml` da v1.0.0 → sem erro, faixa realocada.

---

## 8. Riscos

| Risco | Mitigação |
|---|---|
| ~~`getOfflinePlayerIfCached` pode não existir na paper-api 1.21.4~~ | **Resolvido em 2026-07-21**: confirmado por `javap` no jar da dependência |
| Punição offline curta pode expirar antes do primeiro login | `handleJoin` descarta o registro sem teleportar; o jogador não é jogado no spawn por uma pena que nunca começou |
| `release()` dentro de iteração do mapa causaria `ConcurrentModificationException` | Coletar antes, soltar depois — padrão que o `tick()` já usa |
| Faixas muito profundas em mundos com limite alterado | Faixa é espaço vazio; jogador invulnerável e reposicionado pelo tick |
| Regressão em quem atualiza da v1.0.0 | Faixa 0 é idêntica ao comportamento atual; whitelist vazia preserva bloqueio total |

---

## 9. Entregável

Versão `1.1.0` em `pom.xml` e `plugin.yml` (hoje ambos em `1.0.0`, já
consistentes entre si), README atualizado com as chaves novas, as duas
permissões novas e as duas limitações documentadas (presos offline no disable,
aliases na whitelist).

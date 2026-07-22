<div align="center">

# ⛓️ LouiPlugin

### Castigo no vazio para jogadores mal-intencionados

[![Version](https://img.shields.io/badge/versão-v1.3.0-gold?style=for-the-badge&logo=minecraft&logoColor=white)](../../releases)
[![Server](https://img.shields.io/badge/servidor-NemonicRP-purple?style=for-the-badge)](.)
[![API](https://img.shields.io/badge/Paper%20%2F%20Spigot-1.21+-green?style=for-the-badge&logo=java)](.)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=openjdk)](.)
[![Deps](https://img.shields.io/badge/dependências-nenhuma-lightgrey?style=for-the-badge)](.)
[![PlaceholderAPI](https://img.shields.io/badge/PlaceholderAPI-opcional-blue?style=for-the-badge)](.)

*Alternativa ao ban · Sem dependências · Persistente entre restarts*

**[⬇️ Download](../../releases/latest) · [📩 Contato para aquisição](#-contato)**

</div>

---

## O que é o LouiPlugin?

O **LouiPlugin** é uma ferramenta de moderação para servidores Minecraft Java. Em vez de banir ou congelar um jogador problemático, ele o envia para um **vazio escuro e infinito**, onde o jogador cai eternamente por um tempo determinado — sem ver nada, sem tomar dano, sem usar comandos e sem interagir com o mundo.

Cumprido o castigo, o jogador retorna **exatamente** ao lugar e ao modo de jogo em que estava. Nada é perdido: nem itens, nem posição, nem progresso.

É punição sem ruptura — o jogador continua conectado, o staff mantém o controle, e o servidor não perde um jogador que talvez só precise de um corretivo.

---

## ✦ Destaques

- **Um único comando** — `/loui <nick> <tempo> <motivo>` e pronto
- **Retorno exato**: posição, gamemode e permissão de voo são restaurados ao fim
- **Bossbar permanente** mostrando o motivo e o tempo restante
- **Escuridão total** via Darkness + Blindness infinitos
- **Queda infinita** — ao atingir o fundo, o jogador é reposicionado no topo
- **Funciona com o jogador offline** — o griefer que desloga antes de o staff reagir não escapa
- **Presos simultâneos não colidem** — cada um cai na sua própria faixa de altura
- **Isolamento completo**: sem dano, sem drops, sem coleta de itens, sem fome, sem comandos
- **À prova de fuga**: teleportes externos (ender pearl, `/spawn`, outros plugins) são bloqueados
- **Persistente**: sobrevive a restart, reload e logout — o cronômetro corre em tempo real
- **Auto-correção**: um tick de 1s reaplica o estado caso outro plugin interfira
- **Zero dependências** — só Paper/Spigot

---

## 🎮 Comandos

Todos exigem a permissão `loui.use` (padrão: **op**).

| Permissão | Padrão | Para quê |
|---|---|---|
| `loui.use` | op | Usar `/loui` |
| `loui.exempt` | — | Imunidade: o jogador não pode ser contido |
| `loui.notify` | op | Recebe aviso quando alguém é contido |
| `loui.admin` | op | Configurar a jail com `/loui setjail` |

| Comando | O que faz |
|---|---|
| `/loui <nick> <tempo> <motivo...>` | Envia o jogador para o vazio pelo tempo indicado |
| `/loui free <nick>` | Liberta antes da hora |
| `/loui list` | Lista todos os jogadores contidos |
| `/loui setjail` | Define o centro da jail onde você está |

### Formatos de tempo aceitos

| Entrada | Significado |
|:---:|---|
| `30` | 30 minutos (número puro) |
| `30m` | 30 minutos |
| `2h` | 2 horas |
| `2d` | 2 dias (2880 minutos) |

```
/loui Steve 30m griefando a base do spawn
/loui Alex 2d uso de x-ray reincidente
/loui free Steve
/loui list
```

> Aplicar `/loui` em alguém **já contido** atualiza o tempo e o motivo, mas preserva o local de retorno original — não há risco de "prender" o jogador dentro do próprio vazio.

---

## ⛓️ O que acontece com o jogador contido

| Aspecto | Comportamento |
|---|---|
| Posição | Teleportado para `x=250000.5, z=250000.5`, no topo da sua faixa (a primeira começa em `y=5000`) |
| Queda | Ao cruzar o piso da própria faixa, volta ao topo dela — queda sem fim |
| Visão | Darkness + Blindness infinitos (escuridão completa) |
| Gamemode | Forçado para **Adventure**, voo desativado |
| Dano | Invulnerável — e também **não causa** dano a ninguém |
| Itens | Não dropa e não coleta nada |
| Fome | Congelada |
| Comandos | Bloqueados, exceto os liberados em `allowed-commands` (vazio por padrão = bloqueia tudo) |
| Teleporte | Qualquer teleporte de origem externa é cancelado |
| Interface | Bossbar com o motivo e o tempo restante |

Ao ser libertado, tudo é revertido: efeitos removidos, invulnerabilidade desligada, gamemode e voo restaurados, e teleporte de volta ao ponto exato de origem.

> ⚠️ **Antes de desinstalar o plugin**, rode `/loui list` e solte todos. O
> `release-all-on-disable` alcança apenas quem está **online** — o estado de um
> preso offline (cegueira, invulnerabilidade, posição) fica gravado no playerdata
> e, sem o plugin carregado, não há código capaz de desfazê-lo.

> ⚠️ A whitelist de comandos não resolve aliases. Liberar `/msg` não libera
> `/tell` — ambos precisam constar na lista.

---

## 🏛️ Modos de castigo

O servidor escolhe um modo em `config.yml`. Castigos já aplicados guardam o próprio modo e não mudam quando a chave muda.

| Modo | O que é |
|---|---|
| `void` | Queda infinita num vazio escuro. É o padrão. |
| `jail` | Região física delimitada, onde o preso anda e é visto. |

### Configurando a jail

1. Vá até onde a jail deve ficar
2. `/loui setjail` (exige `loui.admin`)
3. Ajuste `jail.radius` no `config.yml`
4. Troque `mode` para `jail` e reinicie

> ⚠️ A contenção é **horizontal**: o raio ignora a altura. Se o Y contasse, uma
> jail rasa expulsaria quem pulasse. Teto e piso são responsabilidade de quem
> constrói a jail.

### Efeitos por modo

Cada modo tem seu perfil. O padrão do `void` reproduz o castigo clássico; o da `jail` deixa o preso enxergar, porque o castigo ali é ficar preso à vista de todos.

| Efeito | `void` | `jail` |
|---|:---:|:---:|
| `darkness` | ✅ | ❌ |
| `blindness` | ✅ | ❌ |
| `invulnerable` | ✅ | ✅ |
| `adventure-mode` | ✅ | ✅ |

> Com `invulnerable: false` o preso pode morrer. Ao respawnar ele é recolocado
> sob castigo — morrer não é fuga.

---

## ⚙️ Configuração

Gerado em `plugins/LouiPlugin/config.yml` no primeiro start.

```yaml
void:
  world: ''          # vazio = usa o mundo principal do servidor
  x: 250000.5        # coordenadas longe de qualquer construção
  z: 250000.5
  top-y: 5000.0      # altura em que a queda começa
  min-y: 1500.0      # ao cruzar essa altura, volta pro topo
  band-gap: 100.0    # espaco morto entre faixas de presos
  max-bands: 8       # teto de faixas distintas

bossbar:
  color: RED         # RED, BLUE, GREEN, PINK, PURPLE, WHITE, YELLOW

messages:
  prefix: '&8[&cLoui&8] &7'
  jailed-target: '&cVoce foi contido pelo motivo: &f%reason% &7(%time%)'
  jailed-broadcast-staff: '&7%player% foi contido por %time%: &f%reason%'
  released: '&aVoce foi libertado. Comporte-se.'
  no-commands: '&cVoce nao pode usar comandos enquanto estiver contido.'
  # ...

safety:
  release-all-on-disable: true   # solta presos ONLINE ao desabilitar o plugin

restrictions:
  allowed-commands: []           # comandos liberados ao preso; vazio bloqueia tudo

notify:
  permission: loui.notify        # quem recebe o aviso de punicao
```

**Placeholders disponíveis nas mensagens:** `%player%`, `%reason%`, `%time%`, `%minutes%`.

> ⚠️ Escolha coordenadas realmente distantes. O jogador fica em Adventure e cego, mas o chunk existe — evite sobrepor construções.

---

## 💾 Persistência

O estado é gravado em `plugins/LouiPlugin/prisons.yml` e recarregado no start. Para cada jogador contido são guardados o nome, o motivo, o instante de término, a localização de retorno, o gamemode original e a faixa de altura ocupada.

O cronômetro usa **tempo real**, não tempo de jogo: se o castigo expirar enquanto o jogador está offline, ele é libertado automaticamente ao entrar. Se ainda restar tempo, o estado de vazio é reaplicado no login.

Numa punição aplicada a alguém **offline** não há posição a capturar, então o ponto de retorno fica vazio até o primeiro login — é a posição de entrada que vira o destino de volta. Como o relógio corre em tempo real desde o comando, uma pena curta aplicada a quem está fora pode expirar antes de a pessoa voltar; nesse caso o registro é descartado sem teleportar ninguém. Para punição offline, prefira durações longas.

---

## 🔌 Integração com outros plugins

### Eventos

O plugin dispara dois eventos em `com.nemonicorp.loui.api`.

**`LouiImprisonEvent`** — cancelável, disparado **antes** de o castigo ser aplicado. Cancelar impede a punição por completo: nenhum estado muda, nenhum teleporte acontece, e o moderador é avisado.

| Método | Devolve |
|---|---|
| `getTarget()` | `OfflinePlayer` — é um `Player` quando `isOffline()` é `false` |
| `getMinutes()` | Duração em minutos |
| `getReason()` | Motivo informado |
| `getByWhom()` | Quem executou o comando |
| `isOffline()` | `true` se o alvo não estava online |

**`LouiReleaseEvent`** — **não** cancelável, disparado quando um castigo termina. Vetar uma soltura prenderia o jogador para sempre, então a API não permite.

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

Opcional. Se a PlaceholderAPI estiver instalada, quatro placeholders ficam disponíveis; sem ela, o plugin funciona normalmente e nada é registrado.

| Placeholder | Devolve | Sem castigo ativo |
|---|---|---|
| `%loui_jailed%` | `sim` / `nao` (configurável) | `nao` |
| `%loui_time_left%` | `2:00:00` | vazio |
| `%loui_reason%` | O motivo | vazio |
| `%loui_count%` | Total de presos | o número real |
| `%loui_tag%` | O texto de `tab-tag` | vazio |

> `%loui_time_left%` devolve string vazia — e não `00:00` — para quem não está contido, permitindo esconder o campo no scoreboard.

O `%loui_tag%` existe para **somar** ao seu sistema de tab, não para competir com ele. O plugin nunca escreve na tab list, no nametag ou em scoreboards — apenas publica o estado, e quem desenha decide o resto.

```yaml
tab-tag: '&c[APRISIONADO] '        # cor própria
tab-tag: '[APRISIONADO] '          # herda o gradiente/fonte de quem exibe
tab-tag: ':icone_cadeia: '         # glyph do ItemsAdder, passa intacto
tab-tag: ''                        # desliga sem tocar em código
```

No TAB: `tabprefix: "%loui_tag%%player%"`.

---

## ⚙️ Requisitos

| Requisito | Versão |
|---|---|
| Minecraft Java | 1.21+ |
| Server Software | Paper (recomendado) ou Spigot |
| Java Runtime | 21+ |
| Dependências | Nenhuma |

---

## 🚀 Como Instalar

1. Baixe o `LouiPlugin.jar` em [Releases](../../releases/latest).
2. Coloque na pasta `plugins/` do servidor.
3. Inicie o servidor uma vez para gerar `plugins/LouiPlugin/config.yml`.
4. Ajuste o `config.yml` se quiser e reinicie.
5. Dê a permissão `loui.use` à sua equipe de staff.

---

## 🛠️ Como Compilar a partir do Código-Fonte

Pré-requisitos: **JDK 21** e **Maven 3.8+**.

```bash
git clone https://github.com/GEPR1011/LouiPlugin.git
cd LouiPlugin
mvn clean package
```

O JAR final fica em `target/LouiPlugin.jar`.

### Estrutura do projeto
```
LouiPlugin/
├── pom.xml                                  # configuração Maven + Paper API
├── src/main/java/com/nemonicorp/loui/
│   ├── LouiPlugin.java                      # onEnable/onDisable, wiring, tick de 1s
│   ├── LouiCommand.java                     # /loui, tab-complete, imunidade
│   ├── LouiListener.java                    # bloqueios (dano, drops, comandos, tp)
│   ├── PrisonManager.java                   # estado, persistência, bossbar
│   ├── LouiPlaceholders.java                # expansao PlaceholderAPI (opcional)
│   ├── api/                                 # superfície pública: eventos e enum
│   ├── mode/                                # PunishmentMode, VoidMode, JailMode, EffectProfile
│   └── TimeParser.java                      # parse e formatação de durações
├── src/main/resources/
│   ├── plugin.yml                           # metadados, comando e permissões
│   └── config.yml                           # configuração padrão
└── src/test/java/com/nemonicorp/loui/       # 24 testes de unidade
```

---

## 📄 Licença

Este plugin é um **software proprietário**. O código-fonte é público apenas para avaliação e auditoria — isso **não** concede licença de uso. É proibida a redistribuição, cópia, modificação ou uso sem autorização explícita do autor. A licença é concedida individualmente por servidor.

Veja [LICENSE.txt](LICENSE.txt) para os termos completos.

---

## 📩 Contato

- **Discord:** `gepr13`
- **Email:** GEPRWORKOUT@gmail.com

> Licenças por servidor · Suporte técnico incluso · Configuração assistida disponível

---

<div align="center">

*Desenvolvido por **Guilherme Elias (gepr)** · Full Stack Dev*

</div>

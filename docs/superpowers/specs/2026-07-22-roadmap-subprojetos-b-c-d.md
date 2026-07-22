# LouiPlugin — Roteiro dos sub-projetos B, C e D

**Data:** 2026-07-22
**Status:** roteiro; nenhum dos três tem spec aprovado ainda
**Pré-requisito:** sub-projeto A (v1.1.0) validado em servidor

---

## 1. Para que serve este documento

O sub-projeto A está implementado e aguardando teste em runtime. Este roteiro
organiza o que vem depois: o que cada fase entrega, em que ordem, e **quais
decisões precisam ser tomadas antes de cada spec poder ser escrito**.

Não é um plano de implementação. Cada sub-projeto ainda passa pelo ciclo
completo de design → spec → plano → execução. O que está aqui são as perguntas
que precisam de resposta na abertura de cada ciclo.

---

## 2. O que o sub-projeto A ensinou

Três coisas mudaram minhas premissas sobre as fases seguintes.

**A extração criou os encaixes que B, C e D precisam.** `VoidState` isola o
estado físico do castigo, o que é exatamente a costura onde o modo jail (D)
entra. `PrisonManager` ficou com persistência isolada em `save()`/`load()`, que
é onde a abstração de storage (B) se encaixa. Isso não foi acidente do
refactor — mas confirma que a ordem A-primeiro estava certa.

**Duas dívidas do A são pontos de entrada naturais para C.** O bloco de
broadcast duplicado entre `imprison` e `imprisonOffline`, e o `handleJoin` com
quatro saídas, são precisamente os lugares onde eventos canceláveis seriam
disparados. Fazer C-eventos é a desculpa certa para limpar os dois.

**O selo "zero dependências" morre no B.** O README anuncia *"Dependências:
Nenhuma"* como argumento de venda, e a badge `dependências-nenhuma` está no
topo. Um driver MySQL e um pool de conexões (HikariCP) quebram isso, a menos
que sejam empacotados via shading — o que engorda o jar e cria risco de
conflito de classes com outros plugins que fazem o mesmo. **Isso é uma decisão
de produto, não técnica**, e precisa ser resolvida antes de o B começar.

---

## 3. A pergunta que reordena tudo

**O NemonicRP roda uma rede de servidores, ou um servidor só?**

O sub-projeto B (MySQL) existe para impedir que um punido escape trocando de
servidor. Num servidor único, esse problema **não existe** — o `prisons.yml`
local já resolve tudo.

Se o NemonicRP é um servidor só, o B não entrega nada para você. Ele entrega
para um comprador hipotético que rode uma rede. É trabalho especulativo,
e é o maior dos três.

Nesse caso, a ordem sensata inverte: entregar primeiro o que é visível para
os seus jogadores e para você (C e D), e deixar o B para quando houver um
comprador concreto pedindo, ou quando o NemonicRP virar rede.

---

## 4. Sub-projeto C — Observabilidade e integração

**Itens:** 8 (PlaceholderAPI), 9 (histórico + `/loui info`), 11 (eventos de API).

Os três expõem dados para fora, mas têm dependências bem diferentes entre si.
**Vale quebrar C em duas etapas.**

### C1 — Eventos + PlaceholderAPI (independentes de storage)

`LouiImprisonEvent` e `LouiReleaseEvent` são classes pequenas, sem
dependência nova, disparadas de dentro de `imprison`/`imprisonOffline`/
`releaseInternal`. PlaceholderAPI é `softdepend` — o plugin continua
funcionando sem ela.

É a menor quantidade de trabalho com maior retorno de todo o resto do roteiro.

**Decisões em aberto:**
- `LouiImprisonEvent` é cancelável? Se sim, o que acontece quando outro plugin
  cancela — mensagem ao staff, silêncio, log?
- Quais placeholders exatamente? Candidatos: `%loui_jailed%` (bool),
  `%loui_time_left%`, `%loui_reason%`, `%loui_count%` (total de presos).
- O placeholder de tempo devolve texto formatado (`2:00:00`) ou segundos crus,
  deixando a formatação para o scoreboard?

### C2 — Histórico + `/loui info` (precisa de storage)

Guardar punições passadas exige decidir onde elas moram. Em YAML, um arquivo
que só cresce; em banco, é trivial. **Por isso C2 depende do B** — ou de uma
decisão explícita de que o histórico fica em YAML para sempre.

**Decisões em aberto:**
- Retenção: guardar tudo, ou só as N últimas por jogador, ou expirar após X dias?
- `/loui info <nick>` mostra o quê — última punição, contagem total, lista?
- O histórico registra também quem soltou antes da hora, e por quê?

---

## 5. Sub-projeto D — Modos e efeitos

**Item:** 12 (efeitos configuráveis + modo jail).

`VoidState` já isola o "como o castigo se manifesta". D transforma essa classe
numa estratégia com duas implementações: a queda no vazio que existe hoje, e
uma jail fixa numa região.

**Decisões em aberto:**
- A jail é definida por coordenadas no config, ou por seleção in-game
  (`/loui setjail`)? A segunda é muito mais usável e muito mais trabalho.
- No modo jail o jogador anda livremente dentro da região, ou fica travado?
  Se anda, precisa de contenção — bloquear movimento além do raio.
- Cegueira e escuridão viram opcionais. Alguém que desligue as duas no modo
  jail cria uma "cadeia social" onde presos se veem e conversam. É um recurso
  desejado ou um efeito colateral a impedir?
- Os dois modos coexistem no mesmo servidor (por comando), ou é uma escolha
  global de config?

---

## 6. Sub-projeto B — Persistência e rede

**Item:** 7 (sincronização MySQL).

O maior dos três, e o único que muda a fundação. Introduz uma interface
`PrisonStorage` com implementações `YamlStorage` (a atual) e `MySqlStorage`.

**Decisões em aberto, em ordem de impacto:**

- **Dependências.** Shading do driver e do pool, ou exigir que o admin
  instale? Ver seção 2 — isso mata a badge "zero dependências".
- **Falha do banco.** Se o MySQL está fora no boot, o plugin não habilita
  (fail-closed, ninguém escapa) ou cai para YAML local (fail-open, o servidor
  sobe mas a punição não sincroniza)? Não há resposta obviamente certa.
- **Propagação.** Como o servidor B descobre que alguém foi solto no servidor A?
  Polling no banco a cada N segundos é simples e chega atrasado; canal de
  mensagens (plugin messaging via proxy, ou Redis) é imediato e é mais uma peça.
- **Geografia do vazio.** As coordenadas do vazio são por mundo. Cada servidor
  da rede precisa do seu próprio vazio, e o ponto de retorno de um jogador
  punido no servidor A não existe no servidor B. O que acontece quando ele é
  solto estando em outro servidor?
- **Migração.** Servidor que já roda com `prisons.yml` e liga o MySQL: importa
  automaticamente, exige comando explícito, ou começa do zero?

A quarta decisão é a mais subestimada. É onde "sincronizar punições" deixa de
ser um problema de banco e vira um problema de modelo de dados.

---

## 7. Sequência recomendada

Assumindo servidor único (ver seção 3):

| Ordem | Entrega | Tamanho | Por quê agora |
|---|---|---|---|
| 1 | **C1** — eventos + PlaceholderAPI | Pequeno | Maior retorno por esforço; sem dependência nova; limpa duas dívidas do A |
| 2 | **D** — efeitos configuráveis + jail | Médio | O encaixe (`VoidState`) já existe; visível para os jogadores |
| 3 | **C2** — histórico + `/loui info` | Médio | Precisa da decisão de storage; útil mesmo em YAML |
| 4 | **B** — MySQL e rede | Grande | Só se o NemonicRP virar rede, ou se houver comprador pedindo |

Se o NemonicRP **é** uma rede, B sobe para primeiro e C2 vem logo atrás, porque
o histórico herda a fundação de graça.

---

## 8. Próximo passo concreto

Nada aqui deve virar código antes de:

1. O sub-projeto A passar na verificação em servidor (11 checagens do plano
   `2026-07-21-correcoes-e-endurecimento.md`).
2. A branch `feat/v1.1.0-correcoes-endurecimento` ser mesclada.
3. A pergunta da seção 3 ser respondida — ela decide se a sequência da seção 7
   vale ou se o B sobe para primeiro.

Depois disso, abre-se o ciclo de brainstorming do sub-projeto escolhido,
respondendo as decisões em aberto da seção correspondente.

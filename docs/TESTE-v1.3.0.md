# Roteiro de teste — LouiPlugin v1.3.0

Modos de castigo (vazio e jail), efeitos configuráveis e tag na tab.

Marque conforme for passando. Se algo falhar, anote e me mande.

---

## Antes de começar

- [ ] Solte quem estiver contido: `/loui list` e `/loui free <nick>` em cada um
- [ ] **Guarde uma cópia do `plugins/LouiPlugin/prisons.yml` atual** — o bloco 6 precisa dele
- [ ] Suba o `LouiPlugin.jar` (v1.3.0) e reinicie
- [ ] Tenha dois jogadores de teste e acesso ao seu plugin de permissões

**O `prisons.yml` continua sendo a melhor fonte de verdade.** Agora ele tem um
campo novo, `mode`, que diz em qual castigo cada preso foi colocado.

---

## 1. Nada quebrou (a regressão que mais importa)

O `config.yml` novo vem com `mode: void`. Antes de testar qualquer coisa nova,
confirme que o que já funcionava continua funcionando.

- [ ] Console sobe sem stacktrace
- [ ] `/loui <jogador> 30m teste` → cai no vazio, tela escura, bossbar
- [ ] `/loui free <jogador>` → volta ao lugar exato
- [ ] `prisons.yml` durante o castigo mostra `mode: void`

> Se algo aqui falhar, pare. A v1.3.0 reescreveu como o castigo é aplicado; uma
> falha neste bloco significa que a refatoração quebrou o que estava bom.

---

## 2. Definindo a jail

- [ ] Vá até onde a jail deve ficar (um lugar fechado, com teto e piso)
- [ ] `/loui setjail`
- [ ] Mensagem de confirmação aparece
- [ ] `plugins/LouiPlugin/config.yml` agora tem `jail.world`, `x`, `y`, `z` com a sua posição
- [ ] O console loga quem definiu e onde

Agora os dois casos que devem ser recusados:

- [ ] Rode `/loui setjail` **pelo console** → recusado com "só um jogador pode usar isso"
- [ ] Com um jogador **sem** `loui.admin` → recusado com "sem permissão"

> Note que `/loui setjail` **não** mexe no raio. Isso é de propósito: sobrescrever
> um raio que você ajustou seria pior que você editar o YAML.

---

## 3. O modo jail contém

- [ ] No `config.yml`, troque `mode: void` para `mode: jail`
- [ ] Confirme que `jail.radius` está em `20.0`
- [ ] Reinicie o servidor
- [ ] `/loui <jogador> 30m teste jail`
- [ ] O jogador é teleportado para o centro da jail — **e enxerga**, diferente do vazio
- [ ] `prisons.yml` mostra `mode: jail`
- [ ] Ele anda livremente dentro do raio
- [ ] Ao passar de 20 blocos do centro (na horizontal), é puxado de volta
- [ ] `/loui free` devolve ele ao lugar de origem

E a recusa por configuração incompleta:

- [ ] Ponha `jail.radius: 0.0` e reinicie
- [ ] `/loui <jogador> 30m teste` → recusado com "a jail não foi configurada"
- [ ] Devolva o raio para `20.0` e reinicie

---

## 4. Morrer não é fuga (o teste crítico)

É o comportamento mais novo e o de maior risco. Com a invulnerabilidade
desligada, o preso pode morrer — e o respawn o joga no spawn do mundo, **fora**
da jail.

- [ ] No `config.yml`, em `jail.effects`, troque `invulnerable: true` para `false`
- [ ] Reinicie
- [ ] `/loui <jogador> 30m teste morte`
- [ ] Confirme que agora ele **toma dano** (mate-o com `/kill <nick>` pelo console)
- [ ] **Ao respawnar, ele volta contido na jail** — não fica solto no spawn
- [ ] A bossbar reaparece e o tempo continua correndo
- [ ] `/loui free` ainda funciona normalmente

> Se ele respawnar solto, esta correção falhou e morrer virou rota de fuga.
> Me avise imediatamente.

Depois devolva `invulnerable: true` e confirme o oposto:

- [ ] Com `invulnerable: true`, `/kill <nick>` não mata o preso

---

## 5. Trocar o modo com gente presa

Este é o cenário que o campo `mode` existe para proteger.

- [ ] Com `mode: jail`, contenha o jogador1 por 1 dia
- [ ] **Sem soltar ele**, troque o config para `mode: void` e reinicie
- [ ] O jogador1 continua na **jail**, não é teleportado para o vazio
- [ ] `/loui free <jogador1>` devolve ele ao lugar certo
- [ ] Um castigo **novo** agora usa o vazio

> O castigo antigo mantém o modo em que nasceu; só os novos seguem o config.

---

## 6. Compatibilidade com a v1.2.0

Use a cópia do `prisons.yml` que você guardou no começo.

- [ ] Pare o servidor
- [ ] Restaure o `prisons.yml` antigo (sem o campo `mode`)
- [ ] Suba o servidor
- [ ] Carrega sem erro; console mostra `N castigo(s) carregado(s).`
- [ ] O preso antigo é tratado como `void` e continua contido

---

## 7. Efeitos configuráveis

- [ ] Com `mode: void`, confirme que o preso fica **cego** (padrão do vazio)
- [ ] Com `mode: jail`, confirme que o preso **enxerga** (padrão da jail)
- [ ] Em `jail.effects`, ligue `darkness: true` e reinicie → agora a jail também cega
- [ ] Devolva para `false`

---

## 8. Tag na tab

- [ ] Confirme que o PlaceholderAPI está carregado (`/papi list` deve mostrar `loui`)
- [ ] Sem ninguém contido: `/papi parse me %loui_tag%` → vazio
- [ ] Contenha alguém e repita → `[APRISIONADO]`

No seu TAB, configure o prefixo:

```yaml
tabprefix: "%loui_tag%%player%"
```

- [ ] Recarregue o TAB (`/tab reload`)
- [ ] O preso aparece na tab com a tag; os outros, sem
- [ ] Ao soltar, a tag some

E o teste que prova que o plugin **soma** em vez de competir:

- [ ] No `config.yml` do LouiPlugin, troque `tab-tag: '&c[APRISIONADO] '` por `tab-tag: '[APRISIONADO] '` (sem o `&c`)
- [ ] `/norb reload` não existe aqui — reinicie o servidor
- [ ] A tag agora **herda** a cor ou o gradiente que o seu TAB aplica, em vez de forçar vermelho

---

## Se algo falhar

Me mande:

1. **Qual item** falhou (bloco e número)
2. **O que aconteceu** em vez do esperado
3. O trecho do **console** com a stacktrace, se houver
4. O `prisons.yml` e o `config.yml` no momento da falha

Não conserte antes — o estado da falha diagnostica melhor que um servidor já arrumado.

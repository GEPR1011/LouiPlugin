# Roteiro de teste — LouiPlugin v1.1.0

Marque conforme for passando. Se algo falhar, anote o que aconteceu e me mande.

---

## Antes de começar

- [ ] Se houver alguém contido no servidor agora, solte com `/loui free <nick>`
- [ ] Suba o `LouiPlugin.jar` (v1.1.0) para `plugins/` no painel
- [ ] Reinicie o servidor
- [ ] Tenha **dois** jogadores de teste disponíveis (pode ser conta alt)

**Truque que vale para vários testes:** o arquivo `plugins/LouiPlugin/prisons.yml`
mostra o estado real de cada preso — faixa de altura, ponto de retorno, motivo,
instante de término. Abrir esse arquivo é a forma mais confiável de conferir o
que o plugin realmente gravou, em vez de deduzir pela tela.

---

## 1. O plugin sobe

- [ ] Console mostra `LouiPlugin habilitado.` sem stacktrace
- [ ] `/loui` sozinho responde com a mensagem de uso

> Se `/loui` não responder nada, a extração do comando quebrou. É o risco mais
> consequente da v1.1.0 — pare aqui e me avise.

---

## 2. Horas no tempo (a correção-título)

```
/loui <jogador> 2h teste de horas
```

- [ ] Comando aceito
- [ ] Bossbar do punido mostra `2:00:00`
- [ ] `/loui free <jogador>` solta e devolve ao lugar exato

Agora o contra-teste:

```
/loui <jogador> 120m teste de minutos
```

- [ ] Bossbar mostra `2:00:00` também — `2h` e `120m` têm de ser idênticos

E o que deve falhar:

```
/loui <jogador> abc motivo
```

- [ ] Recusado com mensagem de tempo inválido

---

## 3. Dois presos não colidem

Com dois jogadores online:

```
/loui <jogador1> 30m primeiro
/loui <jogador2> 30m segundo
```

- [ ] `/loui list` mostra os dois
- [ ] Abra `plugins/LouiPlugin/prisons.yml`: um tem `band: 0`, o outro `band: 1`
- [ ] Os dois não se empurram (se estiverem no mesmo ponto, se empurrariam)

> `band: 0` cai de y=5000 até y=1500. `band: 1` cai de y=1400 até y=-2100.
> São faixas separadas, no mesmo X/Z — por isso só um chunk fica carregado.

- [ ] Solte os dois e confirme que cada um voltou para o seu lugar

---

## 4. Punição de jogador offline (funcionalidade nova)

- [ ] Peça para o jogador2 deslogar
- [ ] Com ele **offline**: `/loui <jogador2> 1d griefing`
- [ ] Comando aceito (antes da v1.1.0 isso era impossível)
- [ ] `/loui list` mostra ele
- [ ] `prisons.yml` tem o registro dele **sem** a seção `loc:`

Agora peça para ele entrar:

- [ ] Ao entrar, ele é contido imediatamente (tela escura, bossbar)
- [ ] `prisons.yml` agora **tem** a seção `loc:` — capturada no login
- [ ] `/loui free <jogador2>` devolve ele para onde ele entrou

E o nome que não existe:

```
/loui JogadorQueNuncaEntrou 30m teste
```

- [ ] Recusado com "nunca entrou no servidor"

---

## 5. Release no desligamento (a correção mais importante)

- [ ] Contenha o jogador1 por 1 dia: `/loui <jogador1> 1d teste shutdown`
- [ ] Com ele **online e contido**, pare o servidor (`stop` no console)
- [ ] Console mostra `1 preso(s) solto(s) no desligamento.`
- [ ] Suba o servidor de novo
- [ ] O jogador1 entra normal: enxergando, sem bossbar, no lugar original

> Se ele entrar cego e invulnerável, esta correção falhou — é exatamente o bug
> que a v1.1.0 existe para matar. Me avise imediatamente.

---

## 6. Imunidade

Dê `loui.exempt` ao jogador2 (pelo seu plugin de permissões).

- [ ] `/loui <jogador2> 30m teste` → recusado com "é imune ao castigo"

Agora a versão traiçoeira, que fecha a brecha:

- [ ] Remova `loui.exempt` dele
- [ ] Peça para ele deslogar
- [ ] Com ele offline: `/loui <jogador2> 1d teste imunidade`
- [ ] Dê `loui.exempt` a ele **enquanto está offline**
- [ ] Peça para ele entrar
- [ ] Ele entra **livre** — o castigo é descartado no login
- [ ] Staff online com `loui.notify` recebe o aviso do descarte

---

## 7. Aviso para a equipe

Com dois membros de staff online, ambos com `loui.notify`:

- [ ] Um deles executa `/loui <jogador> 30m teste aviso`
- [ ] **Os dois** veem a mensagem (antes só quem digitou via)
- [ ] O punido **não** vê a mensagem de staff, só a dele

---

## 8. Whitelist de comandos

Com o padrão (`allowed-commands: []`):

- [ ] Punido tenta `/spawn` → bloqueado
- [ ] Punido tenta qualquer comando → bloqueado

Agora edite `plugins/LouiPlugin/config.yml`:

```yaml
restrictions:
  allowed-commands: ['/msg']
```

- [ ] Reinicie o servidor
- [ ] Punido tenta `/msg <staff> oi` → **passa**
- [ ] Punido tenta `/spawn` → continua bloqueado

---

## 9. Compatibilidade com a versão anterior

Se você tinha um `prisons.yml` gerado pela v1.0.0:

- [ ] Ele carrega sem erro no console
- [ ] Console mostra `N castigo(s) carregado(s).`
- [ ] O preso antigo continua contido normalmente

---

## Se algo falhar

Me mande:

1. **Qual item** falhou (número)
2. **O que aconteceu** em vez do esperado
3. O trecho do **console** com a stacktrace, se houver
4. O `prisons.yml` no momento da falha, se for relevante

Não tente corrigir — o estado da falha é mais útil para diagnóstico do que um
servidor já consertado.

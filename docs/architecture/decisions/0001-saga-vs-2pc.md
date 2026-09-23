# ADR 0001 - Saga em vez de transacao distribuida (2PC)

- Status: aceito
- Data: 2026-09-22

## Contexto

A transferencia entre bancos distintos atravessa dois servicos, cada um com seu
proprio banco de dados: debitar no Banco A e creditar no Banco B. Precisamos de
consistencia: nunca debitar sem creditar, nem creditar sem debitar.

O problema e que nao existe uma transacao ACID unica sobre dois bancos de dados
separados. Quando o debito no A commita, ele esta feito - se o credito no B falha
depois, nao ha como dar `ROLLBACK` no A: a transacao local dele ja terminou.

## Alternativas consideradas

**A. Two-Phase Commit (2PC / XA) entre os dois bancos.** Um coordenador manteria
a transacao aberta nos dois recursos ate ambos confirmarem. Problemas: acopla os
servicos a um coordenador transacional, os recursos ficam BLOQUEADOS enquanto a
transacao esta aberta (locks distribuidos), e uma falha do coordenador trava
tudo. Nao escala e e fragil. Em arquitetura de microsservicos, 2PC e evitado.
Rejeitado.

**B. Saga.** Uma sequencia de transacoes LOCAIS, cada uma committada no seu
servico. A consistencia nao vem de um rollback global, mas de COMPENSACAO: se um
passo falha, os passos anteriores sao desfeitos por operacoes inversas.
Escolhida.

## Decisao

Usar o padrao Saga. A transferencia vira:

```
Passo 1: debita no Banco A   (transacao local, committada)
Passo 2: credita no Banco B  (transacao local, committada)

Se o Passo 2 falha:
Passo 1c (compensacao): estorna o debito no Banco A
```

Cada passo e local e definitivo. A saga coordena a sequencia e dispara a
compensacao quando necessario.

## Consequencias

**Positivas**
- Sem locks distribuidos: cada servico commita e libera na hora.
- Escala e resiliencia: servicos desacoplados, sem coordenador transacional.
- Cada servico e dono do seu dado e da sua transacao local.

**Negativas / trade-offs**
- **Consistencia eventual, nao imediata.** Durante a saga existe um intervalo em
  que o dinheiro "saiu" de A mas ainda nao chegou em B. O sistema fica
  temporariamente inconsistente ate a saga completar ou compensar.
- **Compensacao nem sempre e trivial.** Estornar um debito e simples; compensar
  um e-mail ja enviado ou uma cobranca ja capturada e mais complexo. No dominio
  de saldo, o inverso e claro.
- **Toda operacao precisa de um inverso.** Cada passo que altera estado tem que
  ter sua compensacao definida.
- **Idempotencia obrigatoria.** Reentrega de mensagem nao pode debitar/creditar
  duas vezes; cada passo e a compensacao precisam ser idempotentes.

## Pergunta de entrevista que esta decisao responde

*"Como voce mantem consistencia numa operacao que atravessa varios servicos?"*
Saga: quebro em transacoes locais, cada uma committada no seu servico, e garanto
consistencia por compensacao - se um passo falha, desfaço os anteriores com
operacoes inversas. Evito 2PC porque ele bloqueia recursos com locks distribuidos
e acopla tudo a um coordenador. O custo da saga e consistencia eventual e a
necessidade de uma compensacao para cada passo, alem de idempotencia.

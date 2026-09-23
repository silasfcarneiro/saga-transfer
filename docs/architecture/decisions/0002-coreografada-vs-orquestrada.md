# ADR 0002 - Saga coreografada vs orquestrada

- Status: aceito (implementar ambas para comparar)
- Data: 2026-09-22

## Contexto

Escolhida a Saga (ver [ADR 0001](0001-saga-vs-2pc.md)), resta decidir COMO
coordenar os passos. Ha dois estilos, e este projeto implementa os DOIS para
comparacao pratica.

## Os dois estilos

### Coreografada (choreography)
Nao ha coordenador. Cada servico reage a eventos e emite o proximo. A logica da
saga fica DISTRIBUIDA entre os servicos.

```
DebitRequested -> A debita -> emite Debited
Debited        -> B credita -> emite Credited        (saga OK)
                            -> emite CreditFailed
CreditFailed   -> A estorna -> emite DebitReverted   (compensada)
```

### Orquestrada (orchestration)
Um orquestrador central mantem uma maquina de estados e comanda cada passo,
aguardando a resposta antes do proximo.

```
Orchestrator: comanda "debita no A" -> aguarda "ok"
              comanda "credita no B" -> aguarda
                 ok    -> estado COMPLETED
                 falha -> comanda "estorna no A" -> estado COMPENSATED
```

## Comparacao

| Aspecto | Coreografada | Orquestrada |
|---|---|---|
| Coordenacao | distribuida (eventos) | central (orquestrador) |
| Acoplamento | baixo entre servicos | servicos acoplados ao orquestrador |
| Rastrear estado da saga | dificil (estado espalhado) | facil (orquestrador conhece o estado) |
| Debug / observabilidade | mais dificil | mais facil |
| Ponto unico | nao ha | o orquestrador (mais um servico a manter) |
| Fluxos complexos (muitos passos) | vira "teia" de eventos dificil de seguir | a maquina de estados organiza |
| Fluxos simples (poucos passos) | leve e direto | pode ser over-engineering |

## Decisao

Implementar as duas para comparar na pratica:

- **Coreografada:** a logica vive distribuida entre `account-a` e `account-b`,
  reagindo a eventos no Kafka.
- **Orquestrada:** um modulo `orchestrator` com maquina de estados comanda os
  passos.

Como regra geral (a levar para entrevista): coreografada brilha em fluxos
simples e com poucos servicos, onde o baixo acoplamento compensa; orquestrada
brilha quando a saga tem muitos passos ou precisa de rastreabilidade e controle
central - o orquestrador vira o lugar unico onde o estado e a logica da saga
vivem.

## Consequencias

**Positivas**
- Entendimento pratico dos dois estilos e de quando usar cada um.
- Base para um ADR comparativo com evidencia real (nao so teoria).

**Negativas / trade-offs**
- Mais codigo (dois estilos) e a necessidade de manter a comparacao coerente.

## Pergunta de entrevista que esta decisao responde

*"Saga coreografada ou orquestrada - qual voce usa?"*
Depende. Coreografada e descentralizada, via eventos, com baixo acoplamento -
boa para fluxos simples e poucos servicos, mas o estado da saga fica espalhado e
e dificil de rastrear. Orquestrada tem um coordenador com maquina de estados -
mais facil de rastrear, debugar e evoluir quando ha muitos passos, ao custo de um
ponto central a mais. Fluxo simples: coreografia. Fluxo complexo ou que exige
observabilidade do estado: orquestracao.

# API / Interface - saga-transfer

Este projeto e orientado a eventos: a "interface" principal nao e HTTP, e o
topico Kafka `saga-events`. Os servicos reagem a eventos, nao a chamadas REST.
Este documento descreve os contratos de entrada e saida.

## Entrada da saga: evento TransferRequested

A saga e iniciada publicando um `TransferRequested` no topico `saga-events`.

**Topico:** `saga-events`
**Envelope + payload:**

```json
{
  "eventId": "uuid unico",
  "eventType": "TransferRequested",
  "sagaId": "uuid da saga",
  "payload": {
    "sourceAccountId": "uuid da conta no Banco A",
    "targetAccountId": "uuid da conta no Banco B",
    "amount": 5000
  }
}
```

`amount` em centavos (5000 = R$ 50,00).

No estudo, esse evento e publicado manualmente (console-producer). A evolucao
natural e um endpoint REST em um servico de entrada (ou no proprio orquestrador)
que recebe a solicitacao e publica o TransferRequested:

```
POST /transfers
{ "sourceAccountId": "...", "targetAccountId": "...", "amount": 5000 }
   -> publica TransferRequested -> 202 Accepted { "sagaId": "..." }
```

Repare: seria `202 Accepted` (aceito, processando), nao `201`, porque a saga e
assincrona - a transferencia nao esta concluida quando o cliente recebe a
resposta; ela esta em andamento.

## Eventos de saida

Os servicos publicam, no mesmo topico, os eventos que representam o andamento da
saga. Contrato completo em
[`../events/event-catalog.md`](../events/event-catalog.md):

| Evento | Emitido por | Significado |
|---|---|---|
| Debited | Banco A | origem debitada |
| Credited | Banco B | destino creditado (saga completa) |
| CreditFailed | Banco B | credito falhou (dispara compensacao) |
| DebitReverted | Banco A | debito estornado (saga compensada) |

## Como observar o estado da saga

Na saga COREOGRAFADA, nao ha um endpoint unico que diga "a saga X esta em que
estado" - o estado esta distribuido nos eventos e nos saldos de cada banco. Para
observar:

- **Eventos:** consumir o topico `saga-events` filtrando pelo `sagaId`.
- **Saldos:** consultar `account` em cada database.

> Essa dificuldade de observar o estado global e justamente uma desvantagem da
> coreografia sobre a orquestracao (ver ADR 0002). Na versao orquestrada, o
> orquestrador mantem o estado da saga e poderia expor um `GET /sagas/{id}`.

## Servicos e portas

| Servico | Porta | Database | Papel |
|---|---|---|---|
| account-a | 8081 | account_a | debita / estorna (Banco A) |
| account-b | 8082 | account_b | credita (Banco B) |
| orchestrator | 8083 | - | saga orquestrada (evolucao) |

No momento os servicos nao expoem endpoints REST de negocio - toda a interacao e
por eventos. Endpoints (POST /transfers, GET /sagas/{id}) ficam como evolucao.

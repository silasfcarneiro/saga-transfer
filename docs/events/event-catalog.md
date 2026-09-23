# Catalogo de Eventos - saga-transfer

Todos os eventos trafegam no topico `saga-events`, com o mesmo envelope.

## Envelope (comum a todos)

```json
{
  "eventId": "uuid",
  "eventType": "Debited",
  "sagaId": "uuid",
  "payload": { }
}
```

| Campo | Papel |
|---|---|
| `eventId` | unico; idempotencia no consumer |
| `eventType` | tipo do evento; o consumer decide o que fazer por ele |
| `sagaId` | correlaciona todos os eventos de uma transferencia; chave de particao |
| `payload` | dados especificos do evento |

Ver [ADR 0003](../architecture/decisions/0003-envelope-de-evento.md) sobre a
importancia do envelope.

## Topico: `saga-events`

Particao pelo `sagaId` (eventos da mesma saga em ordem; sagas diferentes em
paralelo).

### TransferRequested
Inicia a saga. Publicado pela origem do comando.
- payload: `{ sourceAccountId, targetAccountId, amount }`
- consumido por: Banco A (debita)

### Debited
Banco A debitou a origem com sucesso.
- payload: `{ sourceAccountId, targetAccountId, amount }`
- consumido por: Banco B (credita)

### Credited
Banco B creditou o destino. Saga completa (caminho feliz).
- payload: `{ targetAccountId, amount }`
- consumido por: (fim da saga; poderia notificar a origem)

### CreditFailed
Banco B nao conseguiu creditar (conta invalida, etc.). Dispara a compensacao.
- payload: `{ sourceAccountId, amount, reason }`
- consumido por: Banco A (estorna)

### DebitReverted
Banco A estornou o debito. Saga compensada.
- payload: `{ sourceAccountId, amount }`
- consumido por: (fim da saga compensada)

## Quem reage a que

| Evento | Banco A | Banco B |
|---|---|---|
| TransferRequested | debita | ignora |
| Debited | ignora | credita |
| Credited | ignora | ignora (fim) |
| CreditFailed | estorna (compensa) | ignora |
| DebitReverted | ignora (fim) | ignora |

## Nota de producao

No estudo, um unico topico `saga-events` carrega todos os eventos (simplicidade).
Em producao, o comum e topico por tipo de evento ou por dominio, para que cada
servico consuma so o que lhe interessa e os contratos fiquem explicitos.

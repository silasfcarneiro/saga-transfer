# Modelo de Dados - saga-transfer

Cada servico (Banco A e Banco B) tem seu proprio database no mesmo cluster
PostgreSQL: `account_a` e `account_b`. Database por servico - ninguem acessa o
banco do outro; a comunicacao e so por eventos.

Cada database tem as mesmas tres tabelas.

## `account` - conta e saldo

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | uuid PK | identificador da conta |
| `owner_name` | text | titular |
| `balance` | bigint | saldo em centavos (inteiro, nunca float) |
| `version` | bigint | `@Version` - lock otimista contra escrita concorrente |

No Banco A o saldo e debitado (e estornado na compensacao); no Banco B e
creditado.

## `outbox` - eventos pendentes de publicacao

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | uuid PK | usado como eventId no envelope publicado |
| `saga_id` | uuid | correlaciona todos os eventos de uma transferencia; chave de particao |
| `event_type` | text | Debited, Credited, CreditFailed, DebitReverted |
| `topic` | text | saga-events |
| `payload` | text | JSON com os dados do evento |
| `created_at` | timestamptz | |
| `published_at` | timestamptz null | NULL enquanto nao publicado |

Escrita na mesma transacao do debito/credito. O relay le `published_at is null`,
monta o envelope (eventId + eventType + sagaId + payload) e publica no Kafka.

## `processed_event` - idempotencia do consumer

| Coluna | Tipo | Notas |
|---|---|---|
| `event_id` | uuid PK | id do evento ja processado |
| `processed_at` | timestamptz | |

Antes de processar, o consumer verifica se o eventId ja existe; grava na mesma
transacao do efeito. Uma reentrega (at-least-once) e ignorada.

## Decisoes de modelagem

| Decisao | Por que |
|---|---|
| Saldo em centavos (bigint) | float tem erro de arredondamento; inaceitavel em dinheiro |
| Lock otimista (version) | evita lost update em escritas concorrentes na conta |
| Database por servico | isolamento; cada banco e dono do seu dado |
| payload como text (nao jsonb) | simplicidade; o relay so le e publica, nao consulta o JSON no banco |
| outbox.id = eventId | o id da linha da outbox vira o eventId do envelope, garantindo unicidade |

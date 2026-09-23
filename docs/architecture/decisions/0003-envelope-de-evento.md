# ADR 0003 - Envelope de evento comum (produtor e consumidor)

- Status: aceito
- Data: 2026-09-22

## Contexto

Os servicos da saga se comunicam por eventos no Kafka. O consumidor precisa, para
cada mensagem, saber: qual o tipo do evento (para rotear), um id unico (para
idempotencia) e o id da saga (para correlacao). Esses metadados nao fazem parte
dos dados de negocio do evento em si (ex.: `Debited` so tem conta e valor).

Surgiu um bug real durante a implementacao: o relay do Outbox estava publicando
apenas o `payload` do evento (os dados de negocio), sem os metadados. O
consumidor, que esperava um envelope com `eventType`, lia `eventType` como nulo e
IGNORAVA a mensagem silenciosamente (um `?: return`). Resultado: o Banco A
debitava e publicava, mas o Banco B nunca creditava - a saga travava sem erro
visivel.

## Decisao

Todo evento publicado no Kafka usa um ENVELOPE comum:

```json
{
  "eventId": "uuid",
  "eventType": "Debited",
  "sagaId": "uuid",
  "payload": { ...dados do evento... }
}
```

O relay do Outbox monta esse envelope a partir das colunas da tabela outbox
(`id` -> eventId, `event_type` -> eventType, `saga_id` -> sagaId) e do `payload`
armazenado, e publica o envelope completo - nao apenas o payload.

O consumidor le o envelope, roteia pelo `eventType`, deduplica pelo `eventId` e
correlaciona pelo `sagaId`.

## Consequencias

**Positivas**
- Contrato uniforme: todo consumidor sabe extrair tipo, id e saga de qualquer
  evento, sem conhecer o formato interno de cada payload.
- Idempotencia e roteamento padronizados.

**Negativas / trade-offs**
- O relay precisa montar o envelope (uma responsabilidade a mais).
- O envelope e montado por concatenacao de string no relay (simples, mas exige
  cuidado); uma evolucao seria serializar um objeto Envelope com o ObjectMapper.

## Licao registrada

O formato que o produtor publica TEM que bater com o que o consumidor espera. Um
consumidor que ignora o que nao entende (`?: return`) falha em silencio - o
evento some sem erro. Vale logar o que e ignorado, para nao mascarar bug de
contrato.

## Pergunta de entrevista que esta decisao responde

*"Como seus servicos sabem o tipo de um evento e evitam processa-lo duas vezes?"*
Uso um envelope comum em todo evento: eventId para idempotencia, eventType para
roteamento, sagaId para correlacao, e o payload com os dados. O relay monta o
envelope a partir da outbox e publica completo. Aprendi na pratica que publicar so
o payload quebra o consumidor silenciosamente - o contrato do envelope tem que ser
respeitado nas duas pontas.

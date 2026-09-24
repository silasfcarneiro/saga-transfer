# ADR 0004 - Quando usar Saga (mensageria vs APIs) e compensacao vs rollback

- Status: aceito
- Data: 2026-09-24

## Contexto

A saga deste projeto foi implementada com mensageria (Kafka, coreografada). Mas
Saga e um PADRAO, nao uma tecnologia - o meio de comunicacao entre os passos pode
ser mensageria (eventos) ou chamadas de API (HTTP/gRPC). Este ADR registra
quando usar Saga, como ela se aplica a APIs, e a distincao entre compensacao e
rollback (que sao coisas diferentes).

## Saga nao depende de Kafka

Saga = quebrar uma operacao em transacoes LOCAIS, cada uma committada no seu
servico, com compensacao quando um passo falha. Isso vale independente do meio:

- **Via mensageria (eventos):** cada servico reage a eventos e emite o proximo.
  Assincrono, baixo acoplamento. Tende a ser coreografada. (o que este projeto fez)
- **Via API (HTTP/gRPC):** um orquestrador chama os endpoints em sequencia e, se
  um falha, chama os endpoints de compensacao dos passos anteriores. Sincrono,
  acoplamento maior. Tende a ser orquestrada.

```
Saga orquestrada via API:
  1. POST banco-a/debit    -> 200 (debitou)
  2. POST banco-b/credit   -> 500 (falhou)
  3. POST banco-a/revert   -> 200 (compensou)
```

## Compensacao NAO e rollback

Distincao central:

- **Rollback:** desfaz uma transacao AINDA NAO confirmada, dentro de um unico
  banco. Volta ao estado anterior sem deixar rastro. So existe dentro de uma
  transacao local.
- **Compensacao:** uma NOVA operacao que desfaz o EFEITO de uma acao ja
  confirmada. Quando um POST/PUT ja respondeu 200, aquilo aconteceu de verdade
  (ainda mais numa API externa) - nao da para "dar rollback". Voce executa a
  acao inversa.

```
Acao:         POST /charge   -> cobrou R$ 100 (confirmado)
Compensacao:  POST /refund   -> estorna R$ 100 (nova acao, desfaz o efeito)
```

O refund nao apaga o charge - registra o inverso. No extrato aparecem os dois.

## Quando usar Saga

Usar Saga quando as TRES condicoes valem:
1. **Varios passos** que alteram estado (nao uma operacao unica).
2. Em **servicos/recursos diferentes** - sem transacao ACID unica entre eles.
3. Precisa de **atomicidade logica**: ou todos os passos efetivam, ou os que
   efetivaram sao compensados.

Se falta qualquer uma:
- Um POST unico (uma cobranca, e acabou) -> NAO e saga. E uma chamada com
  idempotencia + retry.
- Uma consulta (GET, sem efeito colateral) -> NAO e saga. E so resiliencia
  (timeout, retry, circuit breaker).
- Tudo no mesmo banco -> NAO e saga. Uma transacao ACID local resolve.

## Saga com API externa (ex.: gateway de pagamento)

Quando um passo da saga e uma API externa (Stripe, Cielo, um parceiro), valem
cuidados que a mensageria interna nao exige:

- **Idempotencia na chamada:** enviar uma chave de idempotencia para a API nao
  cobrar duas vezes se houver retry.
- **Timeout e retry com backoff:** a rede falha; a chamada pode responder tarde
  ou nao responder.
- **Incerteza de resultado:** um timeout nao diz se a API processou ou nao.
  Pode ser preciso consultar o status antes de compensar ou repetir.
- **Compensacao via API:** o passo inverso tambem e uma chamada (ex.: refund).

Esses cuidados de resiliencia (circuit breaker, retry, timeout, bulkhead) sao o
tema do proximo estudo (consumo de APIs externas) e se COMBINAM com Saga quando
a API e um passo de um fluxo transacional.

## Pergunta de entrevista que esta decisao responde

*"Quando voce usa Saga, e o que acontece na falha?"*
Uso Saga quando tenho varios passos que alteram estado em servicos diferentes,
sem transacao ACID unica, e preciso de atomicidade logica. Na falha de um passo,
executo a COMPENSACAO dos passos ja concluidos - nao um rollback, porque o que
ja foi confirmado (principalmente numa API externa) aconteceu de verdade; a
compensacao e uma acao inversa (charge -> refund). Se e so uma chamada unica ou
uma consulta, nao e saga - e resiliencia com idempotencia e retry.

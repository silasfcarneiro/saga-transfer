# ADR 0005 - Endpoint REST inicia a saga via Outbox (202 Accepted)

- Status: aceito
- Data: 2026-09-24

## Contexto

A saga precisava de um ponto de entrada. Ate entao, o evento inicial
(`TransferRequested`) era publicado manualmente no topico. Faltava uma interface
para o cliente solicitar uma transferencia.

Duas decisoes surgiram:
1. Onde colocar o endpoint que inicia a saga.
2. Como o endpoint publica o evento inicial: direto no Kafka ou via Outbox.
3. Qual status HTTP retornar.

## Decisao

**Endpoint no Banco A** (`POST /transfers`). O Banco A ja e o primeiro passo da
saga (debita), entao e o ponto de entrada natural. Evita um modulo so para o
endpoint. Em producao, poderia ser um servico de entrada dedicado ou o proprio
orquestrador.

**Publicacao via Outbox.** O endpoint grava o `TransferRequested` na tabela
outbox (transacao local) e o relay publica no Kafka. Nao publica direto no
broker.

**Status 202 Accepted.** A saga e assincrona: quando o cliente recebe a
resposta, a transferencia esta EM ANDAMENTO, nao concluida. 202 (aceito,
processando) e o correto, nao 201 (criado). Retorna o `sagaId` para o cliente
acompanhar.

## Justificativa da publicacao via Outbox

Publicar direto no Kafka acoplaria o request a disponibilidade do broker: se o
Kafka estivesse fora no instante da chamada, o publish falharia e o cliente
receberia erro sem a saga ter comecado. Gravar na outbox garante entrega: o
endpoint responde 202 com seguranca, e o relay publica quando conseguir.

Nuance honesta: aqui o Outbox da GARANTIA DE ENTREGA, mas nao a atomicidade com
um dado de negocio (nao ha saldo mudando junto do evento inicial). A atomicidade
com dado de negocio - o forte do padrao - aparece nos passos que alteram saldo
(debito, credito).

## Consequencias

**Positivas**
- Entrada confiavel: o evento inicial nao se perde se o broker oscilar.
- 202 + sagaId comunica corretamente a natureza assincrona ao cliente.

**Negativas / trade-offs**
- O cliente nao sabe o resultado final na resposta; precisaria consultar o
  estado depois (nao ha GET de status na saga coreografada - ver ADR 0002).

## Pergunta de entrevista que esta decisao responde

*"Como um cliente inicia a saga e o que voce retorna?"*
Um POST /transfers grava o evento inicial na outbox e responde 202 Accepted com
o sagaId - 202 porque a saga e assincrona, a transferencia esta em andamento, nao
concluida. Uso outbox em vez de publicar direto no Kafka pra nao acoplar o
request a disponibilidade do broker: o endpoint responde com seguranca e o relay
publica quando puder.

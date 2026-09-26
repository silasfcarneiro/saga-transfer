# ADR 0006 - Estrategia de testes e comunicacao entre containers

- Status: aceito
- Data: 2026-09-24

## Contexto

A saga foi validada em tres niveis de teste, cada um com um proposito. O nivel
mais alto (end-to-end) revelou um problema classico de sistemas distribuidos em
containers, registrado aqui como licao.

## Estrategia de testes (piramide)

**1. Unitario (mock, sem infra).** Testa a logica de cada AccountService
isolada: debito com/sem saldo, credito, compensacao (revert), idempotencia. Roda
em milissegundos. Mocks nos repositorios, ObjectMapper real.

**2. (Nao implementado aqui) Integracao por servico.** Cada servico contra um
Postgres/Kafka real via Testcontainers. Fica como evolucao; o E2E cobre o
essencial.

**3. End-to-end (E2E).** Sobe QUATRO containers via Testcontainers - Postgres,
Kafka, account-a e account-b (os servicos empacotados em imagem a partir do
Dockerfile) - numa rede compartilhada. Dispara a saga por HTTP no account-a e
espera (Awaitility) a propagacao entre os containers, verificando os saldos nos
dois bancos. E teste de sistema distribuido real.

## Licao central: comunicacao entre containers nao usa localhost

No E2E, o debito nao acontecia dentro dos containers. Causa raiz, descoberta pelo
log:

- O **producer** conectava no Kafka certo (`kafka:9092`).
- O **consumer** conectava no bootstrap, mas o Kafka respondia que o group
  coordinator estava em `localhost:9092` (o valor de `advertised.listeners`). De
  DENTRO de um container, `localhost` e o proprio container - nao o Kafka. O
  consumer entrava em loop tentando localhost e nunca consumia.

Dois pontos corrigidos:
1. **Consumer com endereco hardcoded.** A `consumerFactory` tinha
   `"localhost:9092"` fixo, enquanto o producer ja lia de configuracao. Passou a
   ler `spring.kafka.bootstrap-servers` (que vem por variavel de ambiente no
   container).
2. **advertised.listeners do Kafka.** O KafkaContainer precisa anunciar um
   endereco que os OUTROS containers alcancem. Resolvido com um listener interno
   (`withListener("kafka:19092")`), e os servicos apontando para ele.

Regra geral: **servicos em containers se enderecam pelo NOME de rede
(`kafka`, `postgres`), com a porta - nunca por `localhost`.** `localhost` dentro
de um container e o proprio container. Config de endereco nunca deve ser
hardcoded: vem do ambiente, para funcionar tanto local quanto em container.

## Consequencias

**Positivas**
- Cobertura em camadas: logica (unitario) e sistema completo (E2E).
- Entendimento pratico de rede entre containers e de advertised.listeners -
  aplicavel a qualquer sistema distribuido containerizado.

**Negativas / trade-offs**
- O E2E e pesado (builda imagens, sobe 4 containers) e lento; roda menos vezes
  que os unitarios.
- Config de rede do Kafka em Docker exige cuidado (listeners interno x externo).

## Pergunta de entrevista que esta decisao responde

*"Voce ja testou um sistema distribuido de ponta a ponta? Que dificuldade
apareceu?"*
Sim - subi os servicos + Kafka + Postgres em containers via Testcontainers,
disparei a saga por HTTP e esperei a propagacao com Awaitility. A dificuldade
classica foi rede: dentro de um container, localhost e o proprio container, entao
os servicos tem que se enderecar pelo nome de rede. No Kafka isso aparece no
advertised.listeners - ele precisa anunciar um endereco que os outros containers
alcancem, senao o consumer descobre o coordinator em localhost e nunca consome.
Config de endereco nunca hardcoded: vem do ambiente.

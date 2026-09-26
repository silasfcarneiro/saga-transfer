# saga-transfer

Transferencia entre bancos distintos usando o padrao Saga, em Kotlin/Spring Boot.
Projeto de estudo e portfolio com foco em consistencia distribuida - implementa
os DOIS estilos de saga (coreografada e orquestrada) para comparacao.

## O problema

Transferir dinheiro entre dois bancos diferentes (Banco A -> Banco B) atravessa
dois servicos, cada um com seu proprio banco de dados. Nao existe transacao ACID
que abranja os dois: se o debito no Banco A commita e o credito no Banco B falha,
nao ha `ROLLBACK` - o debito ja aconteceu. A consistencia vem da **compensacao**
(estornar o debito), nao do rollback. Isso e a Saga.

## Os dois estilos (implementados para comparar)

### Coreografada (via eventos)
Cada servico reage a eventos, sem coordenador central:
```
DebitRequested -> Banco A debita -> Debited
                  -> Banco B credita -> Credited (saga OK)
                                     -> CreditFailed -> Banco A estorna -> DebitReverted (compensada)
```

### Orquestrada (coordenador central)
Um orquestrador com maquina de estados comanda cada passo:
```
Orchestrator: debita no A -> ok -> credita no B -> ok  (saga OK)
                                              -> falha -> estorna no A (compensada)
```

Comparacao detalhada em [ADR 0002](docs/architecture/decisions/0002-coreografada-vs-orquestrada.md).

## Fluxo (feliz e compensacao)

Feliz:      debita A (commit local) -> credita B (commit local) -> concluida
Compensa:   debita A (commit local) -> credita B FALHA -> estorna A -> falhou (dinheiro devolvido)

Cada passo e uma transacao LOCAL committada. A saga garante que, ou todos os
passos efetivam, ou os que efetivaram sao compensados.

## Modulos (multi-modulo Gradle)

```
saga-transfer/
  settings.gradle.kts          include("common","account-a","account-b","orchestrator")
  gradle/libs.versions.toml    Version Catalog (versoes centralizadas)
  docker-compose.yml           Kafka + Postgres (um db por servico)
  common/                      envelope de evento, outbox, idempotencia, ProblemDetail
  account-a/                   Banco A: contas, debitar, estornar (compensacao)
  account-b/                   Banco B: contas, creditar, estornar (compensacao)
  orchestrator/                saga orquestrada (maquina de estados)
                               (a coreografada vive distribuida entre account-a/b)
```

## Principios (herdados do transfer-service, aplicados de novo)

1. Cada passo da saga e uma transacao LOCAL - consistencia por compensacao.
2. Idempotencia em cada passo: uma reentrega nao debita/credita duas vezes.
3. Outbox: eventos publicados de forma confiavel, sem 2PC.
4. At-least-once + consumer idempotente.
5. Compensacao sempre possivel: todo passo que altera estado tem seu inverso.

## Stack

- Kotlin + Spring Boot (Web, Data JPA)
- PostgreSQL (um database por servico)
- Apache Kafka (eventos da saga)
- Docker Compose
- Gradle (Kotlin DSL) com Version Catalog

## Decisoes de arquitetura (ADRs)

- [0001 - Saga em vez de transacao distribuida (2PC)](docs/architecture/decisions/0001-saga-vs-2pc.md)
- [0002 - Coreografada vs Orquestrada](docs/architecture/decisions/0002-coreografada-vs-orquestrada.md)

## Endpoints

| Metodo | Rota | Resposta |
|---|---|---|
| POST | `/transfers` (Banco A) | 202 Accepted `{sagaId}` - inicia a saga (assincrona) |

## Testes

```bash
./gradlew :account-a:test    # unitarios do Banco A + E2E
./gradlew :account-b:test    # unitarios do Banco B
```

- **Unitarios** (mock): logica de debito, credito, compensacao e idempotencia.
- **E2E** (Testcontainers): sobe Postgres + Kafka + os dois servicos em
  containers, dispara a saga por HTTP e verifica a propagacao entre os bancos.
  Ver [ADR 0006](docs/architecture/decisions/0006-testes-e-rede-entre-containers.md).

## Roadmap

- [x] Fundacao: multi-modulo Gradle + Version Catalog + common
- [x] account-a e account-b: debitar/creditar/estornar, cada um com seu Postgres
- [x] Saga COREOGRAFADA: eventos entre A e B, com compensacao
- [x] Endpoint REST iniciando a saga (POST /transfers, 202 + Outbox)
- [x] Testes unitarios (logica) e E2E com Testcontainers (4 containers)
- [ ] Saga ORQUESTRADA: orquestrador com maquina de estados (modulo orchestrator)
- [ ] Testar explicitamente o fluxo de compensacao (credito falha -> estorno)
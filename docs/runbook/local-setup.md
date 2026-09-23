# Runbook - Ambiente Local

Como subir e testar a saga de transferencia entre bancos na sua maquina.

## Pre-requisitos

| Ferramenta | Versao | Para que |
|---|---|---|
| JDK | 21 | compilar e rodar os servicos |
| Docker + Docker Compose | recente | subir Postgres e Kafka |
| Git | qualquer | versionar |

## 1. Subir a infraestrutura

O `docker-compose.yml` na raiz sobe Postgres e Kafka. O `init-db.sql` cria os
dois databases (`account_a` e `account_b`) na primeira subida do volume.

```bash
docker compose up -d
docker compose ps          # confira que postgres e kafka estao Up
```

Confirme que os databases existem:

```bash
docker compose exec postgres psql -U saga -c "\l"
```

> Se `account_a` / `account_b` nao aparecerem (o init nao rodou), crie na mao:
> ```bash
> docker compose exec postgres psql -U saga -c "CREATE DATABASE account_a;"
> docker compose exec postgres psql -U saga -c "CREATE DATABASE account_b;"
> ```

## 2. Subir os dois servicos

Cada banco roda em uma porta e um database proprios. Suba em janelas separadas:

```bash
# Janela 1 - Banco A (porta 8081, database account_a)
./gradlew :account-a:bootRun

# Janela 2 - Banco B (porta 8082, database account_b)
./gradlew :account-b:bootRun
```

No startup de cada um, o Flyway cria as tabelas (`account`, `outbox`,
`processed_event`) e o consumer conecta no topico. Espere ver, em cada janela:

```
Subscribed to topic(s): saga-events
partitions assigned: [saga-events-0]
Tomcat started on port 808x
```

> Se o consumer NAO conectar (sem "partitions assigned"), confirme que a
> KafkaConfig tem `@EnableKafka` - no Spring Boot 4 a auto-config nao liga o
> processamento de @KafkaListener sozinha.

## 3. Criar as contas de teste

O servico movimenta saldo, nao cria conta. Insira uma em cada banco, com os ids
que serao usados no evento:

```bash
docker compose exec postgres psql -U saga -d account_a -c "insert into account (id, owner_name, balance, version) values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Alice', 100000, 0);"

docker compose exec postgres psql -U saga -d account_b -c "insert into account (id, owner_name, balance, version) values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Bob', 50000, 0);"
```

Saldos em centavos: Alice R$ 1.000,00, Bob R$ 500,00.

## 4. Disparar a saga

Publique um evento `TransferRequested` no topico (envelope completo). No estudo,
o disparo e manual via console-producer:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic saga-events
```

Cole uma linha e de Enter (depois Ctrl+C para sair):

```json
{"eventId":"10101010-0000-0000-0000-000000000001","eventType":"TransferRequested","sagaId":"20202020-0000-0000-0000-000000000001","payload":{"sourceAccountId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","targetAccountId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","amount":5000}}
```

## 5. Verificar o resultado

```bash
docker compose exec postgres psql -U saga -d account_a -c "select owner_name, balance from account;"
docker compose exec postgres psql -U saga -d account_b -c "select owner_name, balance from account;"
```

Esperado (transferencia de 5000): Alice 95000, Bob 55000. O dinheiro atravessou
os dois bancos via eventos.

Para ver o fluxo de eventos no topico:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic saga-events --from-beginning --timeout-ms 5000
```

Voce vera: TransferRequested -> Debited -> Credited.

## Reset limpo (para um teste do zero)

```bash
# apaga o topico (limpa os eventos)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic saga-events

# zera saldos, outbox e idempotencia dos dois bancos
docker compose exec postgres psql -U saga -d account_a -c "delete from account; delete from outbox; delete from processed_event; insert into account (id,owner_name,balance,version) values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','Alice',100000,0);"
docker compose exec postgres psql -U saga -d account_b -c "delete from account; delete from outbox; delete from processed_event; insert into account (id,owner_name,balance,version) values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','Bob',50000,0);"
```

Depois suba os dois servicos de novo e dispare a saga.

## Troubleshooting

| Sintoma | Causa provavel |
|---|---|
| App nao sobe: "port 808x already in use" | outra instancia rodando; feche a janela antiga ou mate o PID (netstat -ano \| findstr :808x) |
| Consumer nao conecta (sem "partitions assigned") | falta @EnableKafka na KafkaConfig (aresta do Boot 4) |
| "No default constructor" nas entidades do common | falta o plugin kotlin-jpa no common/build.gradle.kts |
| Banco B nao credita, publica CreditFailed | id da conta de destino no evento nao bate com o do banco |
| Banco B ignora os eventos | relay publicando so o payload, sem envelope (eventType nulo) - ver ADR 0003 |
| database "account_x" does not exist | init-db.sql nao rodou; crie os databases na mao ou recrie o volume (down -v) |

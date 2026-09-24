package com.saga.accounta.container

import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.time.Duration

class SagaE2ETest {

    companion object {
        private val logA = LoggerFactory.getLogger("ACCOUNT-A")
        private val logB = LoggerFactory.getLogger("ACCOUNT-B")

        private val projectRoot: File = run {
            val cwd = File(System.getProperty("user.dir"))
            if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile
        }

        private val network: Network = Network.newNetwork()

        private val postgres = PostgreSQLContainer("postgres:16")
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withUsername("saga")
            .withPassword("saga")

        private val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withNetwork(network)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092")

        private val accountA: GenericContainer<*> = GenericContainer(
            ImageFromDockerfile().withDockerfile(File(projectRoot, "account-a/Dockerfile").toPath())
        )
            .withNetwork(network)
            .withExposedPorts(8081)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/account_a")
            .withEnv("SPRING_DATASOURCE_USERNAME", "saga")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "saga")
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
            .withLogConsumer { frame -> print("ACCOUNT-A > ${frame.utf8String}") }
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8081).withStartupTimeout(Duration.ofSeconds(120)))

        private val accountB: GenericContainer<*> = GenericContainer(
            ImageFromDockerfile().withDockerfile(File(projectRoot, "account-b/Dockerfile").toPath())
        )
            .withNetwork(network)
            .withExposedPorts(8082)
            .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/account_b")
            .withEnv("SPRING_DATASOURCE_USERNAME", "saga")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "saga")
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
            .withLogConsumer { frame -> print("ACCOUNT-B > ${frame.utf8String}") }
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8082).withStartupTimeout(Duration.ofSeconds(120)))

        @BeforeAll
        @JvmStatic
        fun setup() {
            postgres.start()
            DriverManager.getConnection(postgres.jdbcUrl, "saga", "saga").use { conn ->
                conn.createStatement().execute("CREATE DATABASE account_a")
                conn.createStatement().execute("CREATE DATABASE account_b")
            }
            kafka.start()
            accountA.start()
            accountB.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            runCatching { accountB.stop() }
            runCatching { accountA.stop() }
            runCatching { kafka.stop() }
            runCatching { postgres.stop() }
        }

        private fun jdbcFor(db: String): String {
            val host = postgres.host
            val port = postgres.getMappedPort(5432)
            return "jdbc:postgresql://$host:$port/$db"
        }

        private fun saldo(db: String, owner: String): Long =
            DriverManager.getConnection(jdbcFor(db), "saga", "saga").use { conn ->
                conn.createStatement()
                    .executeQuery("select balance from account where owner_name = '$owner'")
                    .use { rs -> rs.next(); rs.getLong(1) }
            }

        private fun inserirConta(db: String, id: String, owner: String, saldo: Long) {
            DriverManager.getConnection(jdbcFor(db), "saga", "saga").use { conn ->
                conn.createStatement().execute(
                    "insert into account (id, owner_name, balance, version) values ('$id','$owner',$saldo,0)"
                )
            }
        }
    }

    @Test
    fun `transferencia propaga do Banco A para o Banco B`() {
        val alice = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val bob = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        inserirConta("account_a", alice, "Alice", 100_000)
        inserirConta("account_b", bob, "Bob", 50_000)

        // pequena espera para o consumer de cada serviço assinar o tópico
        Thread.sleep(5_000)

        val client = HttpClient.newHttpClient()
        val body = """{"sourceAccountId":"$alice","targetAccountId":"$bob","amount":30000}"""
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${accountA.host}:${accountA.getMappedPort(8081)}/transfers"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assert(response.statusCode() == 202) { "esperava 202, veio ${response.statusCode()} / ${response.body()}" }

        await()
            .atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(2))
            .untilAsserted {
                val a = saldo("account_a", "Alice")
                val b = saldo("account_b", "Bob")
                println(">>> saldos atuais: Alice=$a Bob=$b")
                assert(a == 70_000L) { "Alice deveria ter 70000, tem $a" }
                assert(b == 80_000L) { "Bob deveria ter 80000, tem $b" }
            }
    }
}
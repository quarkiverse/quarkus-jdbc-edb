package io.quarkiverse.jdbc.edb.it;

import java.util.Map;

import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Supplies datasource configuration to the integration tests.
 * <p>
 * By default a community PostgreSQL container is started: the EDB driver is a fork of pgjdbc and
 * speaks the same wire protocol, so it connects successfully and the extension wiring, XA support,
 * Hibernate mapping and native image are all exercised without needing a subscription-gated EPAS
 * image. This is what lets the tests run in public CI and for outside contributors.
 * <p>
 * When {@code edb.jdbc.url} is set -- the {@code epas} Maven profile -- no container is started and
 * the tests run against that real EPAS instance instead.
 */
public class EdbDatabaseTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String URL_PROPERTY = "edb.jdbc.url";
    private static final String USERNAME_PROPERTY = "edb.jdbc.username";
    private static final String PASSWORD_PROPERTY = "edb.jdbc.password";

    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        String externalUrl = System.getProperty(URL_PROPERTY);
        if (externalUrl != null && !externalUrl.isBlank()) {
            return Map.of(
                    "quarkus.datasource.jdbc.url", externalUrl,
                    "quarkus.datasource.username", System.getProperty(USERNAME_PROPERTY, "enterprisedb"),
                    "quarkus.datasource.password", System.getProperty(PASSWORD_PROPERTY, "enterprisedb"));
        }

        container = new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("edb")
                .withUsername("edb")
                .withPassword("edb");
        container.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", toEdbUrl(container.getJdbcUrl()),
                "quarkus.datasource.username", container.getUsername(),
                "quarkus.datasource.password", container.getPassword());
    }

    /**
     * Rewrites the {@code jdbc:postgresql://} URL Testcontainers produces into the {@code jdbc:edb://}
     * form the EDB driver requires. The driver rejects the {@code postgresql} prefix outright.
     */
    private static String toEdbUrl(String jdbcUrl) {
        return jdbcUrl.replaceFirst("^jdbc:postgresql:", "jdbc:edb:");
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}

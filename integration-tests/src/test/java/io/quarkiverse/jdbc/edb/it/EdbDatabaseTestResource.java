package io.quarkiverse.jdbc.edb.it;

import java.util.HashMap;
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
 * <p>
 * Four named datasources are always configured alongside the default one -- see
 * {@link #NAMED_DATASOURCES}. They are unconditional because their {@code db-kind}, and in some cases
 * more, is build-time configuration declared in {@code application.properties}: a native image bakes
 * that in, so it cannot be switched on per test. Only the URL and credentials are supplied here.
 * <p>
 * The {@code flyway} one is the reason this class appends per-datasource URL parameters rather than
 * one set for all of them. Flyway needs {@code changeServerName=true} against EPAS and Liquibase is
 * actively worse with it, so the two tools cannot share a URL -- a conflict this arrangement encodes
 * rather than merely documents.
 * <p>
 * All of them point at the same database as the default one. That is enough to prove the wiring and
 * keeps the tests free of side effects on a real EPAS instance. Note that a genuine two-phase commit
 * would need two <em>distinct</em> databases, since two datasources onto the same one may be
 * collapsed into a single-phase commit; the XA tests here deliberately do not depend on that, because
 * what they exist to catch breaks well before the commit protocol is reached.
 */
public class EdbDatabaseTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String URL_PROPERTY = "edb.jdbc.url";
    private static final String USERNAME_PROPERTY = "edb.jdbc.username";
    private static final String PASSWORD_PROPERTY = "edb.jdbc.password";

    /**
     * Named datasource to the driver connection properties its URL needs, keyed by the names declared
     * in {@code application.properties}.
     * <p>
     * Only {@code flyway} takes any. Flyway resolves its database type from the product name reported
     * through {@code DatabaseMetaData} and ships none for {@code EnterpriseDB}, so against EPAS it
     * fails with {@code Unsupported Database: EnterpriseDB} unless {@code changeServerName=true} makes
     * the driver report {@code PostgreSQL}. It is harmless against the community PostgreSQL container
     * used in CI, which reports {@code PostgreSQL} regardless, so one code path covers both.
     * <p>
     * {@code liquibase} is pointedly absent: the same flag would cost it the EnterpriseDB
     * implementation it would otherwise select.
     */
    private static final Map<String, String> NAMED_DATASOURCES = Map.of(
            "secondary", "",
            "xa", "",
            "flyway", "changeServerName=true",
            "liquibase", "");

    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    private PostgreSQLContainer container;

    @Override
    public Map<String, String> start() {
        String url;
        String username;
        String password;

        String externalUrl = System.getProperty(URL_PROPERTY);
        if (externalUrl != null && !externalUrl.isBlank()) {
            url = externalUrl;
            username = System.getProperty(USERNAME_PROPERTY, "enterprisedb");
            password = System.getProperty(PASSWORD_PROPERTY, "enterprisedb");
        } else {
            container = new PostgreSQLContainer(POSTGRES_IMAGE)
                    .withDatabaseName("edb")
                    .withUsername("edb")
                    .withPassword("edb");
            container.start();

            url = toEdbUrl(container.getJdbcUrl());
            username = container.getUsername();
            password = container.getPassword();
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", url);
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);

        // Only runtime configuration belongs here. The named datasources' db-kind -- and, for the xa
        // one, jdbc.transactions -- are build-time configuration and live in application.properties:
        // augmentation reads them before these values are visible, and without them Agroal never
        // builds a bean for the datasource.
        for (Map.Entry<String, String> datasource : NAMED_DATASOURCES.entrySet()) {
            String prefix = "quarkus.datasource.\"" + datasource.getKey() + "\".";
            config.put(prefix + "jdbc.url", withUrlParams(url, datasource.getValue()));
            config.put(prefix + "username", username);
            config.put(prefix + "password", password);
        }

        return config;
    }

    private static String withUrlParams(String jdbcUrl, String urlParams) {
        if (urlParams.isBlank()) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + urlParams;
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

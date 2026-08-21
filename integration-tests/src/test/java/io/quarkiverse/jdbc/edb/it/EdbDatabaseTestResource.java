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
 * A test may append driver connection properties to whichever URL is used through the
 * {@code urlParams} init arg, which is how {@link FlywayProbeTest} obtains
 * {@code changeServerName=true}.
 * <p>
 * The {@code secondaryDatasource} init arg additionally configures a named datasource of that name,
 * which is how {@link NamedDataSourceTest} verifies that the extension's build items apply to
 * datasources other than the default one. It points at the same database as the default datasource:
 * that is enough to prove the wiring, and keeps the test free of side effects on a real EPAS
 * instance. Note that a genuine two-phase commit would need two <em>distinct</em> databases, since
 * two datasources onto the same one may be collapsed into a single-phase commit.
 */
public class EdbDatabaseTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String URL_PROPERTY = "edb.jdbc.url";
    private static final String USERNAME_PROPERTY = "edb.jdbc.username";
    private static final String PASSWORD_PROPERTY = "edb.jdbc.password";

    private static final String URL_PARAMS_ARG = "urlParams";
    private static final String SECONDARY_DATASOURCE_ARG = "secondaryDatasource";

    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    private PostgreSQLContainer container;
    private String urlParams = "";
    private String secondaryDatasource = "";

    @Override
    public void init(Map<String, String> initArgs) {
        urlParams = initArgs.getOrDefault(URL_PARAMS_ARG, "");
        secondaryDatasource = initArgs.getOrDefault(SECONDARY_DATASOURCE_ARG, "");
    }

    @Override
    public Map<String, String> start() {
        String url;
        String username;
        String password;

        String externalUrl = System.getProperty(URL_PROPERTY);
        if (externalUrl != null && !externalUrl.isBlank()) {
            url = withUrlParams(externalUrl);
            username = System.getProperty(USERNAME_PROPERTY, "enterprisedb");
            password = System.getProperty(PASSWORD_PROPERTY, "enterprisedb");
        } else {
            container = new PostgreSQLContainer(POSTGRES_IMAGE)
                    .withDatabaseName("edb")
                    .withUsername("edb")
                    .withPassword("edb");
            container.start();

            url = withUrlParams(toEdbUrl(container.getJdbcUrl()));
            username = container.getUsername();
            password = container.getPassword();
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", url);
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);

        if (!secondaryDatasource.isBlank()) {
            // Only runtime configuration belongs here. The datasource's db-kind is build-time
            // configuration, read during augmentation before this resource's values are visible, so
            // it has to come from a test profile instead -- see NamedDataSourceTest. Without it
            // Agroal never builds a bean for the datasource and the injection point goes unsatisfied.
            String prefix = "quarkus.datasource.\"" + secondaryDatasource + "\".";
            config.put(prefix + "jdbc.url", url);
            config.put(prefix + "username", username);
            config.put(prefix + "password", password);
        }

        return config;
    }

    private String withUrlParams(String jdbcUrl) {
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

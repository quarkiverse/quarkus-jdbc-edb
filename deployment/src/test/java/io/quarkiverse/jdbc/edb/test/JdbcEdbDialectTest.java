package io.quarkiverse.jdbc.edb.test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import jakarta.inject.Inject;

import org.hibernate.SessionFactory;
import org.hibernate.dialect.PostgresPlusDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that db-kind {@code edb} selects Hibernate ORM's {@link PostgresPlusDialect}, the dialect
 * written for EDB Postgres Advanced Server, rather than plain {@code PostgreSQLDialect}.
 * <p>
 * The dialect is chosen at build time from the db-kind, so no database connection is needed --
 * schema management is disabled and the database version check is turned off to keep startup
 * offline.
 */
public class JdbcEdbDialectTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(DialectProbeEntity.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "edb")
            // Deliberately unreachable -- see the note in JdbcEdbXaDataSourceTest. Hibernate is
            // enabled here and will try to read JDBC metadata; that attempt fails fast and is only
            // logged as a warning, because the dialect itself comes from the build-time db-kind.
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:edb://localhost:1/edb")
            .overrideConfigKey("quarkus.datasource.username", "edb")
            .overrideConfigKey("quarkus.datasource.password", "edb")
            .overrideConfigKey("quarkus.datasource.jdbc.min-size", "0")
            .overrideConfigKey("quarkus.datasource.health.enabled", "false")
            .overrideConfigKey("quarkus.hibernate-orm.schema-management.strategy", "none")
            .overrideConfigKey("quarkus.hibernate-orm.database.version-check.enabled", "false");

    @Inject
    SessionFactory sessionFactory;

    @Test
    public void dialectIsPostgresPlus() {
        // unwrap rather than cast: the injected SessionFactory is a CDI client proxy, which
        // implements SessionFactory but not SessionFactoryImplementor.
        assertInstanceOf(PostgresPlusDialect.class,
                sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect());
    }
}

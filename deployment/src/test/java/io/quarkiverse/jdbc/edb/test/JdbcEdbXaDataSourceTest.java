package io.quarkiverse.jdbc.edb.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.edb.xa.PGXADataSource;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.exceptionsorter.PostgreSQLExceptionSorter;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies the XA DataSource class registered by {@code JdbcEdbProcessor} actually exists.
 * <p>
 * Quarkus resolves the driver class at build time -- Agroal selects
 * {@code JdbcDriverBuildItem.getDriverXAClass()} when {@code transactions=xa} -- and then calls
 * {@code Class.forName} on it when the datasource bean is created. A wrong class name therefore
 * fails here, without needing a reachable database: Agroal opens no connection at bean creation
 * because {@code min-size} is 0.
 */
public class JdbcEdbXaDataSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "edb")
            .overrideConfigKey("quarkus.datasource.jdbc.transactions", "xa")
            // Deliberately unreachable: this test must never contact a database. Port 1 refuses
            // immediately, so behaviour is identical everywhere and does not depend on whatever
            // happens to be listening on the usual EPAS port 5444.
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:edb://localhost:1/edb")
            .overrideConfigKey("quarkus.datasource.username", "edb")
            .overrideConfigKey("quarkus.datasource.password", "edb")
            .overrideConfigKey("quarkus.datasource.jdbc.min-size", "0")
            .overrideConfigKey("quarkus.datasource.health.enabled", "false")
            // Hibernate ORM is on the test classpath for JdbcEdbDialectTest, and would otherwise
            // bootstrap here too and attempt a real connection. This test is about build-item
            // wiring only and must stay offline.
            .overrideConfigKey("quarkus.hibernate-orm.enabled", "false");

    @Inject
    AgroalDataSource dataSource;

    @Test
    public void xaDataSourceClassIsLoadable() {
        assertNotNull(dataSource);
        assertEquals(PGXADataSource.class, dataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .connectionProviderClass());
    }

    @Test
    public void exceptionSorterIsConfigured() {
        // Proves EdbAgroalConnectionConfigurer was selected for db-kind "edb" rather than Agroal's
        // UnknownDbAgroalConnectionConfigurer, which leaves the sorter unset.
        assertInstanceOf(PostgreSQLExceptionSorter.class,
                dataSource.getConfiguration().connectionPoolConfiguration().exceptionSorter());
    }
}

package io.quarkiverse.jdbc.edb.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.edb.Driver;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.exceptionsorter.PostgreSQLExceptionSorter;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Verifies the extension applies to named datasources, not only the default one.
 * <p>
 * Every build item in {@code JdbcEdbProcessor} is keyed by the {@code edb} database kind rather than
 * by a datasource name, so this is expected to pass without production code changes. It is worth
 * asserting all the same: configuring several datasources is common, and Agroal resolves both the
 * driver class and the {@code AgroalConnectionConfigurer} separately for each one, so a regression
 * here would not be caught by any other test in this module.
 */
@QuarkusTest
@TestProfile(NamedDataSourceTest.SecondaryDataSource.class)
@WithTestResource(value = EdbDatabaseTestResource.class, initArgs = @ResourceArg(name = "secondaryDatasource", value = "secondary"))
public class NamedDataSourceTest {

    @Inject
    AgroalDataSource defaultDataSource;

    @Inject
    @DataSource("secondary")
    AgroalDataSource secondaryDataSource;

    @Test
    public void namedDataSourceIsDistinctFromTheDefaultOne() {
        assertNotSame(defaultDataSource, secondaryDataSource,
                "the named datasource should be its own pool, not an alias of the default one");
    }

    /**
     * Asserts on the resolved pool configuration rather than the connection's own class, because
     * Agroal hands out a wrapper rather than the driver's connection.
     */
    @Test
    public void namedDataSourceResolvesTheEdbDriver() {
        assertEquals(Driver.class, secondaryDataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .connectionProviderClass());
    }

    @Test
    public void namedDataSourceCanQuery() throws Exception {
        try (Connection connection = secondaryDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 1")) {
            assertTrue(rows.next(), "the named datasource should be able to run a query");
            assertEquals(1, rows.getInt(1));
        }
    }

    /**
     * Proves {@code EdbAgroalConnectionConfigurer} was selected for this datasource too. Without the
     * {@code @JdbcDriver("edb")} bean being resolved per datasource, Agroal would fall back to
     * {@code UnknownDbAgroalConnectionConfigurer}, which leaves the exception sorter unset.
     */
    @Test
    public void namedDataSourceUsesTheEdbExceptionSorter() {
        assertInstanceOf(PostgreSQLExceptionSorter.class,
                secondaryDataSource.getConfiguration().connectionPoolConfiguration().exceptionSorter());
    }

    /**
     * Declares the named datasource. A datasource's {@code db-kind} is build-time configuration, so
     * it has to be set here rather than by {@link EdbDatabaseTestResource}: Agroal decides which
     * datasource beans to create during augmentation, which happens before a test resource's values
     * are visible. The resource still supplies the URL and credentials, which are runtime config.
     */
    public static class SecondaryDataSource implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.\"secondary\".db-kind", "edb");
        }
    }
}

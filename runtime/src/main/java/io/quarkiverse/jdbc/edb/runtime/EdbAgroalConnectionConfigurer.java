package io.quarkiverse.jdbc.edb.runtime;

import java.util.Map;

import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.exceptionsorter.PostgreSQLExceptionSorter;
import io.quarkus.agroal.runtime.AgroalConnectionConfigurer;
import io.quarkus.agroal.runtime.JdbcDriver;

/**
 * Applies EDB-specific connection settings to the Agroal pool.
 * <p>
 * The EDB JDBC driver is a fork of pgjdbc and accepts the same connection properties and reports
 * the same SQL states, so the PostgreSQL property names and exception sorter apply unchanged.
 * Without this bean Agroal falls back to {@code UnknownDbAgroalConnectionConfigurer}, which leaves
 * the exception sorter unset.
 * <p>
 * {@code setKeepAlive} and {@code setReadTimeout} are deliberately <em>not</em> implemented. Both were
 * added to {@link AgroalConnectionConfigurer} after the Quarkus LTS this extension is built against,
 * so implementing them would make the extension impossible to compile there -- and building against
 * the LTS is what makes it discoverable to LTS users at all (see the note on {@code quarkus.version}
 * in the root pom). Both are {@code default} methods on newer cores, so nothing breaks at runtime:
 * Quarkus logs <em>"Agroal does not support KeepAlive for database kind: edb"</em>, and the
 * corresponding {@code quarkus.datasource.jdbc.enable-keep-alive} and {@code read-timeout} properties
 * are not applied for this database kind. That is a visible warning rather than a silent loss, which
 * is what makes the trade acceptable.
 */
@JdbcDriver("edb")
public class EdbAgroalConnectionConfigurer implements AgroalConnectionConfigurer {

    @Override
    public void disableSslSupport(String databaseKind, AgroalDataSourceConfigurationSupplier dataSourceConfiguration,
            Map<String, String> additionalProperties) {
        dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().jdbcProperty("sslmode",
                "disable");
    }

    @Override
    public void setExceptionSorter(String databaseKind, AgroalDataSourceConfigurationSupplier dataSourceConfiguration) {
        dataSourceConfiguration.connectionPoolConfiguration().exceptionSorter(new PostgreSQLExceptionSorter());
    }

}

package io.quarkiverse.jdbc.edb.runtime;

import java.time.Duration;
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
 * the exception sorter unset and cannot honour keep-alive or read-timeout configuration.
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

    @Override
    public void setKeepAlive(String databaseKind, AgroalDataSourceConfigurationSupplier dataSourceConfiguration,
            Map<String, String> additionalJdbcProperties, boolean keepAlive) {
        // The driver has its own keep-alive mechanism, enabled through a JDBC property.
        dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().jdbcProperty("tcpKeepAlive",
                Boolean.toString(keepAlive));
    }

    @Override
    public void setReadTimeout(String databaseKind, AgroalDataSourceConfigurationSupplier dataSourceConfiguration,
            Map<String, String> additionalJdbcProperties, Duration timeout) {
        // socketTimeout is expressed in seconds.
        dataSourceConfiguration.connectionPoolConfiguration().connectionFactoryConfiguration().jdbcProperty("socketTimeout",
                Long.toString(timeout.getSeconds()));
    }
}

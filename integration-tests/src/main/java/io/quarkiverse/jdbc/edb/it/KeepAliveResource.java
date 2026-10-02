package io.quarkiverse.jdbc.edb.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;

/**
 * Exposes the JDBC properties {@code EdbAgroalConnectionConfigurer} derives from
 * {@code quarkus.datasource.jdbc.enable-keep-alive} and {@code read-timeout}, which only exist on
 * Quarkus 3.40+. Routed through HTTP for the same reason as {@link NamedDataSourceResource}: so
 * {@link KeepAliveResourceIT} can re-run the same assertions against the native executable, where
 * injection is unavailable.
 */
@Path("/keep-alive")
@Produces(MediaType.TEXT_PLAIN)
public class KeepAliveResource {

    @Inject
    @DataSource("keepalive")
    AgroalDataSource keepAliveDataSource;

    /**
     * {@code tcpKeepAlive}, the driver property {@code setKeepAlive} sets. Asserting it here --
     * rather than just that the build compiles -- is what proves the configurer method actually ran,
     * since Quarkus silently skips it instead of failing when a database kind doesn't implement it.
     */
    @GET
    @Path("/tcp-keep-alive")
    public String tcpKeepAlive() {
        return keepAliveDataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .jdbcProperties()
                .getProperty("tcpKeepAlive");
    }

    /**
     * {@code socketTimeout}, the driver property {@code setReadTimeout} sets, as the whole-second
     * string the implementation converts {@link java.time.Duration} into.
     */
    @GET
    @Path("/socket-timeout")
    public String socketTimeout() {
        return keepAliveDataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .jdbcProperties()
                .getProperty("socketTimeout");
    }
}

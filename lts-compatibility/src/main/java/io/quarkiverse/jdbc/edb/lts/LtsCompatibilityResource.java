package io.quarkiverse.jdbc.edb.lts;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.agroal.api.AgroalDataSource;

/**
 * Reports how the extension configured this application's datasource.
 * <p>
 * Deliberately reads Agroal's configuration rather than opening a connection, so this check needs no
 * database. What it is verifying is that the extension's build steps and runtime code work against the
 * older Quarkus, not that the driver can talk to a server -- the latter is version-independent and is
 * covered by the main integration tests.
 */
@Path("/lts")
@Produces(MediaType.TEXT_PLAIN)
public class LtsCompatibilityResource {

    @Inject
    AgroalDataSource dataSource;

    /**
     * Proves {@code JdbcDriverBuildItem} resolved: Agroal took the driver class from the extension.
     */
    @GET
    @Path("/driver")
    public String driver() {
        return dataSource.getConfiguration()
                .connectionPoolConfiguration()
                .connectionFactoryConfiguration()
                .connectionProviderClass()
                .getName();
    }

    /**
     * Proves {@code EdbAgroalConnectionConfigurer} was resolved and ran. Without it Agroal falls back
     * to {@code UnknownDbAgroalConnectionConfigurer}, which leaves the exception sorter unset -- so
     * this is the assertion that covers the extension's runtime module, not just its build steps.
     */
    @GET
    @Path("/exception-sorter")
    public String exceptionSorter() {
        return dataSource.getConfiguration()
                .connectionPoolConfiguration()
                .exceptionSorter()
                .getClass()
                .getSimpleName();
    }
}

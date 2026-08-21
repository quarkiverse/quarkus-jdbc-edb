package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies the extension applies to named datasources, not only the default one.
 * <p>
 * Every build item in {@code JdbcEdbProcessor} is keyed by the {@code edb} database kind rather than
 * by a datasource name, so this is expected to pass without production code changes. It is worth
 * asserting all the same: configuring several datasources is common, and Agroal resolves both the
 * driver class and the {@code AgroalConnectionConfigurer} separately for each one, so a regression
 * here would not be caught by any other test in this module.
 *
 * @see NamedDataSourceResourceIT for the same assertions against the native executable
 */
@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class NamedDataSourceResourceTest {

    @Test
    public void namedDataSourceIsServedByTheEdbDriver() {
        given()
                .when().get("/named-datasource/connection-class")
                .then().statusCode(200)
                .body(startsWith("com.edb."));
    }

    @Test
    public void namedDataSourceUsesTheEdbExceptionSorter() {
        given()
                .when().get("/named-datasource/exception-sorter")
                .then().statusCode(200)
                .body(is("PostgreSQLExceptionSorter"));
    }

    @Test
    public void namedDataSourceCanQuery() {
        given()
                .when().get("/named-datasource/query")
                .then().statusCode(200)
                .body(is("1"));
    }

    @Test
    public void namedDataSourceIsItsOwnPool() {
        given()
                .when().get("/named-datasource/distinct")
                .then().statusCode(200)
                .body(is("true"));
    }
}

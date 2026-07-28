package io.quarkiverse.jdbc.edb.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithTestResource(EdbDatabaseTestResource.class)
public class ProductResourceTest {

    @Test
    public void dialectIsPostgresPlus() {
        given()
                .when().get("/product/dialect")
                .then().statusCode(200)
                .body(is("PostgresPlusDialect"));
    }

    @Test
    public void persistsAndReadsBackThroughSequenceGeneration() {
        String id = given()
                .when().post("/product?name=widget")
                .then().statusCode(200)
                .extract().asString();

        given()
                .when().get("/product/" + id)
                .then().statusCode(200)
                .body(is("widget"));
    }
}

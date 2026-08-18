package io.quarkiverse.jdbc.edb.it;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Uses SEQUENCE generation deliberately: sequence handling is one of the behaviours
 * {@code PostgresPlusDialect} overrides from {@code PostgreSQLDialect}, so this exercises the
 * dialect rather than merely asserting it was registered.
 */
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    public Long id;

    public String name;

    public Product() {
    }

    public Product(String name) {
        this.name = name;
    }
}

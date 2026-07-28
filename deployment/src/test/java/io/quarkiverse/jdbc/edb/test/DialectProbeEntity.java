package io.quarkiverse.jdbc.edb.test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

/**
 * Minimal entity so that Hibernate ORM bootstraps a persistence unit in
 * {@link JdbcEdbDialectTest}. Hibernate does not build a SessionFactory without at least one entity.
 */
@Entity
public class DialectProbeEntity {

    @Id
    @GeneratedValue
    public Long id;

    public String name;
}

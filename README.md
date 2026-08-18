# Quarkus JDBC EDB

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.edb/quarkus-jdbc-edb?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.edb/quarkus-jdbc-edb-parent)

A Quarkus extension providing JDBC connectivity to
[EDB Postgres Advanced Server](https://www.enterprisedb.com/products/edb-postgres-advanced-server)
(EPAS), including Hibernate ORM dialect selection and GraalVM native image support.

## Installation

```xml
<dependency>
    <groupId>io.quarkiverse.edb</groupId>
    <artifactId>quarkus-jdbc-edb</artifactId>
    <version>${quarkus-jdbc-edb.version}</version>
</dependency>
```

## Configuration

```properties
quarkus.datasource.db-kind=edb
quarkus.datasource.username=enterprisedb
quarkus.datasource.password=secret
quarkus.datasource.jdbc.url=jdbc:edb://localhost:5444/edb
```

## What's supported

- **Datasources** via Agroal, including XA (`quarkus.datasource.jdbc.transactions=xa`).
- **Hibernate ORM**, mapped to `org.hibernate.dialect.PostgresPlusDialect`. No need to set
  `quarkus.hibernate-orm.dialect` manually.
- **GraalVM native image**, with no additional configuration.
- **Kubernetes Service Binding** for binding type `edb`.

## Limitations

- **No Dev Services.** `quarkus.datasource.jdbc.url` must be set explicitly; Quarkus will not start a
  database container for `db-kind=edb`, because EPAS images require a subscription.
- **Flyway needs extra setup** — the `flyway-database-postgresql` artifact and `changeServerName=true`
  in the JDBC URL, since Flyway ships no `EnterpriseDB` database type. **Liquibase is unverified.**

See the [full guide](docs/modules/ROOT/pages/index.adoc) for the complete list, and for notes on
developing against community PostgreSQL.

## Licensing

The `com.enterprisedb:edb-jdbc` driver is published on Maven Central under a dual licence:
BSD-2-Clause and the [EDB Limited Use Software License Agreement](https://www.enterprisedb.com/limited-use-license).
Review both before deploying.

## Documentation

The full guide lives in the `docs/` directory of this repository, following [Antora's Standard File
and Directory Set](https://docs.antora.org/antora/2.3/standard-directories/).

To publish it, this repository needs to be added to the
[Quarkiverse Docs Antora playbook](https://github.com/quarkiverse/quarkiverse-docs/blob/main/antora-playbook.yml#L7)
([example PR](https://github.com/quarkiverse/quarkiverse-docs/pull/1)); it will then appear on
<https://docs.quarkiverse.io/>.

## Building from source

Build and test the extension with:

```bash
mvn clean install
```

Integration tests run against a community PostgreSQL container via Testcontainers, so a working
Docker environment is required. To run them against a real EPAS instance instead:

```bash
mvn clean install -Pepas -Dedb.jdbc.url=jdbc:edb://localhost:5444/edb \
    -Dedb.jdbc.username=enterprisedb -Dedb.jdbc.password=secret
```

Add `-Dnative` to either command to build and test the native image.

CREATE TABLE flyway_probe (
    id INTEGER PRIMARY KEY,
    note VARCHAR(64) NOT NULL
);

INSERT INTO flyway_probe (id, note) VALUES (1, 'migrated');

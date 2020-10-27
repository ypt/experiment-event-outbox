-- set up replication permissions
-- https://debezium.io/documentation/reference/1.0/connectors/postgresql.html#PostgreSQL-permissions
CREATE ROLE name REPLICATION LOGIN;

-- set up table
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  seq BIGSERIAL,
  ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload VARCHAR NOT NULL
);
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  payload VARCHAR NOT NULL
);
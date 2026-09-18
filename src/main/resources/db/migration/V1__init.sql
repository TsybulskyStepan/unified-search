CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE client (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name   text NOT NULL,
    last_name    text NOT NULL,
    email        citext NOT NULL,
    description  text,
    social_links text[] NOT NULL DEFAULT '{}',
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT client_email_uk UNIQUE (email)
);

CREATE TABLE document (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           uuid NOT NULL REFERENCES client (id) ON DELETE CASCADE,
    title               text NOT NULL,
    content             text NOT NULL,
    summary             text,
    summary_status      text NOT NULL DEFAULT 'none'
                        CHECK (summary_status IN ('none', 'pending', 'ready', 'failed')),
    summary_attempts    smallint NOT NULL DEFAULT 0,
    summary_lease_until timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX document_client_idx ON document (client_id, created_at);
CREATE INDEX document_pending_idx ON document (created_at) WHERE summary_status = 'pending';

CREATE TABLE document_chunk (
    document_id     uuid NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    embedding_model text NOT NULL,
    ordinal         int NOT NULL,
    start_offset    int NOT NULL,
    end_offset      int NOT NULL,
    embedding       vector(384) NOT NULL,
    PRIMARY KEY (document_id, embedding_model, ordinal)
);

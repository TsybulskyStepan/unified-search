-- Classify on ingest: the columns that hold a document's labels (ticket 15, system-design.md §2.1).
-- `document_type` and `purposes` are validated against the taxonomy in the application, not by
-- CHECK, so the vocabulary lives in one file (shared/taxonomy/taxonomy.yaml). `unknown` is a legal
-- type with no purposes.
ALTER TABLE document
    ADD COLUMN document_type         text   NOT NULL DEFAULT 'unknown',
    ADD COLUMN purposes              text[] NOT NULL DEFAULT '{}',
    ADD COLUMN classification_source text   NOT NULL DEFAULT 'unknown'
        CHECK (classification_source IN ('request', 'rule', 'llm', 'unknown')),
    ADD COLUMN taxonomy_version      int    NOT NULL DEFAULT 0,
    -- Human-readable form of the labels ("utility bill proof of address"), written by the service
    -- so the generated tsv column below can include it. array_to_string is STABLE and cannot appear
    -- in a generated column, which is why the text is materialised here instead of derived.
    ADD COLUMN label_text            text   NOT NULL DEFAULT '',
    -- One `english` configuration everywhere, so a query term and a label term with the same stem
    -- produce the same lexeme.
    ADD COLUMN tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', title),      'A') ||
        setweight(to_tsvector('english', label_text), 'A') ||
        setweight(to_tsvector('english', content),    'C')) STORED;

CREATE INDEX document_tsv_idx      ON document USING GIN (tsv);
CREATE INDEX document_type_idx     ON document (document_type);
CREATE INDEX document_purposes_idx ON document USING GIN (purposes);

-- Existing rows default to taxonomy_version = 0, below any real taxonomy version, so Reclassifier
-- (§3.4) picks every one of them up at the next startup and brings it up to date in batches.

-- The label chunk (ticket 15, §5.3): one extra vector per document, in the same embedding space as
-- the query, that says what the document is rather than what it says. `kind` joins the primary key
-- so a label row and a body row can share an ordinal without colliding.
ALTER TABLE document_chunk
    ADD COLUMN kind text NOT NULL DEFAULT 'body' CHECK (kind IN ('label', 'body'));
ALTER TABLE document_chunk DROP CONSTRAINT document_chunk_pkey;
ALTER TABLE document_chunk ADD PRIMARY KEY (document_id, embedding_model, kind, ordinal);

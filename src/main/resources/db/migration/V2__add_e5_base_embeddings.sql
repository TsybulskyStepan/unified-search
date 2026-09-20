ALTER TABLE document_chunk
    ADD COLUMN embedding_768 vector(768);

ALTER TABLE document_chunk
    ALTER COLUMN embedding DROP NOT NULL;

ALTER TABLE document_chunk
    ADD CONSTRAINT document_chunk_one_embedding_ck
    CHECK ((embedding IS NOT NULL) <> (embedding_768 IS NOT NULL));

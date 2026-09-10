-- pgvector is provisioned deliberately before this migration in hosted
-- environments. Local/test use the pgvector PostgreSQL 17 image.
create extension if not exists vector;

alter table search_documents
    add column embedding vector(1024),
    add column embedding_model varchar(120),
    add column embedded_content_hash varchar(64),
    add column embedded_at timestamptz;

alter table search_documents
    add constraint chk_search_document_embedding_projection check (
        (
            embedding is null
            and embedding_model is null
            and embedded_content_hash is null
            and embedded_at is null
        )
        or (
            embedding is not null
            and embedding_model is not null
            and embedded_content_hash ~ '^[a-f0-9]{64}$'
            and embedded_at is not null
        )
    );

-- Exact vector scans are intentional for the pilot corpus. This partial
-- B-tree index accelerates readiness/model filtering; add HNSW only after
-- measured EXPLAIN ANALYZE results justify its memory and rebuild cost.
create index idx_search_documents_embedding_ready
    on search_documents(active, embedding_model, publication_id)
    where embedding is not null;

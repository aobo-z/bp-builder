-- A test run keeps what it produced, so the Results page can show the whole
-- run: the canonical invoice and the documents (e.g. the full UBL XML). Step
-- request/response previews stay shortened. The input file is still not stored.
ALTER TABLE workflow_runs
    ADD COLUMN IF NOT EXISTS canonical_invoice JSONB,
    ADD COLUMN IF NOT EXISTS artifacts JSONB;

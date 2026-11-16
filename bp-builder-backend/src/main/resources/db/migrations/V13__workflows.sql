-- Workflows built in BP Builder, their steps and their runs.
--
-- Steps reference catalog operations rather than copying them, so a saved
-- workflow always uses the operations' current reviewed mappings and is
-- re-checked when it is opened. An operation used by a workflow cannot be
-- deleted from the catalog until the workflow stops using it.
CREATE TABLE IF NOT EXISTS workflows (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    -- The requirement in the user's own words, when the workflow was described in text.
    requirement_text TEXT,
    input_artifact_type VARCHAR(40) NOT NULL,
    goal_artifact_type VARCHAR(40) NOT NULL,
    -- The validator the user narrowed planning to; NULL when every validator was offered.
    validation_operation_id INTEGER REFERENCES api_operations(id) ON DELETE SET NULL,
    -- The language model's reading of requirement_text (assumptions, questions, model name).
    interpretation JSONB,
    -- READY, NEEDS_INPUT or NOT_SUPPORTED as of the last save; recomputed whenever the workflow is read.
    assessment_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_steps (
    id SERIAL PRIMARY KEY,
    workflow_id INTEGER NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL CHECK (sequence > 0),
    operation_id INTEGER NOT NULL REFERENCES api_operations(id) ON DELETE RESTRICT,
    UNIQUE (workflow_id, sequence)
);

-- One execution of a workflow. The input document itself is not stored, only
-- its type, name and size. Step results are stored after sensitive request
-- details (credentials, addresses) have been removed.
CREATE TABLE IF NOT EXISTS workflow_runs (
    id SERIAL PRIMARY KEY,
    workflow_id INTEGER NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    -- The chain as it ran, since the workflow's steps can change afterwards.
    operation_ids JSONB NOT NULL,
    input_artifact_type VARCHAR(40) NOT NULL,
    input_name VARCHAR(255),
    input_size_bytes BIGINT,
    -- A run status: COMPLETED, INPUT_REQUIRED, VALIDATION_FAILED or FAILED, or NOT_READY when the check stopped it.
    status VARCHAR(30) NOT NULL,
    message TEXT,
    steps JSONB,
    validation JSONB,
    communication JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_steps_operation ON workflow_steps(operation_id);
CREATE INDEX IF NOT EXISTS idx_workflow_runs_workflow ON workflow_runs(workflow_id, started_at DESC);

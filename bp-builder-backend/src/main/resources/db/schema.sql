CREATE TABLE IF NOT EXISTS apis (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    version VARCHAR(100),
    base_url VARCHAR(512),
    auth_method VARCHAR(100),
    category VARCHAR(50) CHECK (category IN ('transformation', 'validation', 'communication')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    openapi_document JSONB,
    spec_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS api_operations (
    id SERIAL PRIMARY KEY,
    api_id INTEGER NOT NULL REFERENCES apis(id) ON DELETE CASCADE,
    operation_id VARCHAR(255),
    method VARCHAR(10) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    summary TEXT,
    description TEXT,
    operation_category VARCHAR(50) NOT NULL,
    operation_type VARCHAR(50),
    execution_type VARCHAR(20) NOT NULL DEFAULT 'HTTP',
    input_artifact_type VARCHAR(50),
    output_artifact_type VARCHAR(50),
    input_artifact_path VARCHAR(1024),
    output_artifact_path VARCHAR(1024),
    endpoint_url_ref VARCHAR(255),
    credential_ref VARCHAR(255),
    timeout_seconds INTEGER CHECK (timeout_seconds IS NULL OR timeout_seconds > 0),
    verification_status VARCHAR(30) NOT NULL DEFAULT 'UNREVIEWED',
    contract_source VARCHAR(20) NOT NULL DEFAULT 'OPENAPI',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    outcome_rule JSONB,
    request_media_types JSONB,
    response_media_types JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (api_id, method, path)
);

CREATE INDEX IF NOT EXISTS idx_api_operations_api_id ON api_operations(api_id);
CREATE INDEX IF NOT EXISTS idx_api_operations_category ON api_operations(operation_category);

CREATE TABLE IF NOT EXISTS canonical_models (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    standard VARCHAR(100) NOT NULL,
    schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (name, version)
);

CREATE TABLE IF NOT EXISTS canonical_fields (
    id SERIAL PRIMARY KEY,
    canonical_model_id INTEGER NOT NULL REFERENCES canonical_models(id) ON DELETE CASCADE,
    field_path VARCHAR(512) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    cardinality VARCHAR(20) NOT NULL,
    ubl_xpath TEXT,
    xsd_required BOOLEAN NOT NULL DEFAULT FALSE,
    constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (canonical_model_id, field_path)
);

CREATE TABLE IF NOT EXISTS operation_field_mappings (
    id SERIAL PRIMARY KEY,
    operation_id INTEGER NOT NULL REFERENCES api_operations(id) ON DELETE CASCADE,
    canonical_field_id INTEGER REFERENCES canonical_fields(id) ON DELETE CASCADE,
    direction VARCHAR(30) NOT NULL CHECK (direction IN ('OUTPUT_TO_CANONICAL', 'CANONICAL_TO_INPUT')),
    operation_field_path VARCHAR(1024) NOT NULL,
    transform_type VARCHAR(40) NOT NULL DEFAULT 'DIRECT',
    transform_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    required_by_operation BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (review_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    reviewed_at TIMESTAMP,
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_canonical_fields_model ON canonical_fields(canonical_model_id);
CREATE INDEX IF NOT EXISTS idx_operation_mappings_operation ON operation_field_mappings(operation_id);
CREATE INDEX IF NOT EXISTS idx_operation_mappings_canonical_field ON operation_field_mappings(canonical_field_id);

-- Safe additions for databases created before the canonical mapping design.
ALTER TABLE apis ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE apis ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS operation_type VARCHAR(50);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS execution_type VARCHAR(20) NOT NULL DEFAULT 'HTTP';
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS input_artifact_type VARCHAR(50);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS output_artifact_type VARCHAR(50);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS input_artifact_path VARCHAR(1024);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS output_artifact_path VARCHAR(1024);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS endpoint_url_ref VARCHAR(255);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS credential_ref VARCHAR(255);
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER;
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS verification_status VARCHAR(30) NOT NULL DEFAULT 'UNREVIEWED';
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS contract_source VARCHAR(20) NOT NULL DEFAULT 'OPENAPI';
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS outcome_rule JSONB;

CREATE TABLE IF NOT EXISTS workflows (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    -- The requirement in the user's own words, when the workflow was described in text.
    requirement_text TEXT,
    input_artifact_type VARCHAR(40) NOT NULL,
    goal_artifact_type VARCHAR(40) NOT NULL,
    -- The validator the user narrowed planning to; NULL when every validator was offered.
    validation_operation_id INTEGER REFERENCES api_operations(id) ON DELETE SET NULL,
    -- The language model's reading of requirement_text (questions, model name).
    interpretation JSONB,
    -- READY or NOT_SUPPORTED as of the last save; recomputed whenever the workflow is read.
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
    -- COMPLETED, INVOICE_INVALID (the validator's verdict, not a failure), INPUT_REQUIRED or FAILED.
    -- A workflow that is not READY is never run.
    status VARCHAR(30) NOT NULL,
    message TEXT,
    steps JSONB,
    validation JSONB,
    communication JSONB,
    -- What the run produced: the canonical invoice and the documents by artifact type (the input PDF only as a note).
    canonical_invoice JSONB,
    artifacts JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_steps_operation ON workflow_steps(operation_id);
CREATE INDEX IF NOT EXISTS idx_workflow_runs_workflow ON workflow_runs(workflow_id, started_at DESC);

-- A saved workflow deployed as its own AWS Lambda function (V17).
CREATE TABLE IF NOT EXISTS workflow_deployments (
    id SERIAL PRIMARY KEY,
    workflow_id INTEGER NOT NULL UNIQUE REFERENCES workflows(id) ON DELETE CASCADE,
    function_name VARCHAR(64) NOT NULL,
    function_url TEXT NOT NULL,
    -- The Step Functions state machine connecting the workflow's component functions (V18).
    state_machine_arn TEXT,
    region VARCHAR(30) NOT NULL,
    -- SHA-256 of the packaged definition; a different current hash means the deployment is out of date.
    definition_hash VARCHAR(64) NOT NULL,
    deployed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

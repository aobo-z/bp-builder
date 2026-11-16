-- A saved workflow can be deployed as its own AWS Lambda function. The
-- function's code carries the workflow definition, and its function URL takes
-- {"input": ...} in the workflow's input type. Deleting the workflow deletes
-- the function through the backend before this row cascades away.
CREATE TABLE IF NOT EXISTS workflow_deployments (
    id SERIAL PRIMARY KEY,
    workflow_id INTEGER NOT NULL UNIQUE REFERENCES workflows(id) ON DELETE CASCADE,
    function_name VARCHAR(64) NOT NULL,
    function_url TEXT NOT NULL,
    region VARCHAR(30) NOT NULL,
    -- SHA-256 of the packaged definition; a different current hash means the deployment is out of date.
    definition_hash VARCHAR(64) NOT NULL,
    deployed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

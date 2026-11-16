-- Columns left over from the retired OpenAPI import and pair-checking design.
-- Operations are now reviewed by hand and the workflow runtime reads none of
-- them: the contract lives in the artifact types, the field mappings and the
-- outcome rule instead.
ALTER TABLE api_operations
    DROP COLUMN IF EXISTS pipeline_role,
    DROP COLUMN IF EXISTS request_schema,
    DROP COLUMN IF EXISTS responses,
    DROP COLUMN IF EXISTS required_parameters,
    DROP COLUMN IF EXISTS openapi_tags,
    DROP COLUMN IF EXISTS parse_status;

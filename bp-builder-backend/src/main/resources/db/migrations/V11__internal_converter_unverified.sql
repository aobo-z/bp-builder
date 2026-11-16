-- The built-in JSON-to-UBL converter is a port of the Amit converter and does
-- the same job, so it has no verification of its own. Workflows should call
-- the Amit operation; this row stays in the catalog marked as unverified.
-- RUNNABLE_PROFILE_UNVERIFIED is the value InternalTransformOperationSeeder
-- writes at startup, so the two no longer disagree.
UPDATE api_operations operation
SET verification_status = 'RUNNABLE_PROFILE_UNVERIFIED',
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'BP Builder Internal Tools'
  AND operation.operation_id = 'convertJsonToUbl';

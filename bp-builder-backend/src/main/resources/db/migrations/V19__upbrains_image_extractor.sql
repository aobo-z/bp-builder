-- A second UpBrains webhook runs the same Standard Invoice Extractor on a PNG
-- or JPG invoice. Observed on 2026-11-02: it takes {"fileBase64": ...} like the
-- PDF webhook and answers with the same response shape, so the operation reuses
-- the PDF operation's reviewed output mappings. No webhook URL is stored here;
-- it is read from UPBRAINS_IMAGE_EXTRACT_URL.
UPDATE apis
SET description = 'Observed UpBrains workflows that extract structured invoice fields from a Base64 encoded PDF or image.',
    updated_at = NOW()
WHERE name = 'UpBrains Invoice Extractor';

WITH api AS (SELECT id FROM apis WHERE name = 'UpBrains Invoice Extractor')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, endpoint_url_ref, credential_ref,
     timeout_seconds, verification_status, contract_source, is_active,
     request_media_types, response_media_types)
SELECT id, 'extractInvoiceFromImage', 'POST', '/v1/webhooks/configured-at-runtime/image/sync',
       'Extract invoice fields from an image',
       'Reads a PNG or JPG invoice (Base64 content) and returns the extracted invoice fields, which BP Builder maps onto the canonical invoice.',
       'Create or Transform', 'EXTRACT_INVOICE', 'HTTP', 'IMAGE_DOCUMENT', 'CANONICAL_INVOICE',
       '$.fileBase64', '$.extractor_result.documents[0]',
       'UPBRAINS_IMAGE_EXTRACT_URL', 'UPBRAINS_IMAGE_EXTRACT_URL', 60,
       'VERIFIED', 'OBSERVED', TRUE,
       '["application/json"]'::jsonb,
       '{"200":["application/json"]}'::jsonb
FROM api
ON CONFLICT (api_id, method, path) DO UPDATE
SET operation_id = EXCLUDED.operation_id,
    summary = EXCLUDED.summary,
    description = EXCLUDED.description,
    input_artifact_type = EXCLUDED.input_artifact_type,
    output_artifact_type = EXCLUDED.output_artifact_type,
    endpoint_url_ref = EXCLUDED.endpoint_url_ref,
    credential_ref = EXCLUDED.credential_ref,
    verification_status = EXCLUDED.verification_status,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- Same response shape, so the same output mappings (including the V16 tax id
-- and purchase order mappings).
WITH pdf AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'UpBrains Invoice Extractor'
      AND operation.operation_id = 'extractInvoiceFromPdf'
), image AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'UpBrains Invoice Extractor'
      AND operation.operation_id = 'extractInvoiceFromImage'
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT image.id, mapping.canonical_field_id, mapping.direction, mapping.operation_field_path,
       mapping.transform_type, mapping.transform_config, mapping.required_by_operation,
       mapping.priority, mapping.review_status, NOW(), mapping.note
FROM operation_field_mappings mapping
JOIN pdf ON pdf.id = mapping.operation_id
CROSS JOIN image
WHERE mapping.direction = 'OUTPUT_TO_CANONICAL'
  AND NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = image.id
      AND existing.direction = 'OUTPUT_TO_CANONICAL'
      AND existing.operation_field_path = mapping.operation_field_path
);

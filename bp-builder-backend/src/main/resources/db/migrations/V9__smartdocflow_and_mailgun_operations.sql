-- Publisher-reviewed operations for the two selected SmartDocFlow rule packs
-- and Mailgun delivery. Secrets and account-specific values remain environment
-- variables; this migration stores only their names.

-- Validation variants are uniquely identified by operation_id. The former
-- validation_rule_set duplicated that identity and was removed from the live
-- catalog before this migration.
ALTER TABLE api_operations DROP COLUMN IF EXISTS validation_rule_set;

INSERT INTO apis (name, description, version, base_url, auth_method, category, is_active)
VALUES
('SmartDocFlow eInvoicing Services API',
 'Electronic-invoice validation service. The catalog currently exposes EN16931 UBL and Peppol PINT A-NZ Billing variants.',
 '2.0.0', 'https://xml.staging.smartdocflow.com/v1', 'X_API_KEY', 'validation', TRUE),
('Mailgun Email API',
 'Email delivery service used to send a validated UBL XML invoice as an attachment.',
 'v3', 'https://api.mailgun.net', 'BASIC_API_KEY', 'communication', TRUE)
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    version = EXCLUDED.version,
    base_url = EXCLUDED.base_url,
    auth_method = EXCLUDED.auth_method,
    category = EXCLUDED.category,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'SmartDocFlow eInvoicing Services API')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, credential_ref, timeout_seconds,
     verification_status, contract_source, is_active,
     outcome_rule, request_media_types, response_media_types)
SELECT id, 'validateUblEn16931', 'POST', '/schematron/ubl',
       'Validate a UBL invoice against EN16931',
       'Runs the SmartDocFlow ubl-en16931 Schematron profile. HTTP 400 is a business validation result and is interpreted from the JSON report.',
       'validation', 'VALIDATE_UBL', 'HTTP', 'UBL_XML', 'VALIDATION_REPORT',
       NULL, '$', 'SMARTDOCFLOW_API_KEY', 120,
       'VERIFIED_STAGING_2026_10_10', 'OPENAPI', TRUE,
       '{"passedPath":"$.status","passedValues":["success"],"errorsPath":"$.details.failures[*].message","warningsPath":null,"referencePath":null}'::jsonb,
       '["application/json"]'::jsonb,
       '{"200":["application/json"],"400":["application/json"]}'::jsonb
FROM api
ON CONFLICT (api_id, method, path) DO UPDATE
SET operation_id = EXCLUDED.operation_id,
    summary = EXCLUDED.summary,
    description = EXCLUDED.description,
    operation_category = EXCLUDED.operation_category,
    operation_type = EXCLUDED.operation_type,
    execution_type = EXCLUDED.execution_type,
    input_artifact_type = EXCLUDED.input_artifact_type,
    output_artifact_type = EXCLUDED.output_artifact_type,
    input_artifact_path = EXCLUDED.input_artifact_path,
    output_artifact_path = EXCLUDED.output_artifact_path,
    credential_ref = EXCLUDED.credential_ref,
    timeout_seconds = EXCLUDED.timeout_seconds,
    verification_status = EXCLUDED.verification_status,
    contract_source = EXCLUDED.contract_source,
    is_active = EXCLUDED.is_active,
    outcome_rule = EXCLUDED.outcome_rule,
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'SmartDocFlow eInvoicing Services API')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, credential_ref, timeout_seconds,
     verification_status, contract_source, is_active,
     outcome_rule, request_media_types, response_media_types)
SELECT id, 'validateUblPintAunzBilling', 'POST', '/peppol/aunz/validate/ubl',
       'Validate a UBL invoice against Peppol PINT A-NZ Billing',
       'Runs UBL XSD, PINT shared and A-NZ jurisdiction-aligned checks as one external validation operation.',
       'validation', 'VALIDATE_UBL', 'HTTP', 'UBL_XML', 'VALIDATION_REPORT',
       NULL, '$', 'SMARTDOCFLOW_API_KEY', 120,
       'VERIFIED_STAGING_2026_10_10', 'OPENAPI', TRUE,
       '{"passedPath":"$.status","passedValues":["success"],"errorsPath":"$.details.steps[*].details.failures[*].message","warningsPath":null,"referencePath":null}'::jsonb,
       '["application/json"]'::jsonb,
       '{"200":["application/json"],"400":["application/json"]}'::jsonb
FROM api
ON CONFLICT (api_id, method, path) DO UPDATE
SET operation_id = EXCLUDED.operation_id,
    summary = EXCLUDED.summary,
    description = EXCLUDED.description,
    operation_category = EXCLUDED.operation_category,
    operation_type = EXCLUDED.operation_type,
    execution_type = EXCLUDED.execution_type,
    input_artifact_type = EXCLUDED.input_artifact_type,
    output_artifact_type = EXCLUDED.output_artifact_type,
    input_artifact_path = EXCLUDED.input_artifact_path,
    output_artifact_path = EXCLUDED.output_artifact_path,
    credential_ref = EXCLUDED.credential_ref,
    timeout_seconds = EXCLUDED.timeout_seconds,
    verification_status = EXCLUDED.verification_status,
    contract_source = EXCLUDED.contract_source,
    is_active = EXCLUDED.is_active,
    outcome_rule = EXCLUDED.outcome_rule,
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'Mailgun Email API')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, endpoint_url_ref, credential_ref,
     timeout_seconds, verification_status, contract_source, is_active,
     outcome_rule, request_media_types, response_media_types)
SELECT id, 'sendValidatedUblInvoice', 'POST', '/v3/{domain}/messages',
       'Send a validated UBL invoice by email',
       'Uses the complete Mailgun messages URL from MAILGUN_MESSAGES_URL and sends the UBL XML as a multipart attachment.',
       'communication', 'SEND_INVOICE', 'HTTP', 'UBL_XML', 'DELIVERY_RECEIPT',
       NULL, '$', 'MAILGUN_MESSAGES_URL', 'MAILGUN_API_KEY', 60,
       'CONTRACT_REVIEWED_UNTESTED', 'VENDOR_DOCUMENTATION', TRUE,
       '{"passedPath":"$.message","passedValues":["Queued. Thank you."],"errorsPath":"$.message","warningsPath":null,"referencePath":"$.id"}'::jsonb,
       '["multipart/form-data"]'::jsonb,
       '{"200":["application/json"]}'::jsonb
FROM api
ON CONFLICT (api_id, method, path) DO UPDATE
SET operation_id = EXCLUDED.operation_id,
    summary = EXCLUDED.summary,
    description = EXCLUDED.description,
    operation_category = EXCLUDED.operation_category,
    operation_type = EXCLUDED.operation_type,
    execution_type = EXCLUDED.execution_type,
    input_artifact_type = EXCLUDED.input_artifact_type,
    output_artifact_type = EXCLUDED.output_artifact_type,
    input_artifact_path = EXCLUDED.input_artifact_path,
    output_artifact_path = EXCLUDED.output_artifact_path,
    endpoint_url_ref = EXCLUDED.endpoint_url_ref,
    credential_ref = EXCLUDED.credential_ref,
    timeout_seconds = EXCLUDED.timeout_seconds,
    verification_status = EXCLUDED.verification_status,
    contract_source = EXCLUDED.contract_source,
    is_active = EXCLUDED.is_active,
    outcome_rule = EXCLUDED.outcome_rule,
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

-- SmartDocFlow requests: the UBL artifact is Base64 encoded once; the rule
-- selection values are reviewed constants owned by each logical operation.
WITH operations AS (
    SELECT operation.id, operation.operation_id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'SmartDocFlow eInvoicing Services API'
      AND operation.operation_id IN ('validateUblEn16931', 'validateUblPintAunzBilling')
), mappings(operation_id, operation_path, transform_type, transform_config, note) AS (
    SELECT id, '$.filename', 'CONSTANT', '{"value":"invoice.xml"}'::jsonb,
           'Stable filename for the inline UBL document.' FROM operations
    UNION ALL
    SELECT id, '$.content', 'ARTIFACT', '{"artifact":"UBL_XML","encodings":["BASE64"]}'::jsonb,
           'Base64 inline file contract used by SmartDocFlow.' FROM operations
    UNION ALL
    SELECT id, '$.profile', 'CONSTANT', '{"value":"ubl-en16931"}'::jsonb,
           'Reviewed EN16931 UBL Schematron profile.' FROM operations WHERE operation_id = 'validateUblEn16931'
    UNION ALL
    SELECT id, '$.pack', 'CONSTANT', '{"value":"billing"}'::jsonb,
           'Reviewed Peppol PINT A-NZ billing pack.' FROM operations WHERE operation_id = 'validateUblPintAunzBilling'
    UNION ALL
    SELECT id, '$.checks', 'CONSTANT', '{"value":["xsd","pint-shared","pint-aligned"]}'::jsonb,
           'Run the complete PINT A-NZ validation composition.' FROM operations WHERE operation_id = 'validateUblPintAunzBilling'
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operation_id, NULL, 'CANONICAL_TO_INPUT', operation_path, transform_type,
       transform_config, TRUE, 0, 'APPROVED', NOW(), note
FROM mappings
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = mappings.operation_id
      AND existing.direction = 'CANONICAL_TO_INPUT'
      AND existing.operation_field_path = mappings.operation_path
);

-- Mailgun request: addressing remains deployment configuration; only the
-- subject/body and attachment contract are catalog constants.
WITH operation AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'Mailgun Email API'
      AND operation.operation_id = 'sendValidatedUblInvoice'
), mappings(operation_path, transform_type, transform_config, note) AS (
    VALUES
    ('$.from', 'ENVIRONMENT', '{"name":"MAILGUN_FROM"}'::jsonb, 'Configured sender address.'),
    ('$.to', 'ENVIRONMENT', '{"name":"MAILGUN_TO"}'::jsonb, 'Configured demonstration recipient.'),
    ('$.subject', 'CONSTANT', '{"value":"Validated UBL invoice"}'::jsonb, 'Email subject.'),
    ('$.text', 'CONSTANT', '{"value":"The validated UBL invoice is attached."}'::jsonb, 'Email text body.'),
    ('$.attachment.filename', 'CONSTANT', '{"value":"invoice.xml"}'::jsonb, 'Attachment filename.'),
    ('$.attachment.contentType', 'CONSTANT', '{"value":"application/xml"}'::jsonb, 'Attachment media type.'),
    ('$.attachment.content', 'ARTIFACT', '{"artifact":"UBL_XML"}'::jsonb, 'Validated UBL XML attachment content.')
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operation.id, NULL, 'CANONICAL_TO_INPUT', mappings.operation_path, mappings.transform_type,
       mappings.transform_config, TRUE, 0, 'APPROVED', NOW(), mappings.note
FROM operation CROSS JOIN mappings
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = operation.id
      AND existing.direction = 'CANONICAL_TO_INPUT'
      AND existing.operation_field_path = mappings.operation_path
);

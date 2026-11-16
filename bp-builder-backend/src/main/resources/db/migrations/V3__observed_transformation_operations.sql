-- Registers the small, manually reviewed transformation catalog used by the
-- project. No secret webhook URL is stored in this file.
INSERT INTO apis (name, description, version, base_url, auth_method, category, is_active)
VALUES
('UpBrains Invoice Extractor',
 'Observed UpBrains workflow that extracts structured invoice fields from a Base64 encoded PDF.',
 'observed-2026-10-02', 'https://agents.upbrains.ai', 'endpoint URL is secret', 'transformation', TRUE),
('Amit Invoice Conversion API',
 'Local third-party API for converting invoice-shaped JSON to XML. Current checked output is not UBL 2.1 XSD-valid.',
 'local-source', 'http://localhost:3000', 'none', 'transformation', TRUE),
('BP Builder Internal Tools',
 'Operations exposed by BP Builder over HTTP and represented in the catalog like external operations.',
 'local-source', 'http://localhost:8081', 'none', 'transformation', TRUE)
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    version = EXCLUDED.version,
    base_url = EXCLUDED.base_url,
    auth_method = EXCLUDED.auth_method,
    category = EXCLUDED.category,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'UpBrains Invoice Extractor')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, endpoint_url_ref, credential_ref,
     timeout_seconds, verification_status, contract_source, is_active,
     request_media_types, response_media_types)
SELECT id, 'extractInvoiceFromPdf', 'POST', '/v1/webhooks/configured-at-runtime/sync',
       'Extract invoice fields from a PDF',
       'Accepts JSON containing fileName and raw PDF Base64. The full endpoint URL is read from UPBRAINS_INVOICE_EXTRACT_URL.',
       'Create or Transform', 'EXTRACT_INVOICE', 'HTTP', 'PDF_DOCUMENT', 'CANONICAL_INVOICE',
       '$.fileBase64', '$.extractor_result.documents[0]',
       'UPBRAINS_INVOICE_EXTRACT_URL', 'UPBRAINS_INVOICE_EXTRACT_URL', 60,
       'VERIFIED_OBSERVED', 'OBSERVED', TRUE,
       '["application/json"]'::jsonb,
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
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'Amit Invoice Conversion API')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, timeout_seconds, verification_status,
     contract_source, is_active, request_media_types, response_media_types)
SELECT id, 'convertInvoiceJsonToXml', 'POST', '/api/convert',
       'Convert invoice JSON to UBL XML candidate',
       'Runs locally. Endpoint behavior is verified, but checked output currently fails the UBL 2.1 XSD.',
       'Create or Transform', 'CONVERT_TO_UBL', 'HTTP', 'CANONICAL_INVOICE', 'UBL_XML',
       '$.invoiceData', '$.xml', 30, 'RUNNABLE_XSD_INVALID', 'SOURCE_CODE', TRUE,
       '["application/json"]'::jsonb,
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
    timeout_seconds = EXCLUDED.timeout_seconds,
    verification_status = EXCLUDED.verification_status,
    contract_source = EXCLUDED.contract_source,
    is_active = EXCLUDED.is_active,
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

WITH api AS (SELECT id FROM apis WHERE name = 'BP Builder Internal Tools')
INSERT INTO api_operations
    (api_id, operation_id, method, path, summary, description, operation_category,
     operation_type, execution_type, input_artifact_type, output_artifact_type,
     input_artifact_path, output_artifact_path, timeout_seconds, verification_status,
     contract_source, is_active, request_media_types, response_media_types)
SELECT id, 'convertJsonToUbl', 'POST', '/api/transform/json-to-ubl',
       'Convert invoice JSON to UBL 2.1 XML',
       'BP Builder HTTP operation backed by the existing Java transformation service.',
       'Create or Transform', 'CONVERT_TO_UBL', 'HTTP', 'CANONICAL_INVOICE', 'UBL_XML',
       '$.invoice', '$.xml', 30, 'RUNNABLE_PROFILE_UNVERIFIED', 'SOURCE_CODE', TRUE,
       '["application/json"]'::jsonb,
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
    timeout_seconds = EXCLUDED.timeout_seconds,
    verification_status = EXCLUDED.verification_status,
    contract_source = EXCLUDED.contract_source,
    is_active = EXCLUDED.is_active,
    request_media_types = EXCLUDED.request_media_types,
    response_media_types = EXCLUDED.response_media_types,
    updated_at = NOW();

-- UpBrains response -> canonical invoice fields. Duplicate field mappings are
-- allowed for reviewed fallback paths (seller city falls back to suburb).
WITH operation AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'UpBrains Invoice Extractor'
      AND operation.operation_id = 'extractInvoiceFromPdf'
), model AS (
    SELECT id FROM canonical_models
    WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
), mappings(field_path, operation_path, transform_type, transform_config, priority, note) AS (
    VALUES
    ('invoice.id','$.extractor_result.documents[0].fields.InvoiceId.content','DIRECT','{}'::jsonb,0,NULL),
    ('invoice.issue_date','$.extractor_result.documents[0].fields.InvoiceDate.content','DATE_FORMAT','{"input":"YYYY-MM-DD","output":"YYYY-MM-DD"}'::jsonb,0,NULL),
    ('invoice.due_date','$.extractor_result.documents[0].fields.DueDate.content','DATE_FORMAT','{"input":"YYYY-MM-DD","output":"YYYY-MM-DD"}'::jsonb,0,NULL),
    ('invoice.currency_code','$.extractor_result.documents[0].fields.InvoiceTotal.details.code','DIRECT','{}'::jsonb,0,NULL),
    ('seller.name','$.extractor_result.documents[0].fields.VendorName.content','DIRECT','{}'::jsonb,0,NULL),
    ('seller.address.street','$.extractor_result.documents[0].fields.VendorAddress.details.street_address','DIRECT','{}'::jsonb,0,NULL),
    ('seller.address.city','$.extractor_result.documents[0].fields.VendorAddress.details.city','FIRST_NON_NULL','{}'::jsonb,0,'Primary city path.'),
    ('seller.address.city','$.extractor_result.documents[0].fields.VendorAddress.details.suburb','FIRST_NON_NULL','{}'::jsonb,1,'Fallback because the observed Sydney sample placed the locality in suburb.'),
    ('seller.address.state','$.extractor_result.documents[0].fields.VendorAddress.details.state','DIRECT','{}'::jsonb,0,NULL),
    ('seller.address.postal_code','$.extractor_result.documents[0].fields.VendorAddress.details.postal_code','DIRECT','{}'::jsonb,0,NULL),
    ('seller.address.country_code','$.extractor_result.documents[0].fields.VendorAddress.details.country_region','COUNTRY_NAME_TO_ISO2','{}'::jsonb,0,NULL),
    ('buyer.name','$.extractor_result.documents[0].fields.CustomerName.content','DIRECT','{}'::jsonb,0,NULL),
    ('buyer.address.street','$.extractor_result.documents[0].fields.BillingAddress.details.street_address','DIRECT','{}'::jsonb,0,NULL),
    ('buyer.address.city','$.extractor_result.documents[0].fields.BillingAddress.details.city','FIRST_NON_NULL','{}'::jsonb,0,NULL),
    ('buyer.address.city','$.extractor_result.documents[0].fields.BillingAddress.details.suburb','FIRST_NON_NULL','{}'::jsonb,1,NULL),
    ('buyer.address.state','$.extractor_result.documents[0].fields.BillingAddress.details.state','DIRECT','{}'::jsonb,0,NULL),
    ('buyer.address.postal_code','$.extractor_result.documents[0].fields.BillingAddress.details.postal_code','DIRECT','{}'::jsonb,0,NULL),
    ('buyer.address.country_code','$.extractor_result.documents[0].fields.BillingAddress.details.country_region','COUNTRY_NAME_TO_ISO2','{}'::jsonb,0,NULL),
    ('totals.line_extension_amount','$.extractor_result.documents[0].fields.SubTotal.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('totals.tax_exclusive_amount','$.extractor_result.documents[0].fields.SubTotal.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('totals.tax_amount','$.extractor_result.documents[0].fields.TotalTax.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('totals.tax_inclusive_amount','$.extractor_result.documents[0].fields.InvoiceTotal.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('totals.payable_amount','$.extractor_result.documents[0].fields.AmountDue.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('lines[*].id','$.extractor_result.documents[0].items[*]._index','DIRECT','{}'::jsonb,0,NULL),
    ('lines[*].description','$.extractor_result.documents[0].items[*].Description.content','DIRECT','{}'::jsonb,0,NULL),
    ('lines[*].quantity','$.extractor_result.documents[0].items[*].Quantity.content','DECIMAL','{}'::jsonb,0,NULL),
    ('lines[*].net_amount','$.extractor_result.documents[0].items[*].Amount.details.amount','DECIMAL','{}'::jsonb,0,NULL),
    ('lines[*].unit_price','$.extractor_result.documents[0].items[*].UnitPrice.details.amount','DECIMAL','{}'::jsonb,0,NULL)
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operation.id, field.id, 'OUTPUT_TO_CANONICAL', mappings.operation_path,
       mappings.transform_type, mappings.transform_config, FALSE, mappings.priority,
       'APPROVED', NOW(), mappings.note
FROM operation CROSS JOIN model
JOIN canonical_fields field ON field.canonical_model_id = model.id
JOIN mappings ON mappings.field_path = field.field_path
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = operation.id
      AND existing.direction = 'OUTPUT_TO_CANONICAL'
      AND existing.operation_field_path = mappings.operation_path
);
-- Canonical invoice -> input contracts for both JSON-to-XML operations.
WITH operations AS (
    SELECT operation.id,
           CASE api.name
               WHEN 'Amit Invoice Conversion API' THEN '$.invoiceData'
               ELSE '$.invoice'
           END AS root
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE (api.name = 'Amit Invoice Conversion API' AND operation.operation_id = 'convertInvoiceJsonToXml')
       OR (api.name = 'BP Builder Internal Tools' AND operation.operation_id = 'convertJsonToUbl')
), model AS (
    SELECT id FROM canonical_models
    WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
), mappings(field_path, suffix, required, transform_type) AS (
    VALUES
    ('invoice.id','.id',TRUE,'DIRECT'),
    ('invoice.issue_date','.issueDate',TRUE,'DIRECT'),
    ('invoice.due_date','.dueDate',FALSE,'DIRECT'),
    ('invoice.type_code','.invoiceTypeCode',FALSE,'DIRECT'),
    ('invoice.currency_code','.currencyCode',TRUE,'DIRECT'),
    ('seller.name','.supplier.name',TRUE,'DIRECT'),
    ('seller.tax_id','.supplier.tax.companyID',FALSE,'DIRECT'),
    ('seller.address.street','.supplier.address.street',FALSE,'DIRECT'),
    ('seller.address.city','.supplier.address.city',FALSE,'DIRECT'),
    ('seller.address.state','.supplier.address.state',FALSE,'DIRECT'),
    ('seller.address.postal_code','.supplier.address.postalCode',FALSE,'DIRECT'),
    ('seller.address.country_code','.supplier.address.country',FALSE,'DIRECT'),
    ('buyer.name','.customer.name',TRUE,'DIRECT'),
    ('buyer.tax_id','.customer.tax.companyID',FALSE,'DIRECT'),
    ('buyer.address.street','.customer.address.street',FALSE,'DIRECT'),
    ('buyer.address.city','.customer.address.city',FALSE,'DIRECT'),
    ('buyer.address.state','.customer.address.state',FALSE,'DIRECT'),
    ('buyer.address.postal_code','.customer.address.postalCode',FALSE,'DIRECT'),
    ('buyer.address.country_code','.customer.address.country',FALSE,'DIRECT'),
    ('totals.line_extension_amount','.totals.lineExtensionAmount',TRUE,'DIRECT'),
    ('totals.tax_exclusive_amount','.totals.taxExclusiveAmount',FALSE,'DIRECT'),
    ('totals.tax_inclusive_amount','.totals.taxInclusiveAmount',TRUE,'DIRECT'),
    ('totals.payable_amount','.totals.payableAmount',TRUE,'DIRECT'),
    ('lines[*].id','.lines[*].id',TRUE,'DIRECT'),
    ('lines[*].description','.lines[*].description',TRUE,'DIRECT'),
    ('lines[*].quantity','.lines[*].quantity',TRUE,'DIRECT'),
    ('lines[*].unit_code','.lines[*].unitCode',FALSE,'DIRECT'),
    ('lines[*].net_amount','.lines[*].amount',TRUE,'DIRECT'),
    ('lines[*].unit_price','.lines[*].price.amount',TRUE,'DIRECT'),
    ('lines[*].tax_category_code','.lines[*].taxCategory.id',FALSE,'DIRECT'),
    ('lines[*].tax_rate','.lines[*].taxRate',FALSE,'DIRECT'),
    ('lines[*].tax_scheme_code','.lines[*].taxCategory.taxScheme.id',FALSE,'DIRECT')
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operations.id, field.id, 'CANONICAL_TO_INPUT', operations.root || mappings.suffix,
       mappings.transform_type, '{}'::jsonb, mappings.required, 0, 'APPROVED', NOW(),
       'Manually reviewed against the local converter source contract.'
FROM operations CROSS JOIN model
JOIN canonical_fields field ON field.canonical_model_id = model.id
JOIN mappings ON mappings.field_path = field.field_path
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = operations.id
      AND existing.direction = 'CANONICAL_TO_INPUT'
      AND existing.operation_field_path = operations.root || mappings.suffix
);

-- Amit request wrapper values are operation constants, not invoice fields.
WITH operation AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'Amit Invoice Conversion API'
      AND operation.operation_id = 'convertInvoiceJsonToXml'
), constants(path, value) AS (
    VALUES
    ('$.invoiceType','"json"'::jsonb),
    ('$.outputFormat','"UBL XML"'::jsonb),
    ('$.useFlexibleMapping','true'::jsonb)
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operation.id, NULL, 'CANONICAL_TO_INPUT', constants.path, 'CONSTANT',
       jsonb_build_object('value', constants.value), TRUE, 0, 'APPROVED', NOW(),
       'Required Amit request wrapper value.'
FROM operation CROSS JOIN constants
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = operation.id
      AND existing.direction = 'CANONICAL_TO_INPUT'
      AND existing.operation_field_path = constants.path
);

-- The Amit converter now runs as a cloud deployment, so it can also be reached
-- from AWS Lambda. Its request contract is unchanged.
-- A cold start of the free hosting tier took about 20 seconds, so the timeout
-- is raised from 30 to 60 seconds.
UPDATE apis
SET base_url = 'https://invoice-conversion-api.onrender.com',
    description = 'Third-party API that converts invoice JSON into UBL 2.1 Invoice XML, deployed in the cloud.',
    updated_at = NOW()
WHERE name = 'Amit Invoice Conversion API';

UPDATE api_operations operation
SET timeout_seconds = 60,
    description = 'Converts a canonical invoice (JSON) into a UBL 2.1 Invoice XML document, which the validation '
        || 'step then checks.',
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'Amit Invoice Conversion API'
  AND operation.operation_id = 'convertInvoiceJsonToXml';

-- The validation service is presented under its provider's name, ESS. Only
-- displayed text changes: the endpoint host and the
-- SMARTDOCFLOW_API_KEY environment variable name stay as they are.
UPDATE apis
SET name = 'ESS API',
    description = 'ESS electronic-invoice validation service. The catalog currently exposes EN16931 UBL and Peppol '
        || 'PINT A-NZ Billing validation.',
    updated_at = NOW()
WHERE name = 'SmartDocFlow eInvoicing Services API';

UPDATE api_operations operation
SET description = 'Runs the ESS ubl-en16931 Schematron profile. HTTP 400 is a business validation result and is '
        || 'interpreted from the JSON report.',
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'ESS API'
  AND operation.operation_id = 'validateUblEn16931';

UPDATE operation_field_mappings mapping
SET note = 'Base64 inline file contract used by ESS.'
FROM api_operations operation
JOIN apis api ON api.id = operation.api_id
WHERE mapping.operation_id = operation.id
  AND api.name = 'ESS API'
  AND mapping.note = 'Base64 inline file contract used by SmartDocFlow.';

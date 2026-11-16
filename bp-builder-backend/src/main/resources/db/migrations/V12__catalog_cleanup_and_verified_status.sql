-- Removes catalog entries BP Builder no longer uses and simplifies the
-- verification status of checked operations to VERIFIED.
--
-- Xero and Ademico were imported from OpenAPI but never reviewed. The built-in
-- JSON-to-UBL converter duplicates the Amit converter; workflows call Amit.
-- Deleting the API rows cascades to their operations and field mappings.
DELETE FROM apis
WHERE name IN ('Xero Accounting API', 'Ademico Peppol REST API', 'BP Builder Internal Tools');

UPDATE api_operations operation
SET verification_status = 'VERIFIED',
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND (api.name, operation.operation_id) IN (
      ('UpBrains Invoice Extractor', 'extractInvoiceFromPdf'),
      ('Amit Invoice Conversion API', 'convertInvoiceJsonToXml'),
      ('SmartDocFlow eInvoicing Services API', 'validateUblEn16931'),
      ('SmartDocFlow eInvoicing Services API', 'validateUblPintAunzBilling'),
      ('Mailgun Email API', 'sendValidatedUblInvoice'));

-- Descriptions are shown in the Operation Catalog, so they state what the
-- operation is for, not deployment details such as environment variables.
UPDATE api_operations operation
SET summary = 'Convert invoice JSON to UBL 2.1 Invoice XML',
    description = 'Converts a canonical invoice (JSON) into a UBL 2.1 Invoice XML document, '
        || 'which the validation step then checks. Runs as a local API.'
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'Amit Invoice Conversion API'
  AND operation.operation_id = 'convertInvoiceJsonToXml';

UPDATE api_operations operation
SET description = 'Reads a PDF invoice (file name and Base64 content) and returns the extracted '
        || 'invoice fields, which BP Builder maps onto the canonical invoice.'
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'UpBrains Invoice Extractor'
  AND operation.operation_id = 'extractInvoiceFromPdf';

UPDATE api_operations operation
SET description = 'Emails the validated UBL invoice as an XML attachment to the configured recipient.'
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'Mailgun Email API'
  AND operation.operation_id = 'sendValidatedUblInvoice';

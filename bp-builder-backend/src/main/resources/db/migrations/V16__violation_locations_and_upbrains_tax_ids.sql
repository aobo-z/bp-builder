-- ESS reports every rule violation with the XML location it applies to. The
-- validators' outcome rules now read the failure objects and keep that
-- location, so the same message about different elements (e.g. a missing
-- currencyID on twelve amounts) stays distinguishable.
UPDATE api_operations operation
SET outcome_rule = operation.outcome_rule
        || '{"errorsPath":"$.details.failures[*]","errorLocationField":"location"}'::jsonb,
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'ESS API'
  AND operation.operation_id = 'validateUblEn16931';

UPDATE api_operations operation
SET outcome_rule = operation.outcome_rule
        || '{"errorsPath":"$.details.steps[*].details.failures[*]","errorLocationField":"location"}'::jsonb,
    updated_at = NOW()
FROM apis api
WHERE api.id = operation.api_id
  AND api.name = 'ESS API'
  AND operation.operation_id = 'validateUblPintAunzBilling';

-- A purchase order reference (BT-13) is not a buyer reference (BT-10), so it
-- gets its own canonical field.
INSERT INTO canonical_fields
    (canonical_model_id, field_path, data_type, cardinality, ubl_xpath, xsd_required, constraints, description)
SELECT id, 'invoice.order_reference', 'string', '0..1', '/ubl:Invoice/cac:OrderReference/cbc:ID', FALSE,
       '{}'::jsonb, 'Purchase order reference.'
FROM canonical_models
WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
ON CONFLICT (canonical_model_id, field_path) DO NOTHING;

-- UpBrains also returns the parties' tax identifiers and the purchase order
-- number, observed on a test invoice that prints them. ABNs are printed with
-- spaces ("76 200 300 401") and are stored without them.
WITH operation AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'UpBrains Invoice Extractor'
      AND operation.operation_id = 'extractInvoiceFromPdf'
), model AS (
    SELECT id FROM canonical_models
    WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
), mappings(field_path, operation_path, transform_type, note) AS (
    VALUES
    ('seller.tax_id','$.extractor_result.documents[0].fields.VendorTaxId.content','REMOVE_WHITESPACE',
     'ABN as printed on the invoice, stored without spaces.'),
    ('buyer.tax_id','$.extractor_result.documents[0].fields.CustomerTaxId.content','REMOVE_WHITESPACE',
     'ABN as printed on the invoice, stored without spaces.'),
    ('invoice.order_reference','$.extractor_result.documents[0].fields.PurchaseOrder.content','DIRECT',NULL)
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT operation.id, field.id, 'OUTPUT_TO_CANONICAL', mappings.operation_path, mappings.transform_type,
       '{}'::jsonb, FALSE, 0, 'APPROVED', NOW(), mappings.note
FROM operation CROSS JOIN model
JOIN canonical_fields field ON field.canonical_model_id = model.id
JOIN mappings ON mappings.field_path = field.field_path
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = operation.id
      AND existing.direction = 'OUTPUT_TO_CANONICAL'
      AND existing.operation_field_path = mappings.operation_path
);

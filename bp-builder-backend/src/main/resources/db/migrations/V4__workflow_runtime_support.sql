-- Support for the canonical workflow planner/executor. Additive and
-- idempotent: no rows are deleted.

-- Reviewed rule for reading validation/communication responses.
ALTER TABLE api_operations ADD COLUMN IF NOT EXISTS outcome_rule JSONB;

-- Amit converter input, re-checked against its source
-- (invoice-conversion-api flexibleMappingService): the line price is read
-- from a scalar unitPrice/priceAmount/price key, and the tax total from
-- taxTotal.taxAmount. V3 mapped the price to price.amount (never read) and
-- did not map the tax total at all.
--
-- The BP Builder Internal Tools converter (convertJsonToUbl) is deliberately
-- not touched here: on 2026-10-03 its row was rewritten by a code version
-- that is not in this repository, so its contract must be confirmed first.
WITH converter AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'Amit Invoice Conversion API' AND operation.operation_id = 'convertInvoiceJsonToXml'
)
UPDATE operation_field_mappings mapping
SET operation_field_path = '$.invoiceData.lines[*].unitPrice',
    reviewed_at = NOW(),
    note = 'Corrected in V4: Amit reads a scalar unitPrice, not price.amount.'
FROM converter
WHERE mapping.operation_id = converter.id
  AND mapping.direction = 'CANONICAL_TO_INPUT'
  AND mapping.operation_field_path = '$.invoiceData.lines[*].price.amount';

WITH converter AS (
    SELECT operation.id
    FROM api_operations operation
    JOIN apis api ON api.id = operation.api_id
    WHERE api.name = 'Amit Invoice Conversion API' AND operation.operation_id = 'convertInvoiceJsonToXml'
), model AS (
    SELECT id FROM canonical_models
    WHERE name = 'BP Builder Canonical Invoice' AND version = '1.0'
)
INSERT INTO operation_field_mappings
    (operation_id, canonical_field_id, direction, operation_field_path, transform_type,
     transform_config, required_by_operation, priority, review_status, reviewed_at, note)
SELECT converter.id, field.id, 'CANONICAL_TO_INPUT', '$.invoiceData.taxTotal.taxAmount',
       'DIRECT', '{}'::jsonb, FALSE, 0, 'APPROVED', NOW(),
       'Added in V4: Amit reads the invoice tax total from taxTotal.taxAmount.'
FROM converter CROSS JOIN model
JOIN canonical_fields field
  ON field.canonical_model_id = model.id AND field.field_path = 'totals.tax_amount'
WHERE NOT EXISTS (
    SELECT 1 FROM operation_field_mappings existing
    WHERE existing.operation_id = converter.id
      AND existing.direction = 'CANONICAL_TO_INPUT'
      AND existing.operation_field_path = '$.invoiceData.taxTotal.taxAmount'
);

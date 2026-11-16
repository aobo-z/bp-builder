-- Every email needs a From address. For the Mailgun sandbox it is fixed by the
-- domain the messages URL points at, so it belongs to the reviewed operation
-- (a catalog constant), not to the user or to an environment variable. The
-- recipient stays deployment configuration (MAILGUN_TO) because a sandbox
-- only delivers to authorised recipients.
UPDATE operation_field_mappings mapping
SET transform_type = 'CONSTANT',
    transform_config = '{"value":"BP Builder <postmaster@<your-mailgun-domain>>"}'::jsonb,
    reviewed_at = NOW(),
    note = 'Fixed sandbox sender; Mailgun requires a From address and it is not user input.'
FROM api_operations operation
JOIN apis api ON api.id = operation.api_id
WHERE mapping.operation_id = operation.id
  AND api.name = 'Mailgun Email API'
  AND operation.operation_id = 'sendValidatedUblInvoice'
  AND mapping.direction = 'CANONICAL_TO_INPUT'
  AND mapping.operation_field_path = '$.from';

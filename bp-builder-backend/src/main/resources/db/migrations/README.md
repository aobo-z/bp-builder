# Database migrations

Create the tables with `../schema.sql`, then run every file here **once, in
numeric version order** (V2, V3, V4, V6, V7, …, V19), stopping at the first
error. Do not sort them by file name: that runs V10 to V19 before V2 and leaves
the catalog incomplete. The files are not designed to be run twice.

Together they create the 39-field canonical invoice model and an example
catalog of six operations. Secrets are never stored: operations reference
environment variables by name.

| File | Purpose |
| --- | --- |
| `V2__canonical_invoice_catalog.sql` | canonical invoice fields used by operation mappings |
| `V3__observed_transformation_operations.sql` | UpBrains PDF extractor and the JSON-to-UBL converter with their mappings (`UPBRAINS_INVOICE_EXTRACT_URL`) |
| `V4__workflow_runtime_support.sql` | `api_operations.outcome_rule` and converter mapping corrections |
| `V6__remove_legacy_compatibility.sql` | removes tables of an earlier compatibility-checking approach |
| `V7__remove_b2brouter.sql` | removes an unusable provider |
| `V8__drop_unused_operation_columns.sql` | drops unused operation columns |
| `V9__smartdocflow_and_mailgun_operations.sql` | ESS validation operations (EN 16931 and Peppol PINT A-NZ Billing) and the Mailgun sender (`SMARTDOCFLOW_API_KEY`, `MAILGUN_*`) |
| `V10__mailgun_fixed_sender.sql` | Mailgun sender address; replace `<your-mailgun-domain>` with your own domain first |
| `V11__internal_converter_unverified.sql` | marks a duplicate converter as unverified |
| `V12__catalog_cleanup_and_verified_status.sql` | removes unreviewed providers; one `VERIFIED` status for checked operations |
| `V13__workflows.sql` | saved workflows, their steps and runs |
| `V14__amit_cloud_and_ess_naming.sql` | cloud address of the converter; display name of the validation service |
| `V15__workflow_run_results.sql` | canonical invoice and produced documents stored with each run |
| `V16__violation_locations_and_upbrains_tax_ids.sql` | violation locations in validation reports; tax identifier mappings |
| `V17__workflow_deployments.sql` | deployed workflows (Lambda function, URL, region) |
| `V18__workflow_state_machines.sql` | Step Functions state machine of each deployed workflow |
| `V19__upbrains_image_extractor.sql` | UpBrains image extractor (`UPBRAINS_IMAGE_EXTRACT_URL`) |

Whether a converted document is valid UBL is decided by the validation
operation, not by the converter's verification status.

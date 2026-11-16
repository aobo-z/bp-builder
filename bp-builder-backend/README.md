# BP Builder backend

BP Builder builds and executes invoice workflows from a catalog of reviewed
operations. The business order is fixed:

```text
Transformation -> Validation -> Communication
```

Transformation may contain several operations. The planner searches the
active catalog on every request using artifact transitions such as
`PDF_DOCUMENT -> CANONICAL_INVOICE -> UBL_XML`. It does not import OpenAPI,
call an AI compatibility judge, precompute operation pairs, or read a
compatibility cache.

## Catalog boundary

The catalog is stored in:

- `apis` and `api_operations` for executable operation contracts;
- `canonical_models` and `canonical_fields` for the internal invoice model;
- `operation_field_mappings` for approved operation-to-canonical mappings.

BP Builder consumes only active APIs/operations and `APPROVED` mappings. Whoever
maintains the catalog registers operations and reviews their mappings.
The canonical endpoints in this service are read-only.

For an external validation service, each rule variant is its own operation.
Several variants may share one endpoint; their reviewed constant mappings
select the external profile or pack. Choosing a workflow chain therefore
chooses its validator. Country and profile business rules remain the
validator's responsibility.

## Workflow API

- `GET /api/operations` lists the active catalog operations, without endpoint
  or credential details.
- `POST /api/workflows/interpret` reads a free-text requirement with Gemini
  and returns the input artifact, goal artifact and optional validator for a
  plan request, plus open questions. The model only fills a
  fixed schema; the backend checks every value, adds sending only when the
  requirement explicitly asks for it, and leaves choosing operations to the
  planner. It needs `GEMINI_API_KEY`; `GEMINI_MODEL` defaults to
  `gemini-3.8-flash`. It answers HTTP 503 when Gemini is unavailable.
- `POST /api/workflows/plan` searches candidate chains for an input artifact
  and goal artifact. Every validator is offered unless the optional
  `validationOperationId` keeps only that one.
- `GET /api/operations/{operationId}/mappings` lists an operation's approved
  field mappings without their transform configuration (which can name
  environment variables or hold fixed values).
- `POST /api/workflows/assess` re-checks a chosen or edited chain without
  calling any operation.
- `POST /api/workflows/{id}/runs` test-runs a saved workflow with real
  external calls and stores the run (HTTP 409 when the workflow is not
  `READY`); `GET /api/runs` lists recent runs and `GET /api/runs/{id}` returns
  one with its steps, canonical invoice and produced documents.
- `GET /api/workflows` lists saved workflows; `POST /api/workflows` saves a
  chosen chain; `GET`, `PUT` (rename and/or replace steps) and `DELETE`
  `/api/workflows/{id}` manage one. Steps are stored as references to catalog
  operations and every read re-checks them against the current catalog; an
  operation that is no longer active makes the workflow `NOT_SUPPORTED`
  instead of failing to load.
- `POST /api/workflows/{id}/deployment` deploys a `READY` workflow to AWS: a
  shared Lambda component per operation, a Step Functions state machine
  connecting them, and an entry function with a public function URL (see
  `lambda-runner/README.md` for the AWS setup). `GET` returns the deployment
  and whether it is still up to date, or 204 when there is none. Deleting a
  workflow deletes its entry function and state machine.
- `GET /api/canonical/models/active` returns the active canonical model.
- `GET /api/canonical/operations/{operationId}/mappings` returns an
  operation's stored mappings.

Planning verifies stage order, artifact availability, execution support, and
whether each operation's required canonical inputs are produced by an earlier
step. A chain whose steps all connect is `READY`; any issue makes it
`NOT_SUPPORTED`, and the planner only offers `READY` chains. A required field
that no earlier step produces means the two operations cannot be connected; it
is never left for the user to fill in. A `CANONICAL_INVOICE` input supplies
every canonical field itself.

A run request takes `input` in the workflow's input type and an optional
`inputName`: a PDF as raw Base64, UBL as XML text, or a canonical invoice as a
canonical JSON object such as `{"invoice": {"id": "INV-1"}, "lines":
[{"quantity": 2}]}`. Fields that are not in the active canonical model are
rejected with 400. If a particular input lacks a value an operation requires,
the run stops before calling that operation with status `INPUT_REQUIRED`.

Execution materializes a canonical invoice. Each operation request is built
from that invoice and previously produced artifacts. The result keeps these
values separate:

- canonical invoice and field provenance;
- UBL XML;
- validation report and interpreted verdict;
- communication receipt;
- bounded step request/response records.

Produced UBL is passed to the selected validator unchanged; BP Builder does
not check it itself. A failed or unreadable validation verdict prevents
communication.

The framework-independent definitions, canonical mappings and executor are in
the sibling `workflow-runtime` module. Planning and the
Spring/database adapters remain in this module. Endpoint URL secrets are
referenced by environment variable name through
`api_operations.endpoint_url_ref`.

## External operation configuration

Migration `V9__smartdocflow_and_mailgun_operations.sql` adds two reviewed
validation variants of the ESS API (shown as "ESS API" since V14; the service
is hosted under the provider's SmartDocFlow domain) and one Mailgun
communication operation. Set these environment variables in the backend (a
deployed workflow's function receives the ones its operations use):

- `UPBRAINS_INVOICE_EXTRACT_URL`: complete UpBrains extraction webhook URL
  (the `/sync` variant), which is itself the credential;
- `UPBRAINS_IMAGE_EXTRACT_URL`: the same for the image extraction webhook
  (V19);
- `SMARTDOCFLOW_API_KEY`: ESS API `x-api-key` value;
- `MAILGUN_MESSAGES_URL`: complete domain-specific URL, for example
  `https://api.mailgun.net/v3/<domain>/messages`;
- `MAILGUN_API_KEY`: Mailgun API key used with Basic Auth username `api`;
- `MAILGUN_TO`: recipient used by the workflow.

The ESS API catalog row currently points to the staging base URL. The
Mailgun operation sends the preserved UBL XML artifact as an `invoice.xml`
multipart attachment. No credential is stored in the catalog. The only
account-specific value is the sender address that V10 fixes for the Mailgun
domain; set it to your own domain before applying V10.

## UBL conversion

BP Builder has no built-in converter. Workflows convert a canonical invoice to
UBL XML with the Amit operation, which is deployed at
`https://invoice-conversion-api.onrender.com` (V14) and is therefore also
reachable from AWS Lambda. The free hosting tier sleeps when idle; the first
call after a pause can take about 20 seconds, which the 60-second timeout covers.

## Database

`src/main/resources/db/schema.sql` defines the schema; the files in
`src/main/resources/db/migrations` are then applied once each in numeric
version order (see `db/migrations/README.md`).

The default connection in `application.properties` targets a hosted Supabase
database through its transaction pooler (port 6543, with `prepareThreshold=0`),
and the connection pool is kept small (three connections). Set
`SPRING_DATASOURCE_URL`, `SM_DB_USER` and `SM_DB_PASS` to use another
PostgreSQL database.

## Tests

From the repository root:

```powershell
.\mvnw.cmd test
```

Workflow tests use local fixtures and an in-memory HTTP transport. They do not
call UpBrains, ESS, a communication API, or an LLM.

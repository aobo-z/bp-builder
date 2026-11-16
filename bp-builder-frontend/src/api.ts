import type { Stage, Tone } from './ui'

/** Backend base URL; override with VITE_API_BASE_URL (e.g. in .env.local). */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8081'

export type ArtifactType =
  | 'PDF_DOCUMENT'
  | 'IMAGE_DOCUMENT'
  | 'CANONICAL_INVOICE'
  | 'UBL_XML'
  | 'VALIDATION_REPORT'
  | 'DELIVERY_RECEIPT'
  | 'API_REFERENCE'
  | 'UNKNOWN'

/** One row of GET /api/operations (OperationCatalogItem on the backend). */
export interface CatalogOperation {
  id: number
  operationKey: string
  name: string
  provider: string
  summary: string | null
  description: string | null
  stage: 'TRANSFORMATION' | 'VALIDATION' | 'COMMUNICATION' | null
  inputArtifact: ArtifactType
  outputArtifact: ArtifactType
  verificationStatus: string
  contractSource: string
  executionType: string
  method: string
  requestContentType: string
  authMethod: string | null
  inputMappings: number
  requiredInputMappings: number
  outputMappings: number
  outcomeRule: boolean
  timeoutSeconds: number
  chainable: boolean
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message)
  }
}

/** One step of a candidate chain (WorkflowStepView). */
export interface ChainStep {
  sequence: number
  operationId: number
  name: string
  stage: 'TRANSFORMATION' | 'VALIDATION' | 'COMMUNICATION' | null
  input: ArtifactType
  output: ArtifactType
  verificationStatus: string
}

export interface ChainIssue {
  type: 'FIELD_NOT_PRODUCED' | 'UNSUPPORTED_OPERATION' | 'INVALID_SEQUENCE'
  step: number | null
  field: string | null
  message: string
}

export interface ChainCandidate {
  steps: ChainStep[]
  /** READY when every step connects; any issue makes the chain NOT_SUPPORTED. */
  assessment: { status: 'READY' | 'NOT_SUPPORTED'; issues: ChainIssue[]; warnings: string[] }
}

/** The fields of a plan request; null means the user still has to choose. */
export interface Requirement {
  inputArtifactType: ArtifactType | null
  goalArtifactType: ArtifactType | null
  validationOperationId: number | null
}

/** POST /api/workflows/interpret: a requirement read from free text, for review. */
export interface Interpretation extends Requirement {
  validationOperationName: string | null
  deliveryPhrase: string | null
  questions: string[]
  model: string
}

export interface PlanResponse {
  inputArtifactType: ArtifactType
  goalArtifactType: ArtifactType
  validationOperationId: number | null
  candidates: ChainCandidate[]
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  // A FormData body needs its own multipart boundary in Content-Type, which
  // fetch only sets correctly when the header is left out entirely — setting
  // it to application/json (like every JSON caller here needs) would break it.
  const isFormData = init.body instanceof FormData
  try {
    response = await fetch(API_BASE_URL + path, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body && !isFormData ? { 'Content-Type': 'application/json' } : {}) },
    })
  } catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    throw new ApiError(`Cannot reach the BP Builder backend at ${API_BASE_URL}.`)
  }
  if (!response.ok) {
    let detail = ''
    try {
      const body = await response.json()
      detail = body.message || body.error || ''
    } catch {
      /* not JSON */
    }
    throw new ApiError(`The backend answered HTTP ${response.status}${detail ? ': ' + detail : ''}.`, response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const fetchOperations = (signal?: AbortSignal) => request<CatalogOperation[]>('/api/operations', { signal })

export const interpretRequirement = (text: string, signal?: AbortSignal) =>
  request<Interpretation>('/api/workflows/interpret', { method: 'POST', body: JSON.stringify({ text }), signal })

/** POST /api/workflows/describe-sketch: a hand-drawn or photographed workflow read into one description, for review before interpreting it like any other text. */
export const describeSketch = async (file: File, signal?: AbortSignal): Promise<string> => {
  const form = new FormData()
  form.append('file', file)
  const { description } = await request<{ description: string }>('/api/workflows/describe-sketch', { method: 'POST', body: form, signal })
  return description
}

export const planWorkflow = (requirement: Requirement, signal?: AbortSignal) =>
  request<PlanResponse>('/api/workflows/plan', { method: 'POST', body: JSON.stringify(requirement), signal })

/** Checks a chosen or edited chain again without calling any operation. */
export const assessWorkflow = (operationIds: number[], inputArtifactType: ArtifactType) =>
  request<ChainCandidate>('/api/workflows/assess', {
    method: 'POST',
    body: JSON.stringify({ operationIds, inputArtifactType }),
  })

/* ---------- saved workflows ---------- */
export type AssessmentStatus = ChainCandidate['assessment']['status']

/** GET/POST/PUT /api/workflows[/{id}]: a saved workflow, checked against the current catalog. */
export interface SavedWorkflow {
  id: number
  name: string
  requirementText: string | null
  inputArtifactType: ArtifactType
  goalArtifactType: ArtifactType
  validationOperationId: number | null
  interpretation: Record<string, unknown> | null
  steps: ChainStep[]
  assessment: ChainCandidate['assessment']
  createdAt: string
  updatedAt: string
}

export interface WorkflowSummary {
  id: number
  name: string
  inputArtifactType: ArtifactType
  goalArtifactType: ArtifactType
  stepCount: number
  status: AssessmentStatus
  updatedAt: string
  lastRunStatus: string | null
  lastRunAt: string | null
}

export interface NewWorkflow {
  name: string
  requirementText: string | null
  inputArtifactType: ArtifactType
  goalArtifactType: ArtifactType
  validationOperationId: number | null
  interpretation: Interpretation | null
  operationIds: number[]
}

export const listWorkflows = (signal?: AbortSignal) => request<WorkflowSummary[]>('/api/workflows', { signal })
export const getWorkflow = (id: number) => request<SavedWorkflow>(`/api/workflows/${id}`)
export const createWorkflow = (workflow: NewWorkflow) =>
  request<SavedWorkflow>('/api/workflows', { method: 'POST', body: JSON.stringify(workflow) })
export const updateWorkflow = (id: number, change: { name?: string; operationIds?: number[] }) =>
  request<SavedWorkflow>(`/api/workflows/${id}`, { method: 'PUT', body: JSON.stringify(change) })
export const deleteWorkflow = (id: number) => request<void>(`/api/workflows/${id}`, { method: 'DELETE' })

/* ---------- test runs ---------- */
/** INVOICE_INVALID is the validator's verdict on the invoice, an expected outcome rather than a failure. */
export type RunStatus = 'COMPLETED' | 'INVOICE_INVALID' | 'INPUT_REQUIRED' | 'FAILED'
export type StepStatus = 'SUCCEEDED' | 'INPUT_REQUIRED' | 'FAILED' | 'NOT_RUN'

/** One step as it ran; request and response are shortened previews. */
export interface RunStep {
  sequence: number
  operationId: number | null
  operationName: string
  endpoint: string | null
  status: StepStatus
  httpStatus: number | null
  durationMs: number | null
  request: unknown
  response: unknown
  messages: string[]
  stage?: ChainStep['stage']
  outputArtifact?: ArtifactType
}

export interface RunSummary {
  id: number
  workflowId: number
  workflowName: string | null
  inputArtifactType: ArtifactType
  inputName: string | null
  status: RunStatus
  message: string | null
  startedAt: string
  finishedAt: string | null
}

/** One reported violation or warning; location is where in the document it applies, when the service says. */
export interface Issue {
  message: string
  location: string | null
}

/** GET /api/runs/{id}: a stored run with what it produced. artifacts holds documents by artifact type. */
export interface WorkflowRun extends RunSummary {
  operationIds: number[]
  inputSizeBytes: number | null
  steps: RunStep[]
  canonicalInvoice: Record<string, unknown> | null
  artifacts: Partial<Record<ArtifactType, unknown>> | null
  validation: { status: 'PASSED' | 'FAILED' | 'UNDETERMINED'; step: number | null; errors: Issue[]; warnings: Issue[]; detail: string | null } | null
  communication: { status: 'ACCEPTED' | 'REJECTED' | 'UNCONFIRMED'; step: number | null; reference: string | null; errors: string[]; detail: string | null } | null
}

/** Runs a READY saved workflow with real external calls and stores the run. */
export const runWorkflow = (workflowId: number, input: unknown, inputName: string) =>
  request<WorkflowRun>(`/api/workflows/${workflowId}/runs`, { method: 'POST', body: JSON.stringify({ input, inputName }) })
export const listRuns = (signal?: AbortSignal) => request<RunSummary[]>('/api/runs', { signal })
export const getRun = (id: number, signal?: AbortSignal) => request<WorkflowRun>(`/api/runs/${id}`, { signal })

/* ---------- deployment ---------- */
/** A deployed workflow: an entry function whose functionUrl takes POST {"input": ...} and runs its state machine. */
export interface Deployment {
  workflowId: number
  functionName: string
  functionUrl: string
  /** The Step Functions state machine that connects the workflow's component functions. */
  stateMachineArn: string | null
  region: string
  inputArtifactType: ArtifactType
  deployedAt: string
  /** False once the workflow or its operations changed since it was deployed. */
  upToDate: boolean
}

/** The workflow's deployment, or undefined when it has not been deployed (HTTP 204). */
export const getDeployment = (workflowId: number, signal?: AbortSignal) =>
  request<Deployment | undefined>(`/api/workflows/${workflowId}/deployment`, { signal })
/** Creates the workflow's Lambda function, or updates it to the current workflow. */
export const deployWorkflow = (workflowId: number) =>
  request<Deployment>(`/api/workflows/${workflowId}/deployment`, { method: 'POST' })

/** One approved field mapping (OperationMappingView); transform configuration is not exposed. */
export interface MappingView {
  direction: 'CANONICAL_TO_INPUT' | 'OUTPUT_TO_CANONICAL'
  canonicalField: string | null
  operationField: string
  transform: string
  required: boolean
  /** Set for a CONSTANT mapping. */
  fixedValue: unknown
  /** Set for an ARTIFACT mapping: the document an earlier step produced, and how it is encoded. */
  artifact: string | null
  encodings: string[] | null
}

export const fetchMappings = (operationId: number, signal?: AbortSignal) =>
  request<MappingView[]>(`/api/operations/${operationId}/mappings`, { signal })

/* ---------- display helpers ---------- */
export const ARTIFACT_LABEL: Record<ArtifactType, string> = {
  PDF_DOCUMENT: 'PDF Document',
  IMAGE_DOCUMENT: 'Image Document',
  CANONICAL_INVOICE: 'Canonical Invoice',
  UBL_XML: 'UBL XML',
  VALIDATION_REPORT: 'Validation Report',
  DELIVERY_RECEIPT: 'Delivery Receipt',
  API_REFERENCE: 'API Reference',
  UNKNOWN: 'Not reviewed',
}

export const stageOf = (op: { stage: CatalogOperation['stage'] }): Stage | null =>
  op.stage ? (op.stage.toLowerCase() as Stage) : null

/** What a workflow can start from, worded for the requirement review. */
export const INPUT_CHOICES: { id: ArtifactType; label: string }[] = [
  { id: 'PDF_DOCUMENT', label: 'PDF invoice' },
  { id: 'IMAGE_DOCUMENT', label: 'Invoice image (PNG, JPG)' },
  { id: 'CANONICAL_INVOICE', label: 'Invoice data (JSON)' },
  { id: 'UBL_XML', label: 'UBL XML invoice' },
]

/** Where a workflow ends; sending always follows validation. */
export const GOAL_CHOICES: { id: ArtifactType; label: string }[] = [
  { id: 'CANONICAL_INVOICE', label: 'Extracted invoice data' },
  { id: 'UBL_XML', label: 'UBL XML invoice' },
  { id: 'VALIDATION_REPORT', label: 'Validation report' },
  { id: 'DELIVERY_RECEIPT', label: 'Validated invoice, sent' },
]

export const validates = (goal: ArtifactType | null) => goal === 'VALIDATION_REPORT' || goal === 'DELIVERY_RECEIPT'

export const humanize = (value: string) =>
  value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ')

const VERIFICATION: Record<string, { label: string; tone: Tone }> = {
  VERIFIED: { label: 'Verified', tone: 'ok' },
  CONTRACT_REVIEWED_UNTESTED: { label: 'Contract reviewed, untested', tone: 'warn' },
  UNREVIEWED: { label: 'Not reviewed', tone: 'neutral' },
}

/** Maps a stored verification status to a short label and tone; any other status needs attention. */
export function verificationOf(status: string): { label: string; tone: Tone } {
  return VERIFICATION[status] ?? { label: humanize(status), tone: 'warn' }
}

/** Describes how the backend authenticates; secrets themselves stay in deployment configuration. */
export function authLabel(method: string | null): string {
  if (!method || method.toLowerCase() === 'none') return 'None'
  return (
    {
      X_API_KEY: 'API key in x-api-key header',
      BASIC_API_KEY: 'API key via HTTP Basic',
      'endpoint URL is secret': 'Secret endpoint URL',
    }[method] ?? method
  )
}

export const SOURCE_LABEL: Record<string, string> = {
  OPENAPI: 'OpenAPI specification',
  OBSERVED: 'Observed workflow',
  SOURCE_CODE: 'Source code',
  VENDOR_DOCUMENTATION: 'Vendor documentation',
}

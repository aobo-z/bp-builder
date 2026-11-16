import { useEffect, useMemo, useRef, useState } from 'react'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import { toBpmnXml } from './bpmn'
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Download,
  FileCode2,
  Info,
  Pencil,
  Maximize,
  Play,
  Rows3,
  ShieldCheck,
  StretchHorizontal,
  X,
  XCircle,
  ZoomIn,
  ZoomOut,
} from 'lucide-react'
import {
  ArtifactTag,
  Badge,
  Btn,
  Card,
  Dialog,
  EmptyState,
  STAGE,
  Spinner,
  StageTag,
  cx,
  inputCls,
  useApp,
  type BadgeKind,
  type Stage,
} from './ui'
import {
  ARTIFACT_LABEL,
  GOAL_CHOICES,
  INPUT_CHOICES,
  SOURCE_LABEL,
  authLabel,
  fetchMappings,
  fetchOperations,
  stageOf,
  updateWorkflow,
  verificationOf,
  type CatalogOperation,
  type ChainCandidate,
  type ChainStep,
  type MappingView,
  type Requirement,
  type SavedWorkflow,
} from './api'

/* ---------- the workflow being built ---------- */
export interface DraftWorkflow {
  id: number
  name: string
  requirement: Requirement
  candidate: ChainCandidate
  updatedAt: string
}

export function fromSaved(saved: SavedWorkflow): DraftWorkflow {
  return {
    id: saved.id,
    name: saved.name,
    requirement: {
      inputArtifactType: saved.inputArtifactType,
      goalArtifactType: saved.goalArtifactType,
      validationOperationId: saved.validationOperationId,
    },
    candidate: { steps: saved.steps, assessment: saved.assessment },
    updatedAt: saved.updatedAt,
  }
}

/** A default name for a new workflow, from its input and result. */
export function defaultName(requirement: Requirement) {
  const input = INPUT_CHOICES.find((c) => c.id === requirement.inputArtifactType)?.label ?? 'Input'
  const goal = GOAL_CHOICES.find((c) => c.id === requirement.goalArtifactType)?.label ?? 'result'
  return `${input} → ${goal}`
}

export const ASSESSMENT_BADGE: Record<ChainCandidate['assessment']['status'], { kind: BadgeKind; label: string }> = {
  READY: { kind: 'ready', label: 'Ready' },
  NOT_SUPPORTED: { kind: 'action', label: 'Not supported' },
}

/** A workflow whose steps all connect is READY and can run; any issue makes it NOT_SUPPORTED. */
export const canRun = (wf: DraftWorkflow | null) => !!wf && wf.candidate.assessment.status === 'READY'

/** Saves new steps; the server checks the workflow again and returns it. */
async function saveSteps(wf: DraftWorkflow, operationIds: number[]): Promise<DraftWorkflow> {
  return fromSaved(await updateWorkflow(wf.id, { operationIds }))
}

const stepIssues = (wf: DraftWorkflow, step: ChainStep) => wf.candidate.assessment.issues.filter((i) => i.step === step.sequence)

/* ---------- readiness drawer ---------- */
const PASSED_CHECKS = ['Stages and artifacts connect', 'Every required field has a source', 'Every operation can be executed']

export function WorkflowReadiness({ wf, onClose }: { wf: DraftWorkflow; onClose: () => void }) {
  const { go } = useApp()
  const { issues, warnings } = wf.candidate.assessment
  const ready = canRun(wf)
  return (
    <div className="fixed inset-0 z-40 bg-ink/30" onClick={onClose}>
      <aside className="slidein absolute right-0 top-0 flex h-full w-[520px] max-w-full flex-col bg-white shadow-2xl" onClick={(e) => e.stopPropagation()} aria-label="Workflow readiness">
        <div className="flex items-start justify-between border-b border-line p-5">
          <div>
            <h2 className="text-lg font-semibold">Workflow readiness</h2>
            <p className="mt-0.5 text-[13px] text-ink2">{ready ? 'Ready to run.' : 'These operations cannot be connected as they stand.'}</p>
          </div>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 hover:bg-slate-100"><X size={18} /></button>
        </div>
        <div className="flex-1 space-y-2 overflow-y-auto p-5">
          {ready
            ? PASSED_CHECKS.map((c) => (
                <p key={c} className="flex items-center gap-2.5 text-sm"><CheckCircle2 size={18} className="shrink-0 text-ok" /> {c}</p>
              ))
            : issues.map((i, n) => (
                <p key={n} className="flex gap-2 rounded-lg border border-err-bd bg-err-bg p-3 text-[13px] text-err">
                  <XCircle size={16} className="mt-0.5 shrink-0" />
                  <span>{i.step && <span className="font-semibold">Step {i.step}: </span>}{i.message}</span>
                </p>
              ))}
          {warnings.map((w) => (
            <p key={w} className="flex gap-2 rounded-lg border border-warn-bd bg-warn-bg p-3 text-[13px] text-ink2">
              <AlertTriangle size={16} className="mt-0.5 shrink-0 text-warn" /> {w}
            </p>
          ))}
        </div>
        <div className="flex justify-end border-t border-line bg-slate-50 p-4">
          <Btn variant="primary" disabled={!ready} onClick={() => { onClose(); go('test') }}><Play size={14} /> Test workflow</Btn>
        </div>
      </aside>
    </div>
  )
}

/* ---------- operation panel ---------- */
const TRANSFORM_LABEL: Record<string, string> = {
  DIRECT: 'Copy',
  CONSTANT: 'Fixed value',
  FIRST_NON_NULL: 'First available',
  DATE_FORMAT: 'Date format',
  DECIMAL: 'Number',
  COUNTRY_NAME_TO_ISO2: 'Country code',
  REMOVE_WHITESPACE: 'Remove spaces',
  ARRAY_ITEM: 'List item',
  ENVIRONMENT: 'Deployment setting',
  ARTIFACT: 'Document',
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5 text-[13px]">
      <span className="shrink-0 text-ink3">{k}</span>
      <span className="min-w-0 break-words text-right font-medium">{v}</span>
    </div>
  )
}

const ENCODING_LABEL: Record<string, string> = { BASE64: 'Base64', MD5_HEX: 'MD5 checksum' }

/** Says in words where a request field's value comes from, or where a response field is stored. */
function sourceOf(m: MappingView): React.ReactNode {
  switch (m.transform) {
    case 'CONSTANT':
      return <>Fixed value <code className="font-mono text-ink">{typeof m.fixedValue === 'string' ? m.fixedValue : JSON.stringify(m.fixedValue)}</code></>
    case 'ARTIFACT': {
      const doc = ARTIFACT_LABEL[(m.artifact ?? 'UNKNOWN') as keyof typeof ARTIFACT_LABEL] ?? m.artifact
      const enc = (m.encodings ?? []).map((e) => ENCODING_LABEL[e] ?? e)
      return <>The {doc} document{enc.length ? ` (${[...enc].reverse().join(' of ')})` : ''}</>
    }
    case 'ENVIRONMENT':
      return 'A deployment setting'
    default: {
      const how = m.transform === 'DIRECT' ? '' : ` (${TRANSFORM_LABEL[m.transform] ?? m.transform})`
      return <>Invoice field <code className="font-mono text-ink">{m.canonicalField}</code>{how}</>
    }
  }
}

function MappingTable({ rows, input }: { rows: MappingView[]; input: boolean }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full text-left text-[11px]">
        <thead className="bg-slate-50 text-ink3">
          <tr>{(input ? ['Request field', 'Filled with'] : ['Response field', 'Stored as invoice field']).map((h) => <th key={h} className="px-2 py-1.5 font-semibold">{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, n) => (
            <tr key={n} className="border-t border-line align-top">
              <td className="break-all px-2 py-1.5 font-mono">{r.operationField}{input && r.required && <span className="text-err">*</span>}</td>
              <td className="px-2 py-1.5 text-ink2">
                {input ? sourceOf(r) : <><code className="font-mono text-ink">{r.canonicalField}</code>{r.transform !== 'DIRECT' && ` (${TRANSFORM_LABEL[r.transform] ?? r.transform})`}</>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function OpPanel({ wf, step, ops, onReplace, onClose }: { wf: DraftWorkflow; step: ChainStep; ops: Map<number, CatalogOperation>; onReplace: (id: number) => void; onClose: () => void }) {
  const op = ops.get(step.operationId)
  const st = stageOf(step)
  const [mappings, setMappings] = useState<MappingView[] | null>(null)
  const [tech, setTech] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    setMappings(null)
    fetchMappings(step.operationId, controller.signal).then(setMappings).catch(() => setMappings([]))
    return () => controller.abort()
  }, [step.operationId])
  const alternatives = [...ops.values()].filter((o) => o.chainable && o.id !== step.operationId && o.stage === step.stage && o.inputArtifact === step.input && o.outputArtifact === step.output)
  const issues = stepIssues(wf, step)
  const v = verificationOf(step.verificationStatus)
  const inputs = mappings?.filter((m) => m.direction === 'CANONICAL_TO_INPUT') ?? []
  const outputs = mappings?.filter((m) => m.direction === 'OUTPUT_TO_CANONICAL') ?? []
  return (
    <aside className="slidein flex w-[392px] shrink-0 flex-col border-l border-line bg-white" aria-label="Operation details">
      <div className={cx('h-1', st ? STAGE[st].solid : 'bg-slate-300')} />
      <div className="flex items-start justify-between p-4 pb-3">
        <div>
          {st && <StageTag stage={st} small />}
          <h2 className="mt-2 text-[17px] font-semibold leading-tight">{op?.name ?? step.name}</h2>
          <div className="text-[13px] text-ink2">{op?.provider}</div>
        </div>
        <button onClick={onClose} aria-label="Close panel" className="rounded p-1 hover:bg-slate-100"><X size={16} /></button>
      </div>
      <div className="flex-1 space-y-5 overflow-y-auto px-4 pb-6">
        {op?.description && <p className="text-[13px] leading-relaxed text-ink2">{op.description}</p>}
        <div className="flex items-center gap-2 rounded-lg bg-slate-50 p-3">
          <ArtifactTag>{ARTIFACT_LABEL[step.input]}</ArtifactTag>
          <ArrowRight size={14} className="text-ink3" />
          <ArtifactTag>{ARTIFACT_LABEL[step.output]}</ArtifactTag>
        </div>
        {issues.length > 0 && (
          <div className="space-y-1.5">
            {issues.map((i, n) => (
              <p key={n} className="flex gap-2 rounded-lg border border-err-bd bg-err-bg p-2.5 text-[13px] text-err">
                <AlertTriangle size={14} className="mt-0.5 shrink-0" />
                {i.message}
              </p>
            ))}
          </div>
        )}
        <section>
          <h3 className="mb-1 text-xs font-bold uppercase tracking-wide text-ink3">Operation</h3>
          <div className="divide-y divide-line">
            <Row k="Verification" v={<Badge kind={{ ok: 'verified', warn: 'unverified', err: 'unavailable', neutral: 'skipped' }[v.tone] as BadgeKind}>{v.label}</Badge>} />
            {op && <Row k="Contract source" v={SOURCE_LABEL[op.contractSource] ?? op.contractSource} />}
            {op && <Row k="Request" v={`${op.method} · ${op.requestContentType}`} />}
            {op && <Row k="Authentication" v={authLabel(op.authMethod)} />}
            {op && (st === 'validation' || st === 'communication') && <Row k="Outcome rule" v={op.outcomeRule ? 'Reviewed' : 'Missing'} />}
            {op && op.timeoutSeconds > 0 && <Row k="Timeout" v={`${op.timeoutSeconds} s`} />}
          </div>
        </section>
        <section>
          <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-ink3">Compatible alternatives</h3>
          {alternatives.length === 0 ? (
            <p className="text-[13px] text-ink3">None</p>
          ) : (
            <div className="space-y-2">
              {alternatives.map((a) => (
                <div key={a.id} className="rounded-lg border border-line p-3">
                  <div className="text-[13px] font-semibold">{a.name}</div>
                  <div className="text-xs text-ink3">{a.provider}</div>
                  <Btn size="sm" className="mt-2" onClick={() => onReplace(a.id)}>Use this instead</Btn>
                </div>
              ))}
            </div>
          )}
        </section>
        <section>
          <button onClick={() => setTech(!tech)} aria-expanded={tech} className="flex w-full items-center justify-between rounded-lg border border-line px-3 py-2.5 text-sm font-semibold hover:bg-slate-50">
            Field mappings {mappings && <span className="ml-auto mr-2 text-xs font-normal text-ink3">{mappings.length}</span>}
            {tech ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
          </button>
          {tech && (
            <div className="fadeup mt-2 space-y-3">
              {!mappings ? (
                <div className="flex items-center gap-2 text-[13px] text-ink2"><Spinner size={14} /> Loading mappings…</div>
              ) : mappings.length === 0 ? (
                <p className="text-[13px] text-ink3">No field mappings</p>
              ) : (
                <>
                  {inputs.length > 0 && (<div><div className="mb-1 text-xs font-semibold text-ink2">Request</div><MappingTable rows={inputs} input /></div>)}
                  {outputs.length > 0 && (<div><div className="mb-1 text-xs font-semibold text-ink2">Response</div><MappingTable rows={outputs} input={false} /></div>)}
                  {inputs.some((m) => m.required) && <p className="text-xs text-ink3"><span className="text-err">*</span> required by the operation</p>}
                </>
              )}
            </div>
          )}
        </section>
      </div>
    </aside>
  )
}

/* ---------- lanes canvas (hand-built, the original look) ---------- */
const H = 800
const LANES: { s: Stage; y: number; h: number }[] = [
  { s: 'transformation', y: 0, h: 240 },
  { s: 'validation', y: 240, h: 320 },
  { s: 'communication', y: 560, h: 240 },
]
const LANE_Y: Record<Stage, number> = { transformation: 120, validation: 360, communication: 680 }
/** The rejected path stays in the Validation lane: it is the validator's answer, not a communication step. */
const REJECT_Y = 495
const CARD_W = 200
const CARD_H = 152
const GAP = 150

interface Layout {
  width: number
  startY: number
  cards: { step: ChainStep; x: number; y: number }[]
  edges: { d: string; tone?: 'ok' | 'err' }[]
  chips: { x: number; y: number; label: string }[]
  gateway: { x: number; y: number } | null
  reject: { x: number; y: number } | null
  ends: { x: number; y: number; label: string }[]
}

function layout(steps: ChainStep[], input: string): Layout {
  const yOf = (s: ChainStep) => LANE_Y[stageOf(s) ?? 'transformation']
  const startY = steps.length ? yOf(steps[0]) : LANE_Y.transformation
  const out: Layout = { width: 0, startY, cards: [], edges: [], chips: [], gateway: null, reject: null, ends: [] }
  /** dropAt moves the vertical part of a lane change, e.g. past the rejected branch. */
  const connect = (x1: number, y1: number, x2: number, y2: number, label?: string, tone?: 'ok' | 'err', dropAt?: number) => {
    const m = dropAt ?? (x1 + x2) / 2
    out.edges.push({ d: y1 === y2 ? `M${x1},${y1} H${x2}` : `M${x1},${y1} H${m} V${y2} H${x2}`, tone })
    if (!label) return
    if (y1 === y2) out.chips.push({ x: (x1 + x2) / 2, y: y1, label })
    else if (dropAt !== undefined) out.chips.push({ x: (x1 + dropAt) / 2, y: y1, label })
    else out.chips.push({ x: m, y: (y1 + y2) / 2, label })
  }
  out.edges.push({ d: `M73,${startY} H110` })
  let right = 240
  let y = startY
  let carried = input
  let afterGateway = false
  steps.forEach((step, n) => {
    // After the gateway, the passing path runs along the validation row past the rejected branch.
    const pastReject = afterGateway && out.reject ? out.reject.x + CARD_W + 200 : undefined
    const x = pastReject !== undefined ? pastReject + 60 : right + GAP + (n === 0 ? 30 : 0)
    const cy = yOf(step)
    connect(right, y, x, cy, carried, afterGateway ? 'ok' : undefined, pastReject)
    afterGateway = false
    out.cards.push({ step, x, y: cy })
    right = x + CARD_W
    y = cy
    carried = ARTIFACT_LABEL[step.output]
    if (stageOf(step) === 'validation' && n < steps.length - 1) {
      // Later steps only run when validation passes; otherwise the executor stops and
      // returns the validator's errors. With no later step there is nothing to branch.
      const gx = right + 170
      connect(right, y, gx - 38, y, carried)
      out.gateway = { x: gx, y }
      out.reject = { x: gx + 80, y: REJECT_Y }
      out.edges.push({ d: `M${gx},${y + 38} V${REJECT_Y} H${gx + 80}`, tone: 'err' })
      out.ends.push({ x: gx + 80 + CARD_W + 160, y: REJECT_Y, label: 'End (rejected)' })
      out.edges.push({ d: `M${gx + 80 + CARD_W},${REJECT_Y} H${gx + 80 + CARD_W + 143}` })
      out.chips.push({ x: gx + 80 + CARD_W + 75, y: REJECT_Y, label: ARTIFACT_LABEL.VALIDATION_REPORT })
      right = gx + 38
      carried = ARTIFACT_LABEL[step.input]
      afterGateway = true
    }
  })
  const last = steps[steps.length - 1]
  const endX = right + 170
  const lastLabel = afterGateway ? ARTIFACT_LABEL.VALIDATION_REPORT : last ? ARTIFACT_LABEL[last.output] : input
  connect(right, y, endX - 17, y, lastLabel, afterGateway ? 'ok' : undefined)
  out.ends.push({ x: endX, y, label: out.gateway && last && stageOf(last) === 'communication' ? 'End (sent)' : 'End' })
  out.width = Math.max(endX, ...out.ends.map((e) => e.x)) + 80
  return out
}

function Chip({ x, y, label }: { x: number; y: number; label: string }) {
  return (
    <div style={{ left: x, top: y, transform: 'translate(-50%,-50%)' }} className="absolute flex items-center gap-1 whitespace-nowrap rounded-md border border-art-bd bg-art-bg px-2 py-1 text-[11px] font-semibold text-art shadow-sm">
      <FileCode2 size={11} />
      {label}
    </div>
  )
}

function Circle({ x, y, end, label }: { x: number; y: number; end?: boolean; label: string }) {
  return (
    <div aria-label={label} title={label} style={{ left: x - 17, top: y - 17 }} className={cx('absolute grid h-[34px] w-[34px] place-items-center rounded-full bg-white', end ? 'border-[3px] border-ink' : 'border-2 border-ink')}>
      {end ? <span className="h-3 w-3 rounded-full bg-ink" /> : <Play size={12} className="ml-0.5 fill-ink" />}
    </div>
  )
}

function StepCard({ wf, step, op, x, y, selected, onClick }: { wf: DraftWorkflow; step: ChainStep; op?: CatalogOperation; x: number; y: number; selected: boolean; onClick: () => void }) {
  const st = stageOf(step) ?? 'transformation'
  const v = verificationOf(step.verificationStatus)
  const problems = stepIssues(wf, step)
  return (
    <button
      onClick={onClick}
      aria-label={`Step ${step.sequence}: ${op?.name ?? step.name}`}
      style={{ left: x, top: y - CARD_H / 2, width: CARD_W, height: CARD_H }}
      className={cx(
        'absolute overflow-hidden rounded-lg border bg-white text-left shadow-[0_1px_3px_rgba(15,27,51,0.1)] transition-shadow hover:shadow-md',
        selected ? 'border-navy ring-2 ring-navy/25' : problems.length ? 'border-err-bd' : 'border-line',
      )}
    >
      <div className={cx('flex h-6 items-center justify-between px-2.5 text-[10px] font-bold uppercase tracking-wide text-white', STAGE[st].solid)}>
        {step.sequence}. {STAGE[st].label}
        {problems.length || v.tone !== 'ok' ? <AlertTriangle size={13} /> : <CheckCircle2 size={13} />}
      </div>
      <div className="px-2.5 pt-2">
        <div className="line-clamp-2 text-[13px] font-semibold leading-tight">{op?.name ?? step.name}</div>
        <div className="truncate text-[11px] text-ink3">{op?.provider}</div>
        <div className="mt-1.5 space-y-0.5 text-[10.5px] text-ink2">
          <div className="flex gap-1.5"><span className="w-9 text-ink3">Input</span><span className="truncate font-medium">{ARTIFACT_LABEL[step.input]}</span></div>
          <div className="flex gap-1.5"><span className="w-9 text-ink3">Output</span><span className="truncate font-medium">{ARTIFACT_LABEL[step.output]}</span></div>
        </div>
        <div className="mt-1.5">
          <span className={cx('inline-flex max-w-full items-center gap-1 truncate rounded-full border px-2 py-0.5 text-[10.5px] font-semibold', problems.length ? 'border-err-bd bg-err-bg text-err' : v.tone === 'ok' ? 'border-ok-bd bg-ok-bg text-ok' : 'border-warn-bd bg-warn-bg text-warn')}>
            {problems.length ? `${problems.length} ${problems.length === 1 ? 'problem' : 'problems'}` : v.label}
          </span>
        </div>
      </div>
    </button>
  )
}

/* ---------- single-lane canvas (bpmn-js's own plain rendering) ---------- */

/** Owns the bpmn-js Viewer lifecycle for the 'single lane' view: imports fresh XML whenever the workflow's steps change, maps clicks on a task back to its step, and highlights the selected step. Only ever rendered in single-lane mode — the three stage lanes use the hand-built canvas above instead, so this always asks for the single, lane-free layout. */
function BpmnCanvas({ wf, selected, onSelect, viewerRef }: {
  wf: DraftWorkflow
  selected: number | null
  onSelect: (sequence: number | null) => void
  viewerRef: React.MutableRefObject<NavigatedViewer | null>
}) {
  const container = useRef<HTMLDivElement>(null)
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect

  useEffect(() => {
    if (!container.current) return
    const viewer = new NavigatedViewer({ container: container.current })
    viewerRef.current = viewer
    viewer.get<{ on: (event: string, cb: (e: { element: { id: string; type: string } }) => void) => void }>('eventBus').on('element.click', (e) => {
      const m = /^step-(\d+)$/.exec(e.element.id)
      onSelectRef.current(m ? Number(m[1]) : null)
    })
    return () => {
      viewer.destroy()
      viewerRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return
    viewer.importXML(toBpmnXml(wf, { layout: 'single', includeLanes: false }))
      .then(() => viewer.get<{ zoom: (v: string) => void }>('canvas').zoom('fit-viewport'))
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wf])

  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return
    const canvas = viewer.get<{ addMarker: (id: string, c: string) => void; removeMarker: (id: string, c: string) => void }>('canvas')
    const elementRegistry = viewer.get<{ get: (id: string) => unknown }>('elementRegistry')
    wf.candidate.steps.forEach((s) => {
      const id = `step-${s.sequence}`
      if (!elementRegistry.get(id)) return
      if (s.sequence === selected) canvas.addMarker(id, 'bp-selected')
      else canvas.removeMarker(id, 'bp-selected')
    })
  }, [selected, wf, viewerRef])

  return <div ref={container} className="absolute inset-0" onClick={(e) => { if (e.target === container.current) onSelect(null) }} />
}

/* ---------- builder ---------- */
export function WorkflowBuilder({ wf, setWf, openReadiness }: { wf: DraftWorkflow | null; setWf: (w: DraftWorkflow) => void; openReadiness: () => void }) {
  const { go, toast } = useApp()
  const [ops, setOps] = useState<Map<number, CatalogOperation>>(new Map())
  const [sel, setSel] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [renaming, setRenaming] = useState(false)
  const [draftName, setDraftName] = useState('')
  const [mode, setMode] = useState<'lanes' | 'single'>('lanes')
  const [exporting, setExporting] = useState(false)
  const [zoom, setZoom] = useState(0.7)
  const viewerRef = useRef<NavigatedViewer | null>(null)
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchOperations(controller.signal)
      .then((list) => setOps(new Map(list.map((o) => [o.id, o]))))
      .catch((e: Error) => { if (e.name !== 'AbortError') toast(e.message, 'err') })
    return () => controller.abort()
  }, [toast])

  const plan = useMemo(() => (wf ? layout(wf.candidate.steps, ARTIFACT_LABEL[wf.requirement.inputArtifactType ?? 'UNKNOWN']) : null), [wf])
  const fitLanes = () => {
    if (box.current && plan) setZoom(Math.max(0.3, Math.min(1, +((box.current.clientWidth - 24) / plan.width).toFixed(2))))
  }
  useEffect(fitLanes, [plan?.width])

  if (!wf || !plan)
    return (
      <div className="mx-auto max-w-[640px] p-8 pt-24">
        <Card>
          <EmptyState icon={<Info size={22} />} title="No workflow open" action={<Btn variant="primary" onClick={() => go('create')}>Create workflow</Btn>} />
        </Card>
      </div>
    )

  const selected = wf.candidate.steps.find((s) => s.sequence === sel)
  const replace = (operationId: number) => {
    if (!selected) return
    const ids = wf.candidate.steps.map((s) => (s.sequence === selected.sequence ? operationId : s.operationId))
    setBusy(true)
    saveSteps(wf, ids)
      .then((next) => {
        setWf(next)
        toast(`Step ${selected.sequence} replaced`, 'info')
      })
      .catch((e: Error) => toast(e.message, 'err'))
      .finally(() => setBusy(false))
  }
  const zoomBy = (factor: number) => {
    const canvas = viewerRef.current?.get<{ zoom: (v?: number | string) => number }>('canvas')
    if (canvas) canvas.zoom(canvas.zoom() * factor)
  }
  const fitSingle = () => viewerRef.current?.get<{ zoom: (v: string) => void }>('canvas').zoom('fit-viewport')
  const clampLanes = (z: number) => Math.max(0.3, Math.min(1.4, +z.toFixed(2)))
  const exportBpmn = async () => {
    setExporting(true)
    try {
      // Built fresh from wf, not serialized from the live viewer: the canvas's
      // lanes are this app's own stage visualisation, not process semantics, so
      // the downloadable file never has them baked in — independent of whatever
      // the hide/show-lanes toggle currently shows on screen.
      const xml = toBpmnXml(wf, { includeLanes: false })
      const blob = new Blob([xml], { type: 'application/xml' })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${wf.name.replace(/[^a-z0-9]+/gi, '_') || 'workflow'}.bpmn`
      a.click()
      URL.revokeObjectURL(a.href)
    } catch (e) {
      toast((e as Error).message, 'err')
    } finally {
      setExporting(false)
    }
  }
  const rename = () => {
    const name = draftName.trim()
    if (!name || name === wf.name) return setRenaming(false)
    setBusy(true)
    updateWorkflow(wf.id, { name })
      .then((saved) => {
        setWf(fromSaved(saved))
        setRenaming(false)
        toast('Workflow renamed')
      })
      .catch((e: Error) => toast(e.message, 'err'))
      .finally(() => setBusy(false))
  }
  const status = ASSESSMENT_BADGE[wf.candidate.assessment.status]
  const issues = wf.candidate.assessment.issues.length
  const warnings = wf.candidate.assessment.warnings.length
  const line = '#8a94a8'
  return (
    <div className="flex h-full min-h-0">
      <div className="relative flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-2 border-b border-line bg-white px-4 py-2">
          <Btn size="sm" onClick={() => { setDraftName(wf.name); setRenaming(true) }}><Pencil size={13} /> Rename</Btn>
          <Btn size="sm" onClick={() => go('dashboard')}><ArrowLeft size={14} /> All workflows</Btn>
          <Btn size="sm" onClick={() => setMode(mode === 'lanes' ? 'single' : 'lanes')} aria-pressed={mode === 'single'}>
            {mode === 'lanes' ? <StretchHorizontal size={13} /> : <Rows3 size={13} />} {mode === 'lanes' ? 'Switch to single lane' : 'Switch to lanes'}
          </Btn>
          <Btn size="sm" disabled={exporting} onClick={exportBpmn}>
            {exporting ? <Spinner size={13} /> : <Download size={13} />} Export .bpmn
          </Btn>
          {busy && <span className="flex items-center gap-1.5 text-xs text-ink2"><Spinner size={13} /> Saving…</span>}
          <div className="ml-auto">
            <button onClick={openReadiness} className="flex items-center gap-2 rounded-lg border border-line bg-white px-3 py-1.5 text-[13px] font-semibold hover:bg-slate-50">
              <ShieldCheck size={15} /> Readiness
              <Badge kind={status.kind}>{status.label}</Badge>
              {issues > 0 && <Badge kind="action">{issues}</Badge>}
              {warnings > 0 && <Badge kind="warning">{warnings}</Badge>}
            </button>
          </div>
        </div>
        {mode === 'single' ? (
          <div className="relative min-h-0 flex-1 bg-canvas">
            <BpmnCanvas wf={wf} selected={sel} onSelect={setSel} viewerRef={viewerRef} />
            <div className="absolute bottom-4 left-4 flex items-center gap-1 rounded-lg border border-line bg-white p-1 shadow-md">
              <Btn size="sm" variant="ghost" aria-label="Zoom out" onClick={() => zoomBy(1 / 1.2)}><ZoomOut size={15} /></Btn>
              <Btn size="sm" variant="ghost" aria-label="Zoom in" onClick={() => zoomBy(1.2)}><ZoomIn size={15} /></Btn>
              <Btn size="sm" variant="ghost" aria-label="Fit to screen" onClick={fitSingle}><Maximize size={15} /></Btn>
            </div>
          </div>
        ) : (
          <div className="relative min-h-0 flex-1">
            <div ref={box} className="absolute inset-0 overflow-auto bg-canvas" onClick={() => setSel(null)}>
              <div style={{ width: plan.width * zoom, height: H * zoom }} className="relative">
                <div style={{ width: plan.width, height: H, transform: `scale(${zoom})`, transformOrigin: '0 0' }} className="absolute left-0 top-0" onClick={(e) => e.stopPropagation()}>
                  {LANES.map((l) => (
                    <div key={l.s} style={{ top: l.y, height: l.h, width: plan.width }} className={cx('absolute left-0 border-b', STAGE[l.s].bg, STAGE[l.s].bd)}>
                      <div className={cx('absolute left-0 top-0 h-full w-1.5', STAGE[l.s].solid)} />
                      <div className={cx('absolute left-5 top-3 text-[12px] font-bold uppercase tracking-wider', STAGE[l.s].text)}>{STAGE[l.s].label}</div>
                    </div>
                  ))}
                  <svg width={plan.width} height={H} className="pointer-events-none absolute left-0 top-0">
                    <defs>
                      {[['ar', line], ['ar-ok', '#15803d'], ['ar-err', '#b91c1c']].map(([id, fill]) => (
                        <marker key={id} id={id} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-auto"><path d="M0 0L10 5L0 10z" fill={fill} /></marker>
                      ))}
                    </defs>
                    {plan.edges.map((e, n) => (
                      <path key={n} d={e.d} fill="none" strokeWidth={2} stroke={e.tone === 'ok' ? '#15803d' : e.tone === 'err' ? '#b91c1c' : line} markerEnd={`url(#${e.tone ? 'ar-' + e.tone : 'ar'})`} />
                    ))}
                  </svg>
                  <Circle x={56} y={plan.startY} label="Start" />
                  <div style={{ left: 110, top: plan.startY - 26, width: 130, height: 52 }} className="absolute flex items-center justify-center gap-1.5 rounded-lg border border-tx-bd bg-white px-2 text-center text-[12px] font-semibold text-ink shadow-sm">
                    Receive {INPUT_CHOICES.find((c) => c.id === wf.requirement.inputArtifactType)?.label ?? 'input'}
                  </div>
                  {plan.chips.map((c, n) => <Chip key={n} {...c} />)}
                  {plan.cards.map((c) => (
                    <StepCard key={c.step.sequence} wf={wf} step={c.step} op={ops.get(c.step.operationId)} x={c.x} y={c.y} selected={sel === c.step.sequence} onClick={() => setSel(c.step.sequence)} />
                  ))}
                  {plan.gateway && (
                    <>
                      <div style={{ left: plan.gateway.x - 38, top: plan.gateway.y - 38 }} className="absolute h-[76px] w-[76px]" aria-label="Valid? decision">
                        <div className="absolute inset-[9px] rotate-45 border-2 border-va bg-white" />
                        <div className="absolute inset-0 grid place-items-center text-[13px] font-bold text-va">Valid?</div>
                      </div>
                      <span className="absolute rounded-full border border-ok-bd bg-ok-bg px-2 py-0.5 text-[11px] font-bold text-ok" style={{ left: plan.gateway.x + 60, top: plan.gateway.y - 22, transform: 'translate(-50%,-50%)' }}>Valid</span>
                      <span className="absolute rounded-full border border-err-bd bg-err-bg px-2 py-0.5 text-[11px] font-bold text-err" style={{ left: plan.gateway.x, top: plan.gateway.y + 70, transform: 'translate(-50%,-50%)' }}>Invalid</span>
                    </>
                  )}
                  {plan.reject && (
                    <div style={{ left: plan.reject.x, top: plan.reject.y - 44, width: CARD_W, height: 88 }} className="absolute overflow-hidden rounded-lg border border-err-bd bg-white shadow-sm">
                      <div className="flex h-6 items-center bg-slate-500 px-2.5 text-[10px] font-bold uppercase tracking-wide text-white">Built in</div>
                      <div className="px-2.5 pt-2">
                        <div className="text-[13px] font-semibold">Stop and return the validation errors</div>
                        <div className="text-[11px] text-ink3">Later steps are not run</div>
                      </div>
                    </div>
                  )}
                  {plan.ends.map((e) => <Circle key={e.label} x={e.x} y={e.y} end label={e.label} />)}
                </div>
              </div>
            </div>
            <div className="absolute bottom-4 left-4 flex items-center gap-1 rounded-lg border border-line bg-white p-1 shadow-md">
              <Btn size="sm" variant="ghost" aria-label="Zoom out" onClick={() => setZoom(clampLanes(zoom - 0.1))}><ZoomOut size={15} /></Btn>
              <span className="w-10 text-center text-xs font-semibold tabular-nums">{Math.round(zoom * 100)}%</span>
              <Btn size="sm" variant="ghost" aria-label="Zoom in" onClick={() => setZoom(clampLanes(zoom + 0.1))}><ZoomIn size={15} /></Btn>
              <Btn size="sm" variant="ghost" aria-label="Fit to screen" onClick={fitLanes}><Maximize size={15} /></Btn>
            </div>
          </div>
        )}
      </div>
      {selected && <OpPanel wf={wf} step={selected} ops={ops} onReplace={replace} onClose={() => setSel(null)} />}
      {renaming && (
        <Dialog title="Rename workflow" onClose={() => setRenaming(false)} footer={<><Btn onClick={() => setRenaming(false)}>Cancel</Btn><Btn variant="primary" disabled={busy || !draftName.trim()} onClick={rename}>Save</Btn></>}>
          <input autoFocus aria-label="Workflow name" maxLength={200} className={inputCls} value={draftName} onChange={(e) => setDraftName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && rename()} />
        </Dialog>
      )}
    </div>
  )
}

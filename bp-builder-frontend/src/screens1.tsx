import { useEffect, useRef, useState } from 'react'
import {
  AlertTriangle,
  ArrowLeft,
  Info,
  ArrowRight,
  Check,
  FileImage,
  FileText,
  FileUp,
  Maximize,
  MessageSquareText,
  Mic,
  MousePointerSquareDashed,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Trash2,
  Workflow,
  X,
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
  PageHead,
  Segmented,
  Spinner,
  STAGE,
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
  deleteWorkflow,
  describeSketch,
  assessWorkflow,
  fetchOperations,
  interpretRequirement,
  listWorkflows,
  planWorkflow,
  stageOf,
  validates,
  type ArtifactType,
  type RunStatus,
  type CatalogOperation,
  type ChainCandidate,
  type Interpretation,
  type PlanResponse,
  type Requirement,
  type WorkflowSummary,
} from './api'
import { ASSESSMENT_BADGE } from './WorkflowBuilder'
import { RUN_LABEL, when } from './runs'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import { toPreviewXml, type PreviewEdge, type PreviewNode } from './bpmn'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'

/* ================= Dashboard ================= */

export function Dashboard({ onOpen }: { onOpen: (id: number) => Promise<void> }) {
  const { go, toast } = useApp()
  const [list, setList] = useState<WorkflowSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [q, setQ] = useState('')
  const [f, setF] = useState<'ALL' | WorkflowSummary['status']>('ALL')
  const [opening, setOpening] = useState<number | null>(null)
  const [removing, setRemoving] = useState<WorkflowSummary | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setError(null)
    listWorkflows(controller.signal)
      .then(setList)
      .catch((e: Error) => {
        if (e.name !== 'AbortError') setError(e.message)
      })
    return () => controller.abort()
  }, [reload])

  const shown = (list ?? []).filter((w) => (f === 'ALL' || w.status === f) && w.name.toLowerCase().includes(q.toLowerCase()))
  const open = (id: number) => {
    setOpening(id)
    onOpen(id).finally(() => setOpening(null))
  }
  const remove = (w: WorkflowSummary) => {
    deleteWorkflow(w.id)
      .then(() => {
        setList((l) => (l ?? []).filter((x) => x.id !== w.id))
        toast('Workflow deleted')
      })
      .catch((e: Error) => toast(e.message, 'err'))
      .finally(() => setRemoving(null))
  }
  const label = (choices: { id: string; label: string }[], id: string) => choices.find((c) => c.id === id)?.label ?? id
  return (
    <div className="mx-auto max-w-[1200px] p-8">
      <PageHead
        title="Workflows"
        actions={
          <Btn variant="primary" onClick={() => go('create')}>
            <Plus size={16} /> Create workflow
          </Btn>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative w-72">
          <Search size={15} className="absolute left-3 top-3 text-ink3" />
          <input aria-label="Search workflows" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search workflows" className={cx(inputCls, 'pl-9')} />
        </div>
        <Segmented
          options={[
            { id: 'ALL' as const, label: 'All' },
            { id: 'READY' as const, label: 'Ready' },
            { id: 'NOT_SUPPORTED' as const, label: 'Not supported' },
          ]}
          value={f}
          onChange={setF}
        />
        <span className="ml-auto text-xs text-ink3">{list ? `${shown.length} workflows` : ''}</span>
      </div>
      <Card className="overflow-hidden">
        <div className="grid grid-cols-[minmax(0,2.4fr)_minmax(0,1.2fr)_minmax(0,1.3fr)_minmax(0,1.2fr)_auto] gap-4 border-b border-line bg-slate-50 px-5 py-2.5 text-[11px] font-semibold uppercase tracking-wide text-ink3">
          <span>Workflow</span>
          <span>Steps</span>
          <span>Last run</span>
          <span>Status</span>
          <span className="w-[150px]">Actions</span>
        </div>
        {error ? (
          <EmptyState tone="error" icon={<X size={22} />} title="Could not load workflows" body={error} action={<Btn onClick={() => setReload((n) => n + 1)}><RefreshCw size={14} /> Try again</Btn>} />
        ) : !list ? (
          <div className="flex items-center justify-center gap-2 py-12 text-sm text-ink2"><Spinner /> Loading workflows…</div>
        ) : shown.length === 0 ? (
          <EmptyState
            icon={<Workflow size={22} />}
            title={list.length ? 'No workflows match' : 'No workflows yet'}
            body={list.length ? 'Try a different search or filter.' : undefined}
            action={<Btn variant="primary" onClick={() => go('create')}><Plus size={16} /> Create workflow</Btn>}
          />
        ) : (
          shown.map((w) => {
            const status = ASSESSMENT_BADGE[w.status]
            const run = w.lastRunStatus ? RUN_LABEL[w.lastRunStatus as RunStatus] ?? { kind: 'info' as BadgeKind, label: w.lastRunStatus } : null
            return (
              <div key={w.id} className="grid grid-cols-[minmax(0,2.4fr)_minmax(0,1.2fr)_minmax(0,1.3fr)_minmax(0,1.2fr)_auto] items-center gap-4 border-b border-line px-5 py-4 last:border-0 hover:bg-slate-50/70">
                <div className="min-w-0">
                  <button onClick={() => open(w.id)} className="block max-w-full truncate text-left text-[15px] font-semibold hover:underline">
                    {w.name}
                  </button>
                  <div className="mt-1 text-xs text-ink3">
                    {label(INPUT_CHOICES, w.inputArtifactType)} → {label(GOAL_CHOICES, w.goalArtifactType)}
                  </div>
                  <div className="mt-0.5 text-xs text-ink3">Modified {when(w.updatedAt)}</div>
                </div>
                <div className="text-[13px] text-ink2">{w.stepCount} {w.stepCount === 1 ? 'step' : 'steps'}</div>
                <div className="text-[13px] text-ink2">
                  {run ? (
                    <>
                      <Badge kind={run.kind}>{run.label}</Badge>
                      <div className="mt-1 text-xs text-ink3">{when(w.lastRunAt!)}</div>
                    </>
                  ) : (
                    <span className="text-ink3">Not run yet</span>
                  )}
                </div>
                <div><Badge kind={status.kind}>{status.label}</Badge></div>
                <div className="flex w-[150px] gap-1">
                  <Btn size="sm" onClick={() => open(w.id)} disabled={opening !== null}>
                    {opening === w.id ? <Spinner size={12} /> : null} Open
                  </Btn>
                  <Btn size="sm" variant="ghost" onClick={() => setRemoving(w)}>
                    <Trash2 size={12} /> Delete
                  </Btn>
                </div>
              </div>
            )
          })
        )}
      </Card>
      {removing && (
        <Dialog
          title="Delete workflow?"
          onClose={() => setRemoving(null)}
          footer={<><Btn onClick={() => setRemoving(null)}>Cancel</Btn><Btn variant="danger" onClick={() => remove(removing)}>Delete</Btn></>}
        >
          “{removing.name}” and its run history will be deleted. This cannot be undone.
        </Dialog>
      )}
    </div>
  )
}

/* ================= Create: choose method ================= */
export type Method = 'nl' | 'bpmn' | 'visual' | 'sketch'
const METHODS: { id: Method; icon: React.ReactNode; title: string; body: string }[] = [
  {
    id: 'nl',
    icon: <MessageSquareText size={22} />,
    title: 'Describe in natural language',
    body: 'Write the process in your own words.',
  },
  {
    id: 'bpmn',
    icon: <FileUp size={22} />,
    title: 'Upload BPMN',
    body: 'A .bpmn or .xml file.',
  },
  {
    id: 'visual',
    icon: <MousePointerSquareDashed size={22} />,
    title: 'Build visually',
    body: 'Pick the steps for each stage.',
  },
  {
    id: 'sketch',
    icon: <FileImage size={22} />,
    title: 'Upload a sketch',
    body: 'A photo or scan: PNG, JPG or PDF.',
  },
]

const ROUTE: ArtifactType[] = ['PDF_DOCUMENT', 'CANONICAL_INVOICE', 'UBL_XML', 'VALIDATION_REPORT', 'DELIVERY_RECEIPT']
const STAGE_PRODUCING: Partial<Record<ArtifactType, Stage>> = {
  CANONICAL_INVOICE: 'transformation',
  UBL_XML: 'transformation',
  VALIDATION_REPORT: 'validation',
  DELIVERY_RECEIPT: 'communication',
}

/** The stages between input and goal; without them, the full fixed order. */
function StageStrip({ input, goal }: { input?: ArtifactType | null; goal?: ArtifactType | null }) {
  let stages: Stage[] = ['transformation', 'validation', 'communication']
  if (input !== undefined || goal !== undefined) {
    const from = input ? ROUTE.indexOf(input === 'IMAGE_DOCUMENT' ? 'PDF_DOCUMENT' : input) : -1
    const to = goal ? ROUTE.indexOf(goal) : -1
    stages = from < 0 || to <= from ? [] : [...new Set(ROUTE.slice(from + 1, to + 1).map((a) => STAGE_PRODUCING[a]!))]
  }
  if (!stages.length) return null
  return (
    <div className="flex items-center gap-2 text-xs font-semibold">
      {stages.map((s, i) => (
        <span key={s} className="flex items-center gap-2">
          <StageTag stage={s} />
          {i < stages.length - 1 && <ArrowRight size={14} className="text-ink3" />}
        </span>
      ))}
    </div>
  )
}

export function Wizard({
  step,
  children,
  onBack,
  onNext,
  nextLabel,
  nextDisabled,
  extra,
}: {
  step: number
  children: React.ReactNode
  onBack: () => void
  onNext?: () => void
  nextLabel: string
  nextDisabled?: boolean
  extra?: React.ReactNode
}) {
  return (
    <div className="mx-auto max-w-[1040px] p-8">
      <div className="mb-6 flex items-center gap-4">
        <span className="text-xs font-semibold uppercase tracking-wide text-ink3">Step {step} of 3</span>
        <div className="flex gap-1.5">
          {[1, 2, 3].map((i) => (
            <span key={i} className={cx('h-1.5 w-12 rounded-full', i <= step ? 'bg-navy' : 'bg-slate-300')} />
          ))}
        </div>
        <span className="text-xs text-ink3">{['Choose method', 'Describe the process', 'Review requirements'][step - 1]}</span>
      </div>
      {children}
      <div className="mt-8 flex items-center justify-between border-t border-line pt-5">
        <Btn onClick={onBack}>
          <ArrowLeft size={16} /> Back
        </Btn>
        <div className="flex gap-2">
          {extra}
          {onNext && (
            <Btn variant="primary" onClick={onNext} disabled={nextDisabled}>
              {nextLabel} <ArrowRight size={16} />
            </Btn>
          )}
        </div>
      </div>
    </div>
  )
}

export function CreateChoose({
  method,
  setMethod,
}: {
  method: Method
  setMethod: (m: Method) => void
}) {
  const { go } = useApp()
  return (
    <Wizard step={1} onBack={() => go('dashboard')} onNext={() => go('input')} nextLabel="Continue">
      <h1 className="text-2xl font-bold tracking-tight">How would you like to describe the workflow?</h1>
      <div className="mt-6 grid grid-cols-2 gap-4" role="radiogroup">
        {METHODS.map((m) => {
          const on = method === m.id
          return (
            <button
              key={m.id}
              role="radio"
              aria-checked={on}
              onClick={() => setMethod(m.id)}
              className={cx(
                'relative rounded-lg border bg-white p-5 text-left transition-all',
                on ? 'border-navy ring-2 ring-navy/15' : 'border-line hover:border-slate-400',
              )}
            >
              <div className="flex items-start justify-between">
                <div className={cx('grid h-11 w-11 place-items-center rounded-lg', on ? 'bg-navy text-white' : 'bg-slate-100 text-ink2')}>
                  {m.icon}
                </div>
                <span className={cx('grid h-5 w-5 place-items-center rounded-full border', on ? 'border-navy bg-navy text-white' : 'border-slate-300')}>
                  {on && <Check size={12} />}
                </span>
              </div>
              <h3 className="mt-3 text-base font-semibold">{m.title}</h3>
              <p className="mt-1 text-sm text-ink2">{m.body}</p>
            </button>
          )
        })}
      </div>
    </Wizard>
  )
}

/* ================= Create: input ================= */
const STAGES: Stage[] = ['transformation', 'validation', 'communication']

/** A chain picked in the visual editor, with the requirement it implies and whether it is Ready. */
export type VisualChain = { operationIds: number[]; requirement: Requirement; ready: boolean }

/** Catalog operations dragged (or clicked) into their own stage lane; the chain is checked on every change. */
function VisualEditor({ onChange }: { onChange: (chain: VisualChain | null) => void }) {
  const [ops, setOps] = useState<CatalogOperation[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lanes, setLanes] = useState<Record<Stage, number[]>>({ transformation: [], validation: [], communication: [] })
  const [dragging, setDragging] = useState<CatalogOperation | null>(null)
  const [check, setCheck] = useState<ChainCandidate['assessment'] | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchOperations(controller.signal)
      .then((list) => setOps(list.filter((o) => o.chainable && stageOf(o))))
      .catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    return () => controller.abort()
  }, [])

  const byId = new Map((ops ?? []).map((o) => [o.id, o]))
  const ids = STAGES.flatMap((s) => lanes[s])
  const key = ids.join(',')
  useEffect(() => {
    setCheck(null)
    if (!ids.length || !ops) {
      onChange(null)
      return
    }
    const chain = ids.map((id) => byId.get(id)!)
    const requirement: Requirement = {
      inputArtifactType: chain[0].inputArtifact,
      goalArtifactType: chain[chain.length - 1].outputArtifact,
      validationOperationId: chain.find((o) => o.stage === 'VALIDATION')?.id ?? null,
    }
    let current = true
    assessWorkflow(ids, chain[0].inputArtifact)
      .then((c) => {
        if (!current) return
        setCheck(c.assessment)
        onChange({ operationIds: ids, requirement, ready: c.assessment.status === 'READY' })
      })
      .catch((e: Error) => { if (current) setError(e.message) })
    return () => { current = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, ops])

  const add = (o: CatalogOperation) => {
    const s = stageOf(o)!
    if (!lanes[s].includes(o.id)) setLanes({ ...lanes, [s]: [...lanes[s], o.id] })
  }
  const remove = (s: Stage, index: number) => setLanes({ ...lanes, [s]: lanes[s].filter((_, i) => i !== index) })

  if (error) return <p className="text-[13px] text-err">{error}</p>
  if (!ops) return <div className="flex items-center gap-2 text-[13px] text-ink2"><Spinner size={14} /> Loading operations…</div>
  return (
    <div className="grid grid-cols-[280px_minmax(0,1fr)] gap-4">
      <div className="space-y-4">
        {STAGES.map((s) => (
          <div key={s}>
            <div className={cx('mb-1.5 text-xs font-bold uppercase tracking-wide', STAGE[s].text)}>{STAGE[s].label}</div>
            <div className="space-y-1.5">
              {ops.filter((o) => stageOf(o) === s).map((o) => (
                <div
                  key={o.id}
                  draggable
                  onDragStart={() => setDragging(o)}
                  onDragEnd={() => setDragging(null)}
                  onClick={() => add(o)}
                  title="Drag into its stage, or click to add"
                  className={cx('cursor-grab rounded-md border bg-white px-3 py-2 text-[13px] hover:border-slate-400',
                    lanes[s].includes(o.id) ? 'border-line opacity-50' : 'border-line')}
                >
                  <div className="truncate font-medium">{o.name}</div>
                  <div className="mt-0.5 text-[11px] text-ink3">{ARTIFACT_LABEL[o.inputArtifact]} → {ARTIFACT_LABEL[o.outputArtifact]}</div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="space-y-3">
        {STAGES.map((s) => {
          const accepts = !!dragging && stageOf(dragging) === s
          return (
            <div
              key={s}
              onDragOver={(e) => { if (accepts) e.preventDefault() }}
              onDrop={(e) => {
                e.preventDefault()
                if (dragging && accepts) add(dragging)
                setDragging(null)
              }}
              className={cx('min-h-[84px] rounded-lg border-2 border-dashed p-3 transition-colors',
                accepts ? cx(STAGE[s].bd, STAGE[s].bg) : 'border-line bg-white')}
            >
              <div className={cx('mb-2 text-xs font-bold uppercase tracking-wide', STAGE[s].text)}>{STAGE[s].label}</div>
              <div className="flex flex-wrap items-center gap-2">
                {lanes[s].map((id, i) => (
                  <span key={id} className="flex items-center gap-1.5">
                    {i > 0 && <ArrowRight size={12} className="text-ink3" />}
                    <span className="flex items-center gap-2 rounded-md border border-line bg-white px-2.5 py-1.5 text-[13px] shadow-sm">
                      {byId.get(id)?.name}
                      <button aria-label={`Remove ${byId.get(id)?.name}`} onClick={() => remove(s, i)} className="rounded p-0.5 text-ink3 hover:bg-slate-100 hover:text-err">
                        <X size={13} />
                      </button>
                    </span>
                  </span>
                ))}
                {!lanes[s].length && <span className="text-xs text-ink3">Drop here</span>}
              </div>
            </div>
          )
        })}
        {check && (
          <div className="space-y-1.5">
            <Badge kind={check.status === 'READY' ? 'ready' : 'action'}>{check.status === 'READY' ? 'Ready' : 'Not supported'}</Badge>
            {check.issues.slice(0, 3).map((i, n) => (
              <p key={n} className="text-[13px] text-err">{i.step ? `Step ${i.step}: ` : ''}{i.message}</p>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/* ---------- voice dictation (fills the same text box typing does) ---------- */
interface SpeechResultEvent {
  resultIndex: number
  results: { length: number; [i: number]: { isFinal: boolean; 0: { transcript: string } } }
}
interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((e: SpeechResultEvent) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void
  stop(): void
}
const getSpeechRecognition = (): (new () => SpeechRecognitionLike) | undefined =>
  (window as unknown as { SpeechRecognition?: new () => SpeechRecognitionLike; webkitSpeechRecognition?: new () => SpeechRecognitionLike }).SpeechRecognition
  ?? (window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike }).webkitSpeechRecognition

/** Dictation into an existing text value: on start, remembers what was already there so typed text is never wiped, then keeps appending the live transcript to it as speech comes in. English only. */
function useDictation(text: string, setText: (t: string) => void, onError: (msg: string) => void) {
  const [listening, setListening] = useState(false)
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const baseRef = useRef('')
  const supported = !!getSpeechRecognition()

  const stop = () => recognitionRef.current?.stop()
  const start = () => {
    const Ctor = getSpeechRecognition()
    if (!Ctor) {
      onError('Voice input is not supported in this browser. Try Chrome, or type the description instead.')
      return
    }
    baseRef.current = text.trim() ? text.trim() + ' ' : ''
    const recognition = new Ctor()
    recognition.lang = 'en-US'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.onresult = (e) => {
      let finalChunk = ''
      let interim = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i]
        if (r.isFinal) finalChunk += r[0].transcript
        else interim += r[0].transcript
      }
      if (finalChunk) baseRef.current += finalChunk
      setText((baseRef.current + interim).trim())
    }
    recognition.onerror = (e) => {
      setListening(false)
      if (e.error !== 'no-speech' && e.error !== 'aborted') onError(`Voice input stopped: ${e.error}.`)
    }
    recognition.onend = () => setListening(false)
    recognitionRef.current = recognition
    setListening(true)
    recognition.start()
  }
  useEffect(() => () => recognitionRef.current?.stop(), [])

  return { listening, supported, toggle: () => (listening ? stop() : start()) }
}

type BpmnGraph = { nodes: PreviewNode[]; edges: PreviewEdge[] }

/**
 * Reads a .bpmn/.xml file's real task/gateway/event structure and
 * connections — not the file's own diagram layout (DI): that part of the
 * format is optional, often missing or badly laid out for files not drawn in
 * a proper modeller, and we only need the semantic model to recover it.
 * Walks sequenceFlow from the start event rather than trusting document
 * order, so a task is a node here only if the graph actually reaches it.
 *
 * A gateway becomes a real node (there can be as many as the file actually
 * has; this app's own workflows never produce more than one, but an
 * uploaded file reasonably might) so a branch stays visible in the
 * regenerated preview instead of being silently flattened into one chain.
 * Anything else this app has no node type for — a sub-process, a boundary or
 * intermediate event, and so on — is skipped transparently, the same way
 * earlier versions skipped every non-task element: its incoming connection
 * is carried straight through to its own successor instead of being treated
 * as a node of its own.
 *
 * One real limitation from doing this with a single BFS visit per node: a
 * node reached by more than one path (a branch rejoining before the end)
 * only keeps the first-discovered incoming connection, not every one. This
 * app's own gateway branches never rejoin — each ends at its own End event —
 * so it never hits this; an arbitrary uploaded file reasonably might, and
 * there the preview would be missing one incoming arrow rather than wrong or
 * crashed.
 */
function extractGraph(xml: string): BpmnGraph {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.querySelector('parsererror')) throw new Error('This file is not valid XML.')

  const TASK_TAGS = new Set(['task', 'serviceTask', 'userTask', 'scriptTask', 'sendTask', 'receiveTask', 'businessRuleTask', 'manualTask'])
  const GATEWAY_TAGS = new Set(['exclusiveGateway', 'parallelGateway', 'inclusiveGateway', 'eventBasedGateway', 'complexGateway'])
  const elements = Array.from(doc.getElementsByTagName('*'))

  const kindById = new Map<string, PreviewNode['kind']>()
  const nameById = new Map<string, string>()
  const rawOutgoing = new Map<string, string[]>()
  let startId: string | null = null

  for (const el of elements) {
    const tag = el.localName
    const id = el.getAttribute('id')
    if (!id) continue
    const name = el.getAttribute('name')?.trim()
    if (TASK_TAGS.has(tag)) {
      kindById.set(id, 'task')
      nameById.set(id, name || id)
    } else if (GATEWAY_TAGS.has(tag)) {
      kindById.set(id, 'gateway')
      nameById.set(id, name || 'Gateway')
    } else if (tag === 'startEvent') {
      if (!startId) startId = id
      kindById.set(id, 'start')
      nameById.set(id, name || 'Start')
    } else if (tag === 'endEvent') {
      kindById.set(id, 'end')
      nameById.set(id, name || 'End')
    } else if (tag === 'sequenceFlow') {
      const from = el.getAttribute('sourceRef')
      const to = el.getAttribute('targetRef')
      if (from && to) rawOutgoing.set(from, [...(rawOutgoing.get(from) ?? []), to])
    }
  }

  if (![...kindById.values()].includes('task')) throw new Error('No tasks were found in this BPMN file.')
  if (!startId) throw new Error('This BPMN file has no start event, so the task order cannot be determined.')

  const nodes: PreviewNode[] = []
  const edges: PreviewEdge[] = []
  const visited = new Set<string>([startId])
  const queue: { id: string; from: string | null }[] = [{ id: startId, from: null }]
  while (queue.length) {
    const { id, from } = queue.shift()!
    const kind = kindById.get(id)
    const forwardFrom = kind ? id : from
    if (kind) {
      nodes.push({ id, kind, name: nameById.get(id)! })
      if (from) edges.push({ from, to: id })
    }
    for (const next of rawOutgoing.get(id) ?? []) {
      if (visited.has(next)) continue
      visited.add(next)
      queue.push({ id: next, from: forwardFrom })
    }
  }

  if (!nodes.some((n) => n.kind === 'task')) throw new Error('No task is reachable from the start event.')
  return { nodes, edges }
}

/** Turns an extracted graph into the one-sentence description `interpretRequirement` reads, exactly as a typed or dictated description would be — just the tasks, in the order the graph reaches them, same simplification a typed description of "what happens" would make. */
function describeGraph(graph: BpmnGraph): string {
  const sentence = graph.nodes.filter((n) => n.kind === 'task').map((n) => n.name).join(', then ')
  return sentence.charAt(0).toUpperCase() + sentence.slice(1) + '.'
}

/**
 * Renders the uploaded file's real task/gateway/event structure as a clean,
 * auto-laid-out diagram, built from the same extracted graph the description
 * on the right is read from — not the file's own diagram layout. Earlier
 * this rendered the file's own DI verbatim, which meant an uploaded file
 * with no layout, or a badly labelled one, could come out unreadable; before
 * that, it flattened everything to one chain, which lost any branch the
 * file actually had. Generating our own from the full graph keeps both: a
 * clean, correctly-routed diagram, visually consistent with what the
 * description says since both now read from the same extracted graph.
 * Remounts per upload (keyed by filename, see the `key` prop callers pass)
 * so each file gets a clean viewer instance.
 */
function BpmnPreview({ graph }: { graph: BpmnGraph }) {
  const container = useRef<HTMLDivElement>(null)
  const viewerRef = useRef<NavigatedViewer | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (!container.current) return
    let live = true
    const viewer = new NavigatedViewer({ container: container.current })
    viewerRef.current = viewer
    viewer
      .importXML(toPreviewXml(graph))
      .then(() => {
        if (!live) return
        viewer.get<{ zoom: (v: string) => void }>('canvas').zoom('fit-viewport')
        setReady(true)
      })
      .catch(() => {})
    return () => {
      live = false
      viewer.destroy()
      viewerRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const zoomBy = (factor: number) => {
    const canvas = viewerRef.current?.get<{ zoom: (v?: number | string) => number }>('canvas')
    if (canvas) canvas.zoom(canvas.zoom() * factor)
  }
  const fit = () => viewerRef.current?.get<{ zoom: (v: string) => void }>('canvas').zoom('fit-viewport')

  return (
    <div className="relative h-full w-full">
      <div ref={container} className="h-full w-full" />
      {ready && (
        <div className="absolute bottom-3 left-3 flex items-center gap-1 rounded-lg border border-line bg-white p-1 shadow-md">
          <Btn size="sm" variant="ghost" aria-label="Zoom out" onClick={() => zoomBy(1 / 1.2)}><ZoomOut size={14} /></Btn>
          <Btn size="sm" variant="ghost" aria-label="Zoom in" onClick={() => zoomBy(1.2)}><ZoomIn size={14} /></Btn>
          <Btn size="sm" variant="ghost" aria-label="Fit to screen" onClick={fit}><Maximize size={14} /></Btn>
        </div>
      )}
    </div>
  )
}

function BpmnUpload({ fileName, error, onFile }: { fileName: string | null; error: string | null; onFile: (f: File) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)
  return (
    <div>
      <input ref={inputRef} type="file" accept=".bpmn,.xml" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) onFile(f) }} />
      <button
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setOver(true) }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); const f = e.dataTransfer.files[0]; if (f) onFile(f) }}
        className={cx(
          'flex w-full flex-col items-center rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors',
          over ? 'border-navy bg-tx-bg' : fileName ? 'border-ok-bd bg-ok-bg' : 'border-slate-300 bg-white hover:border-slate-400',
        )}
      >
        <FileUp size={26} className={fileName ? 'text-ok' : 'text-ink3'} />
        <span className="mt-2 text-sm font-semibold">{fileName ? `${fileName} — click to replace` : 'Drag a .bpmn or .xml file here, or click to browse'}</span>
        {!fileName && <span className="mt-1 text-xs text-ink3">.bpmn or .xml, up to 5 MB</span>}
      </button>
      {error && <p className="mt-2 text-[13px] text-err">{error}</p>}
    </div>
  )
}

const SKETCH_MIME_TYPES = ['image/png', 'image/jpeg', 'application/pdf']

function SketchUpload({ fileName, error, busy, onFile }: { fileName: string | null; error: string | null; busy: boolean; onFile: (f: File) => void }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)
  return (
    <div>
      <input ref={inputRef} type="file" accept="image/png,image/jpeg,application/pdf" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) onFile(f) }} />
      <button
        disabled={busy}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setOver(true) }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); const f = e.dataTransfer.files[0]; if (f) onFile(f) }}
        className={cx(
          'flex w-full flex-col items-center rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors disabled:cursor-not-allowed',
          over ? 'border-navy bg-tx-bg' : fileName ? 'border-ok-bd bg-ok-bg' : 'border-slate-300 bg-white hover:border-slate-400',
        )}
      >
        {busy ? <Spinner size={22} /> : <FileImage size={26} className={fileName ? 'text-ok' : 'text-ink3'} />}
        <span className="mt-2 text-sm font-semibold">
          {busy ? 'Reading your sketch…' : fileName ? `${fileName} — click to replace` : 'Drag a photo or scan here, or click to browse'}
        </span>
        {!fileName && !busy && <span className="mt-1 text-xs text-ink3">PNG, JPG or PDF, up to 10 MB</span>}
      </button>
      {error && <p className="mt-2 text-[13px] text-err">{error}</p>}
    </div>
  )
}

/** Shows the uploaded sketch next to the description Gemini read from it — an image renders directly, a PDF renders through the browser's own built-in viewer, so no extra library is needed for either. */
function SketchPreview({ file, url }: { file: File; url: string }) {
  if (file.type === 'application/pdf') return <iframe title="Uploaded sketch" src={url} className="h-full w-full border-0" />
  return <img src={url} alt="Uploaded workflow sketch" className="h-full w-full object-contain" />
}

function MicButton({ listening, supported, onToggle }: { listening: boolean; supported: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={!supported}
      aria-pressed={listening}
      aria-label={listening ? 'Stop dictating' : 'Dictate the description'}
      title={supported ? (listening ? 'Stop dictating' : 'Dictate instead of typing') : 'Voice input is not supported in this browser'}
      className={cx(
        'absolute right-3 top-3 flex items-center gap-1.5 rounded-full border px-2.5 py-1.5 text-xs font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40',
        listening ? 'border-err-bd bg-err-bg text-err' : 'border-line bg-white text-ink2 hover:border-slate-400 hover:text-ink',
      )}
    >
      <span className="relative flex items-center justify-center">
        {listening && <span className="absolute inline-flex h-4 w-4 animate-ping rounded-full bg-err opacity-50" />}
        <Mic size={13} className="relative" />
      </span>
      {listening ? 'Listening…' : 'Dictate'}
    </button>
  )
}

export function CreateInput({
  method,
  text,
  setText,
  onInterpreted,
  onBuilt,
}: {
  method: Method
  text: string
  setText: (t: string) => void
  onInterpreted: (result: Interpretation | null) => void
  /** Saves a chain built in the visual editor and opens it. */
  onBuilt: (requirement: Requirement, operationIds: number[]) => Promise<void>
}) {
  const { go, toast } = useApp()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [visual, setVisual] = useState<VisualChain | null>(null)
  const [bpmnFileName, setBpmnFileName] = useState<string | null>(null)
  const [bpmnGraph, setBpmnGraph] = useState<BpmnGraph | null>(null)
  const [bpmnError, setBpmnError] = useState<string | null>(null)
  const [sketchFileName, setSketchFileName] = useState<string | null>(null)
  const [sketchFile, setSketchFile] = useState<File | null>(null)
  const [sketchPreviewUrl, setSketchPreviewUrl] = useState<string | null>(null)
  const [sketchError, setSketchError] = useState<string | null>(null)
  const [sketchBusy, setSketchBusy] = useState(false)
  const dictation = useDictation(text, setText, (msg) => toast(msg, 'err'))

  // BPMN's file upload only establishes task order; a sketch's photo only shows
  // Gemini what to read. Neither tells us which catalog operation a step is, so
  // both just produce a description and reuse the exact same text box and
  // interpretation step typed or dictated text does.
  const bpmnReady = method === 'bpmn' && !!bpmnFileName && !bpmnError
  const sketchReady = method === 'sketch' && !!sketchFileName && !sketchError
  const textBased = method === 'nl' || bpmnReady || sketchReady
  const resetBpmn = () => {
    setBpmnFileName(null)
    setBpmnGraph(null)
    setText('')
  }
  const handleBpmnFile = (f: File) => {
    setBpmnError(null)
    f.text()
      .then((xml) => {
        const graph = extractGraph(xml)
        setBpmnFileName(f.name)
        setBpmnGraph(graph)
        setText(describeGraph(graph))
        setError(null)
      })
      .catch((e: Error) => {
        setBpmnFileName(null)
        setBpmnGraph(null)
        setBpmnError(e.message)
      })
  }

  const resetSketch = () => {
    if (sketchPreviewUrl) URL.revokeObjectURL(sketchPreviewUrl)
    setSketchFileName(null)
    setSketchFile(null)
    setSketchPreviewUrl(null)
    setText('')
  }
  const handleSketchFile = (f: File) => {
    setSketchError(null)
    if (!SKETCH_MIME_TYPES.includes(f.type)) {
      setSketchError('Only PNG, JPG or PDF files are accepted.')
      return
    }
    if (f.size > 10 * 1024 * 1024) {
      setSketchError('That file is over 10 MB.')
      return
    }
    setSketchBusy(true)
    describeSketch(f)
      .then((description) => {
        if (sketchPreviewUrl) URL.revokeObjectURL(sketchPreviewUrl)
        setSketchFileName(f.name)
        setSketchFile(f)
        setSketchPreviewUrl(URL.createObjectURL(f))
        setText(description)
        setError(null)
      })
      .catch((e: Error) => {
        setSketchFileName(null)
        setSketchError(e.message)
      })
      .finally(() => setSketchBusy(false))
  }

  const canRun = textBased ? text.trim().length > 10 : method === 'visual' ? !!visual?.ready : false
  const run = () => {
    if (method === 'visual') {
      if (!visual) return
      setBusy(true)
      onBuilt(visual.requirement, visual.operationIds).finally(() => setBusy(false))
      return
    }
    if (!textBased) return
    setBusy(true)
    setError(null)
    interpretRequirement(text.trim())
      .then((result) => {
        onInterpreted(result)
        go('review')
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false))
  }
  const title = METHODS.find((m) => m.id === method)!.title
  const hasPreview = (method === 'bpmn' && !!bpmnGraph) || (method === 'sketch' && !!sketchFile && !!sketchPreviewUrl)
  return (
    <Wizard
      step={2}
      onBack={() => go('create')}
      nextLabel="Review requirements"
      extra={
        <Btn variant="primary" disabled={!canRun || busy} onClick={run}>
          {busy ? <Spinner size={14} /> : null}
          {method === 'visual' ? 'Create workflow' : 'Interpret requirement'}
        </Btn>
      }
    >
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>

      <div className="mt-6">
        {method === 'bpmn' && !bpmnReady && (
          <>
            <BpmnUpload fileName={bpmnFileName} error={bpmnError} onFile={handleBpmnFile} />
          </>
        )}
        {method === 'sketch' && !sketchReady && (
          <>
            <SketchUpload fileName={sketchFileName} error={sketchError} busy={sketchBusy} onFile={handleSketchFile} />
          </>
        )}
        {textBased && (
          <>
            {(method === 'bpmn' || method === 'sketch') && (
              <button onClick={method === 'bpmn' ? resetBpmn : resetSketch} className="mb-2 text-xs font-semibold text-ink2 hover:text-ink">
                <RefreshCw size={11} className="mr-1 inline" /> Upload a different file
              </button>
            )}
            <div className={cx(hasPreview ? 'flex flex-col gap-5' : undefined)}>
              {hasPreview && (
                <div className="flex h-[600px] flex-col overflow-hidden rounded-xl border border-line bg-white shadow-[0_1px_2px_rgba(15,27,51,0.04)]">
                  <div className="flex items-center gap-1.5 border-b border-line bg-canvas px-4 py-2.5">
                    {method === 'bpmn' ? <Workflow size={13} className="text-ink3" /> : <FileImage size={13} className="text-ink3" />}
                    <span className="text-xs font-semibold uppercase tracking-wide text-ink3">
                      {method === 'bpmn' ? 'Diagram preview' : 'Sketch preview'}
                    </span>
                  </div>
                  <div className="relative flex-1">
                    {method === 'bpmn' && bpmnGraph && <BpmnPreview key={bpmnFileName} graph={bpmnGraph} />}
                    {method === 'sketch' && sketchFile && sketchPreviewUrl && <SketchPreview key={sketchFileName} file={sketchFile} url={sketchPreviewUrl} />}
                  </div>
                </div>
              )}
              <div
                className={cx(
                  'relative',
                  hasPreview && 'flex h-[180px] flex-col overflow-hidden rounded-xl border border-line bg-white shadow-[0_1px_2px_rgba(15,27,51,0.04)]',
                )}
              >
                {hasPreview && (
                  <div className="flex items-center gap-1.5 border-b border-line bg-canvas px-4 py-2.5">
                    <FileText size={13} className="text-ink3" />
                    <span className="text-xs font-semibold uppercase tracking-wide text-ink3">Generated description</span>
                  </div>
                )}
                <textarea
                  aria-label="Workflow description"
                  value={text}
                  onChange={(e) => {
                    setText(e.target.value)
                    setError(null)
                  }}
                  rows={hasPreview ? undefined : 6}
                  placeholder="Describe your invoice workflow, or press Dictate and say it out loud…"
                  className={cx(
                    'w-full resize-none text-[15px] leading-relaxed placeholder:text-ink3',
                    hasPreview ? 'flex-1 border-0 p-4' : 'rounded-lg border border-line bg-white p-4 pr-28 hover:border-slate-300',
                  )}
                />
                {method === 'nl' && <MicButton listening={dictation.listening} supported={dictation.supported} onToggle={dictation.toggle} />}
              </div>
            </div>
            {dictation.listening && (
              <p className="mt-1.5 flex items-center gap-1.5 text-xs text-ink3">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-err" /> Listening…
              </p>
            )}
          </>
        )}
        {method === 'visual' && <VisualEditor onChange={setVisual} />}
      </div>

      {busy && (
        <Card className="mt-4 flex items-center gap-3 border-tx-bd bg-tx-bg p-4">
          <Spinner size={18} />
          <div>
            <div className="text-sm font-semibold text-tx">Reading your description</div>
          </div>
        </Card>
      )}
      {error && (
        <Card className="fadeup mt-4 flex items-start gap-3 border-err-bd bg-err-bg p-4">
          <X size={18} className="mt-0.5 text-err" />
          <div>
            <div className="text-sm font-semibold">The description could not be interpreted</div>
            <div className="text-[13px] text-ink2">{error}</div>
          </div>
        </Card>
      )}
    </Wizard>
  )
}

/* ================= Review requirements ================= */
export const EMPTY_REQUIREMENT: Requirement = { inputArtifactType: null, goalArtifactType: null, validationOperationId: null }

function ReqCard({ title, stage, help, missing, children }: { title: string; stage?: Stage; help?: string; missing?: boolean; children: React.ReactNode }) {
  return (
    <Card className={cx('p-4', missing && 'border-warn-bd')}>
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink3">
          {stage && <span className={cx('h-2 w-2 rounded-full', STAGE[stage].dot)} />}
          {title}
        </div>
        {missing && <Badge kind="confirm">Needs your choice</Badge>}
      </div>
      <div className="mt-3">{children}</div>
      {help && <p className="mt-2 text-[13px] text-ink3">{help}</p>}
    </Card>
  )
}

export function Review({
  interpretation,
  req,
  setReq,
  onGenerate,
}: {
  interpretation: Interpretation | null
  req: Requirement
  setReq: (r: Requirement) => void
  onGenerate: () => void
}) {
  const { go } = useApp()
  const [validators, setValidators] = useState<CatalogOperation[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchOperations(controller.signal)
      .then((ops) => setValidators(ops.filter((o) => o.stage === 'VALIDATION' && o.chainable)))
      .catch((e: Error) => {
        if (e.name !== 'AbortError') setLoadError(e.message)
      })
    return () => controller.abort()
  }, [])

  const validator = validators?.find((v) => v.id === req.validationOperationId)
  const inputLabel = INPUT_CHOICES.find((c) => c.id === req.inputArtifactType)?.label
  const goalLabel = GOAL_CHOICES.find((c) => c.id === req.goalArtifactType)?.label
  const ready = !!req.inputArtifactType && !!req.goalArtifactType
  const delivery = req.goalArtifactType === 'DELIVERY_RECEIPT' ? interpretation?.deliveryPhrase : null
  return (
    <div className="mx-auto max-w-[1040px] p-8">
      <div className="mb-6 flex items-center gap-4">
        <span className="text-xs font-semibold uppercase tracking-wide text-ink3">Step 3 of 3</span>
        <div className="flex gap-1.5">
          {[1, 2, 3].map((i) => (
            <span key={i} className="h-1.5 w-12 rounded-full bg-navy" />
          ))}
        </div>
      </div>
      <PageHead
        title="Review interpreted requirements"
      />
      <div className="grid grid-cols-2 gap-4">
        <ReqCard title="Input" missing={!req.inputArtifactType}>
          <select
            aria-label="Input"
            value={req.inputArtifactType ?? ''}
            onChange={(e) => setReq({ ...req, inputArtifactType: (e.target.value || null) as ArtifactType | null })}
            className={inputCls}
          >
            <option value="">Choose the input…</option>
            {INPUT_CHOICES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
          </select>
        </ReqCard>
        <ReqCard title="Result" missing={!req.goalArtifactType}>
          <select
            aria-label="Result"
            value={req.goalArtifactType ?? ''}
            onChange={(e) => {
              const goal = (e.target.value || null) as ArtifactType | null
              setReq({ ...req, goalArtifactType: goal, validationOperationId: validates(goal) ? req.validationOperationId : null })
            }}
            className={inputCls}
          >
            <option value="">Choose the result…</option>
            {GOAL_CHOICES.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
          </select>
          {delivery && <p className="mt-2 text-xs text-ink2">Sending was requested in your description: “{delivery}”</p>}
        </ReqCard>
        {validates(req.goalArtifactType) && (
          <ReqCard stage="validation" title="Validation standard">
            {loadError ? (
              <p className="text-[13px] text-err">{loadError}</p>
            ) : !validators ? (
              <div className="flex items-center gap-2 text-[13px] text-ink2"><Spinner size={14} /> Loading validators…</div>
            ) : (
              <select
                aria-label="Validation standard"
                value={req.validationOperationId ?? ''}
                onChange={(e) => setReq({ ...req, validationOperationId: e.target.value ? Number(e.target.value) : null })}
                className={inputCls}
              >
                <option value="">Show a workflow for every validator</option>
                {validators.map((v) => <option key={v.id} value={v.id}>{v.name} ({v.provider})</option>)}
              </select>
            )}
          </ReqCard>
        )}
        {interpretation && interpretation.questions.length > 0 && (
          <Card className="p-4">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-warn"><AlertTriangle size={13} /> Please check</div>
            <ul className="mt-2 list-disc space-y-1 pl-5 text-[13px] text-ink2">
              {interpretation.questions.map((q) => <li key={q}>{q}</li>)}
            </ul>
          </Card>
        )}
      </div>
      <Card className="mt-4 flex items-center gap-3 bg-slate-50 p-3 text-[13px] text-ink2">
        <StageStrip input={req.inputArtifactType} goal={req.goalArtifactType} />
        <span className="ml-2">
          {inputLabel ?? 'Input not chosen'} → {goalLabel ?? 'result not chosen'}
          {validates(req.goalArtifactType) && ` · ${validator ? validator.name : 'every validator'}`}
        </span>
      </Card>
      <div className="mt-8 flex items-center justify-between border-t border-line pt-5">
        <Btn variant="ghost" onClick={() => go('create')}>
          Start over
        </Btn>
        <div className="flex gap-2">
          <Btn onClick={() => go('input')}>
            <Pencil size={14} /> Edit description
          </Btn>
          <Btn variant="primary" onClick={onGenerate} disabled={!ready} title={ready ? undefined : 'Choose the input and the result first'}>
            Find workflows <ArrowRight size={16} />
          </Btn>
        </div>
      </div>
    </div>
  )
}

/* ================= Candidate workflows ================= */

export function Generating({ req, onChoose }: { req: Requirement; onChoose: (candidate: ChainCandidate) => Promise<void> }) {
  const { go } = useApp()
  const [saving, setSaving] = useState<number | null>(null)
  const [plan, setPlan] = useState<PlanResponse | null>(null)
  const [ops, setOps] = useState<Map<number, CatalogOperation>>(new Map())
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setPlan(null)
    setError(null)
    Promise.all([planWorkflow(req, controller.signal), fetchOperations(controller.signal)])
      .then(([result, catalog]) => {
        setOps(new Map(catalog.map((o) => [o.id, o])))
        setPlan(result)
      })
      .catch((e: Error) => {
        if (e.name !== 'AbortError') setError(e.message)
      })
    return () => controller.abort()
  }, [req, attempt])

  const route = `${INPUT_CHOICES.find((c) => c.id === req.inputArtifactType)?.label} → ${GOAL_CHOICES.find((c) => c.id === req.goalArtifactType)?.label}`
  if (error)
    return (
      <div className="mx-auto max-w-[640px] p-8 pt-24">
        <Card>
          <EmptyState tone="error" icon={<X size={22} />} title="Could not search for workflows" body={error}
            action={<><Btn onClick={() => go('review')}>Change requirements</Btn><Btn variant="primary" onClick={() => setAttempt((n) => n + 1)}><RefreshCw size={14} /> Try again</Btn></>} />
        </Card>
      </div>
    )
  if (!plan)
    return (
      <div className="mx-auto max-w-[560px] p-8 pt-24">
        <Card className="flex items-center gap-3 p-6">
          <Spinner />
          <div>
            <h2 className="text-lg font-semibold">Finding workflows</h2>
            <p className="text-sm text-ink2">{route}</p>
          </div>
        </Card>
      </div>
    )
  if (plan.candidates.length === 0)
    return (
      <div className="mx-auto max-w-[640px] p-8 pt-24">
        <Card>
          <EmptyState
            tone="error"
            icon={<X size={22} />}
            title="No compatible workflow found"
            body={route}
            action={<><Btn onClick={() => go('review')}>Change requirements</Btn><Btn variant="primary" onClick={() => go('catalog')}>Browse operation catalog</Btn></>}
          />
          <div className="border-t border-line bg-slate-50 px-6 py-3 text-xs text-ink3">
            Register the missing operation in the operation catalog to make this workflow possible.
          </div>
        </Card>
      </div>
    )
  return (
    <div className="mx-auto max-w-[1040px] p-8">
      <PageHead
        title="Candidate workflows"
        sub={route}
        actions={<Btn onClick={() => go('review')}><ArrowLeft size={14} /> Change requirements</Btn>}
      />
      <div className="space-y-4">
        {plan.candidates.map((c, n) => {
          const status = ASSESSMENT_BADGE[c.assessment.status]
          return (
            <Card key={n} className="p-5">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="text-sm font-semibold">Option {n + 1}</span>
                  <Badge kind={status.kind}>{status.label}</Badge>
                  <span className="text-xs text-ink3">{c.steps.length} {c.steps.length === 1 ? 'step' : 'steps'}</span>
                </div>
                <Btn variant="primary" size="sm" disabled={c.assessment.status === 'NOT_SUPPORTED' || saving !== null} onClick={() => { setSaving(n); onChoose(c).finally(() => setSaving(null)) }}>
                  {saving === n ? <Spinner size={12} /> : null} Use this workflow <ArrowRight size={14} />
                </Btn>
              </div>
              <ol className="mt-4 space-y-2">
                {c.steps.map((s) => {
                  const op = ops.get(s.operationId)
                  const st = stageOf(s)
                  return (
                    <li key={s.sequence} className="flex items-center gap-3 rounded-lg border border-line px-3 py-2">
                      <span className="w-5 text-center font-mono text-xs text-ink3">{s.sequence}</span>
                      {st && <StageTag stage={st} small />}
                      <div className="min-w-0 flex-1">
                        <div className="truncate text-[13px] font-semibold">{op?.name ?? s.name}</div>
                        <div className="truncate text-xs text-ink3">{op?.provider ?? ''}</div>
                      </div>
                      <ArtifactTag>{ARTIFACT_LABEL[s.input]}</ArtifactTag>
                      <ArrowRight size={12} className="text-ink3" />
                      <ArtifactTag>{ARTIFACT_LABEL[s.output]}</ArtifactTag>
                    </li>
                  )
                })}
              </ol>
              {(c.assessment.issues.length > 0 || c.assessment.warnings.length > 0) && (
                <ul className="mt-3 space-y-1 text-[13px]">
                  {c.assessment.issues.map((i, k) => (
                    <li key={'i' + k} className="flex gap-2 text-err">
                      <AlertTriangle size={14} className="mt-0.5 shrink-0" />
                      <span>{i.step ? `Step ${i.step}: ` : ''}{i.message}</span>
                    </li>
                  ))}
                  {c.assessment.warnings.map((w) => (
                    <li key={w} className="flex gap-2 text-ink2"><Info size={14} className="mt-0.5 shrink-0" /> <span>{w}</span></li>
                  ))}
                </ul>
              )}
            </Card>
          )
        })}
      </div>
    </div>
  )
}

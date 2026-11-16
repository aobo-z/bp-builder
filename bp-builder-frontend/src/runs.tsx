import { useEffect, useRef, useState, type ReactNode } from 'react'
import { ArrowRight, Check, ChevronDown, ChevronRight, CircleSlash, Clock, Copy, FileUp, FlaskConical, Info, Play, X, XCircle } from 'lucide-react'
import { Badge, Btn, Card, EmptyState, PageHead, Segmented, Spinner, STAGE, Tabs, cx, useApp, type BadgeKind, type Stage } from './ui'
import {
  ARTIFACT_LABEL,
  INPUT_CHOICES,
  getRun,
  listRuns,
  runWorkflow,
  type ArtifactType,
  type Issue,
  type RunStatus,
  type RunStep,
  type RunSummary,
  type StepStatus,
  type WorkflowRun,
} from './api'
import { canRun, type DraftWorkflow } from './WorkflowBuilder'

/* ================= shared ================= */
export const RUN_LABEL: Record<RunStatus, { kind: BadgeKind; label: string }> = {
  COMPLETED: { kind: 'pass', label: 'Completed' },
  INVOICE_INVALID: { kind: 'info', label: 'Invoice invalid' },
  INPUT_REQUIRED: { kind: 'confirm', label: 'Missing input data' },
  FAILED: { kind: 'fail', label: 'Failed' },
}

/** An invalid invoice is a verdict, so it is shown in a neutral tone; only FAILED looks like an error. */
const RUN_TONE: Record<RunStatus, string> = {
  COMPLETED: 'border-ok-bd bg-ok-bg',
  INVOICE_INVALID: 'border-line bg-slate-50',
  INPUT_REQUIRED: 'border-warn-bd bg-warn-bg',
  FAILED: 'border-err-bd bg-err-bg',
}

export const when = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

const seconds = (ms: number | null) => (ms == null ? '—' : ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`)
const elapsed = (r: RunSummary) => (r.finishedAt ? seconds(new Date(r.finishedAt).getTime() - new Date(r.startedAt).getTime()) : '—')
const bytes = (n: number) => (n < 1024 ? `${n} B` : n < 1024 * 1024 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`)
const stageKey = (s: RunStep['stage']) => (s ? (s.toLowerCase() as Stage) : null)

const STEP_STATE: Record<StepStatus, { icon: ReactNode; text: string; label: string }> = {
  SUCCEEDED: { icon: <Check size={16} className="rounded-full bg-ok p-0.5 text-white" />, text: 'text-ink2', label: 'Succeeded' },
  INPUT_REQUIRED: { icon: <XCircle size={16} className="text-warn" />, text: 'font-medium text-warn', label: 'Not called: a required value is missing' },
  FAILED: { icon: <X size={16} className="rounded-full bg-err p-0.5 text-white" />, text: 'font-medium text-err', label: 'Failed' },
  NOT_RUN: { icon: <CircleSlash size={16} className="text-ink3" />, text: 'text-ink3', label: 'Not run' },
}

const pretty = (value: unknown) => (typeof value === 'string' ? value : JSON.stringify(value, null, 2))

/* ================= Test ================= */
const ACCEPT: Partial<Record<ArtifactType, string>> = {
  PDF_DOCUMENT: '.pdf,application/pdf',
  IMAGE_DOCUMENT: '.png,.jpg,.jpeg,image/png,image/jpeg',
  UBL_XML: '.xml,application/xml,text/xml',
  CANONICAL_INVOICE: '.json,application/json',
}

/** Reads a file as the run input: Base64 for a PDF or image, the text for UBL XML, the parsed object for canonical JSON. */
async function readInput(file: File, type: ArtifactType): Promise<unknown> {
  if (type === 'PDF_DOCUMENT' || type === 'IMAGE_DOCUMENT') {
    const data = new Uint8Array(await file.arrayBuffer())
    let binary = ''
    for (let i = 0; i < data.length; i += 0x8000) binary += String.fromCharCode(...data.subarray(i, i + 0x8000))
    return btoa(binary)
  }
  const text = await file.text()
  if (type !== 'CANONICAL_INVOICE') return text
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`${file.name} is not valid JSON.`)
  }
}

export function TestPage({ wf, onResults }: { wf: DraftWorkflow | null; onResults: (runId: number) => void }) {
  const { toast } = useApp()
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [run, setRun] = useState<WorkflowRun | null>(null)
  const [open, setOpen] = useState<number | null>(null)
  const picker = useRef<HTMLInputElement>(null)

  if (!wf) {
    return (
      <div className="mx-auto max-w-[1100px] p-8">
        <Card><EmptyState icon={<Play size={20} />} title="No workflow open" /></Card>
      </div>
    )
  }
  const input = wf.requirement.inputArtifactType ?? 'PDF_DOCUMENT'
  const inputLabel = INPUT_CHOICES.find((c) => c.id === input)?.label ?? ARTIFACT_LABEL[input]
  const ready = canRun(wf)
  const stages = [...new Set(wf.candidate.steps.map((s) => stageKey(s.stage)).filter((s): s is Stage => !!s))]

  const start = async () => {
    if (!file) return
    setBusy(true)
    setRun(null)
    try {
      const result = await runWorkflow(wf.id, await readInput(file, input), file.name)
      setRun(result)
      toast(`${RUN_LABEL[result.status].label}${result.message ? `: ${result.message}` : ''}`,
        result.status === 'COMPLETED' ? 'ok' : result.status === 'INVOICE_INVALID' ? 'info' : 'err')
    } catch (e) {
      toast((e as Error).message, 'err')
    } finally {
      setBusy(false)
    }
  }

  const stageState = (s: Stage) => {
    if (busy) return 'running'
    const states = run?.steps.filter((x) => stageKey(x.stage) === s).map((x) => x.status) ?? []
    if (!states.length) return 'pending'
    if (states.some((x) => x === 'FAILED' || x === 'INPUT_REQUIRED')) return 'fail'
    if (states.every((x) => x === 'NOT_RUN')) return 'skipped'
    return 'pass'
  }

  return (
    <div className="mx-auto grid max-w-[1280px] grid-cols-[400px_minmax(0,1fr)] gap-6 p-8">
      <div className="space-y-4">
        <Card className="p-4">
          <h2 className="text-sm font-semibold">Input invoice</h2>
          <p className="mt-1 text-xs text-ink3">{inputLabel}</p>
          <input
            ref={picker}
            type="file"
            accept={ACCEPT[input]}
            className="hidden"
            onChange={(e) => { setFile(e.target.files?.[0] ?? null); e.target.value = '' }}
          />
          {file ? (
            <div className="mt-3 flex items-center justify-between rounded-lg border border-line bg-slate-50 p-3">
              <div className="min-w-0 text-[13px]">
                <div className="truncate font-semibold">{file.name}</div>
                <div className="text-xs text-ink3">{bytes(file.size)}</div>
              </div>
              <Btn size="sm" variant="ghost" disabled={busy} onClick={() => picker.current?.click()}>Replace</Btn>
            </div>
          ) : (
            <button
              onClick={() => picker.current?.click()}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => { e.preventDefault(); setFile(e.dataTransfer.files[0] ?? null) }}
              className="mt-3 flex w-full flex-col items-center rounded-lg border-2 border-dashed border-slate-300 py-6 text-center hover:border-slate-400"
            >
              <FileUp size={22} className="text-ink3" />
              <span className="mt-1 text-[13px] font-semibold">Drop a file or click to choose one</span>
            </button>
          )}
        </Card>
        <Btn variant="primary" className="h-11 w-full" disabled={!file || busy || !ready} onClick={start}>
          {busy ? <Spinner size={16} /> : <Play size={16} />}
          {busy ? 'Running…' : run ? 'Run again' : 'Run workflow'}
        </Btn>
        {!ready && <p className="text-xs text-err">This workflow is not ready to run. Open Readiness to see why.</p>}
        <Card className="p-4">
          <div className="space-y-3">
            {stages.map((s) => {
              const st = stageState(s)
              return (
                <div key={s}>
                  <div className="mb-1 flex items-center justify-between text-xs font-semibold">
                    <span className={STAGE[s].text}>{STAGE[s].label}</span>
                    <span className={st === 'fail' ? 'text-err' : st === 'pass' ? 'text-ok' : 'text-ink3'}>
                      {{ pending: 'Waiting', running: 'In progress', pass: 'Done', fail: 'Failed', skipped: 'Not run' }[st]}
                    </span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-slate-200">
                    <div
                      className={cx('h-full rounded-full transition-all duration-500', st === 'fail' ? 'bg-err' : st === 'skipped' ? 'bg-slate-400' : STAGE[s].solid)}
                      style={{ width: st === 'pending' ? '0%' : st === 'running' ? '55%' : '100%' }}
                    />
                  </div>
                </div>
              )
            })}
          </div>
        </Card>
      </div>

      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Execution timeline</h2>
          {run && <Btn variant="primary" onClick={() => onResults(run.id)}>View results <ArrowRight size={16} /></Btn>}
        </div>
        {busy ? (
          <Card className="flex items-center gap-3 p-4 text-[13px] text-ink2">
            <Spinner size={18} /> Running…
          </Card>
        ) : !run ? (
          <Card>
            <EmptyState icon={<Play size={20} />} title="Nothing has run yet" />
          </Card>
        ) : (
          <div className="space-y-2">
            {run.steps.map((s) => {
              const st = STEP_STATE[s.status]
              const stage = stageKey(s.stage)
              const isOpen = open === s.sequence
              return (
                <Card key={s.sequence} className={cx('fadeup overflow-hidden', s.status === 'FAILED' && 'border-err-bd', s.status === 'INPUT_REQUIRED' && 'border-warn-bd')}>
                  <div className="flex items-center gap-3 p-3">
                    <div className="grid h-6 w-6 place-items-center">{st.icon}</div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        {stage && <span className={cx('h-2 w-2 rounded-full', STAGE[stage].dot)} />}
                        <span className="text-sm font-semibold">{s.operationName}</span>
                        {s.httpStatus != null && <span className="text-xs text-ink3">HTTP {s.httpStatus}</span>}
                      </div>
                      <div className={cx('mt-0.5 text-[13px]', st.text)}>{s.messages.length ? s.messages.join(' ') : st.label}</div>
                    </div>
                    {s.status !== 'NOT_RUN' && <span className="flex items-center gap-1 text-xs tabular-nums text-ink3"><Clock size={12} />{seconds(s.durationMs)}</span>}
                    {s.status !== 'NOT_RUN' && (
                      <button aria-expanded={isOpen} aria-label="Toggle details" onClick={() => setOpen(isOpen ? null : s.sequence)} className="rounded p-1 text-ink3 hover:bg-slate-100">
                        {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                      </button>
                    )}
                  </div>
                  {isOpen && <StepPayloads step={s} />}
                </Card>
              )
            })}
            <Card className={cx('p-3 text-[13px]', RUN_TONE[run.status])}>
              <b>{RUN_LABEL[run.status].label}.</b> {run.message}
            </Card>
          </div>
        )}
      </div>
    </div>
  )
}

function StepPayloads({ step }: { step: RunStep }) {
  return (
    <div className="grid grid-cols-2 gap-px border-t border-line bg-line">
      {([['Request', step.request], ['Response', step.response]] as const).map(([t, b]) => (
        <div key={t} className="min-w-0 bg-slate-50 p-3">
          <div className="mb-1 text-[11px] font-semibold uppercase text-ink3">{t}</div>
          <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-words font-mono text-[11px] leading-relaxed text-ink2">{b == null ? '(none)' : pretty(b)}</pre>
        </div>
      ))}
    </div>
  )
}

/* ================= Results ================= */
type ResultTab = 'canon' | 'ubl' | 'val' | 'comm' | 'steps'

/** Flattens nested values into "a.b" rows; lists of objects are shown as tables instead. */
function rows(value: unknown, prefix = ''): [string, string][] {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return Object.entries(value).flatMap(([k, v]) => rows(v, prefix ? `${prefix}.${k}` : k))
  }
  return [[prefix, Array.isArray(value) ? JSON.stringify(value) : String(value ?? '')]]
}

function CanonicalView({ invoice }: { invoice: Record<string, unknown> }) {
  const [mode, setMode] = useState<'form' | 'json'>('form')
  const entries = Object.entries(invoice)
  const tables = entries.filter(([, v]) => Array.isArray(v) && v.every((x) => x && typeof x === 'object')) as [string, Record<string, unknown>[]][]
  const blocks = entries.filter(([, v]) => v && typeof v === 'object' && !Array.isArray(v))
  const loose = entries.filter(([, v]) => !v || typeof v !== 'object')
  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Segmented options={[{ id: 'form' as const, label: 'Invoice view' }, { id: 'json' as const, label: 'JSON' }]} value={mode} onChange={setMode} />
      </div>
      {mode === 'json' ? (
        <pre className="max-h-[460px] overflow-auto rounded-lg border border-line bg-slate-50 p-4 font-mono text-[12px] leading-6">{JSON.stringify(invoice, null, 2)}</pre>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4">
            {[...blocks, ...(loose.length ? [['other', Object.fromEntries(loose)] as [string, unknown]] : [])].map(([title, v]) => (
              <Card key={title} className="p-4">
                <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-ink3">{title}</h3>
                <dl className="divide-y divide-line">
                  {rows(v).map(([k, val]) => (
                    <div key={k} className="flex justify-between gap-4 py-1.5 text-[13px]">
                      <dt className="font-mono text-[12px] text-ink3">{k}</dt>
                      <dd className="break-all text-right font-medium">{val}</dd>
                    </div>
                  ))}
                </dl>
              </Card>
            ))}
          </div>
          {tables.map(([title, list]) => {
            const cols = [...new Set(list.flatMap((item) => rows(item).map(([k]) => k)))]
            return (
              <Card key={title} className="overflow-x-auto">
                <h3 className="px-4 pt-3 text-xs font-bold uppercase tracking-wide text-ink3">{title}</h3>
                <table className="mt-2 w-full text-left text-[13px]">
                  <thead className="bg-slate-50 text-xs text-ink3">
                    <tr>{cols.map((c) => <th key={c} className="px-4 py-2 font-mono font-semibold">{c}</th>)}</tr>
                  </thead>
                  <tbody>
                    {list.map((item, i) => {
                      const values = Object.fromEntries(rows(item))
                      return <tr key={i} className="border-t border-line">{cols.map((c) => <td key={c} className="px-4 py-2">{values[c] ?? ''}</td>)}</tr>
                    })}
                  </tbody>
                </table>
              </Card>
            )
          })}
        </>
      )}
    </div>
  )
}

function XmlView({ text }: { text: string }) {
  return (
    <pre className="max-h-[460px] overflow-auto rounded-lg border border-line bg-slate-50 py-3 font-mono text-[12px] leading-6">
      {text.split('\n').map((l, i) => (
        <div key={i} className="flex px-3">
          <span className="mr-4 w-8 shrink-0 select-none text-right text-ink3">{i + 1}</span>
          <span className="whitespace-pre">
            {l.split(/(<[^>]+>)/).map((p, j) =>
              p.startsWith('<') ? (
                <span key={j} className="text-tx">{p.split(/("[^"]*")/).map((q, k) => (q.startsWith('"') ? <span key={k} className="text-va">{q}</span> : q))}</span>
              ) : (
                <span key={j} className="text-ink">{p}</span>
              ),
            )}
          </span>
        </div>
      ))}
    </pre>
  )
}

/** Lists messages once each; the same message reported for several places shows a count and the locations. */
function MessageList({ title, items, tone }: { title: string; items: (Issue | string)[]; tone: 'neutral' | 'err' | 'warn' }) {
  if (!items.length) return null
  const groups = new Map<string, string[]>()
  for (const item of items) {
    const { message, location } = typeof item === 'string' ? { message: item, location: null } : item
    groups.set(message, [...(groups.get(message) ?? []), ...(location ? [location] : [])])
  }
  return (
    <section>
      <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-ink3">{title} ({items.length})</h3>
      <div className="space-y-2">
        {[...groups].map(([message, locations]) => {
          const count = items.filter((i) => (typeof i === 'string' ? i : i.message) === message).length
          return (
            <div key={message} className={cx('rounded-lg border p-3 text-[13px]', { neutral: 'border-line', err: 'border-err-bd', warn: 'border-warn-bd' }[tone])}>
              <div className="flex items-start justify-between gap-3">
                <span>{message}</span>
                {count > 1 && <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-ink2">×{count}</span>}
              </div>
              {locations.length === 1 && <p className="mt-1 break-all font-mono text-[11px] text-ink3">{locations[0]}</p>}
              {locations.length > 1 && (
                <details className="mt-1">
                  <summary className="cursor-pointer text-[11px] font-semibold text-ink3">{locations.length} locations</summary>
                  <ul className="mt-1 space-y-0.5">
                    {locations.map((l, i) => <li key={i} className="break-all font-mono text-[11px] text-ink3">{l}</li>)}
                  </ul>
                </details>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

export function Results({ runId }: { runId: number | null }) {
  const { go, toast } = useApp()
  const [run, setRun] = useState<WorkflowRun | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<ResultTab>('canon')

  useEffect(() => {
    if (runId == null) return
    const ctrl = new AbortController()
    setRun(null)
    setError(null)
    getRun(runId, ctrl.signal).then(setRun).catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    return () => ctrl.abort()
  }, [runId])

  if (runId == null || error || !run) {
    return (
      <div className="mx-auto max-w-[1100px] p-8">
        <Card>
          {runId == null ? (
            <EmptyState icon={<FlaskConical size={20} />} title="No run selected" action={<Btn onClick={() => go('testruns')}>Test Runs</Btn>} />
          ) : error ? (
            <EmptyState icon={<XCircle size={20} />} tone="error" title="The run could not be loaded" body={error} />
          ) : (
            <div className="flex items-center justify-center gap-2 py-10 text-sm text-ink2"><Spinner /> Loading the run…</div>
          )}
        </Card>
      </div>
    )
  }

  const produces = (a: ArtifactType) => run.inputArtifactType === a || run.steps.some((s) => s.outputArtifact === a)
  const has = (stage: RunStep['stage']) => run.steps.some((s) => s.stage === stage)
  const canonical = run.canonicalInvoice && Object.keys(run.canonicalInvoice).length ? run.canonicalInvoice : null
  const ubl = typeof run.artifacts?.UBL_XML === 'string' ? (run.artifacts.UBL_XML as string) : null
  const v = run.validation
  const c = run.communication
  const stepName = (n: number | null) => run.steps.find((s) => s.sequence === n)?.operationName

  const cards: { id: ResultTab; title: string; kind: BadgeKind; label: string; show: boolean }[] = [
    { id: 'canon', title: 'Canonical Invoice', kind: canonical ? 'pass' : 'skipped', label: canonical ? `${rows(canonical).length} values` : 'Not produced', show: produces('CANONICAL_INVOICE') },
    { id: 'ubl', title: 'UBL XML', kind: ubl ? 'pass' : 'skipped', label: !ubl ? 'Not generated' : run.inputArtifactType === 'UBL_XML' ? 'Input' : 'Generated', show: produces('UBL_XML') },
    { id: 'val', title: 'Validation', kind: !v ? 'skipped' : { PASSED: 'pass', FAILED: 'info', UNDETERMINED: 'fail' }[v.status] as BadgeKind, label: !v ? 'Not run' : { PASSED: 'Valid', FAILED: 'Invalid', UNDETERMINED: 'Verdict unreadable' }[v.status], show: has('VALIDATION') },
    { id: 'comm', title: 'Communication', kind: !c ? 'skipped' : c.status === 'ACCEPTED' ? 'pass' : c.status === 'REJECTED' ? 'fail' : 'warning', label: !c ? 'Not attempted' : c.status === 'ACCEPTED' ? 'Accepted' : c.status === 'REJECTED' ? 'Rejected' : 'Unconfirmed', show: has('COMMUNICATION') },
  ].filter((x) => x.show) as { id: ResultTab; title: string; kind: BadgeKind; label: string; show: boolean }[]
  const current = cards.some((x) => x.id === tab) || tab === 'steps' ? tab : cards[0]?.id ?? 'steps'

  return (
    <div className="mx-auto max-w-[1100px] p-8">
      <PageHead
        title="Execution results"
        sub={[run.workflowName, run.inputName, when(run.startedAt)].filter(Boolean).join(' · ')}
        actions={<Btn onClick={() => go('testruns')}>Test Runs</Btn>}
      />
      <Card className={cx('mb-5 p-3 text-[13px]', RUN_TONE[run.status])}>
        <b>{RUN_LABEL[run.status].label}.</b> {run.message}
      </Card>
      <div className="mb-5 grid gap-3" style={{ gridTemplateColumns: `repeat(${Math.max(cards.length, 1)}, minmax(0, 1fr))` }}>
        {cards.map((x) => (
          <button key={x.id} onClick={() => setTab(x.id)} className={cx('rounded-lg border bg-white p-3 text-left hover:border-slate-400', current === x.id ? 'border-navy' : 'border-line')}>
            <div className="flex items-center justify-between gap-2">
              <span className="text-[13px] font-semibold">{x.title}</span>
              <Badge kind={x.kind}>{x.label}</Badge>
            </div>
          </button>
        ))}
      </div>
      <Card>
        <div className="px-4">
          <Tabs value={current} onChange={setTab} tabs={[...cards.map((x) => ({ id: x.id, label: x.title })), { id: 'steps' as const, label: 'Steps' }]} />
        </div>
        <div className="p-5">
          {current === 'canon' && (canonical
            ? <CanonicalView invoice={canonical} />
            : <EmptyState icon={<CircleSlash size={20} />} title="No canonical invoice was produced" body={run.message ?? 'An earlier step stopped the workflow.'} />)}
          {current === 'ubl' && (ubl ? (
            <div className="space-y-3">
              <div className="flex justify-end">
                <Btn size="sm" onClick={() => { navigator.clipboard?.writeText(ubl); toast('XML copied to clipboard') }}><Copy size={13} /> Copy</Btn>
              </div>
              <XmlView text={ubl} />
            </div>
          ) : (
            <EmptyState icon={<XCircle size={20} />} tone="error" title="UBL XML was not generated" body={run.message ?? 'An earlier step stopped the workflow.'} />
          ))}
          {current === 'val' && (v ? (
            <div className="space-y-4">
              <div className={cx('flex items-center gap-3 rounded-lg border p-4', { PASSED: 'border-ok-bd bg-ok-bg', FAILED: 'border-line bg-slate-50', UNDETERMINED: 'border-err-bd bg-err-bg' }[v.status])}>
                {{ PASSED: <Check className="rounded-full bg-ok p-0.5 text-white" />, FAILED: <Info className="text-ink2" />, UNDETERMINED: <XCircle className="text-err" /> }[v.status]}
                <div>
                  <div className="text-base font-semibold">{{ PASSED: 'Valid', FAILED: `Invalid · ${v.errors.length} rule ${v.errors.length === 1 ? 'violation' : 'violations'}`, UNDETERMINED: 'The verdict could not be read' }[v.status]}</div>
                  <div className="text-[13px] text-ink2">{stepName(v.step)}{v.detail ? ` · ${v.detail}` : ''}</div>
                </div>
              </div>
              <MessageList title="Rule violations" items={v.errors} tone="neutral" />
              <MessageList title="Warnings" items={v.warnings} tone="warn" />
            </div>
          ) : (
            <EmptyState icon={<CircleSlash size={20} />} title="Validation was not run" body={run.message ?? 'An earlier step stopped the workflow.'} />
          ))}
          {current === 'comm' && (c ? (
            <div className="space-y-4">
              <div className={cx('rounded-lg border p-4', c.status === 'ACCEPTED' ? 'border-ok-bd bg-ok-bg' : c.status === 'REJECTED' ? 'border-err-bd bg-err-bg' : 'border-warn-bd bg-warn-bg')}>
                <div className="text-base font-semibold">{{ ACCEPTED: 'Accepted', REJECTED: 'Rejected', UNCONFIRMED: 'Sent, acceptance not confirmed' }[c.status]}</div>
                <div className="text-[13px] text-ink2">{stepName(c.step)}{c.reference ? ` · reference ${c.reference}` : ''}{c.detail ? ` · ${c.detail}` : ''}</div>
              </div>
              <MessageList title="Errors" items={c.errors} tone="err" />
            </div>
          ) : (
            <EmptyState icon={<CircleSlash size={20} />} title="Nothing was sent" body={run.message ?? 'Sending only happens after the invoice passes validation.'} />
          ))}
          {current === 'steps' && (
            <div className="space-y-2">
              {run.steps.map((s) => {
                const stage = stageKey(s.stage)
                return (
                  <details key={s.sequence} className="rounded-lg border border-line">
                    <summary className="flex cursor-pointer items-center gap-2 px-3 py-2 text-[13px] font-semibold">
                      {stage && <span className={cx('h-2 w-2 rounded-full', STAGE[stage].dot)} />}
                      {s.sequence}. {s.operationName}
                      <span className="ml-auto text-xs font-normal text-ink3">{STEP_STATE[s.status].label} · {seconds(s.durationMs)}</span>
                    </summary>
                    <StepPayloads step={s} />
                  </details>
                )
              })}
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

/* ================= Test runs ================= */
export function TestRuns({ onResults }: { onResults: (runId: number) => void }) {
  const [runs, setRuns] = useState<RunSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    listRuns(ctrl.signal).then(setRuns).catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    return () => ctrl.abort()
  }, [])

  return (
    <div className="mx-auto max-w-[1100px] p-8">
      <PageHead title="Test Runs" />
      <Card className="overflow-hidden">
        {error ? (
          <EmptyState icon={<XCircle size={20} />} tone="error" title="Test runs could not be loaded" body={error} />
        ) : !runs ? (
          <div className="flex items-center justify-center gap-2 py-10 text-sm text-ink2"><Spinner /> Loading test runs…</div>
        ) : !runs.length ? (
          <EmptyState icon={<FlaskConical size={20} />} title="No test runs yet" />
        ) : (
          runs.map((r) => (
            <div key={r.id} className="grid grid-cols-[1.6fr_1.6fr_190px_70px_170px_auto] items-center gap-3 border-b border-line px-5 py-3 text-[13px] last:border-0">
              <span className="truncate font-semibold">{r.workflowName ?? `Workflow ${r.workflowId}`}</span>
              <span className="truncate text-ink2">{r.inputName ?? ARTIFACT_LABEL[r.inputArtifactType]}</span>
              <span><Badge kind={RUN_LABEL[r.status]?.kind ?? 'info'}>{RUN_LABEL[r.status]?.label ?? r.status}</Badge></span>
              <span className="tabular-nums text-ink3">{elapsed(r)}</span>
              <span className="text-ink3">{when(r.startedAt)}</span>
              <Btn size="sm" onClick={() => onResults(r.id)}>Results</Btn>
            </div>
          ))
        )}
      </Card>
    </div>
  )
}

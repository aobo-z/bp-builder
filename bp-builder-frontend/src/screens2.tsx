import { useEffect, useState } from 'react'
import {
  ArrowRight,
  Info,
  RefreshCw,
  Search,
  XCircle,
} from 'lucide-react'
import {
  ArtifactTag,
  Badge,
  Btn,
  Card,
  EmptyState,
  PageHead,
  Segmented,
  Spinner,
  STAGE,
  StageTag,
  cx,
  inputCls,
  type BadgeKind,
  type Tone,
} from './ui'
import {
  ARTIFACT_LABEL,
  SOURCE_LABEL,
  authLabel,
  fetchOperations,
  stageOf,
  verificationOf,
  type CatalogOperation,
} from './api'

/* ================= Catalog ================= */
const TONE_BADGE: Record<Tone, BadgeKind> = { ok: 'verified', warn: 'unverified', err: 'unavailable', neutral: 'skipped' }

export function Catalog() {
  const [ops, setOps] = useState<CatalogOperation[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)
  const [q, setQ] = useState('')
  const [stage, setStage] = useState('all')
  const [inp, setInp] = useState('all')
  const [out, setOut] = useState('all')
  const [ver, setVer] = useState('all')
  const [showUnreviewed, setShowUnreviewed] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    setError(null)
    fetchOperations(controller.signal)
      .then(setOps)
      .catch((e: Error) => {
        if (e.name !== 'AbortError') setError(e.message)
      })
    return () => controller.abort()
  }, [reload])

  const all = ops ?? []
  const unreviewed = all.filter((o) => !o.chainable).length
  const visible = showUnreviewed ? all : all.filter((o) => o.chainable)
  const arts = (k: 'inputArtifact' | 'outputArtifact') => [...new Set(visible.map((o) => o[k]))]
  const list = visible.filter(
    (o) =>
      (stage === 'all' || stageOf(o) === stage) &&
      (inp === 'all' || o.inputArtifact === inp) &&
      (out === 'all' || o.outputArtifact === out) &&
      (ver === 'all' || verificationOf(o.verificationStatus).tone === ver) &&
      (o.name + o.provider + o.operationKey).toLowerCase().includes(q.toLowerCase()),
  )
  const clear = () => { setStage('all'); setInp('all'); setOut('all'); setVer('all'); setQ('') }
  const sel = 'h-9 rounded-lg border border-line bg-white px-2 text-[13px]'
  return (
    <div className="mx-auto max-w-[1200px] p-8">
      <PageHead
        title="Operation Catalog"
        actions={<Btn size="sm" onClick={() => setReload((n) => n + 1)} disabled={!ops && !error}><RefreshCw size={14} /> Refresh</Btn>}
      />
      {error ? (
        <Card><EmptyState tone="error" icon={<XCircle size={20} />} title="Could not load the catalog" body={error} action={<Btn onClick={() => setReload((n) => n + 1)}><RefreshCw size={14} /> Try again</Btn>} /></Card>
      ) : !ops ? (
        <Card><div className="flex items-center justify-center gap-2 py-12 text-sm text-ink2"><Spinner /> Loading operations…</div></Card>
      ) : (
      <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative w-60">
          <Search size={14} className="absolute left-3 top-3 text-ink3" />
          <input aria-label="Search operations" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search operations" className={cx(inputCls, 'h-9 pl-8')} />
        </div>
        <Segmented
          options={[{ id: 'all', label: 'All stages' }, { id: 'transformation', label: 'Transformation' }, { id: 'validation', label: 'Validation' }, { id: 'communication', label: 'Communication' }]}
          value={stage}
          onChange={setStage}
        />
        <select aria-label="Input artifact" className={sel} value={inp} onChange={(e) => setInp(e.target.value)}>
          <option value="all">Any input</option>
          {arts('inputArtifact').map((a) => <option key={a} value={a}>{ARTIFACT_LABEL[a]}</option>)}
        </select>
        <select aria-label="Output artifact" className={sel} value={out} onChange={(e) => setOut(e.target.value)}>
          <option value="all">Any output</option>
          {arts('outputArtifact').map((a) => <option key={a} value={a}>{ARTIFACT_LABEL[a]}</option>)}
        </select>
        <select aria-label="Verification status" className={sel} value={ver} onChange={(e) => setVer(e.target.value)}>
          <option value="all">Any verification</option>
          <option value="ok">Verified</option>
          <option value="warn">Needs attention</option>
          <option value="neutral">Not reviewed</option>
        </select>
        {unreviewed > 0 && (
          <label className="flex items-center gap-2 text-[13px] text-ink2">
            <input type="checkbox" checked={showUnreviewed} onChange={(e) => setShowUnreviewed(e.target.checked)} />
            Show {unreviewed} unreviewed
          </label>
        )}
        <span className="ml-auto text-[13px] text-ink3">{list.length} of {all.length} operations</span>
      </div>
      {list.length === 0 ? (
        <Card><EmptyState icon={<Info size={20} />} title={all.length ? 'No operations match' : 'The catalog is empty'} body={all.length ? 'Clear a filter to see more of the catalog.' : 'No active operations are registered in Publisher yet.'} action={all.length ? <Btn onClick={clear}>Clear filters</Btn> : undefined} /></Card>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {list.map((o) => {
            const st = stageOf(o)
            const v = verificationOf(o.verificationStatus)
            return (
            <Card key={o.id} className="overflow-hidden">
              <div className={cx('h-1', st ? STAGE[st].solid : 'bg-slate-300')} />
              <div className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    {st ? <StageTag stage={st} small /> : <Badge kind="draft">No stage</Badge>}
                    <h3 className="mt-2 text-[15px] font-semibold">{o.name}</h3>
                    <div className="text-[13px] text-ink3">{o.provider}</div>
                  </div>
                  <span className="shrink-0 font-mono text-[11px] text-ink3">#{o.id}</span>
                </div>
                {o.description && o.description !== o.summary && <p className="mt-2 line-clamp-3 text-[13px] text-ink2">{o.description}</p>}
                <div className="mt-3 flex items-center gap-2"><ArtifactTag>{ARTIFACT_LABEL[o.inputArtifact]}</ArtifactTag><ArrowRight size={12} className="text-ink3" /><ArtifactTag>{ARTIFACT_LABEL[o.outputArtifact]}</ArtifactTag></div>
                <dl className="mt-3 divide-y divide-line border-t border-line text-xs">
                  {([
                    ['Operation', <span key="k" className="font-mono">{o.operationKey}</span>],
                    ['Request', `${o.method} · ${o.requestContentType}`],
                    ['Authentication', authLabel(o.authMethod)],
                    ['Approved mappings', o.inputMappings + o.outputMappings > 0 && `${o.inputMappings} input (${o.requiredInputMappings} required) · ${o.outputMappings} output`],
                    ['Outcome rule', (st === 'validation' || st === 'communication') && (o.outcomeRule ? 'Reviewed' : 'Missing')],
                    ['Verification status', <Badge key="v" kind={TONE_BADGE[v.tone]}>{v.label}</Badge>],
                    ['Contract source', SOURCE_LABEL[o.contractSource] ?? o.contractSource],
                    ['Timeout', o.timeoutSeconds > 0 && `${o.timeoutSeconds} s`],
                  ] as [string, React.ReactNode][]).filter(([, value]) => value).map(([k, value]) => (
                    <div key={k} className="flex items-start justify-between gap-3 py-1.5">
                      <dt className="shrink-0 text-ink3">{k}</dt>
                      <dd className="min-w-0 break-words text-right font-medium text-ink2">{value}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            </Card>
            )
          })}
        </div>
      )}
      </>
      )}
    </div>
  )
}

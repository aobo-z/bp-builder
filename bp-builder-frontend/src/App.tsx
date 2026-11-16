import { useCallback, useState } from 'react'
import {
  FilePlus2,
  FlaskConical,
  Layers,
  LayoutList,
  Lock,
  Play,
  Rocket,
  Workflow,
} from 'lucide-react'
import { ASSESSMENT_BADGE, WorkflowBuilder, WorkflowReadiness, canRun, defaultName, fromSaved, type DraftWorkflow } from './WorkflowBuilder'
import { CreateChoose, CreateInput, Dashboard, EMPTY_REQUIREMENT, Generating, Review, type Method } from './screens1'
import { createWorkflow, getWorkflow, type ChainCandidate, type Interpretation, type Requirement } from './api'
import { Catalog } from './screens2'
import { Deploy } from './deploy'
import { Results, TestPage, TestRuns } from './runs'
import { AppCtx, Badge, Btn, ToastView, cx, type View } from './ui'

const NAV: { id: View; label: string; icon: React.ReactNode; match?: View[] }[] = [
  { id: 'dashboard', label: 'Workflows', icon: <Workflow size={17} /> },
  { id: 'create', label: 'Create Workflow', icon: <FilePlus2 size={17} />, match: ['create', 'input', 'review', 'generating'] },
  { id: 'testruns', label: 'Test Runs', icon: <FlaskConical size={17} />, match: ['testruns', 'results'] },
  { id: 'deploy', label: 'Deployments', icon: <Rocket size={17} /> },
  { id: 'catalog', label: 'Operation Catalog', icon: <Layers size={17} /> },
]
const WF_VIEWS: View[] = ['builder', 'test', 'deploy']
export default function App() {
  const [view, setView] = useState<View>('dashboard')
  const [method, setMethod] = useState<Method>('nl')
  const [text, setText] = useState('')
  const [interpretation, setInterpretation] = useState<Interpretation | null>(null)
  const [req, setReq] = useState<Requirement>(EMPTY_REQUIREMENT)
  const [workflow, setWorkflow] = useState<DraftWorkflow | null>(null)
  const [runId, setRunId] = useState<number | null>(null)
  const [readiness, setReadiness] = useState(false)
  const [toasts, setToasts] = useState<{ id: number; msg: string; kind: 'ok' | 'err' | 'info' }[]>([])

  const toast = useCallback((msg: string, kind: 'ok' | 'err' | 'info' = 'ok') => {
    const id = Date.now() + Math.random()
    setToasts((t) => [...t, { id, msg, kind }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3200)
  }, [])

  const wfStatus = workflow ? ASSESSMENT_BADGE[workflow.candidate.assessment.status] : null
  const canDeploy = canRun(workflow)
  const inWf = WF_VIEWS.includes(view)

  const ctx = {
    go: setView,
    toast,
    openReadiness: () => setReadiness(true),
  }
  const onInterpreted = (result: Interpretation | null) => {
    setInterpretation(result)
    setReq(result
      ? { inputArtifactType: result.inputArtifactType, goalArtifactType: result.goalArtifactType, validationOperationId: result.validationOperationId }
      : EMPTY_REQUIREMENT)
  }
  /** Saves a chain as a new workflow and opens it. */
  const save = async (requirement: Requirement, operationIds: number[], described: Interpretation | null) => {
    try {
      const saved = await createWorkflow({
        name: defaultName(requirement),
        requirementText: described && method === 'nl' ? text.trim() : null,
        inputArtifactType: requirement.inputArtifactType!,
        goalArtifactType: requirement.goalArtifactType!,
        validationOperationId: requirement.validationOperationId,
        interpretation: described,
        operationIds,
      })
      setWorkflow(fromSaved(saved))
      setView('builder')
      toast('Workflow saved')
    } catch (e) {
      toast((e as Error).message, 'err')
    }
  }
  const onChoose = (candidate: ChainCandidate) => save(req, candidate.steps.map((s) => s.operationId), interpretation)
  const onBuilt = (requirement: Requirement, operationIds: number[]) => {
    setReq(requirement)
    setInterpretation(null)
    return save(requirement, operationIds, null)
  }
  const showResults = (id: number) => {
    setRunId(id)
    setView('results')
  }
  const onOpen = async (id: number) => {
    try {
      const saved = await getWorkflow(id)
      setWorkflow(fromSaved(saved))
      setReq({ inputArtifactType: saved.inputArtifactType, goalArtifactType: saved.goalArtifactType, validationOperationId: saved.validationOperationId })
      setView('builder')
    } catch (e) {
      toast((e as Error).message, 'err')
    }
  }

  return (
    <AppCtx.Provider value={ctx}>
        <div className="flex h-screen overflow-hidden bg-canvas text-ink">
          <nav className="flex w-[232px] shrink-0 flex-col border-r border-line bg-white max-[1100px]:w-[68px]" aria-label="Main">
            <div className="flex h-14 items-center gap-2.5 border-b border-line px-5 max-[1100px]:justify-center max-[1100px]:px-0">
              <div className="grid h-7 w-7 place-items-center rounded-md bg-navy text-white"><LayoutList size={15} /></div>
              <div className="text-[13px] font-bold leading-tight max-[1100px]:hidden">Invoice Workflow<br />Builder</div>
            </div>
            <div className="flex-1 space-y-0.5 p-3">
              {NAV.map((n) => {
                const on = n.match ? n.match.includes(view) : view === n.id
                return (
                  <button
                    key={n.id}
                    onClick={() => ctx.go(n.id)}
                    title={n.label}
                    aria-current={on ? 'page' : undefined}
                    className={cx(
                      'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-[13.5px] font-semibold transition-colors max-[1100px]:justify-center',
                      on ? 'bg-slate-100 text-ink' : 'text-ink2 hover:bg-slate-50',
                    )}
                  >
                    {n.icon}
                    <span className="max-[1100px]:hidden">{n.label}</span>
                    {n.id === 'catalog' && <Lock size={11} className="ml-auto text-ink3 max-[1100px]:hidden" />}
                  </button>
                )
              })}
            </div>
          </nav>

          <div className="flex min-w-0 flex-1 flex-col">
            <header className="flex h-14 shrink-0 items-center justify-between border-b border-line bg-white px-6">
              <div className="flex min-w-0 items-center gap-3">
                {inWf && workflow && wfStatus ? (
                  <>
                    <h1 className="truncate text-[15px] font-bold">{workflow.name}</h1>
                    <Badge kind={wfStatus.kind}>{wfStatus.label}</Badge>
                    <span className="text-xs text-ink3">{workflow.candidate.steps.length} {workflow.candidate.steps.length === 1 ? 'step' : 'steps'} · saved {new Date(workflow.updatedAt).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })}</span>
                  </>
                ) : (
                  <span className="text-sm font-semibold text-ink2">No workflow open</span>
                )}
              </div>
              {inWf && workflow && (
                <div className="flex items-center gap-2">
                  <Btn size="sm" onClick={() => ctx.go('test')}><Play size={14} /> Test Workflow</Btn>
                  {view !== 'deploy' && (
                    <Btn size="sm" variant="primary" onClick={() => ctx.go('deploy')} disabled={!canDeploy} title={canDeploy ? undefined : 'This workflow cannot run as it stands'}>
                      <Rocket size={14} /> Deploy
                    </Btn>
                  )}
                </div>
              )}
            </header>
            <main className={cx('min-h-0 flex-1', view === 'builder' ? 'overflow-hidden' : 'overflow-y-auto')} key={view === 'builder' ? 'b' : undefined}>
              {view === 'dashboard' && <Dashboard onOpen={onOpen} />}
              {view === 'create' && <CreateChoose method={method} setMethod={setMethod} />}
              {view === 'input' && <CreateInput method={method} text={text} setText={setText} onInterpreted={onInterpreted} onBuilt={onBuilt} />}
              {view === 'review' && <Review interpretation={interpretation} req={req} setReq={setReq} onGenerate={() => setView('generating')} />}
              {view === 'generating' && <Generating req={req} onChoose={onChoose} />}
              {view === 'builder' && <WorkflowBuilder wf={workflow} setWf={setWorkflow} openReadiness={() => setReadiness(true)} />}
              {view === 'test' && <TestPage key={workflow?.id} wf={workflow} onResults={showResults} />}
              {view === 'results' && <Results runId={runId} />}
              {view === 'deploy' && <Deploy wf={workflow} />}
              {view === 'testruns' && <TestRuns onResults={showResults} />}
              {view === 'catalog' && <Catalog />}
            </main>
          </div>
        </div>
        {readiness && workflow && <WorkflowReadiness wf={workflow} onClose={() => setReadiness(false)} />}
        <div className="fixed bottom-5 right-5 z-[60] flex flex-col gap-2">
          {toasts.map((t) => <ToastView key={t.id} kind={t.kind} msg={t.msg} />)}
        </div>
    </AppCtx.Provider>
  )
}

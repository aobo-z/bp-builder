import { useEffect, useState } from 'react'
import { Copy, ExternalLink, Rocket, XCircle } from 'lucide-react'
import { Badge, Btn, Card, EmptyState, PageHead, Spinner, cx, useApp } from './ui'
import { deployWorkflow, getDeployment, type ArtifactType, type Deployment } from './api'
import { canRun, type DraftWorkflow } from './WorkflowBuilder'
import { when } from './runs'

/** What the "input" of a request looks like for each workflow input type. */
const EXAMPLE_INPUT: Partial<Record<ArtifactType, string>> = {
  PDF_DOCUMENT: '"<the PDF invoice as Base64>"',
  IMAGE_DOCUMENT: '"<the PNG or JPG invoice as Base64>"',
  UBL_XML: '"<the UBL XML invoice as text>"',
  CANONICAL_INVOICE: '{"invoice": {"id": "INV-1", "issue_date": "2026-10-16"}, "lines": [...]}',
}

export function Deploy({ wf }: { wf: DraftWorkflow | null }) {
  const { toast } = useApp()
  const [deployment, setDeployment] = useState<Deployment | null | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!wf) return
    const ctrl = new AbortController()
    setDeployment(undefined)
    setError(null)
    getDeployment(wf.id, ctrl.signal)
      .then((d) => setDeployment(d ?? null))
      .catch((e: Error) => { if (e.name !== 'AbortError') setError(e.message) })
    return () => ctrl.abort()
  }, [wf?.id, wf?.updatedAt])

  if (!wf) {
    return (
      <div className="mx-auto max-w-[920px] p-8">
        <Card><EmptyState icon={<Rocket size={20} />} title="No workflow open" /></Card>
      </div>
    )
  }

  const deploy = () => {
    setBusy(true)
    deployWorkflow(wf.id)
      .then((d) => { setDeployment(d); toast('Workflow deployed') })
      .catch((e: Error) => toast(e.message, 'err'))
      .finally(() => setBusy(false))
  }
  const copy = (text: string) => { navigator.clipboard?.writeText(text); toast('Copied to clipboard') }
  const ready = canRun(wf)
  const request = deployment
    ? `curl -X POST "${deployment.functionUrl}" \\\n  -H "Content-Type: application/json" \\\n  -d '{"input": ${EXAMPLE_INPUT[deployment.inputArtifactType] ?? '...'}}'`
    : ''

  return (
    <div className="mx-auto max-w-[920px] p-8">
      <PageHead
        title="Deployment"
        actions={
          <Btn variant="primary" disabled={!ready || busy || deployment === undefined} onClick={deploy}>
            {busy ? <Spinner size={14} /> : <Rocket size={14} />} {busy ? 'Deploying…' : deployment ? 'Redeploy' : 'Deploy'}
          </Btn>
        }
      />
      {!ready && <p className="mb-4 text-[13px] text-err">Only a Ready workflow can be deployed.</p>}
      {busy && <p className="mb-4 text-[13px] text-ink2">This can take a few minutes.</p>}
      <Card className="p-5">
        {error ? (
          <EmptyState icon={<XCircle size={20} />} tone="error" title="The deployment could not be loaded" body={error} />
        ) : deployment === undefined ? (
          <div className="flex items-center justify-center gap-2 py-6 text-sm text-ink2"><Spinner /> Loading…</div>
        ) : deployment === null ? (
          <div className="flex items-center gap-3 text-[13px] text-ink2">
            <Badge kind="draft">Not deployed</Badge>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-3 text-[13px] text-ink2">
              <Badge kind={deployment.upToDate ? 'deployed' : 'warning'}>{deployment.upToDate ? 'Deployed' : 'Out of date'}</Badge>
              <span className="font-mono text-[12px]">{deployment.functionName}</span>
              <span>{deployment.region}</span>
              <span>Deployed {when(deployment.deployedAt)}</span>
              {deployment.stateMachineArn && (
                <a
                  className="ml-auto inline-flex items-center gap-1 font-semibold text-tx hover:underline"
                  href={`https://${deployment.region}.console.aws.amazon.com/states/home?region=${deployment.region}#/statemachines/view/${encodeURIComponent(deployment.stateMachineArn)}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  View flow in AWS Step Functions <ExternalLink size={13} />
                </a>
              )}
            </div>
            {!deployment.upToDate && (
              <p className="rounded-lg border border-warn-bd bg-warn-bg p-3 text-[13px]">
                The workflow changed since this deployment. Redeploy to update it.
              </p>
            )}
            <div>
              <div className="mb-1 text-xs font-semibold text-ink2">Endpoint</div>
              <div className="flex items-center gap-2">
                <code className="flex h-10 min-w-0 flex-1 items-center truncate rounded-lg border border-co-bd bg-co-bg px-3 font-mono text-[12px] text-co">{deployment.functionUrl}</code>
                <Btn size="sm" onClick={() => copy(deployment.functionUrl)}><Copy size={13} /> Copy</Btn>
              </div>
            </div>
            <div>
              <div className="mb-1 text-xs font-semibold text-ink2">Example request</div>
              <pre className={cx('overflow-x-auto rounded-lg border border-line bg-slate-50 p-3 font-mono text-[12px] leading-relaxed')}>{request}</pre>
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}

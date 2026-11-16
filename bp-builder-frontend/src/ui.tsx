import { createContext, useContext, type ReactNode } from 'react'
import {
  AlertTriangle,
  CheckCircle2,
  CircleDashed,
  CircleSlash,
  FileCode2,
  Info,
  Loader2,
  X,
  XCircle,
} from 'lucide-react'

export type Stage = 'transformation' | 'validation' | 'communication'

export type View =
  | 'dashboard'
  | 'create'
  | 'input'
  | 'review'
  | 'generating'
  | 'builder'
  | 'test'
  | 'results'
  | 'deploy'
  | 'testruns'
  | 'catalog'

export interface Ctx {
  go: (v: View) => void
  toast: (msg: string, kind?: 'ok' | 'err' | 'info') => void
  openReadiness: () => void
}
export const AppCtx = createContext<Ctx>(null as unknown as Ctx)
export const useApp = () => useContext(AppCtx)

export const STAGE: Record<
  Stage,
  { label: string; text: string; bg: string; bd: string; solid: string; dot: string; hex: string }
> = {
  transformation: {
    label: 'Transformation',
    text: 'text-tx',
    bg: 'bg-tx-bg',
    bd: 'border-tx-bd',
    solid: 'bg-tx',
    dot: 'bg-tx',
    hex: '#4338ca',
  },
  validation: {
    label: 'Validation',
    text: 'text-va',
    bg: 'bg-va-bg',
    bd: 'border-va-bd',
    solid: 'bg-va',
    dot: 'bg-va',
    hex: '#b45309',
  },
  communication: {
    label: 'Communication',
    text: 'text-co',
    bg: 'bg-co-bg',
    bd: 'border-co-bd',
    solid: 'bg-co',
    dot: 'bg-co',
    hex: '#0f766e',
  },
}

/* ---------- catalog data ---------- */
export type Tone = 'ok' | 'warn' | 'err' | 'neutral'
/* ---------- primitives ---------- */
export function cx(...a: (string | false | undefined | null)[]) {
  return a.filter(Boolean).join(' ')
}

export type BadgeKind =
  | 'ready'
  | 'action'
  | 'warning'
  | 'draft'
  | 'published'
  | 'deployed'
  | 'verified'
  | 'unverified'
  | 'unavailable'
  | 'pass'
  | 'fail'
  | 'skipped'
  | 'info'
  | 'running'
  | 'confirm'

const BADGE: Record<BadgeKind, { cls: string; label: string; icon: ReactNode }> = {
  ready: { cls: 'bg-ok-bg text-ok border-ok-bd', label: 'Ready', icon: <CheckCircle2 size={12} /> },
  action: {
    cls: 'bg-err-bg text-err border-err-bd',
    label: 'Action required',
    icon: <XCircle size={12} />,
  },
  warning: {
    cls: 'bg-warn-bg text-warn border-warn-bd',
    label: 'Warning',
    icon: <AlertTriangle size={12} />,
  },
  draft: {
    cls: 'bg-slate-100 text-ink2 border-slate-300',
    label: 'Draft',
    icon: <CircleDashed size={12} />,
  },
  published: {
    cls: 'bg-tx-bg text-tx border-tx-bd',
    label: 'Published',
    icon: <CheckCircle2 size={12} />,
  },
  deployed: {
    cls: 'bg-co-bg text-co border-co-bd',
    label: 'Deployed',
    icon: <CheckCircle2 size={12} />,
  },
  verified: {
    cls: 'bg-ok-bg text-ok border-ok-bd',
    label: 'Verified',
    icon: <CheckCircle2 size={12} />,
  },
  unverified: {
    cls: 'bg-warn-bg text-warn border-warn-bd',
    label: 'Unverified',
    icon: <AlertTriangle size={12} />,
  },
  unavailable: {
    cls: 'bg-err-bg text-err border-err-bd',
    label: 'Endpoint unavailable',
    icon: <XCircle size={12} />,
  },
  pass: { cls: 'bg-ok-bg text-ok border-ok-bd', label: 'Passed', icon: <CheckCircle2 size={12} /> },
  fail: { cls: 'bg-err-bg text-err border-err-bd', label: 'Failed', icon: <XCircle size={12} /> },
  skipped: {
    cls: 'bg-slate-100 text-ink2 border-slate-300',
    label: 'Not attempted',
    icon: <CircleSlash size={12} />,
  },
  info: { cls: 'bg-tx-bg text-tx border-tx-bd', label: 'Info', icon: <Info size={12} /> },
  running: {
    cls: 'bg-tx-bg text-tx border-tx-bd',
    label: 'Running',
    icon: <Loader2 size={12} className="animate-spin" />,
  },
  confirm: {
    cls: 'bg-warn-bg text-warn border-warn-bd',
    label: 'Needs confirmation',
    icon: <AlertTriangle size={12} />,
  },
}
export function Badge({ kind, children }: { kind: BadgeKind; children?: ReactNode }) {
  const b = BADGE[kind]
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-semibold',
        b.cls,
      )}
    >
      {b.icon}
      {children ?? b.label}
    </span>
  )
}

export function Btn({
  variant = 'secondary',
  size = 'md',
  children,
  className,
  ...p
}: {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'teal'
  size?: 'sm' | 'md'
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const v = {
    primary: 'bg-navy text-white hover:bg-[#0c1738] border-navy',
    secondary: 'bg-white text-ink border-line hover:bg-slate-50 hover:border-slate-300',
    ghost: 'bg-transparent text-ink2 border-transparent hover:bg-slate-100',
    danger: 'bg-white text-err border-err-bd hover:bg-err-bg',
    teal: 'bg-co text-white border-co hover:bg-[#0b5f58]',
  }[variant]
  return (
    <button
      {...p}
      className={cx(
        'inline-flex items-center justify-center gap-2 rounded-lg border font-semibold transition-colors disabled:opacity-45',
        size === 'sm' ? 'h-8 px-3 text-[13px]' : 'h-10 px-4 text-sm',
        v,
        className,
      )}
    >
      {children}
    </button>
  )
}

export function Card({
  children,
  className,
  ...p
}: { children: ReactNode; className?: string } & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      {...p}
      className={cx('rounded-lg border border-line bg-white shadow-[0_1px_2px_rgba(15,27,51,0.04)]', className)}
    >
      {children}
    </div>
  )
}

export function StageTag({ stage, small }: { stage: Stage; small?: boolean }) {
  const s = STAGE[stage]
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full border font-semibold',
        s.bg,
        s.bd,
        s.text,
        small ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs',
      )}
    >
      <span className={cx('h-1.5 w-1.5 rounded-full', s.dot)} />
      {s.label}
    </span>
  )
}

export function ArtifactTag({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-md border border-art-bd bg-art-bg px-1.5 py-0.5 text-[11px] font-semibold text-art">
      <FileCode2 size={11} />
      {children}
    </span>
  )
}

export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
}: {
  tabs: { id: T; label: string; badge?: ReactNode }[]
  value: T
  onChange: (t: T) => void
}) {
  return (
    <div role="tablist" className="flex gap-1 border-b border-line">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={value === t.id}
          onClick={() => onChange(t.id)}
          className={cx(
            '-mb-px flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-semibold transition-colors',
            value === t.id ? 'border-navy text-ink' : 'border-transparent text-ink3 hover:text-ink',
          )}
        >
          {t.label}
          {t.badge}
        </button>
      ))}
    </div>
  )
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { id: T; label: string }[]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div className="inline-flex rounded-lg border border-line bg-slate-100 p-0.5">
      {options.map((o) => (
        <button
          key={o.id}
          onClick={() => onChange(o.id)}
          className={cx(
            'rounded-md px-3 py-1 text-[13px] font-semibold transition-colors',
            value === o.id ? 'bg-white text-ink shadow-sm' : 'text-ink3 hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

export function Spinner({ size = 16 }: { size?: number }) {
  return <Loader2 size={size} className="animate-spin text-tx" />
}

export function EmptyState({
  icon,
  title,
  body,
  action,
  tone = 'neutral',
}: {
  icon: ReactNode
  title: string
  body?: string
  action?: ReactNode
  tone?: 'neutral' | 'error' | 'warn'
}) {
  const t = { neutral: 'bg-slate-100 text-ink2', error: 'bg-err-bg text-err', warn: 'bg-warn-bg text-warn' }[tone]
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center">
      <div className={cx('mb-3 grid h-12 w-12 place-items-center rounded-full', t)}>{icon}</div>
      <h3 className="text-base font-semibold">{title}</h3>
      {body && <p className="mt-1 max-w-md text-sm text-ink2">{body}</p>}
      {action && <div className="mt-4 flex gap-2">{action}</div>}
    </div>
  )
}

export function Dialog({
  title,
  children,
  onClose,
  footer,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  footer: ReactNode
}) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-ink/40 p-6" onClick={onClose}>
      <div
        role="dialog"
        aria-label={title}
        className="fadeup w-full max-w-md rounded-lg bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-line px-5 py-4">
          <h2 className="text-base font-semibold">{title}</h2>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 text-ink3 hover:bg-slate-100">
            <X size={16} />
          </button>
        </div>
        <div className="px-5 py-4 text-sm text-ink2">{children}</div>
        <div className="flex justify-end gap-2 border-t border-line bg-slate-50 px-5 py-3 rounded-b-lg">{footer}</div>
      </div>
    </div>
  )
}

export function ToastView({ kind, msg }: { kind: 'ok' | 'err' | 'info'; msg: string }) {
  const k = {
    ok: ['border-ok-bd', <CheckCircle2 key="i" size={16} className="text-ok" />],
    err: ['border-err-bd', <XCircle key="i" size={16} className="text-err" />],
    info: ['border-tx-bd', <Info key="i" size={16} className="text-tx" />],
  }[kind]
  return (
    <div
      role="status"
      className={cx(
        'slidein flex items-center gap-3 rounded-lg border bg-white px-4 py-3 text-sm font-medium shadow-lg',
        k[0] as string,
      )}
    >
      {k[1]}
      {msg}
    </div>
  )
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-semibold text-ink2">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-ink3">{hint}</span>}
    </label>
  )
}
export const inputCls =
  'h-10 w-full rounded-lg border border-line bg-white px-3 text-sm text-ink placeholder:text-ink3 hover:border-slate-300'

export function PageHead({
  title,
  sub,
  actions,
}: {
  title: string
  sub?: string
  actions?: ReactNode
}) {
  return (
    <div className="mb-6 flex items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {sub && <p className="mt-1 text-sm text-ink2">{sub}</p>}
      </div>
      <div className="flex gap-2">{actions}</div>
    </div>
  )
}

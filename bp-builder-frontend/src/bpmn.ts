import { ARTIFACT_LABEL, type ChainStep } from './api'
import type { DraftWorkflow } from './WorkflowBuilder'

/* ---------- geometry ---------- */
const LANE_W = 2000
const LANE_H = 260
const LANE_ORDER: ChainStep['stage'][] = ['TRANSFORMATION', 'VALIDATION', 'COMMUNICATION']
const LANE_LABEL: Record<string, string> = { TRANSFORMATION: 'Transformation', VALIDATION: 'Validation', COMMUNICATION: 'Communication' }
const LANE_Y: Record<string, number> = Object.fromEntries(LANE_ORDER.map((s, n) => [s as string, n * LANE_H]))

const EVENT_D = 36
const GATEWAY_D = 50
const TASK_W = 160
const TASK_H = 90
// Wide enough that a flow's name label — placed in the gap between two nodes,
// see route() below — has room to sit without touching either one.
const X_GAP = 260

type Layout = 'lanes' | 'single'

/** `lane` is only read when the caller asks for lanes to be emitted (see emitXml); a caller with no lane concept at all, like the single flat row built for an uploaded file's preview, just leaves it unset. */
interface Node { id: string; kind: 'start' | 'end' | 'task' | 'gateway'; name: string; lane?: string; x: number; y: number; w: number; h: number }
interface Flow { id: string; from: string; to: string; name?: string }

/** Lays out the workflow's steps left to right. In 'lanes' layout, steps sit inside their own stage's band (the three coloured lanes). In 'single' layout every step sits on one shared row instead — stage is still tracked on each node for interpretation purposes, it just stops driving vertical position — because that view intentionally drops the app's own lane styling for bpmn-js's own default rendering, and a single row is what that rendering reads as "no lanes" rather than three empty bands. */
function buildGraph(wf: DraftWorkflow, layout: Layout) {
  const centerY = (stage: string) => (layout === 'single' ? LANE_H / 2 : LANE_Y[stage] + LANE_H / 2)
  const place = (id: string, kind: Node['kind'], name: string, lane: string, x: number, w: number, h: number): Node =>
    ({ id, kind, name, lane, x, y: centerY(lane) - h / 2, w, h })

  const nodes: Node[] = []
  const flows: Flow[] = []
  const steps = wf.candidate.steps
  const firstLane = (steps[0]?.stage ?? 'TRANSFORMATION') as string
  let x = 80
  let prevId = 'start'
  let prevLabel = ARTIFACT_LABEL[wf.requirement.inputArtifactType ?? 'UNKNOWN']
  let sawGateway = false
  nodes.push(place('start', 'start', 'Start', firstLane, x, EVENT_D, EVENT_D))

  steps.forEach((step, n) => {
    x += X_GAP
    const lane = (step.stage ?? 'TRANSFORMATION') as string
    const id = `step-${step.sequence}`
    nodes.push(place(id, 'task', step.name, lane, x, TASK_W, TASK_H))
    flows.push({ id: `flow-${prevId}-${id}`, from: prevId, to: id, name: prevLabel })
    prevId = id
    prevLabel = ARTIFACT_LABEL[step.output]

    const isLastStep = n === steps.length - 1
    if (step.stage === 'VALIDATION' && !isLastStep) {
      x += X_GAP
      const gwId = `gw-${step.sequence}`
      nodes.push(place(gwId, 'gateway', 'Valid?', lane, x, GATEWAY_D, GATEWAY_D))
      flows.push({ id: `flow-${prevId}-${gwId}`, from: prevId, to: gwId, name: prevLabel })
      prevId = gwId
      prevLabel = ARTIFACT_LABEL[step.input]
      sawGateway = true

      const rejId = `end-rejected-${step.sequence}`
      const rejNode = place(rejId, 'end', 'End (rejected)', 'VALIDATION', x + X_GAP * 0.6, EVENT_D, EVENT_D)
      rejNode.y = (layout === 'single' ? LANE_H : LANE_Y.VALIDATION + LANE_H) - EVENT_D - 20
      nodes.push(rejNode)
      flows.push({ id: `flow-${gwId}-${rejId}`, from: gwId, to: rejId, name: ARTIFACT_LABEL.VALIDATION_REPORT })
    }
  })

  x += X_GAP
  const lastStage = (steps[steps.length - 1]?.stage ?? firstLane) as string
  const sentPath = sawGateway && lastStage === 'COMMUNICATION'
  nodes.push(place('end', 'end', sentPath ? 'End (sent)' : 'End', lastStage, x, EVENT_D, EVENT_D))
  flows.push({ id: `flow-${prevId}-end`, from: prevId, to: 'end', name: prevLabel })

  const width = x + X_GAP
  return { nodes, flows, width }
}

/** Rough px-per-character estimate for the label font — not an actual text measurement, just enough to size a label's own DI bounds so it doesn't visually run into a neighbouring node. */
const labelWidth = (text: string) => Math.min(180, Math.max(30, text.length * 6.2))
const LABEL_H = 14
const LABEL_GAP = 6

/**
 * Routes a sequence flow the way BPMN tools actually draw one — straight across
 * when both ends sit at the same height, otherwise out horizontally, one
 * vertical bend, then horizontally into the target — never the direct diagonal
 * a naive two-point line would give two nodes in different lanes.
 *
 * Also gives the flow's name an explicit DI label position instead of leaving
 * bpmn-js to pick one: its default is to centre the label on the connection,
 * which for an elbow can land it on the vertical bend — right beside, or on
 * top of, whichever node happens to sit at that height. Placing it on the
 * horizontal segment leaving the source, offset above the line, keeps it in
 * the gap between nodes instead.
 *
 * `branch` separates labels that would otherwise stack on each other when a
 * single node has more than one outgoing flow (a gateway's two branches both
 * leave from the same point) — each later branch is pushed one more label's
 * height further up.
 */
function route(ax: number, ay: number, bx: number, by: number, name: string | undefined, branch = 0): { waypoints: string; label: string } {
  const point = (x: number, y: number) => `<di:waypoint x="${x}" y="${y}"/>`
  const sameHeight = ay === by
  const midX = ax + (bx - ax) / 2
  const waypoints = sameHeight
    ? point(ax, ay) + point(bx, by)
    : point(ax, ay) + point(midX, ay) + point(midX, by) + point(bx, by)
  if (!name) return { waypoints, label: '' }
  const w = labelWidth(name)
  const segMidX = sameHeight ? midX : ax + (midX - ax) / 2
  const y = ay - (branch + 1) * (LABEL_H + LABEL_GAP)
  const label = `<bpmndi:BPMNLabel><dc:Bounds x="${segMidX - w / 2}" y="${y}" width="${w}" height="${LABEL_H}"/></bpmndi:BPMNLabel>`
  return { waypoints, label }
}

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/**
 * Turns already-laid-out nodes and flows into a standalone BPMN 2.0 XML
 * document (process + diagram interchange), so it can be rendered by bpmn-js
 * or opened in any other BPMN-compliant tool. This is the one place that
 * knows how to write that XML — both `toBpmnXml` (a DraftWorkflow's steps,
 * grouped into the app's three stage lanes or laid out as a single row) and
 * `toPreviewXml` (an uploaded file's tasks, which have no stage of their own
 * to group by) compute their own node positions and then call this, so the
 * elbow routing and label placement stay identical everywhere a diagram gets
 * drawn, instead of each caller reimplementing it.
 */
function emitXml(defsId: string, processName: string, nodes: Node[], flows: Flow[], width: number, includeLanes: boolean): string {
  const byId = new Map(nodes.map((n) => [n.id, n]))
  const laneRefs = (stage: string) =>
    nodes
      .filter((n) => n.lane === stage)
      .map((n) => `<bpmn:flowNodeRef>${n.id}</bpmn:flowNodeRef>`)
      .join('')

  const flowNode = (n: Node) => {
    const incoming = flows.filter((f) => f.to === n.id).map((f) => `<bpmn:incoming>${f.id}</bpmn:incoming>`).join('')
    const outgoing = flows.filter((f) => f.from === n.id).map((f) => `<bpmn:outgoing>${f.id}</bpmn:outgoing>`).join('')
    const name = ` name="${esc(n.name)}"`
    if (n.kind === 'start') return `<bpmn:startEvent id="${n.id}"${name}>${outgoing}</bpmn:startEvent>`
    if (n.kind === 'end') return `<bpmn:endEvent id="${n.id}"${name}>${incoming}</bpmn:endEvent>`
    if (n.kind === 'gateway') return `<bpmn:exclusiveGateway id="${n.id}"${name}>${incoming}${outgoing}</bpmn:exclusiveGateway>`
    return `<bpmn:task id="${n.id}"${name}>${incoming}${outgoing}</bpmn:task>`
  }

  const laneSet = includeLanes
    ? `
    <bpmn:laneSet id="lanes">
      ${LANE_ORDER.map((s) => `<bpmn:lane id="lane-${(s as string).toLowerCase()}" name="${LANE_LABEL[s as string]}">${laneRefs(s as string)}</bpmn:lane>`).join('\n      ')}
    </bpmn:laneSet>`
    : ''

  const process = `
    ${laneSet}
    ${nodes.map(flowNode).join('\n    ')}
    ${flows.map((f) => `<bpmn:sequenceFlow id="${f.id}" sourceRef="${f.from}" targetRef="${f.to}"${f.name ? ` name="${esc(f.name)}"` : ''}/>`).join('\n    ')}
  `

  const shapes = [
    ...(includeLanes
      ? LANE_ORDER.map((s) => `<bpmndi:BPMNShape id="lane-${(s as string).toLowerCase()}_di" bpmnElement="lane-${(s as string).toLowerCase()}" isHorizontal="true"><dc:Bounds x="0" y="${LANE_Y[s as string]}" width="${width}" height="${LANE_H}"/></bpmndi:BPMNShape>`)
      : []),
    ...nodes.map((n) => `<bpmndi:BPMNShape id="${n.id}_di" bpmnElement="${n.id}"><dc:Bounds x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}"/></bpmndi:BPMNShape>`),
  ].join('\n      ')

  const outgoingSeen = new Map<string, number>()
  const edges = flows
    .map((f) => {
      const a = byId.get(f.from)!
      const b = byId.get(f.to)!
      const ax = a.x + a.w, ay = a.y + a.h / 2
      const bx = b.x, by = b.y + b.h / 2
      const branch = outgoingSeen.get(f.from) ?? 0
      outgoingSeen.set(f.from, branch + 1)
      const { waypoints, label } = route(ax, ay, bx, by, f.name, branch)
      return `<bpmndi:BPMNEdge id="${f.id}_di" bpmnElement="${f.id}">${waypoints}${label}</bpmndi:BPMNEdge>`
    })
    .join('\n      ')

  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="${defsId}" targetNamespace="http://bpbuilder.local/bpmn">
  <bpmn:process id="process-${defsId}" name="${esc(processName)}" isExecutable="false">
    ${process}
  </bpmn:process>
  <bpmndi:BPMNDiagram id="diagram-${defsId}">
    <bpmndi:BPMNPlane id="plane-${defsId}" bpmnElement="process-${defsId}">
      ${shapes}
      ${edges}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

/** Builds a BPMN diagram for the workflow (process + diagram interchange, lanes optional), so it can be rendered by bpmn-js or opened in any other BPMN-compliant tool. The three stage lanes are how this app visualises a workflow, not part of its process semantics, so `includeLanes` defaults to true for the in-app canvas (which can switch to the `single` layout, bpmn-js's own plain rendering with no lanes at all) but is turned off for the downloadable file: the exported .bpmn describes tasks and flow only, with no app-specific grouping baked in. */
export function toBpmnXml(wf: DraftWorkflow, opts: { includeLanes?: boolean; layout?: Layout } = {}): string {
  const { nodes, flows, width } = buildGraph(wf, opts.layout ?? 'lanes')
  return emitXml(`defs-${wf.id}`, wf.name, nodes, flows, width, opts.includeLanes ?? true)
}

/**
 * Builds a BPMN diagram for an arbitrary ordered task list — e.g. one read
 * from an uploaded file before it has gone through interpretation, so there
 * is no stage or artifact-type information to lay out with yet, only names
 * and order. Lays every task out on a single row with the same elbow routing
 * `toBpmnXml` uses; flows are left unnamed since the artifact moving between
 * tasks isn't known at this point, so there is nothing for a label to say.
 */
export interface PreviewNode { id: string; kind: 'start' | 'end' | 'task' | 'gateway'; name: string }
export interface PreviewEdge { from: string; to: string }

const SIZE_OF: Record<PreviewNode['kind'], number[]> = {
  start: [EVENT_D, EVENT_D],
  end: [EVENT_D, EVENT_D],
  gateway: [GATEWAY_D, GATEWAY_D],
  task: [TASK_W, TASK_H],
}
const ROW_GAP = TASK_H + 40

/**
 * Lays out an uploaded file's real task/gateway/event graph and connections
 * — not the file's own layout, and not flattened to a single chain either —
 * as columns by BFS depth from the start event, with the same elbow routing
 * `toBpmnXml` uses. A node several steps away sits several columns over;
 * nodes at the same depth (e.g. both branches of a gateway) stack in the
 * same column. Flows are left unnamed since the artifact moving between
 * nodes isn't known at this point. However many gateways or branches the
 * graph has, the layering doesn't care — this app's own workflows never
 * produce more than one, but an uploaded file reasonably might.
 */
export function toPreviewXml(graph: { nodes: PreviewNode[]; edges: PreviewEdge[] }): string {
  const startId = graph.nodes.find((n) => n.kind === 'start')?.id
  if (!startId) return emitXml('defs-preview', 'Uploaded diagram', [], [], 400, false)

  const outgoing = new Map<string, string[]>()
  graph.edges.forEach((e) => outgoing.set(e.from, [...(outgoing.get(e.from) ?? []), e.to]))

  // BFS from the start event assigns each node a column (its shortest
  // distance from start); discovery order decides each column's row order.
  const depth = new Map<string, number>([[startId, 0]])
  const order: string[] = [startId]
  const queue = [startId]
  while (queue.length) {
    const current = queue.shift()!
    for (const next of outgoing.get(current) ?? []) {
      if (depth.has(next)) continue
      depth.set(next, depth.get(current)! + 1)
      order.push(next)
      queue.push(next)
    }
  }

  const byId = new Map(graph.nodes.map((n) => [n.id, n]))
  const columns = new Map<number, string[]>()
  order.forEach((id) => {
    const d = depth.get(id)!
    columns.set(d, [...(columns.get(d) ?? []), id])
  })
  const maxDepth = Math.max(...depth.values())
  const maxRows = Math.max(...[...columns.values()].map((ids) => ids.length))

  const nodes: Node[] = []
  for (let d = 0; d <= maxDepth; d++) {
    const ids = columns.get(d) ?? []
    const top = ((maxRows - ids.length) * ROW_GAP) / 2
    ids.forEach((id, i) => {
      const n = byId.get(id)!
      const [w, h] = SIZE_OF[n.kind]
      nodes.push({ id, kind: n.kind, name: n.name, x: 80 + d * X_GAP, y: top + i * ROW_GAP + (ROW_GAP - h) / 2, w, h })
    })
  }

  const reachable = new Set(order)
  const flows: Flow[] = graph.edges
    .filter((e) => reachable.has(e.from) && reachable.has(e.to))
    .map((e, i) => ({ id: `flow-${i}-${e.from}-${e.to}`, from: e.from, to: e.to }))

  const width = 80 + (maxDepth + 1) * X_GAP
  return emitXml('defs-preview', 'Uploaded diagram', nodes, flows, width, false)
}

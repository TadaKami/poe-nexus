import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useNexusStore } from '@/stores/nexusStore'
import { useAtlasStore } from '@/stores/atlasStore'
import type { TreeNodeDto } from '@/types/pob'

const btnPrimary =
  'bg-poe-gold/90 px-4 py-2 text-sm font-semibold text-black hover:bg-poe-gold disabled:opacity-40'
const inputCls =
  'w-full bg-black/40 border border-poe-gold/20 px-3 py-2 text-sm text-amber-100 focus:border-poe-gold outline-none font-mono'

const MEMBER_COLORS = ['#60a5fa', '#f472b6', '#4ade80', '#f97316', '#a78bfa', '#22d3ee']

export default function AtlasPage() {
  const { nexuses, fetchMyNexuses } = useNexusStore()
  const {
    tree, mine, members, overlay, alloc,
    fetchTree, fetchMine, saveAtlas, fetchNexusAtlases, toggleOverlay, fetchAlloc, toggleNode
  } = useAtlasStore()

  const [nexusId, setNexusId] = useState('')
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    fetchMyNexuses().catch(() => {})
    fetchTree().catch(() => setError('Не удалось загрузить атлас-дерево'))
    fetchMine().catch(() => {})
  }, [fetchMyNexuses, fetchTree, fetchMine])

  useEffect(() => {
    if (!nexusId && nexuses.length > 0) setNexusId(nexuses[0].id)
  }, [nexuses, nexusId])

  useEffect(() => {
    if (!nexusId) return
    fetchAlloc(nexusId).catch(() => {})
    fetchNexusAtlases(nexusId).catch(() => {})
  }, [nexusId, fetchAlloc, fetchNexusAtlases])

  const allocSet = useMemo(() => new Set(alloc), [alloc])
  const mineSet = useMemo(() => new Set(mine), [mine])
  const memberSets = useMemo(
    () =>
      members.map((m, i) => ({
        ...m,
        color: MEMBER_COLORS[i % MEMBER_COLORS.length],
        set: new Set(m.nodeIds)
      })),
    [members]
  )

  async function onSave() {
    setError('')
    setInfo('')
    setSaving(true)
    try {
      const count = await saveAtlas(url.trim())
      setInfo(`Сохранено: ${count} нод атласа`)
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Не удалось сохранить атлас')
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="min-h-screen bg-poe-bg p-8 text-amber-100">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <Link to="/nexus" className="text-sm text-poe-gold/70 hover:text-poe-gold">← К Нексусам</Link>
        <h1 className="font-serif text-3xl text-poe-gold">Атлас миров</h1>
        <select
          value={nexusId}
          onChange={(e) => setNexusId(e.target.value)}
          className="border border-poe-gold/30 bg-black/40 px-2 py-1 text-sm"
        >
          {nexuses.length === 0 && <option value="">— нет Нексусов —</option>}
          {nexuses.map((n) => (
            <option key={n.id} value={n.id}>{n.name}</option>
          ))}
        </select>
      </div>

      <section className="mb-6 max-w-3xl space-y-2 border border-poe-gold/30 bg-poe-panel p-4">
        <p className="text-xs text-amber-100/60">
          Общий план атласа Нексуса: клик по ноде — взять/снять, изменения видят все участники
          в реальном времени. Ниже — ссылка на твой личный атлас для оверлея.
        </p>
        <div className="flex gap-2">
          <input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://www.pathofexile.com/atlas-tree/..."
            className={inputCls}
          />
          <button onClick={onSave} disabled={saving} className={btnPrimary}>
            {saving ? 'Сохраняю…' : 'Сохранить мой атлас'}
          </button>
        </div>
        {error && <p className="text-sm text-red-400">{error}</p>}
        {info && <p className="text-sm text-green-400">{info}</p>}
        {memberSets.length > 0 && (
          <div className="flex flex-wrap gap-3 pt-2">
            {memberSets.map((m) => (
              <label key={m.email} className="flex items-center gap-1 text-xs">
                <input
                  type="checkbox"
                  checked={!!overlay[m.email]}
                  onChange={() => toggleOverlay(m.email)}
                />
                <span style={{ color: m.color }}>
                  {m.email} ({m.nodeIds.length})
                </span>
              </label>
            ))}
          </div>
        )}
        <p className="pt-1 text-xs text-amber-100/60">
          Золотом — ноды общего плана ({alloc.length}) · синий пунктир — есть в твоём реальном
          атласе, но не в плане · цветные кольца — оверлей атласов участников.
        </p>
      </section>

      <AtlasSvg
        allocSet={allocSet}
        mineSet={mineSet}
        memberSets={memberSets}
        overlay={overlay}
        onToggle={(nodeId) => nexusId && toggleNode(nexusId, nodeId).catch(() => {})}
      />
    </main>
  )
}

/* ================= SVG-дерево атласа ================= */

function AtlasSvg(props: {
  allocSet: Set<number>
  mineSet: Set<number>
  memberSets: Array<{ email: string; color: string; set: Set<number> }>
  overlay: Record<string, boolean>
  onToggle: (nodeId: number) => void
}) {
  const { tree } = useAtlasStore()
  const [view, setView] = useState({ scale: 1, tx: 0, ty: 0 })
  const drag = useRef<{ x: number; y: number } | null>(null)
  const movedRef = useRef(false)

  const { bounds, nodeById } = useMemo(() => {
    if (!tree || tree.nodes.length === 0) {
      return {
        bounds: null as { minX: number; minY: number; w: number; h: number } | null,
        nodeById: new Map<number, TreeNodeDto>()
      }
    }
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
    for (const n of tree.nodes) {
      if (n.x < minX) minX = n.x
      if (n.x > maxX) maxX = n.x
      if (n.y < minY) minY = n.y
      if (n.y > maxY) maxY = n.y
    }
    const pad = 500
    return {
      bounds: { minX: minX - pad, minY: minY - pad, w: maxX - minX + pad * 2, h: maxY - minY + pad * 2 },
      nodeById: new Map(tree.nodes.map((n) => [n.id, n]))
    }
  }, [tree])

  if (!tree) return <p className="text-sm text-amber-100/60">Атлас загружается…</p>
  if (!bounds || tree.nodes.length === 0) {
    return (
      <p className="text-sm text-red-400">
        Бэкенд не смог получить атлас-дерево (глянь консоль бэка: строки [AtlasTree]).
      </p>
    )
  }

  return (
    <div
      className="relative h-[720px] cursor-grab overflow-hidden border border-poe-gold/30 bg-black/70 active:cursor-grabbing"
      onWheel={(e) =>
        setView((v) => ({ ...v, scale: Math.min(8, Math.max(0.4, v.scale * (e.deltaY > 0 ? 0.9 : 1.11))) }))
      }
      onMouseDown={(e) => {
        movedRef.current = false
        drag.current = { x: e.clientX - view.tx, y: e.clientY - view.ty }
      }}
      onMouseMove={(e) => {
        if (drag.current) {
          const d = drag.current
          if (Math.abs(e.clientX - d.x) + Math.abs(e.clientY - d.y) > 3) movedRef.current = true
          setView((v) => ({ ...v, tx: e.clientX - d.x, ty: e.clientY - d.y }))
        }
      }}
      onMouseUp={() => (drag.current = null)}
      onMouseLeave={() => (drag.current = null)}
    >
      <div style={{ transform: `translate(${view.tx}px, ${view.ty}px) scale(${view.scale})`, transformOrigin: 'center' }}>
        <svg width={900} height={900} viewBox={`${bounds.minX} ${bounds.minY} ${bounds.w} ${bounds.h}`}>
          {tree.edges.map(([a, b], i) => {
            const na = nodeById.get(a)
            const nb = nodeById.get(b)
            if (!na || !nb) return null
            return <line key={i} x1={na.x} y1={na.y} x2={nb.x} y2={nb.y} stroke="#292524" strokeWidth={25} />
          })}

          {/* оверлей: цветные кольца на нодах реальных атласов участников */}
          {props.memberSets
            .filter((m) => props.overlay[m.email])
            .map((m) =>
              [...m.set]
                .filter((id) => !props.allocSet.has(id))
                .map((id) => {
                  const n = nodeById.get(id)
                  if (!n) return null
                  const r = (n.kind === 'keystone' ? 200 : n.kind === 'notable' ? 130 : 60) + 40
                  return (
                    <circle
                      key={`${m.email}-${id}`}
                      cx={n.x}
                      cy={n.y}
                      r={r}
                      fill="none"
                      stroke={m.color}
                      strokeWidth={25}
                      opacity={0.7}
                    />
                  )
                })
            )}

          {/* ноды: клик = взять/снять в общем плане */}
          {tree.nodes.map((n) => {
            const taken = props.allocSet.has(n.id)
            const inMine = props.mineSet.has(n.id)
            const r = n.kind === 'keystone' ? 200 : n.kind === 'notable' ? 130 : 60
            return (
              <circle
                key={n.id}
                cx={n.x}
                cy={n.y}
                r={r}
                className="cursor-pointer"
                fill={taken ? '#c8a95b' : n.kind === 'normal' ? '#44403c' : '#57534e'}
                stroke={taken ? '#fde68a' : inMine ? '#60a5fa' : '#1c1917'}
                strokeWidth={taken ? 30 : inMine ? 25 : 12}
                strokeDasharray={inMine && !taken ? '40 20' : undefined}
                onClick={() => {
                  if (movedRef.current) return
                  props.onToggle(n.id)
                }}
              >
                <title>
                  {`${n.name ?? `#${n.id}`}${taken ? ' · взято в плане' : ''}${inMine ? ' · есть в твоём атласе' : ''}`}
                </title>
              </circle>
            )
          })}
        </svg>
      </div>
    </div>
  )
}
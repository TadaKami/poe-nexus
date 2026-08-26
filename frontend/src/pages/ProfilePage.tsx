import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { usePobStore } from '@/stores/pobStore'
import type { GemDto, GearScoreDto, PobBuildDto, TreeNodeDto } from '@/types/pob'

const inputCls =
  'w-full bg-black/40 border border-poe-gold/20 px-3 py-2 text-sm text-amber-100 focus:border-poe-gold outline-none font-mono'
const btnPrimary =
  'bg-poe-gold/90 px-4 py-2 text-sm font-semibold text-black hover:bg-poe-gold disabled:opacity-40'

const rarityCls: Record<string, string> = {
  UNIQUE: 'text-orange-400',
  RARE: 'text-yellow-300',
  MAGIC: 'text-sky-400',
  NORMAL: 'text-gray-200'
}

// Иконки грузим через наш бэкенд-прокси (адблок их иначе режет)
const proxyIcon = (url: string) => `/api/pob/icon?url=${encodeURIComponent(url)}`


function gemIcon(g: GemDto): string {
  const base = g.name.replace(/ /g, '_')
  const suffix = (g.skillId ?? '').startsWith('Support') ? '_support_icon' : '_skill_icon'
  return `https://www.poewiki.net/wiki/Special:FilePath/${encodeURIComponent(base + suffix + '.png')}`
}

function scoreCls(s: number): string {
  return s >= 95 ? 'text-green-400' : s >= 90 ? 'text-yellow-300' : 'text-red-400'
}

export default function ProfilePage() {
  const user = useAuthStore((s) => s.user)
  const { builds, diff, loading, savingScope, fetchBuilds, save, compare } = usePobStore()

  const [currentSource, setCurrentSource] = useState('')
  const [targetSource, setTargetSource] = useState('')
  const [error, setError] = useState('')
  const [lastSaved, setLastSaved] = useState<'current' | 'target' | null>(null)

  useEffect(() => {
    fetchBuilds().catch(() => {})
  }, [fetchBuilds])

  const current = builds.find((b) => b.scope === 'current')
  const target = builds.find((b) => b.scope === 'target')

  const curGemSet = new Set(current?.parsed.gems.map((g) => g.name.toLowerCase()) ?? [])
  const tgtGemSet = new Set(target?.parsed.gems.map((g) => g.name.toLowerCase()) ?? [])
  const missingSet = useMemo(() => new Set(diff?.missingPassiveIds ?? []), [diff])

  async function onSave(scope: 'current' | 'target') {
    setError('')
    setLastSaved(null)
    const source = scope === 'current' ? currentSource : targetSource
    if (!source.trim()) return setError('Вставь ссылку или сырой PoB-код')
    try {
      await save(source, scope)
      setLastSaved(scope)
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Не удалось сохранить PoB')
    }
  }

  async function onCompare() {
    setError('')
    try {
      await compare()
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Не удалось сравнить билды')
    }
  }

  return (
    <main className="min-h-screen bg-poe-bg p-8 text-amber-100">
      <div className="mb-6 flex items-center justify-between">
        <Link to="/nexus" className="text-sm text-poe-gold/70 hover:text-poe-gold">← К Нексусам</Link>
        <span className="text-sm">{user?.email}</span>
      </div>
      <h1 className="mb-8 font-serif text-3xl text-poe-gold">Профиль PoB</h1>

      <div className="flex gap-6">
        {/* ---------- левая колонка ---------- */}
        <div className="min-w-0 flex-1">
          <section className="grid grid-cols-1 gap-6 xl:grid-cols-2">
            <BuildCard title="Current (текущий билд)" source={currentSource} setSource={setCurrentSource}
              saved={current} saving={savingScope === 'current'} lastSaved={lastSaved === 'current'}
              onSave={() => onSave('current')} />
            <BuildCard title="Target (эталонный билд)" source={targetSource} setSource={setTargetSource}
              saved={target} saving={savingScope === 'target'} lastSaved={lastSaved === 'target'}
              onSave={() => onSave('target')} />
          </section>

          {error && <p className="mt-6 text-sm text-red-400">{error}</p>}

          {current && target && (
            <section className="mt-10 grid grid-cols-1 gap-6 xl:grid-cols-2">
              <GemGrid title="Камни: Current" gems={current.parsed.gems} other={tgtGemSet} />
              <GemGrid title="Камни: Target" gems={target.parsed.gems} other={curGemSet} />
            </section>
          )}

          {diff && diff.gearScores.length > 0 && (
            <section className="mt-10">
              <h2 className="mb-2 font-serif text-xl text-poe-gold">Оценка шмота против таргета</h2>
              <p className="mb-3 text-xs text-amber-100/60">
                Имена рарок не важны — сравниваем моды и имплики по слотам. ≥95% — почти идеал ·
                90–94% — терпимо · &lt;90% — менять. Наведи — чего не хватает.
              </p>
              <GearScores scores={diff.gearScores} />
            </section>
          )}

          <section className="mt-10">
            <button onClick={onCompare} disabled={!current || !target} className={btnPrimary}>
              Сравнить билды
            </button>
            {(!current || !target) && (
              <p className="mt-2 text-xs text-amber-100/60">Для сравнения сохрани оба билда</p>
            )}
          </section>

          {diff && (
            <>
              {diff.missingPassives.length > 0 && (
                <section className="mt-8">
                  <h2 className="mb-3 font-serif text-xl text-poe-gold">Недобрано пассивок</h2>
                  {diff.levelGap > 0 && (
                    <p className="mb-3 border border-orange-400/40 bg-orange-400/10 px-3 py-2 text-sm text-orange-300">
                      Текущий билд на {diff.levelGap} ур. ниже таргета — повысь уровень и возьми эти ноды.
                    </p>
                  )}
                  <div className="grid grid-cols-1 gap-3 xl:grid-cols-2">
                    {diff.missingPassives.map((p) => (
                      <div key={p.id} className="flex gap-3 border border-poe-gold/20 bg-poe-panel p-3">
                        {p.icon && (
                          <img src={proxyIcon(p.icon)} alt="" className="h-10 w-10"
                            onError={(e) => { e.currentTarget.style.display = 'none' }} />
                        )}
                        <div>
                          <p className="text-sm text-amber-100">
                            {p.name}{' '}
                            {p.keystone && <span className="rounded bg-orange-400/20 px-1 text-xs text-orange-300">Keystone</span>}
                            {!p.keystone && p.notable && <span className="rounded bg-poe-gold/20 px-1 text-xs text-poe-gold">Notable</span>}
                          </p>
                          <ul className="mt-1 space-y-0.5">
                            {p.effects.map((s, i) => <li key={i} className="text-xs text-sky-300">{s}</li>)}
                          </ul>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              <section className="mt-8">
                <h2 className="mb-4 font-serif text-xl text-poe-gold">
                  Расхождения {diff.entries.length === 0 && '(билды совпадают)'}
                </h2>
                {diff.entries.length === 0 ? (
                  <p className="text-sm text-green-400">Поздравляю! Current совпадает с таргетом.</p>
                ) : (
                  <ul className="space-y-2">
                    {diff.entries.map((e, i) => (
                      <li key={i} className="flex gap-3 border border-poe-gold/20 bg-poe-panel px-4 py-2 text-sm">
                        <span className="shrink-0 rounded bg-poe-gold/20 px-2 py-0.5 font-mono text-xs text-poe-gold">{e.category}</span>
                        <span className="text-amber-100/90">{e.message}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>

        {/* ---------- правая колонка: дерево ---------- */}
        {target && (
          <aside className="w-[46%] shrink-0">
            <TreePanel version={target.parsed.treeVersion ?? '3_29'} missing={missingSet} />
          </aside>
        )}
      </div>

      {loading && <p className="mt-6 text-sm">Загрузка…</p>}
    </main>
  )
}

/* ================= компоненты ================= */

function GearScores({ scores }: { scores: GearScoreDto[] }) {
  return (
    <ul className="space-y-1">
      {scores.map((s) => (
        <li key={s.slot}
          className="group relative flex justify-between gap-2 border border-poe-gold/10 bg-poe-panel px-2 py-1 text-xs">
          <span className="text-amber-100/60">{s.slot}</span>
          <span className="text-right text-amber-100">
            {s.currentName ?? 'пусто'} <b className={scoreCls(s.score)}>{s.score}%</b>
          </span>
          <div className="pointer-events-none absolute right-0 top-full z-30 hidden w-96 border border-poe-gold/40 bg-black/95 p-3 group-hover:block">
            {s.missingMods.length === 0 ? (
              <p className="text-xs text-green-400">Моды и имплики совпадают с таргетом</p>
            ) : (
              <>
                <p className="mb-1 text-xs text-amber-100/60">До таргета «{s.targetName}» не хватает:</p>
                <ul className="space-y-0.5">
                  {s.missingMods.map((m, i) => <li key={i} className="text-xs text-red-400">{m}</li>)}
                </ul>
              </>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}

function GemGrid({ title, gems, other }: { title: string; gems: GemDto[]; other: Set<string> }) {
  return (
    <div>
      <h3 className="mb-3 font-serif text-lg text-poe-gold">{title}</h3>
      <div className="flex flex-wrap gap-2">
        {gems.map((g, i) => {
          const differs = !other.has(g.name.toLowerCase())
          return (
            <div key={i} title={`${g.name} ${g.level}/${g.quality}${g.slot ? ` · ${g.slot}` : ''}`}
              className={`flex items-center gap-2 border bg-poe-panel px-2 py-1 ${differs ? 'border-red-500/70' : 'border-poe-gold/20'}`}>
              <img src={proxyIcon(gemIcon(g))} alt="" className="h-7 w-7"
                onError={(e) => { e.currentTarget.style.display = 'none' }} />
              <div className="text-xs">
                <p className="text-amber-100">{g.name}</p>
                <p className="text-amber-100/60">{g.level}/{g.quality}{g.slot ? ` · ${g.slot}` : ''}</p>
              </div>
            </div>
          )
        })}
      </div>
      <p className="mt-2 text-xs text-amber-100/50">Красная рамка — гема нет в другом билде</p>
    </div>
  )
}

// Дерево спрятано по умолчанию: ~5000 SVG-элементов не должны жить в DOM постоянно.
function TreePanel({ version, missing }: { version: string; missing: Set<number> }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="sticky top-4">
      <button onClick={() => setOpen((v) => !v)} className={btnPrimary}>
        {open ? 'Скрыть дерево пассивок' : 'Показать дерево пассивок'}
      </button>
      <p className="mt-2 text-xs text-amber-100/60">
        Красным — ноды таргета, не взятые в current. Колесо — зум, мышь — пан.
      </p>
      {open && <TreeSvg version={version} missing={missing} />}
    </div>
  )
}

function TreeSvg({ version, missing }: { version: string; missing: Set<number> }) {
  const { tree, fetchTree } = usePobStore()
  const [view, setView] = useState({ scale: 1, tx: 0, ty: 0 })
  const drag = useRef<{ x: number; y: number } | null>(null)

  useEffect(() => {
    fetchTree(version).catch(() => {})
  }, [version, fetchTree])

  const { bounds, nodeById } = useMemo(() => {
    if (!tree || tree.nodes.length === 0) {
      return { bounds: null as { minX: number; minY: number; w: number; h: number } | null, nodeById: new Map<number, TreeNodeDto>() }
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

  if (!tree) return <p className="text-sm text-amber-100/60">Дерево загружается… (первый раз качает tree.lua)</p>
  if (!bounds || tree.nodes.length === 0) {
    return (
      <p className="text-sm text-red-400">
        Бэкенд не смог получить tree.lua (глянь его консоль: строки [TreeData]). Дерево недоступно.
      </p>
    )
  }

  return (
    <div className="sticky top-4">
      <h3 className="mb-2 font-serif text-lg text-poe-gold">Дерево пассивок</h3>
      <p className="mb-2 text-xs text-amber-100/60">
        Красным — ноды таргета, не взятые в current. Колесо — зум, мышь — пан.
      </p>
      <div
        className="relative h-[720px] cursor-grab overflow-hidden border border-poe-gold/30 bg-black/70 active:cursor-grabbing"
        onWheel={(e) =>
          setView((v) => ({ ...v, scale: Math.min(6, Math.max(0.4, v.scale * (e.deltaY > 0 ? 0.9 : 1.11))) }))
        }
        onMouseDown={(e) => (drag.current = { x: e.clientX - view.tx, y: e.clientY - view.ty })}
        onMouseMove={(e) => {
          if (drag.current) {
            const dx = drag.current
            setView((v) => ({ ...v, tx: e.clientX - dx.x, ty: e.clientY - dx.y }))
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
            {tree.nodes.map((n) => {
              const miss = missing.has(n.id)
              const r = n.kind === 'keystone' ? 160 : n.kind === 'notable' ? 110 : n.kind === 'mastery' ? 90 : 55
              return (
                // Без <image>: poecdn недоступен из нашей сети, а сотни запросов по 1с = лаги.
                // Имя ноды — в нативный SVG-тултип.
                <circle key={n.id} cx={n.x} cy={n.y} r={r}
                  fill={miss ? '#7f1d1d' : n.kind === 'normal' ? '#44403c' : '#57534e'}
                  stroke={miss ? '#ef4444' : '#1c1917'} strokeWidth={miss ? 30 : 12}>
                  <title>{n.name ?? `#${n.id}`}</title>
                </circle>
              )
            })}
          </svg>
        </div>
      </div>
    </div>
  )
}

function BuildCard(props: {
  title: string
  source: string
  setSource: (v: string) => void
  saved?: PobBuildDto
  saving: boolean
  lastSaved: boolean
  onSave: () => void
}) {
  return (
    <div className="border border-poe-gold/30 bg-poe-panel p-6">
      <h2 className="mb-2 font-serif text-lg text-poe-gold">{props.title}</h2>
      <p className="mb-3 text-xs text-amber-100/60">Ссылка pobb.in / pastebin или сырой код</p>
      <textarea rows={4} value={props.source} onChange={(e) => props.setSource(e.target.value)}
        placeholder="https://pobb.in/XXXXX или сырой код..." className={inputCls} />
      <div className="mt-3 flex items-center gap-3">
        <button onClick={props.onSave} disabled={props.saving} className={btnPrimary}>
          {props.saving ? 'Сохраняю…' : 'Сохранить'}
        </button>
        {props.lastSaved && <span className="text-xs text-green-400">Сохранено!</span>}
      </div>
      {props.saved && (
        <div className="mt-4 space-y-1 border-t border-poe-gold/10 pt-3 text-xs">
          <p>Уровень: <b>{props.saved.parsed.level ?? '—'}</b> · {props.saved.parsed.className} / {props.saved.parsed.ascendancy}</p>
          <p>Камней: {props.saved.parsed.gems.length} · Пассивок: {props.saved.parsed.passiveNodeIds.length} · Предметов: {props.saved.parsed.items.length}</p>
          <p>
            DPS: {Math.round(props.saved.parsed.stats['CombinedDPS'] ?? 0).toLocaleString()} ·
            ES: {Math.round(props.saved.parsed.stats['EnergyShield'] ?? 0).toLocaleString()}
          </p>
        </div>
      )}
    </div>
  )
}
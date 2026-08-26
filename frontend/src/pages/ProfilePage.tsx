import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { usePobStore } from '@/stores/pobStore'
import type { GemDto, PobBuildDto } from '@/types/pob'

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

// Иконки гемов с PoE wiki (skill / support)
function gemIcon(g: GemDto): string {
  const base = g.name.replace(/ /g, '_')
  const suffix = (g.skillId ?? '').startsWith('Support') ? '_support_icon' : '_skill_icon'
  return `https://www.poewiki.net/wiki/Special:FilePath/${encodeURIComponent(base + suffix + '.png')}`
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

      <section className="grid max-w-6xl grid-cols-1 gap-6 md:grid-cols-2">
        <BuildCard title="Current (текущий билд)" source={currentSource} setSource={setCurrentSource}
          saved={current} saving={savingScope === 'current'} lastSaved={lastSaved === 'current'}
          onSave={() => onSave('current')} />
        <BuildCard title="Target (эталонный билд)" source={targetSource} setSource={setTargetSource}
          saved={target} saving={savingScope === 'target'} lastSaved={lastSaved === 'target'}
          onSave={() => onSave('target')} />
      </section>

      {error && <p className="mt-6 max-w-6xl text-sm text-red-400">{error}</p>}

      {/* ---------- Камни с иконками ---------- */}
      {current && target && (
        <section className="mt-10 grid max-w-6xl grid-cols-1 gap-6 md:grid-cols-2">
          <GemGrid title="Камни: Current" gems={current.parsed.gems} other={tgtGemSet} />
          <GemGrid title="Камни: Target" gems={target.parsed.gems} other={curGemSet} />
        </section>
      )}

      {/* ---------- Экипировка ---------- */}
      {current && target && (
        <section className="mt-10 grid max-w-6xl grid-cols-1 gap-6 md:grid-cols-2">
          <GearColumn title="Шмот: Current" build={current} other={target} />
          <GearColumn title="Шмот: Target" build={target} other={current} />
        </section>
      )}

      <section className="mt-10 max-w-6xl">
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
            <section className="mt-8 max-w-6xl">
              <h2 className="mb-3 font-serif text-xl text-poe-gold">Недобрано пассивок</h2>
              {diff.levelGap > 0 && (
                <p className="mb-3 border border-orange-400/40 bg-orange-400/10 px-3 py-2 text-sm text-orange-300">
                  Текущий билд на {diff.levelGap} ур. ниже таргета — повысь уровень и возьми эти ноды.
                </p>
              )}
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {diff.missingPassives.map((p) => (
                  <div key={p.id} className="flex gap-3 border border-poe-gold/20 bg-poe-panel p-3">
                    {p.icon && (
                      <img src={p.icon} alt="" className="h-10 w-10"
                        onError={(e) => { e.currentTarget.style.display = 'none' }} />
                    )}
                    <div>
                      <p className="text-sm text-amber-100">
                        {p.name}{' '}
                        {p.keystone && <span className="rounded bg-orange-400/20 px-1 text-xs text-orange-300">Keystone</span>}
                        {!p.keystone && p.notable && <span className="rounded bg-poe-gold/20 px-1 text-xs text-poe-gold">Notable</span>}
                      </p>
                      <ul className="mt-1 space-y-0.5">
                        {p.effects.map((s, i) => (
                          <li key={i} className="text-xs text-sky-300">{s}</li>
                        ))}
                      </ul>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
          <section className="mt-8 max-w-6xl">
            <h2 className="mb-4 font-serif text-xl text-poe-gold">
              Расхождения {diff.entries.length === 0 && '(билды совпадают)'}
            </h2>
            {diff.entries.length === 0 ? (
              <p className="text-sm text-green-400">Поздравляю! Current совпадает с таргетом.</p>
            ) : (
              <ul className="space-y-2">
                {diff.entries.map((e, i) => (
                  <li key={i} className="flex gap-3 border border-poe-gold/20 bg-poe-panel px-4 py-2 text-sm">
                    <span className="shrink-0 rounded bg-poe-gold/20 px-2 py-0.5 font-mono text-xs text-poe-gold">
                      {e.category}
                    </span>
                    <span className="text-amber-100/90">{e.message}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>        
        </>
      )}

      {loading && <p className="mt-6 text-sm">Загрузка…</p>}
    </main>
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
              className={`flex items-center gap-2 border bg-poe-panel px-2 py-1 ${
                differs ? 'border-red-500/70' : 'border-poe-gold/20'
              }`}>
              <img src={gemIcon(g)} alt="" className="h-7 w-7"
                onError={(e) => { e.currentTarget.style.display = 'none' }} />
              <div className="text-xs">
                <p className="text-amber-100">{g.name}</p>
                <p className="text-amber-100/60">
                  {g.level}/{g.quality}
                  {g.slot ? ` · ${g.slot}` : ''}
                </p>
              </div>
            </div>
          )
        })}
      </div>
      <p className="mt-2 text-xs text-amber-100/50">Красная рамка — гема нет в другом билде</p>
    </div>
  )
}

function GearColumn({ title, build, other }: { title: string; build: PobBuildDto; other?: PobBuildDto }) {
  return (
    <div>
      <h3 className="mb-3 font-serif text-lg text-poe-gold">{title}</h3>
      <ul className="space-y-1">
        {Object.entries(build.parsed.gear).map(([slot, name]) => {
          const item = build.parsed.items.find((i) => i.name === name)
          const otherName = other?.parsed.gear[slot]
          const otherItem = other?.parsed.items.find((i) => i.name === otherName)
          const differs = otherName !== undefined && otherName !== name
          return (
            <li key={slot}
              className={`group relative flex justify-between gap-2 border bg-poe-panel px-2 py-1 text-xs ${
                differs ? 'border-red-500/60' : 'border-poe-gold/10'
              }`}>
              <span className="text-amber-100/60">{slot}</span>
              <span className={`text-right ${rarityCls[item?.rarity ?? 'NORMAL']}`}>{name}</span>
              {item && (
                <div className="pointer-events-none absolute right-0 top-full z-30 hidden w-96 border border-poe-gold/40 bg-black/95 p-3 group-hover:block">
                  <p className={`mb-1 text-sm font-semibold ${rarityCls[item.rarity]}`}>{item.name}</p>
                  <p className="mb-2 text-xs text-amber-100/60">{item.base}</p>
                  <ul className="space-y-0.5">
                    {item.mods.map((m, i) => {
                      const inOther = !otherItem || otherItem.mods.includes(m)
                      return (
                        <li key={i} className={`text-xs ${inOther ? 'text-amber-100/90' : 'text-red-400'}`}>
                          {m}{!inOther && ' — в другом билде нет'}
                        </li>
                      )
                    })}
                  </ul>
                  {otherItem && otherItem.name !== item.name && (
                    <>
                      <p className="mt-2 mb-1 text-xs text-amber-100/60">
                        В другом билде ({otherItem.name}), но нет тут:
                      </p>
                      <ul className="space-y-0.5">
                        {otherItem.mods.filter((m) => !item.mods.includes(m)).map((m, i) => (
                          <li key={i} className="text-xs text-orange-300">{m}</li>
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ul>
      <p className="mt-2 text-xs text-amber-100/50">Наведи на предмет — моды; красным — чего нет в другом билде</p>
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
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useNexusStore } from '@/stores/nexusStore'

export default function NexusPage() {
  const { id = '' } = useParams()
  const user = useAuthStore((s) => s.user)
  const { current, invite, synergy, loading, fetchNexus, fetchSynergy, generateInvite, changeRole, kick } =
    useNexusStore()

  useEffect(() => {
    fetchNexus(id).catch(() => {})
    fetchSynergy(id).catch(() => {})
  }, [id, fetchNexus, fetchSynergy])

  const myRole = current?.members.find((m) => m.userId === user?.id)?.role ?? 'member'
  const canManage = myRole === 'leader' || myRole === 'officer'
  const isLeader = myRole === 'leader'

  const [copied, setCopied] = useState(false)

  async function copyCode() {
    if (!invite) return
    try {
      await navigator.clipboard.writeText(invite.code)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // браузер запретил clipboard — молча игнорируем
    }
  }

  return (
    <main className="min-h-screen bg-poe-bg p-8 text-amber-100">
      <Link to="/nexus" className="text-sm text-poe-gold/70 hover:text-poe-gold">← К списку</Link>

      {loading && !current && <p className="mt-4">Загрузка…</p>}

      {current && (
        <>
          <header className="mt-4 mb-8">
            <h1 className="font-serif text-3xl text-poe-gold">{current.nexus.name}</h1>
            {current.nexus.description && (
              <p className="mt-1 text-sm text-amber-100/70">{current.nexus.description}</p>
            )}
          </header>

          {canManage && (
            <section className="mb-8 max-w-xl border border-poe-gold/30 bg-poe-panel p-4">
              <button
                onClick={() => generateInvite(id)}
                className="border border-poe-gold/40 px-3 py-1 text-sm hover:bg-poe-gold/10"
              >
                Сгенерировать приглашение
              </button>
              {invite && (
                <p className="mt-2 text-sm">
                  Код:{' '}
                  <span
                    className="font-mono text-poe-gold cursor-pointer hover:underline"
                    title="Кликни, чтобы скопировать"
                    onClick={copyCode}
                  >
                    {invite.code}
                  </span>{' '}
                  {copied && <span className="text-xs text-green-400">Скопировано!</span>}{' '}
                  (действует до {invite.expiresAt})
                </p>
              )}
            </section>
          )}

          {/* ---------- Модуль 2: синергия пати ---------- */}
          {synergy && (
            <section className="mb-8 max-w-3xl">
              <h2 className="mb-3 font-serif text-xl text-poe-gold">Синергия пати</h2>
              {synergy.duplicates.length > 0 && (
                <p className="mb-3 border border-yellow-400/40 bg-yellow-400/10 px-3 py-2 text-sm text-yellow-300">
                  Дубли в пати (зряшный манарезерв): {synergy.duplicates.join(', ')}
                </p>
              )}
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {synergy.members.map((m) => (
                  <div key={m.userId} className="border border-poe-gold/20 bg-poe-panel p-3">
                    <p className="text-sm text-amber-100">
                      {m.email}{' '}
                      {m.aura?.auraBot && (
                        <span className="rounded bg-poe-blood/40 px-1.5 py-0.5 text-xs text-red-300">Аура-бот</span>
                      )}
                    </p>
                    {!m.hasBuild && (
                      <p className="mt-1 text-xs text-amber-100/50">
                        Нет сохранённого PoB — пусть закинет билд в «Профиль PoB»
                      </p>
                    )}
                    {m.aura && (
                      <div className="mt-2 space-y-1 text-xs">
                        <p className="text-amber-100/80">
                          Резисты:{' '}
                          <span className="text-orange-300">🔥{m.aura.fireResist}%</span>{' '}
                          <span className="text-sky-300">❄{m.aura.coldResist}%</span>{' '}
                          <span className="text-yellow-300">⚡{m.aura.lightResist}%</span>{' '}
                          <span className="text-purple-300">☠{m.aura.chaosResist}%</span>{' '}
                          · макс <b className="text-amber-100">{m.aura.maxResist}%</b>
                        </p>
                        <p className="text-amber-100/80">
                          <span className="text-red-300">HP {m.life}</span> ·{' '}
                          <span className="text-sky-300">ES {m.energyShield}</span> ·{' '}
                          <span className="text-blue-300">Mana {m.mana}</span>
                        </p>                        
                        <p className="text-amber-100/80">
                          Aura Effect <b className="text-poe-gold">+{m.aura.auraEffect}%</b> ·
                          Area Effect +{m.aura.areaEffect}% ·
                          ResEff +{m.aura.reservationEff}%
                        </p>
                      </div>
                    )}                    
                    <div className="mt-2 flex flex-wrap gap-1">
                      {m.auras.map((a) => (
                        <span key={a.name} className="rounded bg-poe-gold/20 px-1.5 py-0.5 text-xs text-poe-gold">
                          {a.name} <b>{a.level}</b>
                        </span>
                      ))}
                      {m.curses.map((c) => (
                        <span key={c.name} className="rounded bg-purple-400/20 px-1.5 py-0.5 text-xs text-purple-300">
                          {c.name} <b>{c.level}</b>
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-amber-100/60">
                Аур в пати: {Object.keys(synergy.auraCounts).length} · Проклятий: {Object.keys(synergy.curseCounts).length}
              </p>
            </section>
          )}

          <section className="max-w-xl space-y-2">
            <h2 className="mb-2 font-serif text-xl text-poe-gold">Участники</h2>
            {current.members.map((m) => (
              <div
                key={m.userId}
                className="flex items-center justify-between border border-poe-gold/20 bg-poe-panel px-4 py-2"
              >
                <div>
                  <p className="text-sm">{m.email}</p>
                  <p className="text-xs text-amber-100/60">{m.role}</p>
                </div>
                {isLeader && m.role !== 'leader' && (
                  <div className="flex items-center gap-2">
                    <select
                      value={m.role}
                      onChange={(e) => changeRole(id, m.userId, e.target.value)}
                      className="border border-poe-gold/20 bg-black/40 px-2 py-1 text-xs"
                    >
                      <option value="member">member</option>
                      <option value="officer">officer</option>
                    </select>
                    <button
                      onClick={() => kick(id, m.userId)}
                      className="text-xs text-red-400 hover:text-red-300"
                    >
                      Кик
                    </button>
                  </div>
                )}
              </div>
            ))}
          </section>
        </>
      )}
    </main>
  )
}
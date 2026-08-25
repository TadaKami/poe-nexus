import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useNexusStore } from '@/stores/nexusStore'

export default function NexusPage() {
  const { id = '' } = useParams()
  const user = useAuthStore((s) => s.user)
  const { current, invite, loading, fetchNexus, generateInvite, changeRole, kick } = useNexusStore()

  useEffect(() => {
    fetchNexus(id).catch(() => {})
  }, [id, fetchNexus])

  const myRole = current?.members.find((m) => m.userId === user?.id)?.role ?? 'member'
  const canManage = myRole === 'leader' || myRole === 'officer'
  const isLeader = myRole === 'leader'

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
                  Код: <span className="font-mono text-poe-gold">{invite.code}</span>{' '}
                  (действует до {invite.expiresAt})
                </p>
              )}
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
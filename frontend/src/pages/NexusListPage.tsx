
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { useNexusStore } from '@/stores/nexusStore'

const inputCls =
  'w-full bg-black/40 border border-poe-gold/20 px-3 py-2 text-sm text-amber-100 focus:border-poe-gold outline-none'

export default function NexusListPage() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  const { nexuses, loading, fetchMyNexuses, createNexus, joinByCode } = useNexusStore()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    fetchMyNexuses().catch(() => setError('Не удалось загрузить Нексусы'))
  }, [fetchMyNexuses])

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      const created = await createNexus(name, description)
      navigate(`/nexus/${created.id}`)
    } catch {
      setError('Не удалось создать Нексус')
    }
  }

  async function onJoin(e: FormEvent) {
    e.preventDefault()
    setError('')
    try {
      const joined = await joinByCode(code)
      navigate(`/nexus/${joined.id}`)
    } catch {
      setError('Код приглашения недействителен')
    }
  }

  async function onLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <main className="min-h-screen bg-poe-bg p-8 text-amber-100">
      <header className="mb-8 flex items-center justify-between">
        <h1 className="font-serif text-3xl text-poe-gold">Нексусы</h1>
        <div className="flex items-center gap-4 text-sm">
          <span>{user?.email}</span>
          <button onClick={onLogout} className="border border-poe-gold/40 px-3 py-1 hover:bg-poe-gold/10">
            Выйти
          </button>
        </div>
      </header>

      {error && <p className="mb-4 text-sm text-red-400">{error}</p>}

      <section className="grid max-w-3xl grid-cols-1 gap-4 md:grid-cols-2">
        {loading && <p>Загрузка…</p>}
        {!loading && nexuses.length === 0 && (
          <p className="text-sm text-amber-100/60">Ты пока не состоишь ни в одном Нексусе.</p>
        )}
        {nexuses.map((n) => (
          <Link
            key={n.id}
            to={`/nexus/${n.id}`}
            className="border border-poe-gold/30 bg-poe-panel p-4 hover:border-poe-gold"
          >
            <h2 className="font-serif text-xl text-poe-gold">{n.name}</h2>
            <p className="mt-1 text-sm text-amber-100/70">{n.description ?? 'Без описания'}</p>
          </Link>
        ))}
      </section>

      <section className="mt-10 grid max-w-3xl gap-6 md:grid-cols-2">
        <form onSubmit={onCreate} className="space-y-3 border border-poe-gold/30 bg-poe-panel p-6">
          <h2 className="font-serif text-lg text-poe-gold">Создать Нексус</h2>
          <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="Название" className={inputCls} />
          <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Описание (опц.)" className={inputCls} />
          <button className="w-full bg-poe-gold/90 py-2 font-semibold text-black hover:bg-poe-gold">Создать</button>
        </form>
        <form onSubmit={onJoin} className="space-y-3 border border-poe-gold/30 bg-poe-panel p-6">
          <h2 className="font-serif text-lg text-poe-gold">Вступить по коду</h2>
          <input value={code} onChange={(e) => setCode(e.target.value)} required placeholder="Код приглашения" className={inputCls} />
          <button className="w-full border border-poe-gold/40 py-2 hover:bg-poe-gold/10">Вступить</button>
        </form>
      </section>
    </main>
  )
}
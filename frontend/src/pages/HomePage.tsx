import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <main className="min-h-screen bg-poe-bg p-8 text-amber-100">
      <h1 className="font-serif text-3xl text-poe-gold">Nexus</h1>
      <p className="mt-2">Сессия активна: {user?.email}</p>
      <button
        onClick={onLogout}
        className="mt-4 border border-poe-gold/40 px-4 py-2 text-sm hover:bg-poe-gold/10"
      >
        Выйти
      </button>
    </main>
  )
}
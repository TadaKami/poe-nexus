import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

const inputCls =
  'w-full bg-black/40 border border-poe-gold/20 px-3 py-2 text-sm text-amber-100 focus:border-poe-gold outline-none';

export default function RegisterPage(){
    const register = useAuthStore((s) => s.register)
    const navigate = useNavigate()

    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [confirm, setConfirm] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    // Только UX-валидация; источник истины — сервер
    async function onSubmit(e: FormEvent) {
        e.preventDefault()
        setError('')
        if (password.length < 8) return setError('Пароль — минимум 8 символов')
        if (password !== confirm) return setError('Пароли не совпадают')
        setLoading(true)
        try {
            await register(email, password)
            navigate('/')
        } catch {
            setError('Не удалось зарегистрироваться. Возможно, email уже занят')
        } finally {
            setLoading(false)
        }
    }
    return (
        <main className="min-h-screen bg-poe-bg flex items-center justify-center p-4">
            <form onSubmit={onSubmit} className="w-full max-w-sm bg-poe-panel border border-poe-gold/30 p-6 space-y-4">
                <h1 className="text-2xl font-serif text-center text-poe-gold">Регистрация</h1>
                <input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                required
                placeholder="Email"
                className={inputCls}
                />
                <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                required
                placeholder="Пароль (мин. 8)"
                className={inputCls}
                />
                <input
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                type="password"
                required
                placeholder="Повтори пароль"
                className={inputCls}
                />
                {error && <p className="text-sm text-red-400">{error}</p>}
                <button
                    disabled={loading}
                    className="w-full bg-poe-gold/90 hover:bg-poe-gold text-black font-semibold py-2 disabled:opacity-50"
                >
                {loading ? 'Создание…' : 'Создать аккаунт'}
                </button>
                <Link to="/login" className="block text-center text-sm text-poe-gold/70 hover:text-poe-gold">
                    Уже есть аккаунт? Войти
                </Link>                  
            </form>
        </main>
    );
}
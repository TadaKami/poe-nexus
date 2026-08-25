import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/authStore";

const inputCls = 'w-full bg-black/40 border border-poe-gold/20 px-3 py-2 text-sm text-amber-100 focus:border-poe-gold outline-none';

export default function LoginPage(){
    const login = useAuthStore((s) => s.login);
    const navigate = useNavigate();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    async function onSubmit(e: FormEvent) {
        e.preventDefault();
        setError('');
        setLoading(true);
        try{
            await login(email, password);
            navigate('/');
        }catch {
            setError('Неверный email или пароль');
        }finally{
            setLoading(false);
        }
    }

    return (
        <main className="min-h-screen bg-poe-bg flex items-center justify-center">
            <form onSubmit={onSubmit} className="w-full max-w-sm bg-poe-panel border border-poe-gold/30 p-6 space-y-4">
                <h1 className="text-2xl font-serif text-center text-poe-gold">Вход в Nexus</h1>
                <input type="email"
                    value={email}
                    onChange={(e)=>setEmail(e.target.value)}
                    required
                    placeholder="Email"
                    className={inputCls}
                />
                <input type="password" 
                    onChange={(e)=>setPassword(e.target.value)}
                    value={password}
                    required
                    placeholder="Password"
                    className={inputCls}
                />
                {error && <p className="text-sm text-red-400">{error}</p>}
                +                <button
                    className="w-full bg-poe-gold/90 hover:bg-poe-gold text-black font-semibold py-2 disabled:opacity-50"
                    disabled={loading}
                >
                    {loading ? 'Вход…' : 'Войти'}
               </button>
                <Link to="/register" className="block text-center text-sm text-poe-gold/70 hover:text-poe-gold">Нет аккаунта? Регистрация</Link>
            </form>
        </main>
    );
}
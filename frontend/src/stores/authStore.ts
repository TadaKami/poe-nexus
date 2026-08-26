import { create } from "zustand";
import { http } from "@/services/ApiService";
import { session } from "@/services/session";
import type { AuthResponse, UserDto } from "@/types/auth";

interface AuthState {
    user: UserDto | null
    ready: boolean
    init: () => Promise<void>
    login: (email: string, password: string) => Promise<void>
    register: (email: string, password: string) => Promise<void>
    logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  ready: false,

  // F5 не выбрасывает на /login: молча восстанавливаемся по HttpOnly refresh-cookie
  async init() {
    if (get().ready) return
    try {
      const { data } = await http.post<AuthResponse>('/auth/refresh')
      session.token = data.accessToken
      set({ user: data.user })
    } catch {
      // нет валидной refresh-cookie — остаёмся гостем
    } finally {
      set({ ready: true })
    }
  },

  async login(email, password) {
    const { data } = await http.post<AuthResponse>('/auth/login', { email, password })
    session.token = data.accessToken
    set({ user: data.user })
  },

  async register(email, password) {
    const { data } = await http.post<AuthResponse>('/auth/register', { email, password })
    session.token = data.accessToken
    set({ user: data.user })
  },

  async logout() {
    try {
      await http.post('/auth/logout')
    } finally {
      session.token = null
      set({ user: null })
    }
  }
}))
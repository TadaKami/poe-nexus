import { create } from "zustand";
import { http } from "@/services/ApiService";
import { session } from "@/services/session";
import type { AuthResponse, UserDto } from "@/types/auth";

interface AuthState {
    user: UserDto | null
    login: (email: string, password: string) => Promise<void>
    register: (email: string, password: string) => Promise<void>
    logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
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
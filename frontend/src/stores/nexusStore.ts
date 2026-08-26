import { create } from 'zustand'
import { http } from '@/services/ApiService'
import type { NexusDto, NexusDetails, InviteDto } from '@/types/nexus'

interface NexusState {
  nexuses: NexusDto[]
  current: NexusDetails | null
  invite: InviteDto | null
  loading: boolean
  kickedNotice: string | null
  setKickedNotice: (msg: string | null) => void
  fetchMyNexuses: () => Promise<void>
  createNexus: (name: string, description: string) => Promise<NexusDto>
  joinByCode: (code: string) => Promise<NexusDto>
  fetchNexus: (id: string) => Promise<void>
  generateInvite: (id: string) => Promise<void>
  changeRole: (nexusId: string, userId: string, role: string) => Promise<void>
  kick: (nexusId: string, userId: string) => Promise<void>
}

export const useNexusStore = create<NexusState>((set, get) => ({
  nexuses: [],
  current: null,
  invite: null,
  loading: false,
  kickedNotice: null,

  setKickedNotice: (msg) => set({ kickedNotice: msg }),

  async fetchMyNexuses() {
    set({ loading: true })
    try {
      const { data } = await http.get<NexusDto[]>('/nexus')
      set({ nexuses: data })
    } finally {
      set({ loading: false })
    }
  },

  async createNexus(name, description) {
    const { data } = await http.post<NexusDto>('/nexus', { name, description })
    await get().fetchMyNexuses()
    return data
  },

  async joinByCode(code) {
    const { data } = await http.post<NexusDto>('/nexus/join', { code })
    await get().fetchMyNexuses()
    return data
  },

  async fetchNexus(id) {
    set({ loading: true })
    try {
      const { data } = await http.get<NexusDetails>(`/nexus/${id}`)
      set({ current: data })
    } finally {
      set({ loading: false })
    }
  },

  async generateInvite(id) {
    const { data } = await http.post<InviteDto>(`/nexus/${id}/invite`)
    set({ invite: data })
  },

  async changeRole(nexusId, userId, role) {
    await http.patch(`/nexus/${nexusId}/members/${userId}`, { role })
    await get().fetchNexus(nexusId)
  },

  async kick(nexusId, userId) {
    await http.delete(`/nexus/${nexusId}/members/${userId}`)
    await get().fetchNexus(nexusId)
  }
}))
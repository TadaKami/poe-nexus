import { create } from 'zustand'
import { http } from '@/services/ApiService'
import type { PobBuildDto, DiffReport } from '@/types/pob'

interface PobState {
  builds: PobBuildDto[]
  diff: DiffReport | null
  loading: boolean
  savingScope: 'current' | 'target' | null
  fetchBuilds: () => Promise<void>
  save: (source: string, scope: 'current' | 'target') => Promise<PobBuildDto>
  compare: () => Promise<void>
}

export const usePobStore = create<PobState>((set, get) => ({
  builds: [],
  diff: null,
  loading: false,
  savingScope: null,

  async fetchBuilds() {
    set({ loading: true })
    try {
      const { data } = await http.get<PobBuildDto[]>('/pob')
      set({ builds: data })
    } finally {
      set({ loading: false })
    }
  },

  async save(source, scope) {
    set({ savingScope: scope })
    try {
      const { data } = await http.post<PobBuildDto>('/pob', { source, scope })
      await get().fetchBuilds()
      return data
    } finally {
      set({ savingScope: null })
    }
  },

  async compare() {
    const { data } = await http.post<DiffReport>('/pob/diff')
    set({ diff: data })
  }
}))
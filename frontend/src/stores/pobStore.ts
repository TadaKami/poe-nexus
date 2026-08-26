import { create } from 'zustand'
import { http } from '@/services/ApiService'
import type { PobBuildDto, DiffReport,TreePayload } from '@/types/pob'

interface PobState {
  builds: PobBuildDto[]
  diff: DiffReport | null
  tree: TreePayload | null
  treeVersion: string | null  
  loading: boolean
  savingScope: 'current' | 'target' | null
  fetchBuilds: () => Promise<void>
  save: (source: string, scope: 'current' | 'target') => Promise<PobBuildDto>
  compare: () => Promise<void>
  fetchTree: (version: string) => Promise<void>
}

export const usePobStore = create<PobState>((set, get) => ({
  builds: [],
  diff: null,
  tree: null,
  treeVersion: null,  
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
  },
  
  async fetchTree(version) {
    if (get().treeVersion === version && get().tree) return
    const { data } = await http.get<TreePayload>(`/pob/tree/${version}`)
    set({ tree: data, treeVersion: version })
   }  
}))
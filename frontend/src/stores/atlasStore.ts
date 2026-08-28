import { create } from 'zustand'
import { http } from '@/services/ApiService'
import type { TreePayload } from '@/types/pob'
import type { MemberAtlas } from '@/types/atlas'

interface AtlasState {
  tree: TreePayload | null
  mine: number[]
  members: MemberAtlas[]
  overlay: Record<string, boolean>
  alloc: number[]
  allocNexusId: string | null
  fetchTree: () => Promise<void>
  fetchMine: () => Promise<void>
  saveAtlas: (url: string) => Promise<number>
  fetchNexusAtlases: (nexusId: string) => Promise<void>
  toggleOverlay: (email: string) => void
  fetchAlloc: (nexusId: string) => Promise<void>
  toggleNode: (nexusId: string, nodeId: number) => Promise<void>
  applyRemoteAlloc: (nexusId: string, nodeIds: number[]) => void
}

export const useAtlasStore = create<AtlasState>((set, get) => ({
  tree: null,
  mine: [],
  members: [],
  overlay: {},
  alloc: [],
  allocNexusId: null,

  async fetchTree() {
    if (get().tree) return
    const { data } = await http.get<TreePayload>('/atlas/tree')
    set({ tree: data })
  },

  async fetchMine() {
    const { data } = await http.get<{ url?: string; nodeIds?: number[] }>('/atlas')
    set({ mine: data.nodeIds ?? [] })
  },

  async saveAtlas(url) {
    const { data } = await http.post<{ nodeCount: number }>('/atlas', { url })
    await get().fetchMine()
    return data.nodeCount
  },

  async fetchNexusAtlases(nexusId) {
    const { data } = await http.get<MemberAtlas[]>(`/nexus/${nexusId}/atlas`)
    set({ members: data })
  },

  toggleOverlay(email) {
    set((s) => ({ overlay: { ...s.overlay, [email]: !s.overlay[email] } }))
  },

  async fetchAlloc(nexusId) {
    const { data } = await http.get<{ nodeIds: number[] }>(`/nexus/${nexusId}/atlas/alloc`)
    set({ alloc: data.nodeIds ?? [], allocNexusId: nexusId })
  },

  async toggleNode(nexusId, nodeId) {
    const has = get().alloc.includes(nodeId)
    const body = has ? { add: [], remove: [nodeId] } : { add: [nodeId], remove: [] }
    const { data } = await http.post<{ nodeIds: number[] }>(`/nexus/${nexusId}/atlas/alloc`, body)
    set({ alloc: data.nodeIds ?? [], allocNexusId: nexusId })
  },

  applyRemoteAlloc(nexusId, nodeIds) {
    if (get().allocNexusId === nexusId) set({ alloc: nodeIds })
  }
}))
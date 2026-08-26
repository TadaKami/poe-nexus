import { create } from 'zustand'
import { session } from '@/services/session'
import { useAuthStore } from '@/stores/authStore'
import { useNexusStore } from '@/stores/nexusStore'
import { usePobStore } from '@/stores/pobStore'
import { router } from '@/router'

interface WsState {
  status: 'off' | 'connecting' | 'on'
  connect: () => void
  disconnect: () => void
}

let socket: WebSocket | null = null
let manualClose = false
let retryMs = 1000
let retryTimer: number | undefined

export const useWsStore = create<WsState>((set, get) => ({
  status: 'off',

  connect() {
    if (socket && socket.readyState <= WebSocket.OPEN) return
    const token = session.token
    if (!token) return
    manualClose = false
    set({ status: 'connecting' })
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    const ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`)
    socket = ws

    ws.onopen = () => {
      retryMs = 1000
      set({ status: 'on' })
    }
    ws.onmessage = (ev) => route(ev.data as string)
    ws.onclose = () => {
      socket = null
      set({ status: 'off' })
      if (!manualClose) {
        retryTimer = window.setTimeout(() => get().connect(), retryMs)
        retryMs = Math.min(retryMs * 2, 10000)
      }
    }
  },

  disconnect() {
    manualClose = true
    if (retryTimer) window.clearTimeout(retryTimer)
    socket?.close()
    socket = null
    set({ status: 'off' })
  }
}))

/** Роутинг серверных событий в сторы. */
function route(raw: string) {
  try {
    const msg = JSON.parse(raw)
    const payload = msg.payload ?? {}
    switch (msg.type) {
      case 'hello':
        break

      case 'nexus.members.changed': {
        const nx = useNexusStore.getState()
        nx.fetchMyNexuses().catch(() => {})
        if (nx.current) nx.fetchNexus(nx.current.nexus.id).catch(() => {})
        break
      }

      // Личное событие: меня кикнули
      case 'nexus.kicked': {
        const nx = useNexusStore.getState()
        nx.fetchMyNexuses().catch(() => {})
        if (nx.current && nx.current.nexus.id === payload.nexusId) {
          useNexusStore.setState({
            current: null,
            kickedNotice: `Вас исключили из Нексуса «${payload.nexusName ?? ''}»`
          })
          router.navigate('/nexus')
        }
        break
      }

      // Нексус распустили
      case 'nexus.deleted': {
        const nx = useNexusStore.getState()
        nx.fetchMyNexuses().catch(() => {})
        if (nx.current && nx.current.nexus.id === payload.nexusId) {
          useNexusStore.setState({
            current: null,
            kickedNotice: `Нексус «${payload.nexusName ?? ''}» был распущен лидером`
          })
          router.navigate('/nexus')
        }
        break
      }

      case 'pob.updated':
        usePobStore.getState().fetchBuilds().catch(() => {})
        break

      case 'nexus.synergy.changed': {
        const nx = useNexusStore.getState()
        if (nx.current && nx.current.nexus.id === payload.nexusId) nx.fetchSynergy(payload.nexusId).catch(() => {})
        break
      }        

      default:
        console.debug('[ws]', msg.type, payload)
    }
  } catch {
    // мусорное сообщение — игнорируем
  }
}

// Авто-коннект при появлении сессии, авто-дисконнект при logout
useAuthStore.subscribe((s, prev) => {
  if (s.user && !prev.user) useWsStore.getState().connect()
  if (!s.user && prev.user) useWsStore.getState().disconnect()
})
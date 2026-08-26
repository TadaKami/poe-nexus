import { useEffect } from 'react'
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import NexusListPage from './pages/NexusListPage'
import NexusPage from './pages/NexusPage'
import ProfilePage from './pages/ProfilePage'

// Guard: защищённые маршруты. Сначала ждём silent-restore сессии.
function ProtectedRoute() {
  const user = useAuthStore((s) => s.user)
  const ready = useAuthStore((s) => s.ready)
  const init = useAuthStore((s) => s.init)
  useEffect(() => { init() }, [init])
  if (!ready) return null
  if (!user) return <Navigate to="/login" replace />
  return <Outlet />
}

// Guard: гостевые маршруты для авторизованных
function GuestRoute() {
  const user = useAuthStore((s) => s.user)
  const ready = useAuthStore((s) => s.ready)
  const init = useAuthStore((s) => s.init)
  useEffect(() => { init() }, [init])
  if (!ready) return null
  if (user) return <Navigate to="/nexus" replace />
  return <Outlet />
}

export const router = createBrowserRouter([
  {
    element: <GuestRoute />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> }
    ]
  },
  {
    element: <ProtectedRoute />,
    children: [
      { path: '/', element: <Navigate to="/nexus" replace /> },
      { path: '/nexus', element: <NexusListPage /> },
      { path: '/nexus/:id', element: <NexusPage /> },
      { path: '/profile', element: <ProfilePage /> }
    ]
  }
])
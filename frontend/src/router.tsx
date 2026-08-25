import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import NexusListPage from './pages/NexusListPage'
import NexusPage from './pages/NexusPage'

// Guard: защищённые маршруты (аналог meta.requiresAuth)
function ProtectedRoute() {
  const user = useAuthStore((s) => s.user)
  if (user) return <Navigate to="/nexus" replace />
  return <Outlet />
}

// Guard: гостевые маршруты для авторизованных
function GuestRoute() {
  const user = useAuthStore((s) => s.user)
  if (user) return <Navigate to="/" replace />
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
      { path: '/nexus/:id', element: <NexusPage /> }
    ]
  }
])
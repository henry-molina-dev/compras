import { Navigate, Outlet, useLocation } from 'react-router'
import type { Rol } from '@/api/types'
import { useAuth } from '@/features/auth/AuthContext'
import { NoAutorizado } from '@/pages/NoAutorizado'

/** Redirige a /login sin sesión y muestra "no autorizado" si el rol no tiene acceso a la ruta. */
export function ProtectedRoute({ rol }: { rol?: Rol[] }) {
  const { sesion } = useAuth()
  const location = useLocation()
  if (!sesion) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (rol && !rol.includes(sesion.rol)) return <NoAutorizado />
  return <Outlet />
}

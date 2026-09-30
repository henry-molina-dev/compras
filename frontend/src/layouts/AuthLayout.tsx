import { Outlet } from 'react-router'
import { PieAutor } from '@/components/shared/PieAutor'

export function AuthLayout() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-4">
      <Outlet />
      <PieAutor className="text-center" />
    </main>
  )
}

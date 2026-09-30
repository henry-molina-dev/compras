import { ClipboardList, FileUp, Hammer, LogOut, Menu, PackagePlus, Users, Truck, Boxes, UserCog, History, Store } from 'lucide-react'
import { useState, type ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import type { Rol } from '@/api/types'
import { PieAutor } from '@/components/shared/PieAutor'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/AuthContext'
import { cn } from '@/lib/utils'

interface Enlace {
  to: string
  etiqueta: string
  icono: ComponentType<{ className?: string }>
  roles: Rol[]
  grupo?: string
}

const ENLACES: Enlace[] = [
  { to: '/ordenes', etiqueta: 'Órdenes', icono: ClipboardList, roles: ['ADMIN', 'COMPRADOR', 'GERENTE_SUCURSAL'] },
  { to: '/ordenes/nueva', etiqueta: 'Nueva orden', icono: PackagePlus, roles: ['ADMIN', 'COMPRADOR'] },
  { to: '/ordenes/importar', etiqueta: 'Importar órdenes', icono: FileUp, roles: ['ADMIN', 'COMPRADOR'] },
  { to: '/administracion/productos', etiqueta: 'Productos', icono: Boxes, roles: ['ADMIN'], grupo: 'Administración' },
  { to: '/administracion/proveedores', etiqueta: 'Proveedores', icono: Truck, roles: ['ADMIN'], grupo: 'Administración' },
  { to: '/administracion/sucursales', etiqueta: 'Sucursales', icono: Store, roles: ['ADMIN'], grupo: 'Administración' },
  { to: '/administracion/clientes', etiqueta: 'Clientes', icono: Users, roles: ['ADMIN'], grupo: 'Administración' },
  { to: '/administracion/usuarios', etiqueta: 'Usuarios', icono: UserCog, roles: ['ADMIN'], grupo: 'Administración' },
  { to: '/administracion/auditoria', etiqueta: 'Auditoría', icono: History, roles: ['ADMIN'], grupo: 'Administración' },
]

const ROL_ETIQUETA: Record<Rol, string> = {
  ADMIN: 'Administrador',
  COMPRADOR: 'Comprador',
  GERENTE_SUCURSAL: 'Gerente de sucursal',
}

function Navegacion({ rol, alNavegar }: { rol: Rol; alNavegar?: () => void }) {
  const visibles = ENLACES.filter((e) => e.roles.includes(rol))
  const grupos = [...new Set(visibles.map((e) => e.grupo ?? ''))]
  return (
    <nav className="flex flex-col gap-4" aria-label="Navegación principal">
      {grupos.map((grupo) => (
        <div key={grupo} className="flex flex-col gap-1">
          {grupo && <p className="px-3 text-xs text-muted-foreground">{grupo}</p>}
          {visibles
            .filter((e) => (e.grupo ?? '') === grupo)
            .map(({ to, etiqueta, icono: Icono }) => (
              <NavLink
                key={to}
                to={to}
                end
                onClick={alNavegar}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-md px-3 py-2 text-[13px] transition-colors hover:bg-accent',
                    isActive ? 'bg-accent font-medium text-accent-foreground' : 'text-foreground',
                  )
                }
              >
                <Icono className="size-4" />
                {etiqueta}
              </NavLink>
            ))}
        </div>
      ))}
    </nav>
  )
}

export function AppLayout() {
  const { sesion, logout } = useAuth()
  const navigate = useNavigate()
  const [menuAbierto, setMenuAbierto] = useState(false)
  if (!sesion) return null

  const salir = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const marca = (
    <div className="flex items-center gap-2 text-primary">
      <Hammer className="size-5" aria-hidden />
      <span className="text-[15px] font-medium">Órdenes de compra</span>
    </div>
  )

  const usuario = (
    <div className="flex flex-col gap-2 border-t pt-3">
      <div className="px-3">
        <p className="text-[13px] font-medium">{sesion.username}</p>
        <p className="text-xs text-muted-foreground">{ROL_ETIQUETA[sesion.rol]}</p>
      </div>
      <Button variant="ghost" size="sm" className="justify-start" onClick={salir}>
        <LogOut /> Cerrar sesión
      </Button>
      <PieAutor className="px-3" />
    </div>
  )

  return (
    <div className="min-h-screen md:flex">
      <header className="flex items-center justify-between border-b bg-card px-4 py-3 md:hidden">
        {marca}
        <Sheet open={menuAbierto} onOpenChange={setMenuAbierto}>
          <SheetTrigger asChild>
            <Button variant="outline" size="icon" aria-label="Abrir menú">
              <Menu />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="flex w-64 flex-col gap-4 p-4">
            <SheetHeader className="p-0">
              <SheetTitle className="sr-only">Menú</SheetTitle>
              {marca}
            </SheetHeader>
            <div className="flex-1">
              <Navegacion rol={sesion.rol} alNavegar={() => setMenuAbierto(false)} />
            </div>
            {usuario}
          </SheetContent>
        </Sheet>
      </header>

      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col gap-6 border-r bg-card p-4 md:flex">
        {marca}
        <div className="flex-1">
          <Navegacion rol={sesion.rol} />
        </div>
        {usuario}
      </aside>

      <main className="min-w-0 flex-1 px-4 py-6 md:px-8">
        <Outlet />
      </main>
    </div>
  )
}

import { ShieldAlert } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'

export function NoAutorizado() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <ShieldAlert className="size-10 text-primary" aria-hidden />
      <h1 className="text-xl font-medium">No autorizado</h1>
      <p className="text-[13px] text-muted-foreground">Tu rol no tiene acceso a esta sección.</p>
      <Button asChild variant="outline">
        <Link to="/ordenes">Volver a órdenes</Link>
      </Button>
    </div>
  )
}

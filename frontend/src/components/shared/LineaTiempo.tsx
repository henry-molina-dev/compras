import { CircleCheck, CircleX, Flag, PackageCheck, PackageOpen, PencilLine, Truck, type LucideIcon } from 'lucide-react'
import type { LineaTiempoItem } from '@/api/types'
import { formatoFechaHora } from '@/lib/format'
import { cn } from '@/lib/utils'

const ETIQUETAS: Record<string, { texto: string; icono: LucideIcon; tono: string }> = {
  CREADA: { texto: 'Orden creada', icono: PencilLine, tono: 'text-info' },
  APROBADA: { texto: 'Orden aprobada', icono: CircleCheck, tono: 'text-success' },
  CERRADA: { texto: 'Orden cerrada', icono: Flag, tono: 'text-muted-foreground' },
  ANULADA: { texto: 'Orden anulada', icono: CircleX, tono: 'text-destructive' },
  ACEPTADA: { texto: 'Proveedor aceptó la orden', icono: CircleCheck, tono: 'text-success' },
  RECHAZADA: { texto: 'Proveedor rechazó la orden', icono: CircleX, tono: 'text-destructive' },
  PREPARADA: { texto: 'Proveedor preparó el pedido', icono: PackageOpen, tono: 'text-info' },
  DESPACHADA: { texto: 'Proveedor despachó el pedido', icono: Truck, tono: 'text-info' },
  ENTREGADA: { texto: 'Proveedor reporta entrega', icono: PackageCheck, tono: 'text-success' },
}

/** Historial vertical que combina cambios de estado internos y eventos reportados por el proveedor. */
export function LineaTiempo({ items }: { items: LineaTiempoItem[] }) {
  if (items.length === 0) return <p className="text-[13px] text-muted-foreground">Sin movimientos todavía.</p>
  return (
    <ol className="flex flex-col">
      {items.map((item, indice) => {
        const { texto, icono: Icono, tono } = ETIQUETAS[item.tipo] ?? { texto: item.tipo, icono: Flag, tono: 'text-muted-foreground' }
        return (
          <li key={`${item.origen}-${item.tipo}-${item.fecha}-${indice}`} className="relative flex gap-3 pb-5 last:pb-0">
            {indice < items.length - 1 && <span className="absolute top-6 left-[11px] h-full w-px bg-border" aria-hidden />}
            <Icono className={cn('relative mt-0.5 size-6 shrink-0 rounded-full bg-card p-0.5', tono)} aria-hidden />
            <div className="min-w-0 text-[13px]">
              <p className="font-medium">{texto}</p>
              <p className="text-xs text-muted-foreground">
                {formatoFechaHora(item.fecha)} · {item.origen === 'proveedor' ? 'Proveedor' : 'Interno'}
              </p>
              {item.observacion && <p className="mt-1 text-muted-foreground">{item.observacion}</p>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

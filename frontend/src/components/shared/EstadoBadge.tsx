import type { EstadoOrden } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

type Tono = 'info' | 'success' | 'danger' | 'neutral'

const ESTADO_ORDEN: Record<EstadoOrden, { etiqueta: string; tono: Tono }> = {
  CREADA: { etiqueta: 'Creada', tono: 'info' },
  APROBADA: { etiqueta: 'Aprobada', tono: 'success' },
  CERRADA: { etiqueta: 'Cerrada', tono: 'neutral' },
  ANULADA: { etiqueta: 'Anulada', tono: 'danger' },
}

const TONOS: Record<Tono, string> = {
  info: 'bg-info/10 text-info',
  success: 'bg-success/10 text-success',
  danger: 'bg-destructive/10 text-destructive',
  neutral: 'bg-muted text-muted-foreground border-border',
}

/** Pill de color reutilizable: estado de una orden o resultado (creada/rechazada) de una importación. */
export function EstadoBadge({ estado, etiqueta, tono }: { estado?: EstadoOrden; etiqueta?: string; tono?: Tono }) {
  const base = estado ? ESTADO_ORDEN[estado] : { etiqueta: etiqueta ?? '', tono: tono ?? 'neutral' }
  return (
    <Badge variant="outline" className={cn('rounded-full border-transparent px-2.5 text-xs font-medium', TONOS[tono ?? base.tono])}>
      {etiqueta ?? base.etiqueta}
    </Badge>
  )
}

import { CircleCheck, Info, OctagonX, TriangleAlert } from 'lucide-react'
import type { ComponentProps, ReactNode } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'

export type TonoAviso = 'info' | 'exito' | 'advertencia' | 'error'

const TONOS = {
  info: { Icono: Info, variante: 'info', role: 'status' },
  exito: { Icono: CircleCheck, variante: 'success', role: 'status' },
  advertencia: { Icono: TriangleAlert, variante: 'warning', role: 'status' },
  error: { Icono: OctagonX, variante: 'destructive', role: 'alert' },
} as const

/**
 * Mensaje dentro de una pantalla, con el color de su significado: azul informa, verde confirma,
 * ámbar advierte y rojo es solo para errores. Informar y advertir usan role="status" (se anuncia sin
 * interrumpir); un error usa role="alert".
 */
export function Aviso({
  tono,
  children,
  className,
  ...props
}: { tono: TonoAviso; children: ReactNode } & Omit<ComponentProps<'div'>, 'children' | 'role'>) {
  const { Icono, variante, role } = TONOS[tono]
  return (
    <Alert variant={variante} role={role} className={cn('items-start text-[13px]', className)} {...props}>
      <Icono aria-hidden />
      <AlertDescription>{children}</AlertDescription>
    </Alert>
  )
}

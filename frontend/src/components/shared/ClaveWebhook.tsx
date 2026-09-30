import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Copy, Eye, EyeOff, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { api, ApiError } from '@/api/client'
import type { Proveedor } from '@/api/types'
import { Button } from '@/components/ui/button'
import { invalidarCatalogo } from '@/features/catalogos/hooks'

const MASCARA = '•'.repeat(24)

/**
 * Clave de webhook de un proveedor: oculta por defecto, con "mostrar" y "copiar". Si recibe
 * `proveedorId` (solo la pantalla de edicion) ofrece ademas regenerarla, con confirmacion, porque
 * la clave anterior deja de funcionar al instante.
 */
export function ClaveWebhook({ valor, proveedorId }: { valor: string | null; proveedorId?: number }) {
  const queryClient = useQueryClient()
  // La clave regenerada en esta sesion tiene prioridad sobre la del listado hasta que este se refresque.
  const [regenerada, setRegenerada] = useState<string | null>(null)
  const [visible, setVisible] = useState(false)
  const [confirmando, setConfirmando] = useState(false)
  const clave = regenerada ?? valor

  const regenerar = useMutation({
    mutationFn: () => api.patch<Proveedor>(`/proveedores/${proveedorId}/regenerar-clave-webhook`),
    onSuccess: (proveedor) => {
      setRegenerada(proveedor.webhook_api_key ?? null)
      setVisible(true) // el admin acaba de generarla para entregarsela al proveedor: la ve y la copia
      setConfirmando(false)
      invalidarCatalogo(queryClient, '/proveedores')
      toast.success('Clave regenerada. La anterior dejó de funcionar.')
    },
    onError: (error) => {
      setConfirmando(false)
      toast.error(error instanceof ApiError ? error.message : 'No se pudo regenerar la clave.')
    },
  })

  async function copiar() {
    if (!clave) return
    try {
      await navigator.clipboard.writeText(clave)
      toast.success('Clave copiada.')
    } catch {
      toast.error('No se pudo copiar; muéstrala y cópiala a mano.')
    }
  }

  if (!clave) {
    return <span className="text-muted-foreground">—</span>
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-1">
        <code className="text-xs break-all" data-testid="clave-webhook">
          {visible ? clave : MASCARA}
        </code>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={visible ? 'Ocultar clave' : 'Mostrar clave'}
          onClick={() => setVisible((v) => !v)}
        >
          {visible ? <EyeOff /> : <Eye />}
        </Button>
        <Button type="button" variant="ghost" size="icon" aria-label="Copiar clave" onClick={() => void copiar()}>
          <Copy />
        </Button>
      </div>

      {proveedorId !== undefined &&
        (confirmando ? (
          <div className="flex flex-col gap-2 rounded-[8px] border border-destructive/40 bg-destructive/5 p-3 text-xs">
            <p>La clave actual dejará de funcionar de inmediato y habrá que entregarle la nueva al proveedor.</p>
            <div className="flex gap-2">
              <Button type="button" size="sm" variant="destructive" disabled={regenerar.isPending} onClick={() => regenerar.mutate()}>
                Sí, regenerar
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={() => setConfirmando(false)}>
                Cancelar
              </Button>
            </div>
          </div>
        ) : (
          <Button type="button" size="sm" variant="outline" className="w-fit" onClick={() => setConfirmando(true)}>
            <RefreshCw /> Regenerar clave
          </Button>
        ))}
    </div>
  )
}

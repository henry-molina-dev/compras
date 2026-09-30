import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '@/api/client'
import type { AuditoriaEvento, ListEnvelope } from '@/api/types'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EstadoBadge } from '@/components/shared/EstadoBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatoFechaHora } from '@/lib/format'

export function AuditoriaPage() {
  const [entrada, setEntrada] = useState('')
  const [numeroOrden, setNumeroOrden] = useState<string | null>(null)

  const auditoria = useQuery({
    queryKey: ['auditoria', numeroOrden],
    queryFn: () => api.get<ListEnvelope<AuditoriaEvento>>(`/auditoria/ordenes/${numeroOrden}`),
    select: (r) => r.data,
    enabled: numeroOrden !== null,
    retry: false,
  })

  return (
    <div className="flex flex-col gap-5">
      <EncabezadoPagina titulo="Auditoría de órdenes" />
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          const valor = entrada.trim()
          setNumeroOrden(valor || null)
        }}
      >
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="numero-orden">Número de orden</Label>
          <Input
            id="numero-orden"
            type="text"
            placeholder="OC-2026-000123"
            value={entrada}
            onChange={(e) => setEntrada(e.target.value)}
            className="w-48"
          />
        </div>
        <Button type="submit" disabled={!entrada.trim()}>
          Consultar
        </Button>
      </form>

      {auditoria.isError && <ErrorMessage error={auditoria.error} />}
      {auditoria.isSuccess && auditoria.data.length === 0 && <p className="text-[13px] text-muted-foreground">Esta orden no tiene cambios de estado registrados.</p>}
      {auditoria.isSuccess && auditoria.data.length > 0 && (
        <div className="overflow-hidden rounded-[12px] border bg-card">
          <Table className="text-[13px]">
            <TableHeader>
              <TableRow>
                <TableHead>Fecha</TableHead>
                <TableHead>Cambio</TableHead>
                <TableHead>Usuario</TableHead>
                <TableHead>Observación</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {auditoria.data.map((e) => (
                <TableRow key={e.id}>
                  <TableCell>{formatoFechaHora(e.fecha)}</TableCell>
                  <TableCell>
                    <span className="inline-flex items-center gap-2">
                      {e.estado_anterior ? <EstadoBadge estado={e.estado_anterior} /> : <span className="text-muted-foreground">—</span>}
                      <span aria-hidden>→</span>
                      <EstadoBadge estado={e.estado_nuevo} />
                    </span>
                  </TableCell>
                  <TableCell>#{e.usuario_id}</TableCell>
                  <TableCell>{e.observacion ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}

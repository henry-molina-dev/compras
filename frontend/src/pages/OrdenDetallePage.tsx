import { Ban, CheckCheck, Download, Pencil, PackageCheck } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { api } from '@/api/client'
import type { OrdenCompra } from '@/api/types'
import { Aviso } from '@/components/shared/Aviso'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { mismoPrecio, textoVariacion, variacionPorcentual } from '@/features/ordenes/precios'
import { pasosDetalle } from '@/features/tours/pasos'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EstadoBadge } from '@/components/shared/EstadoBadge'
import { LineaTiempo } from '@/components/shared/LineaTiempo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/features/auth/AuthContext'
import { useSucursales } from '@/features/catalogos/hooks'
import { estadoDeAccion } from '@/features/ordenes/acciones'
import { useAnularOrden, useAprobarOrden, useCerrarOrden, useLineaTiempo, useOrden } from '@/features/ordenes/hooks'
import { formatoFecha, formatoMonto } from '@/lib/format'

function Dato({ etiqueta, children }: { etiqueta: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{etiqueta}</dt>
      <dd className="text-[13px]">{children}</dd>
    </div>
  )
}

function DialogoAnular({ orden, abierto, onClose }: { orden: OrdenCompra; abierto: boolean; onClose: () => void }) {
  const anular = useAnularOrden(orden.id)
  const [motivo, setMotivo] = useState('')
  const enviar = () =>
    anular.mutate(motivo.trim(), {
      onSuccess: () => {
        toast.success('Orden anulada.')
        setMotivo('')
        onClose()
      },
    })
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Anular orden {orden.numero_orden}</DialogTitle>
          <DialogDescription>Se notificará al proveedor. Indica el motivo de la anulación.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="motivo">Motivo</Label>
          <Textarea id="motivo" value={motivo} maxLength={250} onChange={(e) => setMotivo(e.target.value)} />
        </div>
        <ErrorMessage error={anular.error} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={enviar} disabled={!motivo.trim() || anular.isPending}>
            Anular orden
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function DialogoCerrar({ orden, abierto, onClose }: { orden: OrdenCompra; abierto: boolean; onClose: () => void }) {
  const cerrar = useCerrarOrden(orden.id)
  const [conforme, setConforme] = useState(true)
  const [observacion, setObservacion] = useState('')
  const enviar = () =>
    cerrar.mutate(
      { conforme, observacion: observacion.trim() || undefined },
      {
        onSuccess: () => {
          toast.success('Recepción confirmada.')
          onClose()
        },
      },
    )
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmar recepción de {orden.numero_orden}</DialogTitle>
          <DialogDescription>Al cerrar la orden se da por ingresada la mercadería en la sucursal.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2">
          <Checkbox id="conforme" checked={conforme} onCheckedChange={(v) => setConforme(v === true)} />
          <Label htmlFor="conforme">La mercadería llegó conforme</Label>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="observacion">Observación (opcional)</Label>
          <Textarea id="observacion" value={observacion} maxLength={250} onChange={(e) => setObservacion(e.target.value)} />
        </div>
        <ErrorMessage error={cerrar.error} />
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={enviar} disabled={cerrar.isPending}>
            Confirmar recepción
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function OrdenDetallePage() {
  const id = Number(useParams().id)
  const { sesion } = useAuth()
  const orden = useOrden(id)
  const lineaTiempo = useLineaTiempo(id)
  const sucursales = useSucursales()
  const aprobar = useAprobarOrden(id)
  const [dialogo, setDialogo] = useState<'anular' | 'cerrar' | null>(null)
  const [exportando, setExportando] = useState(false)

  if (orden.isPending) return <p className="text-[13px] text-muted-foreground">Cargando orden…</p>
  if (orden.isError) return <ErrorMessage error={orden.error} />
  if (!sesion) return null

  const o = orden.data
  const accion = (a: Parameters<typeof estadoDeAccion>[0]) => estadoDeAccion(a, o.estado, sesion.rol)
  const entregadaPorProveedor = lineaTiempo.data?.some((i) => i.origen === 'proveedor' && i.tipo === 'ENTREGADA') ?? false
  const sucursal = sucursales.data?.find((s) => s.id === o.sucursal_destino_id)?.nombre ?? `Sucursal ${o.sucursal_destino_id}`
  const cliente = o.cliente_id ? (o.cliente_nombre ?? `Cliente #${o.cliente_id}`) : null

  const exportarPdf = async () => {
    setExportando(true)
    try {
      await api.download(`/ordenes/${o.id}/exportar`, { formato: 'pdf' }, `${o.numero_orden}.pdf`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'No se pudo exportar el PDF.')
    } finally {
      setExportando(false)
    }
  }

  const botonAprobar = accion('aprobar')
  const botonAnular = accion('anular')
  const botonCerrar = accion('cerrar')
  const botonEditar = accion('editar')

  return (
    <div className="flex flex-col gap-5">
      <EncabezadoPagina
        titulo={o.numero_orden}
        extra={<EstadoBadge estado={o.estado} />}
        anclaTitulo="estado-orden"
        pasos={pasosDetalle(sesion.rol)}
        acciones={
          <div className="flex flex-wrap gap-2" data-tour="acciones-orden">
            <Button variant="outline" onClick={exportarPdf} disabled={exportando}>
              <Download /> Exportar PDF
            </Button>
            {botonEditar.visible && (
              <Button variant="outline" asChild={botonEditar.habilitada} disabled={!botonEditar.habilitada}>
                {botonEditar.habilitada ? (
                  <Link to={`/ordenes/${o.id}/editar`}>
                    <Pencil /> Editar
                  </Link>
                ) : (
                  <>
                    <Pencil /> Editar
                  </>
                )}
              </Button>
            )}
            {botonAprobar.visible && (
              <Button
                disabled={!botonAprobar.habilitada || aprobar.isPending}
                onClick={() => aprobar.mutate(undefined, { onSuccess: () => toast.success('Orden aprobada.') })}
              >
                <CheckCheck /> Aprobar
              </Button>
            )}
            {botonAnular.visible && (
              <Button variant="outline" disabled={!botonAnular.habilitada} onClick={() => setDialogo('anular')}>
                <Ban /> Anular
              </Button>
            )}
            {botonCerrar.visible && (
              <Button
                variant={entregadaPorProveedor && botonCerrar.habilitada ? 'default' : 'outline'}
                className={entregadaPorProveedor && botonCerrar.habilitada ? 'ring-2 ring-ring' : undefined}
                disabled={!botonCerrar.habilitada}
                onClick={() => setDialogo('cerrar')}
              >
                <PackageCheck /> {sesion.rol === 'GERENTE_SUCURSAL' ? 'Confirmar recepción' : 'Cerrar orden'}
              </Button>
            )}
          </div>
        }
      />

      <ErrorMessage error={aprobar.error} />

      {entregadaPorProveedor && o.estado === 'APROBADA' && (
        <Aviso tono="exito">El proveedor reportó la entrega. Cuando verifiques la mercadería, cierra la orden.</Aviso>
      )}

      <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-5">
          <Card className="rounded-[12px]" data-tour="datos-orden">
            <CardHeader>
              <CardTitle className="text-[15px] font-medium">Datos generales</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-2 gap-4 md:grid-cols-3">
                <Dato etiqueta="Proveedor">{o.proveedor?.nombre ?? `Proveedor ${o.proveedor_id}`}</Dato>
                <Dato etiqueta="Sucursal destino">{sucursal}</Dato>
                {cliente && <Dato etiqueta="Cliente">{cliente}</Dato>}
                <Dato etiqueta="Fecha necesaria">{formatoFecha(o.fecha_necesaria)}</Dato>
                {o.motivo_anulacion && <Dato etiqueta="Motivo de anulación">{o.motivo_anulacion}</Dato>}
                {o.conforme !== null && <Dato etiqueta="Recepción">{o.conforme ? 'Conforme' : 'Con observaciones'}</Dato>}
                {o.observacion_cierre && <Dato etiqueta="Observación de cierre">{o.observacion_cierre}</Dato>}
              </dl>
            </CardContent>
          </Card>

          <Card className="rounded-[12px]" data-tour="detalle-orden">
            <CardHeader>
              <CardTitle className="text-[15px] font-medium">Detalle de productos</CardTitle>
            </CardHeader>
            <CardContent>
              <Table className="text-[13px]">
                <TableHeader>
                  <TableRow>
                    <TableHead>Producto</TableHead>
                    <TableHead className="text-right">Cantidad</TableHead>
                    <TableHead className="text-right">Precio</TableHead>
                    <TableHead className="text-right">Subtotal</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {o.detalle.map((linea) => (
                    <TableRow key={linea.id}>
                      <TableCell>
                        {linea.producto_nombre} <span className="text-xs text-muted-foreground">{linea.producto_codigo}</span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{linea.cantidad}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatoMonto(linea.precio_unitario)}
                        {linea.precio_catalogo != null && !mismoPrecio(linea.precio_unitario, linea.precio_catalogo) && (
                          <div className="text-[11px] font-normal text-info-ink">
                            Negociado · catálogo {formatoMonto(linea.precio_catalogo)} (
                            {textoVariacion(variacionPorcentual(linea.precio_unitario, linea.precio_catalogo))})
                          </div>
                        )}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{formatoMonto(linea.subtotal)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <p className="mt-4 text-right text-xl font-medium tabular-nums">Total {formatoMonto(o.total)}</p>
            </CardContent>
          </Card>
        </div>

        <Card className="h-fit rounded-[12px]" data-tour="linea-tiempo">
          <CardHeader>
            <CardTitle className="text-[15px] font-medium">Línea de tiempo</CardTitle>
          </CardHeader>
          <CardContent>
            {lineaTiempo.isError ? <ErrorMessage error={lineaTiempo.error} /> : <LineaTiempo items={lineaTiempo.data ?? []} />}
          </CardContent>
        </Card>
      </div>

      <DialogoAnular orden={o} abierto={dialogo === 'anular'} onClose={() => setDialogo(null)} />
      <DialogoCerrar orden={o} abierto={dialogo === 'cerrar'} onClose={() => setDialogo(null)} />
    </div>
  )
}

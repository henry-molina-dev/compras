import { Link } from 'react-router'
import type { OrdenCompra, Sucursal } from '@/api/types'
import { EstadoBadge } from '@/components/shared/EstadoBadge'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatoFecha, formatoMonto } from '@/lib/format'

interface Props {
  ordenes: OrdenCompra[]
  sucursales: Sucursal[]
}

/** Tabla con columnas desde `md`; tarjetas apiladas (una por orden) por debajo de ese ancho. */
export function OrdenesTable({ ordenes, sucursales }: Props) {
  const nombreSucursal = (id: number) => sucursales.find((s) => s.id === id)?.nombre ?? `Sucursal ${id}`
  const nombreProveedor = (o: OrdenCompra) => o.proveedor?.nombre ?? `Proveedor ${o.proveedor_id}`
  const nombreCliente = (o: OrdenCompra) => (o.cliente_id ? (o.cliente_nombre ?? `Cliente ${o.cliente_id}`) : null)

  return (
    <>
      <div className="hidden overflow-hidden rounded-[12px] border bg-card md:block">
        <Table className="text-[13px]">
          <TableHeader>
            <TableRow>
              <TableHead>Número</TableHead>
              <TableHead>Proveedor</TableHead>
              <TableHead>Sucursal</TableHead>
              <TableHead>Cliente</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead className="text-right">Total</TableHead>
              <TableHead>Fecha necesaria</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ordenes.map((o) => (
              <TableRow key={o.id}>
                <TableCell>
                  <Link to={`/ordenes/${o.id}`} className="font-medium text-primary hover:underline">
                    {o.numero_orden}
                  </Link>
                </TableCell>
                <TableCell>{nombreProveedor(o)}</TableCell>
                <TableCell>{nombreSucursal(o.sucursal_destino_id)}</TableCell>
                <TableCell>{nombreCliente(o) ?? '—'}</TableCell>
                <TableCell>
                  <EstadoBadge estado={o.estado} />
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatoMonto(o.total)}</TableCell>
                <TableCell>{formatoFecha(o.fecha_necesaria)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <ul className="flex flex-col gap-3 md:hidden">
        {ordenes.map((o) => (
          <li key={o.id}>
            <Link to={`/ordenes/${o.id}`}>
              <Card className="gap-2 rounded-[12px] p-4 text-[13px]">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-primary">{o.numero_orden}</span>
                  <EstadoBadge estado={o.estado} />
                </div>
                <p>{nombreProveedor(o)}</p>
                <p className="text-muted-foreground">{nombreSucursal(o.sucursal_destino_id)}</p>
                {nombreCliente(o) && <p className="text-muted-foreground">Cliente: {nombreCliente(o)}</p>}
                <div className="flex justify-between text-muted-foreground">
                  <span>Necesaria: {formatoFecha(o.fecha_necesaria)}</span>
                  <span className="text-[15px] font-medium text-foreground tabular-nums">{formatoMonto(o.total)}</span>
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </>
  )
}

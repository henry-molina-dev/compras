import { ChevronLeft, ChevronRight, Download, FileUp, PackagePlus } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { api } from '@/api/client'
import type { EstadoOrden } from '@/api/types'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { OrdenesTable } from '@/components/shared/OrdenesTable'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useAuth } from '@/features/auth/AuthContext'
import { useClientes, useProveedores, useSucursales } from '@/features/catalogos/hooks'
import { OPCIONES_ORDENES_POR_PAGINA, ORDENES_POR_PAGINA, useOrdenes, type FiltrosOrdenes } from '@/features/ordenes/hooks'
import { pasosLista } from '@/features/tours/pasos'

const TODOS = 'todos'
const ESTADOS: EstadoOrden[] = ['CREADA', 'APROBADA', 'CERRADA', 'ANULADA']

function FiltroFecha({
  id,
  etiqueta,
  etiquetaAccesible,
  valor,
  onChange,
  invalido,
}: {
  id: string
  etiqueta: string
  etiquetaAccesible: string
  valor: string
  onChange: (valor: string) => void
  invalido: boolean
}) {
  return (
    // "Desde"/"Hasta" van a la izquierda del campo (no encima) para que el grupo mida lo mismo que
    // los demás filtros y "Fecha necesaria" quede a la altura de sus etiquetas.
    <div className="flex items-center gap-2">
      <Label htmlFor={id} className="text-xs font-normal text-muted-foreground">
        {etiqueta}
      </Label>
      <Input
        id={id}
        type="date"
        aria-label={etiquetaAccesible}
        aria-invalid={invalido}
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        className="w-full text-[13px] sm:w-40"
      />
    </div>
  )
}

function FiltroSelect({
  id,
  etiqueta,
  valor,
  onChange,
  opciones,
}: {
  id: string
  etiqueta: string
  valor: string
  onChange: (valor: string) => void
  opciones: { valor: string; etiqueta: string }[]
}) {
  return (
    <div className="flex flex-col gap-1">
      <Label htmlFor={id} className="text-xs font-normal text-muted-foreground">
        {etiqueta}
      </Label>
      <Select value={valor} onValueChange={onChange}>
        <SelectTrigger id={id} className="w-full text-[13px] sm:w-52" aria-label={etiqueta}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={TODOS}>Todos</SelectItem>
          {opciones.map((o) => (
            <SelectItem key={o.valor} value={o.valor}>
              {o.etiqueta}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

export function OrdenesListPage() {
  const { sesion } = useAuth()
  const esGerente = sesion?.rol === 'GERENTE_SUCURSAL'
  const puedeCrear = sesion?.rol === 'ADMIN' || sesion?.rol === 'COMPRADOR'

  const [numeroOrdenTexto, setNumeroOrdenTexto] = useState('')
  const [numeroOrden, setNumeroOrden] = useState('')
  const [proveedor, setProveedor] = useState(TODOS)
  const [sucursal, setSucursal] = useState(TODOS)
  const [cliente, setCliente] = useState(TODOS)
  const [estado, setEstado] = useState(TODOS)
  const [fechaDesde, setFechaDesde] = useState('')
  const [fechaHasta, setFechaHasta] = useState('')
  const [porPagina, setPorPagina] = useState(ORDENES_POR_PAGINA)
  const [exportando, setExportando] = useState(false)

  // Comparación lexicográfica válida porque <input type="date"> siempre entrega "YYYY-MM-DD".
  const rangoFechaInvalido = Boolean(fechaDesde && fechaHasta && fechaDesde > fechaHasta)

  const sucursales = useSucursales()
  const proveedores = useProveedores()
  const clientes = useClientes()

  // Espera una pausa al escribir antes de filtrar, para no disparar una consulta por tecla.
  useEffect(() => {
    const id = setTimeout(() => setNumeroOrden(numeroOrdenTexto.trim()), 400)
    return () => clearTimeout(id)
  }, [numeroOrdenTexto])

  // El gerente solo trabaja con lo que está por recibir en su sucursal: el backend ya restringe
  // por sucursal, aquí solo se fija el estado.
  const filtros: FiltrosOrdenes = esGerente
    ? { estado: 'APROBADA' }
    : {
        numero_orden: numeroOrden || undefined,
        proveedor_id: proveedor === TODOS ? undefined : Number(proveedor),
        sucursal_id: sucursal === TODOS ? undefined : Number(sucursal),
        cliente_id: cliente === TODOS ? undefined : Number(cliente),
        estado: estado === TODOS ? undefined : estado,
        // Mientras el rango este invertido no se envia ninguna de las dos fechas: se corrige la
        // entrada del usuario en vez de dejar que el backend rechace la consulta con un 400.
        fecha_desde: rangoFechaInvalido ? undefined : fechaDesde || undefined,
        fecha_hasta: rangoFechaInvalido ? undefined : fechaHasta || undefined,
      }
  const paginacionClave = `${JSON.stringify(filtros)}|${porPagina}`

  // Anterior/Siguiente sobre paginación por cursor: `historial` guarda los cursores de las
  // páginas ya visitadas (para poder retroceder) y `cursor` es el de la página actual. Si
  // cambian los filtros o el tamaño de página, se reinicia a la primera página durante el
  // render (en vez de un efecto) para no disparar una segunda pasada de render.
  const [historial, setHistorial] = useState<(string | undefined)[]>([])
  const [cursor, setCursor] = useState<string | undefined>(undefined)
  const [paginacionClavePrevia, setPaginacionClavePrevia] = useState(paginacionClave)
  if (paginacionClavePrevia !== paginacionClave) {
    setPaginacionClavePrevia(paginacionClave)
    setHistorial([])
    setCursor(undefined)
  }

  const ordenes = useOrdenes(filtros, cursor, porPagina)
  const filas = ordenes.data?.data ?? []

  const irASiguiente = () => {
    const siguienteCursor = filas.at(-1)?.numero_orden
    if (!siguienteCursor) return
    setHistorial((h) => [...h, cursor])
    setCursor(siguienteCursor)
  }

  const irAAnterior = () => {
    if (historial.length === 0) return
    setCursor(historial.at(-1))
    setHistorial((h) => h.slice(0, -1))
  }

  const exportarExcel = async () => {
    setExportando(true)
    try {
      await api.download('/ordenes/exportar', { formato: 'xlsx', ...filtros }, 'ordenes.xlsx')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'No se pudo exportar el reporte.')
    } finally {
      setExportando(false)
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <EncabezadoPagina
        titulo={esGerente ? 'Órdenes por recibir' : 'Órdenes de compra'}
        pasos={pasosLista(sesion?.rol ?? 'COMPRADOR')}
        acciones={
          <>
            <Button variant="outline" onClick={exportarExcel} disabled={exportando} data-tour="exportar">
              <Download /> Exportar Excel
            </Button>
            {puedeCrear && (
              <div className="flex gap-2" data-tour="crear-importar">
                <Button variant="outline" asChild>
                  <Link to="/ordenes/importar">
                    <FileUp /> Importar
                  </Link>
                </Button>
                <Button asChild>
                  <Link to="/ordenes/nueva">
                    <PackagePlus /> Nueva orden
                  </Link>
                </Button>
              </div>
            )}
          </>
        }
      />

      {!esGerente && (
        <Card size="sm" className="rounded-[12px]" data-tour="filtros">
          <CardContent className="flex flex-col gap-2">
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-end">
              <div className="flex flex-col gap-1">
                <Label htmlFor="filtro-numero-orden" className="text-xs font-normal text-muted-foreground">
                  Número de orden
                </Label>
                <Input
                  id="filtro-numero-orden"
                  type="text"
                  placeholder="OC-2026-000123"
                  value={numeroOrdenTexto}
                  onChange={(e) => setNumeroOrdenTexto(e.target.value)}
                  className="w-full text-[13px] sm:w-52"
                />
              </div>
              <FiltroSelect
                id="filtro-proveedor"
                etiqueta="Proveedor"
                valor={proveedor}
                onChange={setProveedor}
                opciones={(proveedores.data ?? []).map((p) => ({ valor: String(p.id), etiqueta: p.nombre }))}
              />
              <FiltroSelect
                id="filtro-sucursal"
                etiqueta="Sucursal"
                valor={sucursal}
                onChange={setSucursal}
                opciones={(sucursales.data ?? []).map((s) => ({ valor: String(s.id), etiqueta: s.nombre }))}
              />
              <FiltroSelect
                id="filtro-cliente"
                etiqueta="Cliente"
                valor={cliente}
                onChange={setCliente}
                opciones={(clientes.data ?? []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre }))}
              />
              <FiltroSelect
                id="filtro-estado"
                etiqueta="Estado"
                valor={estado}
                onChange={setEstado}
                opciones={ESTADOS.map((e) => ({ valor: e, etiqueta: e.charAt(0) + e.slice(1).toLowerCase() }))}
              />
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted-foreground">Fecha necesaria</span>
                <div className="flex gap-2">
                  <FiltroFecha
                    id="fecha-necesaria-desde"
                    etiqueta="Desde"
                    etiquetaAccesible="Fecha necesaria desde"
                    valor={fechaDesde}
                    onChange={setFechaDesde}
                    invalido={rangoFechaInvalido}
                  />
                  <FiltroFecha
                    id="fecha-necesaria-hasta"
                    etiqueta="Hasta"
                    etiquetaAccesible="Fecha necesaria hasta"
                    valor={fechaHasta}
                    onChange={setFechaHasta}
                    invalido={rangoFechaInvalido}
                  />
                </div>
              </div>
            </div>
            {rangoFechaInvalido && (
              <p className="text-xs text-destructive">"Fecha necesaria desde" no puede ser posterior a "hasta".</p>
            )}
          </CardContent>
        </Card>
      )}

      {ordenes.isError && <ErrorMessage error={ordenes.error} />}
      {ordenes.isPending && <p className="text-[13px] text-muted-foreground">Cargando órdenes…</p>}
      {ordenes.isSuccess && filas.length === 0 && (
        <p className="rounded-[12px] border bg-card p-6 text-center text-[13px] text-muted-foreground">
          No hay órdenes que coincidan con los filtros.
        </p>
      )}
      {/* El contenedor envuelve tabla (escritorio) y tarjetas (móvil): el tour resalta el que se ve. */}
      {filas.length > 0 && (
        <div data-tour="tabla-ordenes">
          <OrdenesTable ordenes={filas} sucursales={sucursales.data ?? []} />
        </div>
      )}

      {ordenes.isSuccess && filas.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
            <span>Por página</span>
            <Select value={String(porPagina)} onValueChange={(v) => setPorPagina(Number(v))}>
              <SelectTrigger className="w-18 text-[13px]" aria-label="Órdenes por página">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {OPCIONES_ORDENES_POR_PAGINA.map((n) => (
                  <SelectItem key={n} value={String(n)}>
                    {n}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {(historial.length > 0 || ordenes.data?.has_more) && (
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={irAAnterior} disabled={historial.length === 0 || ordenes.isFetching}>
                <ChevronLeft /> Anterior
              </Button>
              <Button
                variant="outline"
                onClick={irASiguiente}
                disabled={!ordenes.data?.has_more || ordenes.isFetching}
              >
                Siguiente <ChevronRight />
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

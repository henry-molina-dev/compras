import { zodResolver } from '@hookform/resolvers/zod'
import { Plus, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { Aviso } from '@/components/shared/Aviso'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { PrecioLinea } from '@/components/shared/PrecioLinea'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useClientes, useProductos, useProveedores, useSucursales } from '@/features/catalogos/hooks'
import { useActualizarOrden, useCrearOrden, useOrden } from '@/features/ordenes/hooks'
import {
  LINEA_VACIA,
  aRequest,
  calcularTotal,
  campoDeParam,
  crearSchema,
  desdeOrden,
  erroresDePrecios,
  esVentaDirecta,
  limitesDeProveedor,
  manana,
  precioEfectivo,
  precioReferencia,
  subtotalLinea,
  type OrdenFormValues,
} from '@/features/ordenes/formulario'
import { admiteNegociar, rangoPrecio } from '@/features/ordenes/precios'
import { PASOS_ORDEN_EDITAR, PASOS_ORDEN_NUEVA } from '@/features/tours/pasos'
import { ETIQUETA_FORMATO, formatoMonto } from '@/lib/format'

/**
 * El <select> nativo oculto de Radix dispara onValueChange("") cuando el valor controlado llega antes
 * que sus opciones (p. ej. `reset()` con la orden y los catálogos ya en caché). Ningún SelectItem
 * tiene valor vacío, así que un "" nunca es una elección del usuario y se ignora.
 */
const alElegir = (onChange: (valor: string) => void) => (valor: string) => {
  if (valor !== '') onChange(valor)
}

function Campo({ etiqueta, error, children }: { etiqueta: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label>{etiqueta}</Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

export function OrdenFormPage() {
  const params = useParams()
  const editandoId = params.id ? Number(params.id) : null
  const navigate = useNavigate()

  const sucursales = useSucursales()
  const proveedores = useProveedores()
  const clientes = useClientes()
  const ordenExistente = useOrden(editandoId ?? 0)

  const schema = useMemo(() => crearSchema(sucursales.data ?? []), [sucursales.data])
  const {
    control,
    register,
    handleSubmit,
    reset,
    setError,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<OrdenFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { proveedor_id: '', sucursal_destino_id: '', cliente_id: '', fecha_necesaria: '', detalle: [{ ...LINEA_VACIA }] },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'detalle' })

  const sucursalId = useWatch({ control, name: 'sucursal_destino_id' })
  const proveedorId = useWatch({ control, name: 'proveedor_id' })
  const detalle = useWatch({ control, name: 'detalle' })
  const ventaDirecta = esVentaDirecta(sucursales.data ?? [], sucursalId)
  const sucursal = sucursales.data?.find((s) => String(s.id) === sucursalId)

  const productos = useProductos(sucursalId ? Number(sucursalId) : undefined)
  const productosDisponibles = useMemo(() => productos.data ?? [], [productos.data])
  const total = calcularTotal(detalle ?? [], productosDisponibles)

  // Negociación: los límites son del proveedor elegido y se aplican a todas sus líneas.
  const limites = useMemo(() => limitesDeProveedor(proveedores.data ?? [], proveedorId), [proveedores.data, proveedorId])
  const puedeNegociar = admiteNegociar(limites)
  const erroresPrecio = useMemo(() => erroresDePrecios(detalle ?? [], productosDisponibles, limites), [detalle, productosDisponibles, limites])
  // Líneas con el precio abierto a edición (por id del campo, que no cambia al quitar otras líneas).
  const [preciosEnEdicion, setPreciosEnEdicion] = useState<Set<string>>(new Set())
  const alternarEdicionPrecio = (idCampo: string, abierto: boolean) =>
    setPreciosEnEdicion((actual) => {
      const siguiente = new Set(actual)
      if (abierto) siguiente.add(idCampo)
      else siguiente.delete(idCampo)
      return siguiente
    })

  const crear = useCrearOrden()
  const actualizar = useActualizarOrden(editandoId ?? 0)
  const guardando = crear.isPending || actualizar.isPending
  const errorGeneral = crear.error ?? actualizar.error

  useEffect(() => {
    if (ordenExistente.data && editandoId) reset(desdeOrden(ordenExistente.data))
  }, [ordenExistente.data, editandoId, reset])

  // Al cambiar de sucursal el catálogo permitido cambia: las líneas con productos que ya no
  // aplican se descartan para no enviar una orden que el backend rechazaría.
  useEffect(() => {
    if (!sucursalId || !productos.data) return
    const permitidos = new Set(productos.data.map((p) => String(p.id)))
    const actuales = getValues('detalle')
    if (actuales.some((l) => l.producto_id && !permitidos.has(l.producto_id))) {
      setValue(
        'detalle',
        actuales.map((l) => (l.producto_id && !permitidos.has(l.producto_id) ? { ...l, producto_id: '' } : l)),
      )
      toast.info('Se limpiaron productos que no están permitidos para esta sucursal.')
    }
    if (!ventaDirecta) setValue('cliente_id', '')
  }, [sucursalId, productos.data, ventaDirecta, getValues, setValue])

  const enviar = handleSubmit(async (valores) => {
    if (erroresPrecio.some(Boolean)) {
      toast.error('Corrige los precios que están fuera del rango permitido.')
      return
    }
    const request = aRequest(valores, sucursales.data ?? [])
    const alTerminar = (id: number) => {
      toast.success(editandoId ? 'Orden actualizada.' : 'Orden creada.')
      navigate(`/ordenes/${id}`)
    }
    try {
      if (editandoId) alTerminar((await actualizar.mutateAsync(request)).id)
      else alTerminar((await crear.mutateAsync(request)).id)
    } catch (error) {
      const campo = error instanceof ApiError ? campoDeParam(error.param) : null
      if (campo && error instanceof Error) setError(campo as keyof OrdenFormValues, { message: error.message })
    }
  })

  // No se "esconde" el formulario detrás de un `if (isPending) return ...` mientras carga la orden:
  // eso retrasaría el primer montaje de los <Select> de proveedor/sucursal hasta que sus valores ya
  // fueran los reales, y el Select de Radix pierde la selección cuando su valor controlado cambia a
  // uno "real" en el mismo render en que sus opciones se registran por primera vez (dispara
  // onValueChange("") como si el valor no existiera). Montando el formulario de una vez - vacío,
  // igual que "Nueva orden" - las opciones ya están registradas para cuando `reset()` carga los
  // datos reales, evitando la carrera.
  if (editandoId) {
    if (ordenExistente.isError) return <ErrorMessage error={ordenExistente.error} />
    if (ordenExistente.data && ordenExistente.data.estado !== 'CREADA') {
      return <ErrorMessage message="Solo se puede modificar una orden en estado Creada." />
    }
  }
  const cargandoOrden = !!editandoId && ordenExistente.isPending

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-5">
      <EncabezadoPagina
        titulo={editandoId ? `Editar ${ordenExistente.data?.numero_orden ?? '…'}` : 'Nueva orden de compra'}
        pasos={editandoId ? PASOS_ORDEN_EDITAR : PASOS_ORDEN_NUEVA}
      />

      <div className="grid gap-5 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div className="flex flex-col gap-4">
          <Card className="rounded-[12px]" data-tour="proveedor-sucursal">
            <CardHeader>
              <CardTitle className="text-[15px] font-medium">Datos de la orden</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <Campo etiqueta="Proveedor" error={errors.proveedor_id?.message}>
                <Controller
                  control={control}
                  name="proveedor_id"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={alElegir(field.onChange)}>
                      <SelectTrigger className="w-full" aria-label="Proveedor" aria-invalid={!!errors.proveedor_id}>
                        <SelectValue placeholder="Selecciona un proveedor" />
                      </SelectTrigger>
                      <SelectContent>
                        {(proveedores.data ?? [])
                          .filter((p) => p.activo)
                          .map((p) => (
                            <SelectItem key={p.id} value={String(p.id)}>
                              {p.nombre}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Campo>

              <Campo etiqueta="Sucursal destino" error={errors.sucursal_destino_id?.message}>
                <Controller
                  control={control}
                  name="sucursal_destino_id"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={alElegir(field.onChange)}>
                      <SelectTrigger className="w-full" aria-label="Sucursal destino" aria-invalid={!!errors.sucursal_destino_id}>
                        <SelectValue placeholder="Selecciona una sucursal" />
                      </SelectTrigger>
                      <SelectContent>
                        {(sucursales.data ?? [])
                          .filter((s) => s.activo)
                          .map((s) => (
                            <SelectItem key={s.id} value={String(s.id)}>
                              {s.nombre}
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Campo>

              {ventaDirecta && (
                <Campo etiqueta="Cliente" error={errors.cliente_id?.message}>
                  <Controller
                    control={control}
                    name="cliente_id"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={alElegir(field.onChange)}>
                        <SelectTrigger className="w-full" aria-label="Cliente" aria-invalid={!!errors.cliente_id}>
                          <SelectValue placeholder="Selecciona un cliente" />
                        </SelectTrigger>
                        <SelectContent>
                          {(clientes.data ?? [])
                            .filter((c) => c.activo)
                            .map((c) => (
                              <SelectItem key={c.id} value={String(c.id)}>
                                {c.nombre}
                              </SelectItem>
                            ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </Campo>
              )}

              <Campo etiqueta="Fecha necesaria" error={errors.fecha_necesaria?.message}>
                <Input type="date" min={manana()} aria-label="Fecha necesaria" aria-invalid={!!errors.fecha_necesaria} {...register('fecha_necesaria')} />
              </Campo>
            </CardContent>
          </Card>

          {sucursal && (
            <Aviso tono="info" data-tour="aviso-categorias">
              Formato {ETIQUETA_FORMATO[sucursal.formato]}: solo se muestran los productos de las categorías permitidas para esta sucursal.
              {ventaDirecta && ' El cliente es obligatorio.'}
            </Aviso>
          )}
        </div>

        <Card className="h-fit rounded-[12px]">
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle className="text-[15px] font-medium">Detalle de productos</CardTitle>
            <Button type="button" variant="outline" size="sm" onClick={() => append({ ...LINEA_VACIA })} disabled={!sucursalId} data-tour="agregar-linea">
              <Plus /> Agregar producto
            </Button>
          </CardHeader>
          <CardContent className="flex flex-col gap-3" data-tour="lineas">
            {!sucursalId && <p className="text-[13px] text-muted-foreground">Selecciona primero la sucursal destino para ver los productos disponibles.</p>}
            {fields.map((campo, indice) => {
              // Los productos ya elegidos en otras líneas no se ofrecen de nuevo: evita duplicados
              // desde la UI en lugar de solo detectarlos al validar.
              const elegidosEnOtrasLineas = new Set((detalle ?? []).filter((_, i) => i !== indice).map((l) => l.producto_id))
              return (
              <div key={campo.id} className="grid grid-cols-[minmax(0,1fr)_88px_auto] items-start gap-2">
                <div className="flex flex-col gap-1">
                  <Controller
                    control={control}
                    name={`detalle.${indice}.producto_id`}
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onValueChange={alElegir((valor) => {
                          if (valor !== field.value) {
                            // Otro producto: el precio y la referencia de la línea anterior ya no aplican.
                            setValue(`detalle.${indice}.precio_unitario`, '')
                            setValue(`detalle.${indice}.precio_referencia`, '')
                            alternarEdicionPrecio(campo.id, false)
                          }
                          field.onChange(valor)
                        })}
                        disabled={!sucursalId}
                      >
                        <SelectTrigger className="w-full text-[13px]" aria-label={`Producto ${indice + 1}`} aria-invalid={!!errors.detalle?.[indice]?.producto_id}>
                          <SelectValue placeholder="Producto" />
                        </SelectTrigger>
                        <SelectContent>
                          {productosDisponibles
                            .filter((p) => p.activo && !elegidosEnOtrasLineas.has(String(p.id)))
                            .map((p) => (
                              <SelectItem key={p.id} value={String(p.id)}>
                                {p.nombre} · {formatoMonto(p.precio)}
                              </SelectItem>
                            ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.detalle?.[indice]?.producto_id && <p className="text-xs text-destructive">{errors.detalle[indice].producto_id?.message}</p>}
                  {errors.detalle?.[indice]?.cantidad && <p className="text-xs text-destructive">{errors.detalle[indice].cantidad?.message}</p>}
                  {errors.detalle?.[indice]?.precio_unitario && (
                    <p className="text-xs text-destructive">{errors.detalle[indice].precio_unitario?.message}</p>
                  )}
                  {(() => {
                    const lineaActual = detalle?.[indice] ?? LINEA_VACIA
                    const referencia = precioReferencia(lineaActual, productosDisponibles)
                    const efectivo = precioEfectivo(lineaActual, productosDisponibles)
                    return (
                      <PrecioLinea
                        numero={indice + 1}
                        referencia={referencia}
                        efectivo={lineaActual.producto_id ? efectivo : null}
                        valor={lineaActual.precio_unitario}
                        puedeNegociar={puedeNegociar}
                        editando={preciosEnEdicion.has(campo.id)}
                        error={erroresPrecio[indice] ?? null}
                        rango={limites && referencia !== null && puedeNegociar ? rangoPrecio(referencia, limites) : null}
                        onEditar={() => {
                          // Se abre con el precio vigente como valor real, para poder ajustarlo.
                          if (lineaActual.precio_unitario === '' && efectivo !== null) {
                            setValue(`detalle.${indice}.precio_unitario`, String(efectivo))
                          }
                          alternarEdicionPrecio(campo.id, true)
                        }}
                        onCambiar={(valor) => setValue(`detalle.${indice}.precio_unitario`, valor, { shouldDirty: true })}
                        onListo={() => alternarEdicionPrecio(campo.id, false)}
                        onRestablecer={() => {
                          // Línea nueva: vuelve a "sin definir" (catálogo). Línea existente: al catálogo con el que se creó.
                          setValue(`detalle.${indice}.precio_unitario`, lineaActual.precio_referencia)
                          alternarEdicionPrecio(campo.id, false)
                        }}
                      />
                    )
                  })()}
                </div>
                <Input
                  type="number"
                  min="0"
                  step="any"
                  inputMode="decimal"
                  aria-label={`Cantidad ${indice + 1}`}
                  aria-invalid={!!errors.detalle?.[indice]?.cantidad}
                  className="text-right tabular-nums"
                  {...register(`detalle.${indice}.cantidad`)}
                />
                <div className="flex items-center gap-1">
                  <span className="hidden w-20 text-right text-[13px] tabular-nums sm:inline">
                    {formatoMonto(subtotalLinea(detalle?.[indice] ?? LINEA_VACIA, productosDisponibles))}
                  </span>
                  <Button type="button" variant="ghost" size="icon" aria-label={`Quitar línea ${indice + 1}`} onClick={() => remove(indice)} disabled={fields.length === 1}>
                    <Trash2 />
                  </Button>
                </div>
              </div>
              )
            })}
            {errors.detalle?.root?.message && <p className="text-xs text-destructive">{errors.detalle.root.message}</p>}
            {errors.detalle?.message && <p className="text-xs text-destructive">{errors.detalle.message}</p>}

            <div className="mt-2 flex items-center justify-between border-t pt-4" data-tour="total">
              <span className="text-[13px] text-muted-foreground">Total</span>
              <span className="text-xl font-medium tabular-nums" aria-label="Total de la orden">
                {formatoMonto(total)}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      <ErrorMessage error={errorGeneral} />

      <div className="flex justify-end gap-2" data-tour="acciones">
        <Button type="button" variant="outline" onClick={() => navigate(-1)}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardando || cargandoOrden}>
          {guardando ? 'Guardando…' : editandoId ? 'Guardar cambios' : 'Crear orden'}
        </Button>
      </div>
    </form>
  )
}

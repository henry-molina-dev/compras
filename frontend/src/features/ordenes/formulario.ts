import { z } from 'zod'
import type { OrdenCompra, OrdenCompraRequest, Producto, Proveedor, Sucursal } from '@/api/types'
import { errorDePrecio, type LimitesNegociacion } from './precios'

export interface LineaForm {
  producto_id: string
  cantidad: string
  /**
   * Precio unitario de la línea. Vacío = sin definir: una línea nueva usa el precio de catálogo. Con
   * valor es el precio efectivo: el que negoció el usuario, o el que una línea existente ya tenía.
   */
  precio_unitario: string
  /** Precio de catálogo guardado en una línea existente (referencia de los límites); vacío en líneas nuevas. */
  precio_referencia: string
}

export interface OrdenFormValues {
  proveedor_id: string
  sucursal_destino_id: string
  cliente_id: string
  fecha_necesaria: string
  detalle: LineaForm[]
}

export const LINEA_VACIA: LineaForm = { producto_id: '', cantidad: '1', precio_unitario: '', precio_referencia: '' }

const dosDigitos = (n: number) => String(n).padStart(2, '0')

/** Fecha de mañana (yyyy-mm-dd, hora local): la fecha necesaria debe ser futura. */
export function manana(): string {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return `${d.getFullYear()}-${dosDigitos(d.getMonth() + 1)}-${dosDigitos(d.getDate())}`
}

/** Solo las sucursales de Venta Directa venden a un cliente final, por eso solo ahí aplica el campo Cliente. */
export function esVentaDirecta(sucursales: Sucursal[], sucursalId: string): boolean {
  return sucursales.find((s) => String(s.id) === sucursalId)?.formato === 'VENTA_DIRECTA'
}

/** Precio de catálogo contra el que se validan los límites: el guardado en la línea, o el vigente si es nueva. */
export function precioReferencia(linea: LineaForm, productos: Producto[]): number | null {
  if (linea.precio_referencia !== '') return Number(linea.precio_referencia)
  return productos.find((p) => String(p.id) === linea.producto_id)?.precio ?? null
}

/** Precio con el que se calcula la línea: el definido en ella, o el de referencia. */
export function precioEfectivo(linea: LineaForm, productos: Producto[]): number | null {
  if (linea.precio_unitario !== '') {
    const n = Number(linea.precio_unitario)
    return Number.isFinite(n) ? n : null
  }
  return precioReferencia(linea, productos)
}

export function subtotalLinea(linea: LineaForm, productos: Producto[]): number {
  const precio = precioEfectivo(linea, productos)
  const cantidad = Number(linea.cantidad)
  if (precio === null || !Number.isFinite(cantidad) || cantidad <= 0) return 0
  return Math.round(precio * cantidad * 100) / 100
}

/** Límites de negociación del proveedor elegido, o null si aún no hay proveedor. */
export function limitesDeProveedor(proveedores: Proveedor[], proveedorId: string): LimitesNegociacion | null {
  const proveedor = proveedores.find((p) => String(p.id) === proveedorId)
  if (!proveedor) return null
  return { descuento: proveedor.descuento_maximo_pct ?? 0, aumento: proveedor.aumento_maximo_pct ?? 0 }
}

/** Mensaje de error de precio por línea (null = correcta), con las mismas reglas que valida el backend. */
export function erroresDePrecios(detalle: LineaForm[], productos: Producto[], limites: LimitesNegociacion | null): (string | null)[] {
  return detalle.map((linea) => {
    if (linea.precio_unitario === '') return null
    const referencia = precioReferencia(linea, productos)
    if (referencia === null) return null
    return errorDePrecio(Number(linea.precio_unitario), referencia, limites)
  })
}

export function calcularTotal(detalle: LineaForm[], productos: Producto[]): number {
  return Math.round(detalle.reduce((suma, linea) => suma + subtotalLinea(linea, productos), 0) * 100) / 100
}

/** Mismas reglas que valida el backend (campos obligatorios, fecha futura, cantidad positiva, cliente en Venta Directa). */
export function crearSchema(sucursales: Sucursal[]) {
  return z
    .object({
      proveedor_id: z.string().min(1, 'Selecciona un proveedor.'),
      sucursal_destino_id: z.string().min(1, 'Selecciona la sucursal destino.'),
      cliente_id: z.string(),
      fecha_necesaria: z
        .string()
        .min(1, 'Indica la fecha necesaria.')
        .regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha no tiene un formato válido.'),
      detalle: z
        .array(
          z.object({
            producto_id: z.string().min(1, 'Selecciona un producto.'),
            cantidad: z.string().refine((v) => Number(v) > 0, 'La cantidad debe ser mayor a 0.'),
            precio_unitario: z.string(),
            precio_referencia: z.string(),
          }),
        )
        .min(1, 'Agrega al menos un producto.'),
    })
    .superRefine((valores, ctx) => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(valores.fecha_necesaria) && valores.fecha_necesaria < manana()) {
        ctx.addIssue({ code: 'custom', path: ['fecha_necesaria'], message: 'La fecha necesaria debe ser futura.' })
      }
      if (esVentaDirecta(sucursales, valores.sucursal_destino_id) && !valores.cliente_id) {
        ctx.addIssue({ code: 'custom', path: ['cliente_id'], message: 'El cliente es obligatorio para Venta Directa.' })
      }
      const vistos = new Set<string>()
      valores.detalle.forEach((linea, indice) => {
        if (!linea.producto_id) return
        if (vistos.has(linea.producto_id)) {
          ctx.addIssue({ code: 'custom', path: ['detalle', indice, 'producto_id'], message: 'Este producto ya está en la orden.' })
        }
        vistos.add(linea.producto_id)
      })
    })
}

export function aRequest(valores: OrdenFormValues, sucursales: Sucursal[]): OrdenCompraRequest {
  return {
    proveedor_id: Number(valores.proveedor_id),
    sucursal_destino_id: Number(valores.sucursal_destino_id),
    cliente_id: esVentaDirecta(sucursales, valores.sucursal_destino_id) ? Number(valores.cliente_id) : null,
    fecha_necesaria: valores.fecha_necesaria,
    detalle: valores.detalle.map((l) => ({
      producto_id: Number(l.producto_id),
      cantidad: Number(l.cantidad),
      // Sin precio definido el backend usa el de catálogo; con valor lo valida contra los límites.
      ...(l.precio_unitario !== '' ? { precio_unitario: Number(l.precio_unitario) } : {}),
    })),
  }
}

export function desdeOrden(orden: OrdenCompra): OrdenFormValues {
  return {
    proveedor_id: String(orden.proveedor_id),
    sucursal_destino_id: String(orden.sucursal_destino_id),
    cliente_id: orden.cliente_id ? String(orden.cliente_id) : '',
    fecha_necesaria: orden.fecha_necesaria,
    detalle: orden.detalle.map((l) => ({
      producto_id: String(l.producto_id),
      cantidad: String(l.cantidad),
      // La línea conserva su precio y la referencia de catálogo con la que se creó.
      precio_unitario: l.precio_unitario != null ? String(l.precio_unitario) : '',
      precio_referencia: (l.precio_catalogo ?? l.precio_unitario) != null ? String(l.precio_catalogo ?? l.precio_unitario) : '',
    })),
  }
}

/** Traduce el `param` del error de la API (p. ej. `detalle[1].producto_id`) al nombre de campo del formulario. */
export function campoDeParam(param: string | null): string | null {
  if (!param) return null
  return /^(proveedor_id|sucursal_destino_id|cliente_id|fecha_necesaria|detalle\[\d+\]\.(producto_id|cantidad|precio_unitario))$/.test(param) ? param : null
}

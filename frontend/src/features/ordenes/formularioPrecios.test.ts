import { describe, expect, it } from 'vitest'
import type { OrdenCompra, Producto, Proveedor, Sucursal } from '@/api/types'
import {
  LINEA_VACIA,
  aRequest,
  calcularTotal,
  desdeOrden,
  erroresDePrecios,
  limitesDeProveedor,
  precioEfectivo,
  precioReferencia,
  subtotalLinea,
  type LineaForm,
  type OrdenFormValues,
} from './formulario'

const productos = [{ id: 10, precio: 3.2 }, { id: 11, precio: 18.5 }] as unknown as Producto[]
const sucursales = [{ id: 1, formato: 'FERRETERIA' }] as unknown as Sucursal[]
const linea = (sobre: Partial<LineaForm> = {}): LineaForm => ({ ...LINEA_VACIA, producto_id: '10', cantidad: '10', ...sobre })

describe('precio de una línea del formulario', () => {
  it('una línea nueva usa el precio de catálogo', () => {
    expect(precioReferencia(linea(), productos)).toBe(3.2)
    expect(precioEfectivo(linea(), productos)).toBe(3.2)
    expect(subtotalLinea(linea(), productos)).toBe(32)
  })

  it('con precio negociado, el subtotal y el total usan ese precio', () => {
    const negociada = linea({ precio_unitario: '3' })

    expect(precioEfectivo(negociada, productos)).toBe(3)
    expect(subtotalLinea(negociada, productos)).toBe(30)
    expect(calcularTotal([negociada, linea({ producto_id: '11', cantidad: '2' })], productos)).toBe(67)
  })

  // Una línea existente conserva la referencia con la que se creó, aunque el catálogo haya cambiado.
  it('la referencia guardada en la línea manda sobre el catálogo actual', () => {
    const guardada = linea({ precio_unitario: '2.9', precio_referencia: '3.1' })

    expect(precioReferencia(guardada, productos)).toBe(3.1)
    expect(precioEfectivo(guardada, productos)).toBe(2.9)
  })

  it('sin producto cargado y sin precio definido no hay precio que calcular', () => {
    expect(precioEfectivo(linea({ producto_id: '99' }), productos)).toBeNull()
    expect(subtotalLinea(linea({ producto_id: '99' }), productos)).toBe(0)
  })
})

describe('límites del proveedor elegido', () => {
  const proveedores = [
    { id: 1, descuento_maximo_pct: 10, aumento_maximo_pct: 5 },
    { id: 2, descuento_maximo_pct: 0, aumento_maximo_pct: 0 },
    { id: 3 },
  ] as unknown as Proveedor[]

  it('toma el descuento y el aumento del proveedor', () => {
    expect(limitesDeProveedor(proveedores, '1')).toEqual({ descuento: 10, aumento: 5 })
    expect(limitesDeProveedor(proveedores, '2')).toEqual({ descuento: 0, aumento: 0 })
  })

  it('un proveedor sin los campos se trata como sin margen, y sin proveedor elegido no hay límites', () => {
    expect(limitesDeProveedor(proveedores, '3')).toEqual({ descuento: 0, aumento: 0 })
    expect(limitesDeProveedor(proveedores, '')).toBeNull()
  })
})

describe('errores de precio por línea', () => {
  const limites = { descuento: 10, aumento: 5 }

  it('marca solo las líneas con precio fuera de rango, en su posición', () => {
    const errores = erroresDePrecios(
      [linea({ precio_unitario: '3' }), linea({ producto_id: '11', precio_unitario: '5' }), linea()],
      productos,
      limites,
    )

    expect(errores[0]).toBeNull()
    expect(errores[1]).toBe('El precio debe estar entre 16.65 y 19.42.')
    expect(errores[2]).toBeNull()
  })

  it('valida contra la referencia guardada de la línea, no contra el catálogo de hoy', () => {
    // Catálogo actual 3.2 (rango 2.88-3.36) pero la línea se creó con 5.0 (rango 4.50-5.25).
    const errores = erroresDePrecios([linea({ precio_unitario: '4.6', precio_referencia: '5' })], productos, limites)

    expect(errores[0]).toBeNull()
  })

  it('al cambiar a un proveedor sin margen, un precio negociado deja de ser válido', () => {
    const errores = erroresDePrecios([linea({ precio_unitario: '3' })], productos, { descuento: 0, aumento: 0 })

    expect(errores[0]).toBe('Este proveedor no admite negociar precios.')
  })
})

describe('petición y edición de una orden con precio negociado', () => {
  const valores: OrdenFormValues = {
    proveedor_id: '1',
    sucursal_destino_id: '1',
    cliente_id: '',
    fecha_necesaria: '2030-01-01',
    detalle: [linea({ precio_unitario: '3' }), linea({ producto_id: '11', cantidad: '2' })],
  }

  it('manda el precio solo de las líneas que lo tienen definido', () => {
    const { detalle } = aRequest(valores, sucursales)

    expect(detalle?.[0]).toEqual({ producto_id: 10, cantidad: 10, precio_unitario: 3 })
    expect(detalle?.[1]).toEqual({ producto_id: 11, cantidad: 2 })
    expect(detalle?.[1]).not.toHaveProperty('precio_unitario')
  })

  it('al editar, cada línea conserva su precio y la referencia de catálogo con la que se creó', () => {
    const orden = {
      proveedor_id: 1,
      sucursal_destino_id: 1,
      cliente_id: null,
      fecha_necesaria: '2030-01-01',
      detalle: [{ producto_id: 10, cantidad: 10, precio_unitario: 3.04, precio_catalogo: 3.2 }],
    } as unknown as OrdenCompra

    expect(desdeOrden(orden).detalle[0]).toMatchObject({ precio_unitario: '3.04', precio_referencia: '3.2' })
  })

  it('una orden sin datos de precio no produce textos "undefined"', () => {
    const orden = {
      proveedor_id: 1,
      sucursal_destino_id: 1,
      cliente_id: null,
      fecha_necesaria: '2030-01-01',
      detalle: [{ producto_id: 10, cantidad: 10 }],
    } as unknown as OrdenCompra

    expect(desdeOrden(orden).detalle[0]).toMatchObject({ precio_unitario: '', precio_referencia: '' })
  })
})

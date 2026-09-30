import { describe, expect, it } from 'vitest'
import type { Producto, Sucursal } from '@/api/types'
import { calcularTotal, crearSchema, esVentaDirecta, manana, type OrdenFormValues } from './formulario'

const sucursales = [
  { id: 1, nombre: 'Ferretería San Salvador', formato: 'FERRETERIA', activo: true },
  { id: 3, nombre: 'Venta Directa', formato: 'VENTA_DIRECTA', activo: true },
] as Sucursal[]

const productos = [
  { id: 10, precio: 3.2 },
  { id: 11, precio: 18.5 },
] as Producto[]

const valido: OrdenFormValues = {
  proveedor_id: '1',
  sucursal_destino_id: '1',
  cliente_id: '',
  fecha_necesaria: manana(),
  detalle: [{ producto_id: '10', cantidad: '2', precio_unitario: '', precio_referencia: '' }],
}

function mensajes(valores: OrdenFormValues) {
  const resultado = crearSchema(sucursales).safeParse(valores)
  return resultado.success ? [] : resultado.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`)
}

describe('cliente según formato de la sucursal', () => {
  it('solo Venta Directa lo hace aplicable', () => {
    expect(esVentaDirecta(sucursales, '3')).toBe(true)
    expect(esVentaDirecta(sucursales, '1')).toBe(false)
    expect(esVentaDirecta(sucursales, '')).toBe(false)
  })

  it('es obligatorio en Venta Directa', () => {
    expect(mensajes({ ...valido, sucursal_destino_id: '3' })).toContain('cliente_id: El cliente es obligatorio para Venta Directa.')
    expect(mensajes({ ...valido, sucursal_destino_id: '3', cliente_id: '5' })).toEqual([])
  })

  it('no se exige en los otros formatos', () => {
    expect(mensajes(valido)).toEqual([])
  })
})

describe('total de la orden', () => {
  it('se recalcula al agregar, modificar y quitar líneas', () => {
    const una = [{ producto_id: '10', cantidad: '10', precio_unitario: '', precio_referencia: '' }]
    expect(calcularTotal(una, productos)).toBe(32)

    const dos = [...una, { producto_id: '11', cantidad: '2', precio_unitario: '', precio_referencia: '' }]
    expect(calcularTotal(dos, productos)).toBe(69)

    expect(calcularTotal([{ producto_id: '10', cantidad: '5', precio_unitario: '', precio_referencia: '' }, dos[1]], productos)).toBe(53)
    expect(calcularTotal([dos[1]], productos)).toBe(37)
  })

  it('ignora líneas incompletas o con cantidad inválida', () => {
    expect(calcularTotal([{ producto_id: '', cantidad: '3', precio_unitario: '', precio_referencia: '' }, { producto_id: '10', cantidad: '0', precio_unitario: '', precio_referencia: '' }], productos)).toBe(0)
  })
})

describe('validaciones del formulario', () => {
  it('rechaza campos vacíos', () => {
    const errores = mensajes({ ...valido, proveedor_id: '', sucursal_destino_id: '', fecha_necesaria: '', detalle: [] })
    expect(errores).toContain('proveedor_id: Selecciona un proveedor.')
    expect(errores).toContain('sucursal_destino_id: Selecciona la sucursal destino.')
    expect(errores).toContain('fecha_necesaria: Indica la fecha necesaria.')
    expect(errores).toContain('detalle: Agrega al menos un producto.')
  })

  it('rechaza una fecha que no es futura', () => {
    expect(mensajes({ ...valido, fecha_necesaria: '2020-01-01' })).toContain('fecha_necesaria: La fecha necesaria debe ser futura.')
  })

  it('rechaza cantidades no positivas y líneas sin producto', () => {
    const errores = mensajes({ ...valido, detalle: [{ producto_id: '', cantidad: '0', precio_unitario: '', precio_referencia: '' }] })
    expect(errores).toContain('detalle.0.producto_id: Selecciona un producto.')
    expect(errores).toContain('detalle.0.cantidad: La cantidad debe ser mayor a 0.')
  })
})

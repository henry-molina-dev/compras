import { describe, expect, it } from 'vitest'
import { estadoDeAccion } from './acciones'

describe('acciones sobre una orden según estado y rol', () => {
  it('el gerente de sucursal no ve aprobar ni anular', () => {
    expect(estadoDeAccion('aprobar', 'CREADA', 'GERENTE_SUCURSAL').visible).toBe(false)
    expect(estadoDeAccion('anular', 'APROBADA', 'GERENTE_SUCURSAL').visible).toBe(false)
  })

  it('el comprador no ve cerrar', () => {
    expect(estadoDeAccion('cerrar', 'APROBADA', 'COMPRADOR').visible).toBe(false)
  })

  it('aprobar solo se habilita en una orden creada', () => {
    expect(estadoDeAccion('aprobar', 'CREADA', 'COMPRADOR').habilitada).toBe(true)
    expect(estadoDeAccion('aprobar', 'APROBADA', 'COMPRADOR').habilitada).toBe(false)
  })

  it('anular se habilita en creada o aprobada, nunca en cerrada ni anulada', () => {
    expect(estadoDeAccion('anular', 'CREADA', 'ADMIN').habilitada).toBe(true)
    expect(estadoDeAccion('anular', 'APROBADA', 'ADMIN').habilitada).toBe(true)
    expect(estadoDeAccion('anular', 'CERRADA', 'ADMIN').habilitada).toBe(false)
    expect(estadoDeAccion('anular', 'ANULADA', 'ADMIN').habilitada).toBe(false)
  })

  it('cerrar solo se habilita en una orden aprobada, para gerente o administrador', () => {
    expect(estadoDeAccion('cerrar', 'APROBADA', 'GERENTE_SUCURSAL').habilitada).toBe(true)
    expect(estadoDeAccion('cerrar', 'APROBADA', 'ADMIN').habilitada).toBe(true)
    expect(estadoDeAccion('cerrar', 'CREADA', 'GERENTE_SUCURSAL').habilitada).toBe(false)
  })

  it('editar solo se habilita en una orden creada', () => {
    expect(estadoDeAccion('editar', 'CREADA', 'COMPRADOR').habilitada).toBe(true)
    expect(estadoDeAccion('editar', 'APROBADA', 'COMPRADOR').habilitada).toBe(false)
  })
})

import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Rol } from '@/api/types'
import { pasosDetalle } from '@/features/tours/pasos'
import { formatoMonto } from '@/lib/format'
import { renderPagina, sesionComo } from '@/test/render'
import { anclasFaltantes } from '@/test/tour'
import { OrdenDetallePage } from './OrdenDetallePage'

const estado = vi.hoisted(() => ({ rol: 'COMPRADOR' as string, orden: 'CREADA' as string, eventos: [] as unknown[], negociada: false }))

vi.mock('@/features/auth/AuthContext', () => ({ useAuth: () => sesionComo(estado.rol as Rol) }))
vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  const lista = (data: unknown[]) => ({ object: 'list', data, has_more: false })
  return {
    ...original,
    api: {
      get: vi.fn(async (ruta: string) => {
        if (ruta === '/ordenes/5')
          return {
            id: 5,
            numero_orden: 'OC-2026-000005',
            estado: estado.orden,
            proveedor_id: 1,
            proveedor: { nombre: 'Proveedor Uno' },
            sucursal_destino_id: 1,
            cliente_id: null,
            fecha_necesaria: '2026-12-01',
            total: 32,
            conforme: null,
            detalle: [{ id: 1, producto_id: 10, producto_codigo: 'T-1', producto_nombre: 'Tornillo', cantidad: 10, precio_unitario: estado.negociada ? 3.04 : 3.2, precio_catalogo: 3.2, subtotal: 32 }],
          }
        if (ruta === '/ordenes/5/linea-tiempo') return lista(estado.eventos)
        if (ruta === '/sucursales') return lista([{ id: 1, nombre: 'Ferretería San Salvador', formato: 'FERRETERIA', activo: true }])
        return lista([])
      }),
    },
  }
})

function renderizar() {
  return renderPagina(<OrdenDetallePage />, { ruta: '/ordenes/5', patron: '/ordenes/:id' })
}

describe('acciones del detalle de una orden', () => {
  beforeEach(() => {
    estado.eventos = []
  })

  it('el comprador ve aprobar y anular habilitados sobre una orden creada, y no ve cerrar', async () => {
    estado.rol = 'COMPRADOR'
    estado.orden = 'CREADA'
    renderizar()

    expect(await screen.findByRole('button', { name: /Aprobar/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Anular/ })).toBeEnabled()
    expect(screen.queryByRole('button', { name: /Cerrar orden|Confirmar recepción/ })).not.toBeInTheDocument()
  })

  it('sobre una orden aprobada el comprador ya no puede aprobar', async () => {
    estado.rol = 'COMPRADOR'
    estado.orden = 'APROBADA'
    renderizar()

    expect(await screen.findByRole('button', { name: /Aprobar/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Anular/ })).toBeEnabled()
  })

  it('el gerente de sucursal no ve aprobar ni anular y puede confirmar la recepción de una orden aprobada', async () => {
    estado.rol = 'GERENTE_SUCURSAL'
    estado.orden = 'APROBADA'
    renderizar()

    expect(await screen.findByRole('button', { name: /Confirmar recepción/ })).toBeEnabled()
    expect(screen.queryByRole('button', { name: /Aprobar/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Anular/ })).not.toBeInTheDocument()
  })

  it('una orden cerrada deja todas las acciones deshabilitadas', async () => {
    estado.rol = 'ADMIN'
    estado.orden = 'CERRADA'
    renderizar()

    expect(await screen.findByRole('button', { name: /Aprobar/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Anular/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Cerrar orden/ })).toBeDisabled()
  })
})

describe('tour del detalle de una orden', () => {
  beforeEach(() => {
    estado.eventos = []
  })

  it.each([
    ['COMPRADOR', 'CREADA'],
    ['ADMIN', 'APROBADA'],
    ['GERENTE_SUCURSAL', 'APROBADA'],
    ['COMPRADOR', 'CERRADA'],
  ] as const)('para %s con la orden %s todos los pasos apuntan a elementos de la pantalla', async (rol, orden) => {
    estado.rol = rol
    estado.orden = orden
    renderizar()
    await screen.findByRole('button', { name: /Tour guiado/ })

    expect(anclasFaltantes(pasosDetalle(rol))).toEqual([])
  })

  it('el gerente ve un paso de acciones propio: confirmar la recepción, no aprobar ni anular', () => {
    const acciones = (rol: 'COMPRADOR' | 'GERENTE_SUCURSAL') => pasosDetalle(rol).find((p) => p.ancla === 'acciones-orden')

    expect(acciones('GERENTE_SUCURSAL')?.contenido).toContain('Confirmar recepción')
    expect(acciones('GERENTE_SUCURSAL')?.contenido).not.toMatch(/Aprobar|Anular/)
    expect(acciones('COMPRADOR')?.contenido).toMatch(/Aprobar/)
  })
})

describe('precio negociado en el detalle de una orden', () => {
  beforeEach(() => {
    estado.rol = 'COMPRADOR'
    estado.orden = 'CREADA'
    estado.eventos = []
  })
  afterEach(() => {
    estado.negociada = false
  })

  it('una línea negociada muestra el precio pactado, el de catálogo y la variación', async () => {
    estado.negociada = true
    renderizar()

    expect(await screen.findByText(new RegExp(`Negociado · catálogo ${formatoMonto(3.2)}`))).toHaveTextContent('-5.0%')
  })

  it('una línea al precio de catálogo no muestra ninguna variación', async () => {
    renderizar()

    await screen.findByRole('button', { name: /Aprobar/ })
    expect(screen.queryByText(/Negociado/)).not.toBeInTheDocument()
  })
})

describe('aviso de entrega reportada por el proveedor', () => {
  const entrega = [{ origen: 'proveedor', tipo: 'ENTREGADA', fecha: '2026-09-30T00:20:00Z', detalle: null }]

  beforeEach(() => {
    estado.rol = 'COMPRADOR'
    estado.orden = 'APROBADA'
    estado.eventos = []
  })

  // Regresion: este aviso se veia naranja (el color de la marca), como una alarma, aunque es una
  // buena noticia; ahora es un mensaje de exito.
  it('es un mensaje de éxito (verde), no naranja ni rojo', async () => {
    estado.eventos = entrega
    renderizar()

    const aviso = await screen.findByRole('status')
    expect(aviso).toHaveTextContent('El proveedor reportó la entrega')
    expect(aviso.className).toContain('bg-success-soft')
    expect(aviso.className).not.toMatch(/destructive|primary|accent/)
  })

  it('no aparece si el proveedor no reportó la entrega', async () => {
    renderizar()

    await screen.findByRole('button', { name: /Aprobar/ })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('no aparece cuando la orden ya está cerrada', async () => {
    estado.eventos = entrega
    estado.orden = 'CERRADA'
    renderizar()

    await screen.findByRole('button', { name: /Aprobar/ })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})

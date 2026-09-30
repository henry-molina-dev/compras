import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Rol } from '@/api/types'
import { pasosLista } from '@/features/tours/pasos'
import { renderPagina, sesionComo } from '@/test/render'
import { anclasFaltantes } from '@/test/tour'
import { OrdenesListPage } from './OrdenesListPage'

const { obtenerOrdenes } = vi.hoisted(() => {
  const PAGINA_1 = [
    { id: 1, numero_orden: 'OC-2026-000001', estado: 'CREADA', proveedor_id: 1, proveedor: { nombre: 'Proveedor Uno' }, sucursal_destino_id: 1, total: 100, fecha_necesaria: '2026-12-01' },
    { id: 2, numero_orden: 'OC-2026-000002', estado: 'CREADA', proveedor_id: 1, proveedor: { nombre: 'Proveedor Uno' }, sucursal_destino_id: 1, total: 100, fecha_necesaria: '2026-12-01' },
  ]
  const PAGINA_2 = [
    { id: 3, numero_orden: 'OC-2026-000003', estado: 'CREADA', proveedor_id: 1, proveedor: { nombre: 'Proveedor Uno' }, sucursal_destino_id: 1, total: 100, fecha_necesaria: '2026-12-01' },
  ]
  const CLIENTES = [{ id: 5, nombre: 'Cliente Uno', tipo: 'INDUSTRIAL', activo: true }]
  return {
    obtenerOrdenes: vi.fn(async (ruta: string, query?: Record<string, unknown>) => {
      if (ruta === '/ordenes') {
        const cursor = query?.starting_after as string | undefined
        if (!cursor) return { object: 'list', data: PAGINA_1, has_more: true }
        if (cursor === 'OC-2026-000002') return { object: 'list', data: PAGINA_2, has_more: false }
        return { object: 'list', data: [], has_more: false }
      }
      if (ruta === '/clientes') return { object: 'list', data: CLIENTES, has_more: false }
      return { object: 'list', data: [], has_more: false }
    }),
  }
})

const sesionActual = vi.hoisted(() => ({ rol: 'COMPRADOR' as string }))

vi.mock('@/features/auth/AuthContext', () => ({ useAuth: () => sesionComo(sesionActual.rol as Rol) }))
vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return { ...original, api: { ...original.api, get: obtenerOrdenes } }
})

function renderizar() {
  return renderPagina(<OrdenesListPage />, { ruta: '/ordenes', patron: '/ordenes' })
}

async function elegir(usuario: ReturnType<typeof userEvent.setup>, etiqueta: string, opcion: string) {
  await usuario.click(screen.getByRole('combobox', { name: etiqueta }))
  await usuario.click(await screen.findByRole('option', { name: opcion }))
}

describe('paginación de la lista de órdenes', () => {
  it('navega a la siguiente página y de regreso con Anterior/Siguiente', async () => {
    const usuario = userEvent.setup()
    renderizar()

    expect((await screen.findAllByText('OC-2026-000001')).length).toBeGreaterThan(0)
    const siguiente = screen.getByRole('button', { name: /Siguiente/ })
    const anterior = screen.getByRole('button', { name: /Anterior/ })
    expect(siguiente).toBeEnabled()
    expect(anterior).toBeDisabled()

    await usuario.click(siguiente)

    await waitFor(() => expect(screen.getAllByText('OC-2026-000003').length).toBeGreaterThan(0))
    expect(screen.queryByText('OC-2026-000001')).not.toBeInTheDocument()
    expect(obtenerOrdenes).toHaveBeenCalledWith(
      '/ordenes',
      expect.objectContaining({ starting_after: 'OC-2026-000002' }),
    )
    expect(screen.getByRole('button', { name: /Siguiente/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Anterior/ })).toBeEnabled()

    await usuario.click(screen.getByRole('button', { name: /Anterior/ }))

    await waitFor(() => expect(screen.getAllByText('OC-2026-000001').length).toBeGreaterThan(0))
    expect(screen.getByRole('button', { name: /Siguiente/ })).toBeEnabled()
    expect(screen.getByRole('button', { name: /Anterior/ })).toBeDisabled()
  })

  it('cambiar las órdenes por página vuelve a la primera página con el nuevo límite', async () => {
    const usuario = userEvent.setup()
    renderizar()

    expect((await screen.findAllByText('OC-2026-000001')).length).toBeGreaterThan(0)
    obtenerOrdenes.mockClear()

    await elegir(usuario, 'Órdenes por página', '50')

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith(
        '/ordenes',
        expect.objectContaining({ limit: 50, starting_after: undefined }),
      ),
    )
    // Vuelve a la primera página: Anterior no debe quedar habilitado tras el cambio.
    expect(screen.getByRole('button', { name: /Anterior/ })).toBeDisabled()
  })
})

describe('filtros de la lista de órdenes', () => {
  it('aparecen en el mismo orden que las columnas de la tabla', async () => {
    renderizar()
    await screen.findAllByText('OC-2026-000001')

    expect(screen.getByRole('textbox', { name: 'Número de orden' })).toBeInTheDocument()
    const etiquetasFiltros = screen
      .getAllByRole('combobox')
      .map((el) => el.getAttribute('aria-label'))
      .slice(0, 4)
    expect(etiquetasFiltros).toEqual(['Proveedor', 'Sucursal', 'Cliente', 'Estado'])
  })

  it('escribir un número de orden filtra tras una pausa (debounce)', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    await usuario.type(screen.getByRole('textbox', { name: 'Número de orden' }), '123')

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith('/ordenes', expect.objectContaining({ numero_orden: '123' })),
    )
  })

  it('filtrar por cliente envía cliente_id a la API', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    await elegir(usuario, 'Cliente', 'Cliente Uno')

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith('/ordenes', expect.objectContaining({ cliente_id: 5 })),
    )
  })

  it('filtra solo por fecha desde', async () => {
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    fireEvent.change(screen.getByLabelText('Fecha necesaria desde'), { target: { value: '2026-01-10' } })

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith(
        '/ordenes',
        expect.objectContaining({ fecha_desde: '2026-01-10', fecha_hasta: undefined }),
      ),
    )
  })

  it('filtra solo por fecha hasta', async () => {
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    fireEvent.change(screen.getByLabelText('Fecha necesaria hasta'), { target: { value: '2026-01-20' } })

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith(
        '/ordenes',
        expect.objectContaining({ fecha_desde: undefined, fecha_hasta: '2026-01-20' }),
      ),
    )
  })

  it('filtra por ambas fechas cuando el rango es válido', async () => {
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    fireEvent.change(screen.getByLabelText('Fecha necesaria desde'), { target: { value: '2026-01-10' } })
    fireEvent.change(screen.getByLabelText('Fecha necesaria hasta'), { target: { value: '2026-01-20' } })

    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith(
        '/ordenes',
        expect.objectContaining({ fecha_desde: '2026-01-10', fecha_hasta: '2026-01-20' }),
      ),
    )
  })

  it('un rango invertido muestra un error y no se envía a la API', async () => {
    renderizar()
    await screen.findAllByText('OC-2026-000001')
    obtenerOrdenes.mockClear()

    fireEvent.change(screen.getByLabelText('Fecha necesaria desde'), { target: { value: '2026-01-20' } })
    fireEvent.change(screen.getByLabelText('Fecha necesaria hasta'), { target: { value: '2026-01-10' } })

    expect(await screen.findByText(/no puede ser posterior/)).toBeInTheDocument()
    expect(screen.getByLabelText('Fecha necesaria desde')).toHaveAttribute('aria-invalid', 'true')
    await waitFor(() =>
      expect(obtenerOrdenes).toHaveBeenCalledWith(
        '/ordenes',
        expect.objectContaining({ fecha_desde: undefined, fecha_hasta: undefined }),
      ),
    )
  })
})

describe('tour de la lista de órdenes', () => {
  afterEach(() => {
    sesionActual.rol = 'COMPRADOR'
  })

  it.each(['COMPRADOR', 'ADMIN'] as const)('para %s todos los pasos apuntan a elementos visibles', async (rol) => {
    sesionActual.rol = rol
    renderizar()
    await screen.findAllByText('OC-2026-000001')

    expect(screen.getByRole('button', { name: /Tour guiado/ })).toBeInTheDocument()
    expect(pasosLista(rol).map((p) => p.ancla)).toEqual(['filtros', 'tabla-ordenes', 'exportar', 'crear-importar'])
    expect(anclasFaltantes(pasosLista(rol))).toEqual([])
  })

  // El gerente no ve filtros ni Importar/Nueva orden: su tour no puede mencionarlos.
  it('para el gerente de sucursal el tour se limita a lo que ve', async () => {
    sesionActual.rol = 'GERENTE_SUCURSAL'
    renderizar()
    await screen.findAllByText('OC-2026-000001')

    expect(screen.getByRole('heading', { name: 'Órdenes por recibir' })).toBeInTheDocument()
    expect(pasosLista('GERENTE_SUCURSAL').map((p) => p.ancla)).toEqual(['tabla-ordenes', 'exportar'])
    expect(anclasFaltantes(pasosLista('GERENTE_SUCURSAL'))).toEqual([])
  })
})

import { QueryClient } from '@tanstack/react-query'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invalidarCatalogo } from '@/features/catalogos/hooks'
import { renderPagina } from '@/test/render'
import { SucursalesAdminPage } from './catalogos'

const mock = vi.hoisted(() => ({
  put: [] as { ruta: string; cuerpo: unknown }[],
  rechazarPutCon: null as null | { status: number; body: { type: string; code: string; message: string; param: string | null } },
}))

vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return {
    ...original,
    api: {
      get: vi.fn(async (ruta: string) => {
        if (ruta === '/sucursales') {
          return {
            object: 'list',
            has_more: false,
            data: [
              { object: 'sucursal', id: 1, nombre: 'Sucursal Centro', formato: 'FERRETERIA', activo: true },
              { object: 'sucursal', id: 2, nombre: 'Venta Directa Sur', formato: 'VENTA_DIRECTA', activo: false },
            ],
          }
        }
        throw new Error(`Ruta no simulada: ${ruta}`)
      }),
      put: vi.fn(async (ruta: string, cuerpo: unknown) => {
        if (mock.rechazarPutCon) throw new original.ApiError(mock.rechazarPutCon.status, mock.rechazarPutCon.body)
        mock.put.push({ ruta, cuerpo })
        return {}
      }),
    },
  }
})

function renderizar() {
  return renderPagina(<SucursalesAdminPage />, { ruta: '/administracion/sucursales', patron: '/administracion/sucursales' })
}

// La pantalla renderiza tabla (escritorio) y tarjetas (móvil); en jsdom ambas están en el DOM. Se usan los botones de la tabla.
async function abrirEdicion(usuario: ReturnType<typeof userEvent.setup>, nombre: string) {
  await waitFor(() => expect(screen.getAllByText(nombre).length).toBeGreaterThan(0))
  const fila = screen.getAllByText(nombre)[0].closest('tr') as HTMLElement
  await usuario.click(within(fila).getByRole('button', { name: /Editar/ }))
}

describe('administración de sucursales', () => {
  beforeEach(() => {
    mock.put.length = 0
    mock.rechazarPutCon = null
  })

  it('lista las sucursales con su formato legible y su estado', async () => {
    renderizar()

    await waitFor(() => expect(screen.getAllByText('Sucursal Centro').length).toBeGreaterThan(0))
    expect(screen.getAllByText('Ferretería').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Venta Directa').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Inactivo').length).toBeGreaterThan(0)
  })

  it('editar manda nombre, formato y activo al PUT de esa sucursal', async () => {
    const usuario = userEvent.setup()
    renderizar()

    await abrirEdicion(usuario, 'Sucursal Centro')
    const campo = await screen.findByLabelText('Nombre')
    await usuario.clear(campo)
    await usuario.type(campo, 'Sucursal Centro II')
    await usuario.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(mock.put).toHaveLength(1))
    expect(mock.put[0]).toEqual({
      ruta: '/sucursales/1',
      cuerpo: { nombre: 'Sucursal Centro II', formato: 'FERRETERIA', activo: true },
    })
  })

  it('si el backend bloquea el cambio de formato (409), muestra su mensaje y deja abierto el formulario', async () => {
    mock.rechazarPutCon = {
      status: 409,
      body: {
        type: 'state_conflict_error',
        code: 'formato_bloqueado',
        message: 'No se puede cambiar el formato de una sucursal que ya tiene ordenes; desactivala y crea otra.',
        param: 'formato',
      },
    }
    const usuario = userEvent.setup()
    renderizar()

    await abrirEdicion(usuario, 'Sucursal Centro')
    await screen.findByLabelText('Nombre')
    await usuario.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText(/ya tiene ordenes; desactivala y crea otra/)).toBeInTheDocument()
    expect(screen.getByLabelText('Nombre')).toBeInTheDocument()
  })
})

describe('caché de catálogos', () => {
  // Regresion: la pantalla de administración refrescaba solo su propia clave ('/sucursales'), pero
  // los selectores del resto de la app usan ['sucursales'] con 5 minutos de vigencia, así que un
  // cambio en administración no se veía en "Nueva orden" ni en "Usuarios" hasta que expiraba.
  it('invalida tanto la clave de administración como la que usan los demás selectores', () => {
    const queryClient = new QueryClient()
    const invalidar = vi.spyOn(queryClient, 'invalidateQueries')

    invalidarCatalogo(queryClient, '/sucursales')

    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['/sucursales'] })
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['sucursales'] })
  })
})

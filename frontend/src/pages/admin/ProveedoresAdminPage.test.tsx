import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderPagina } from '@/test/render'
import { ProveedoresAdminPage } from './catalogos'

const mock = vi.hoisted(() => ({ put: [] as { ruta: string; cuerpo: unknown }[] }))

vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return {
    ...original,
    api: {
      get: vi.fn(async (ruta: string) => {
        if (ruta === '/proveedores') {
          return {
            object: 'list',
            has_more: false,
            data: [
              { id: 1, nombre: 'Ferretera del Norte', email: 'norte@example.com', activo: true, webhook_api_key: null, descuento_maximo_pct: 10, aumento_maximo_pct: 5 },
              { id: 3, nombre: 'Suministros', email: 'sum@example.com', activo: true, webhook_api_key: null, descuento_maximo_pct: 0, aumento_maximo_pct: 0 },
            ],
          }
        }
        throw new Error(`Ruta no simulada: ${ruta}`)
      }),
      put: vi.fn(async (ruta: string, cuerpo: unknown) => {
        mock.put.push({ ruta, cuerpo })
        return {}
      }),
    },
  }
})

function renderizar() {
  return renderPagina(<ProveedoresAdminPage />, { ruta: '/administracion/proveedores', patron: '/administracion/proveedores' })
}

async function abrirEdicion(usuario: ReturnType<typeof userEvent.setup>, nombre: string) {
  await waitFor(() => expect(screen.getAllByText(nombre).length).toBeGreaterThan(0))
  const fila = screen.getAllByText(nombre)[0].closest('tr') as HTMLElement
  await usuario.click(within(fila).getByRole('button', { name: /Editar/ }))
}

describe('límites de negociación en la administración de proveedores', () => {
  beforeEach(() => {
    mock.put.length = 0
  })

  it('la lista muestra cuánto se puede negociar con cada proveedor', async () => {
    renderizar()

    await waitFor(() => expect(screen.getAllByText('-10% / +5%').length).toBeGreaterThan(0))
    expect(screen.getAllByText('No admite').length).toBeGreaterThan(0)
  })

  it('el formulario de edición carga los límites actuales del proveedor', async () => {
    const usuario = userEvent.setup()
    renderizar()

    await abrirEdicion(usuario, 'Ferretera del Norte')

    expect(await screen.findByLabelText('Descuento máximo negociable (%)')).toHaveValue(10)
    expect(screen.getByLabelText('Aumento máximo aceptable (%)')).toHaveValue(5)
  })

  it('al guardar manda los límites como números', async () => {
    const usuario = userEvent.setup()
    renderizar()

    await abrirEdicion(usuario, 'Suministros')
    const descuento = await screen.findByLabelText('Descuento máximo negociable (%)')
    await usuario.clear(descuento)
    await usuario.type(descuento, '7.5')
    await usuario.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(mock.put).toHaveLength(1))
    expect(mock.put[0].ruta).toBe('/proveedores/3')
    expect(mock.put[0].cuerpo).toMatchObject({ descuento_maximo_pct: 7.5, aumento_maximo_pct: 0 })
  })

  it.each([
    ['Descuento máximo negociable (%)', '100', /entre 0 y 99.99/],
    ['Descuento máximo negociable (%)', '-1', /entre 0 y 99.99/],
    ['Aumento máximo aceptable (%)', '100.5', /entre 0 y 100/],
    ['Aumento máximo aceptable (%)', '5.555', /máximo dos decimales/],
  ])('rechaza %s = %s sin llamar al backend', async (campo, valor, mensaje) => {
    const usuario = userEvent.setup()
    renderizar()

    await abrirEdicion(usuario, 'Ferretera del Norte')
    const input = await screen.findByLabelText(campo)
    await usuario.clear(input)
    await usuario.type(input, valor)
    await usuario.click(screen.getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText(mensaje)).toBeInTheDocument()
    expect(mock.put).toHaveLength(0)
  })
})

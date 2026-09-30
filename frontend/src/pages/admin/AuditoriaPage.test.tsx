import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderPagina } from '@/test/render'
import { AuditoriaPage } from './AuditoriaPage'

const consultas = vi.hoisted(() => ({ rutas: [] as string[] }))

vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return {
    ...original,
    api: {
      get: vi.fn(async (ruta: string) => {
        consultas.rutas.push(ruta)
        if (ruta === '/auditoria/ordenes/OC-2026-000123') {
          return {
            object: 'list',
            data: [{ id: 1, estado_anterior: null, estado_nuevo: 'CREADA', usuario_id: 3, observacion: null, fecha: '2026-10-01T10:00:00Z' }],
            has_more: false,
          }
        }
        throw new Error(`Ruta no simulada: ${ruta}`)
      }),
    },
  }
})

function renderizar() {
  return renderPagina(<AuditoriaPage />, { ruta: '/administracion/auditoria', patron: '/administracion/auditoria' })
}

describe('página de auditoría', () => {
  // Regresion: el campo pedia el id interno de base de datos, que un ADMIN no tiene forma de
  // conocer - el unico identificador de una orden visible en el resto de la app es numero_orden.
  it('busca por número de orden (no por id interno) y lo manda tal cual al backend', async () => {
    const usuario = userEvent.setup()
    renderizar()

    expect(screen.queryByLabelText('ID de la orden')).not.toBeInTheDocument()
    const campo = screen.getByLabelText('Número de orden')

    await usuario.type(campo, 'OC-2026-000123')
    await usuario.click(screen.getByRole('button', { name: 'Consultar' }))

    await waitFor(() => expect(screen.getByText('Creada')).toBeInTheDocument())
    expect(consultas.rutas).toContain('/auditoria/ordenes/OC-2026-000123')
  })
})

import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderPagina } from '@/test/render'
import { ClaveWebhook } from './ClaveWebhook'

const CLAVE_VIEJA = 'whsk_viejaviejaviejaviejaviejavieja12'
const CLAVE_NUEVA = 'whsk_nuevanuevanuevanuevanuevanueva34'

const llamadas = vi.hoisted(() => ({ patch: [] as string[] }))

vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return {
    ...original,
    api: {
      patch: vi.fn(async (ruta: string) => {
        llamadas.patch.push(ruta)
        return { id: 7, webhook_api_key: 'whsk_nuevanuevanuevanuevanuevanueva34' }
      }),
    },
  }
})

function renderizar(props: { valor: string | null; proveedorId?: number }) {
  return renderPagina(<ClaveWebhook {...props} />, { ruta: '/', patron: '/' })
}

describe('clave de webhook', () => {
  beforeEach(() => {
    llamadas.patch.length = 0
  })

  it('la oculta por defecto y la muestra u oculta a pedido', async () => {
    const usuario = userEvent.setup()
    renderizar({ valor: CLAVE_VIEJA })

    expect(screen.queryByText(CLAVE_VIEJA)).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Mostrar clave' }))
    expect(screen.getByText(CLAVE_VIEJA)).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Ocultar clave' }))
    expect(screen.queryByText(CLAVE_VIEJA)).not.toBeInTheDocument()
  })

  it('copia la clave completa aunque esté oculta', async () => {
    const usuario = userEvent.setup()
    renderizar({ valor: CLAVE_VIEJA })

    await usuario.click(screen.getByRole('button', { name: 'Copiar clave' }))

    await waitFor(async () => expect(await navigator.clipboard.readText()).toBe(CLAVE_VIEJA))
  })

  it('sin clave (COMPRADOR o proveedor sin webhook) muestra un guion y ningún botón', () => {
    renderizar({ valor: null })

    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('no ofrece regenerar en la lista (sin proveedorId)', () => {
    renderizar({ valor: CLAVE_VIEJA })

    expect(screen.queryByRole('button', { name: /Regenerar/ })).not.toBeInTheDocument()
  })

  it('regenerar pide confirmación, llama al backend y muestra la clave nueva', async () => {
    const usuario = userEvent.setup()
    renderizar({ valor: CLAVE_VIEJA, proveedorId: 7 })

    await usuario.click(screen.getByRole('button', { name: /Regenerar clave/ }))
    expect(llamadas.patch).toEqual([]) // todavia no: falta confirmar
    expect(screen.getByText(/dejará de funcionar de inmediato/)).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Sí, regenerar' }))

    await waitFor(() => expect(screen.getByText(CLAVE_NUEVA)).toBeInTheDocument())
    expect(llamadas.patch).toEqual(['/proveedores/7/regenerar-clave-webhook'])
    expect(screen.queryByText(CLAVE_VIEJA)).not.toBeInTheDocument()
  })

  it('cancelar la confirmación no toca el backend', async () => {
    const usuario = userEvent.setup()
    renderizar({ valor: CLAVE_VIEJA, proveedorId: 7 })

    await usuario.click(screen.getByRole('button', { name: /Regenerar clave/ }))
    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(llamadas.patch).toEqual([])
    expect(screen.getByRole('button', { name: /Regenerar clave/ })).toBeInTheDocument()
  })
})

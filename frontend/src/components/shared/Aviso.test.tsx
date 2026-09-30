import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { toast } from 'sonner'
import { Toaster } from '@/components/ui/sonner'
import { Aviso, type TonoAviso } from './Aviso'

const CLASE_POR_TONO: Record<TonoAviso, string> = {
  info: 'bg-info-soft',
  exito: 'bg-success-soft',
  advertencia: 'bg-warning-soft',
  error: 'text-destructive',
}

describe('aviso', () => {
  it.each(Object.entries(CLASE_POR_TONO))('el tono %s usa el color de su significado', (tono, clase) => {
    render(<Aviso tono={tono as TonoAviso}>Mensaje de prueba</Aviso>)

    expect(screen.getByText('Mensaje de prueba').closest('[data-slot="alert"]')?.className).toContain(clase)
  })

  // Solo el error es "rojo"; el resto no debe parecerlo ni usar el naranja de la marca.
  it.each(['info', 'exito', 'advertencia'] as const)('el tono %s no usa colores de error ni de la marca', (tono) => {
    render(<Aviso tono={tono}>Mensaje de prueba</Aviso>)

    const clases = screen.getByText('Mensaje de prueba').closest('[data-slot="alert"]')?.className ?? ''
    expect(clases).not.toMatch(/destructive|primary|accent/)
  })

  it('informar, confirmar y advertir se anuncian como estado; un error, como alerta', () => {
    render(
      <>
        <Aviso tono="info">uno</Aviso>
        <Aviso tono="exito">dos</Aviso>
        <Aviso tono="advertencia">tres</Aviso>
        <Aviso tono="error">cuatro</Aviso>
      </>,
    )

    expect(screen.getAllByRole('status')).toHaveLength(3)
    expect(screen.getAllByRole('alert')).toHaveLength(1)
    expect(screen.getByRole('alert')).toHaveTextContent('cuatro')
  })

  it('acepta atributos extra, como los que usa el tour guiado', () => {
    render(
      <Aviso tono="info" data-tour="aviso-categorias">
        Mensaje
      </Aviso>,
    )

    expect(screen.getByText('Mensaje').closest('[data-tour="aviso-categorias"]')).not.toBeNull()
  })
})

describe('notificaciones (toasts)', () => {
  it('se pueden cerrar con el botón de la notificación', async () => {
    const usuario = userEvent.setup()
    render(<Toaster />)

    act(() => {
      toast.success('Orden guardada', { duration: Infinity })
    })
    expect(await screen.findByText('Orden guardada')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Cerrar notificación' }))

    await waitFor(() => expect(screen.queryByText('Orden guardada')).not.toBeInTheDocument())
  })

  it('cada tipo usa colores propios (richColors) y no un único estilo neutro', async () => {
    render(<Toaster />)

    act(() => {
      toast.success('ok', { duration: Infinity })
      toast.error('mal', { duration: Infinity })
    })

    const exito = (await screen.findByText('ok')).closest('[data-sonner-toast]')
    const error = (await screen.findByText('mal')).closest('[data-sonner-toast]')
    expect(exito).toHaveAttribute('data-type', 'success')
    expect(error).toHaveAttribute('data-type', 'error')
    expect(exito).toHaveAttribute('data-rich-colors', 'true')
    expect(error).toHaveAttribute('data-rich-colors', 'true')
  })
})

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { PasoTour } from '@/features/tours/pasos'
import { TourGuiado } from './TourGuiado'

const PASOS: PasoTour[] = [
  { ancla: 'uno', titulo: 'Primer paso', contenido: 'Contenido del primer paso.' },
  { ancla: 'dos', titulo: 'Segundo paso', contenido: 'Contenido del segundo paso.' },
]

function pantalla() {
  return (
    <>
      <TourGuiado pasos={PASOS} />
      <div data-tour="uno">Elemento uno</div>
      <div data-tour="dos">Elemento dos</div>
    </>
  )
}

describe('tour guiado', () => {
  // Decision de producto: la ayuda se ofrece, no se impone.
  it('no arranca solo ni guarda nada en el navegador', () => {
    localStorage.clear()
    render(pantalla())

    expect(screen.getByRole('button', { name: /Tour guiado/ })).toBeInTheDocument()
    expect(screen.queryByText('Primer paso')).not.toBeInTheDocument()
    expect(localStorage.length).toBe(0)
  })

  it('arranca en el primer paso al pulsar el botón y avanza con Siguiente', async () => {
    const usuario = userEvent.setup()
    render(pantalla())

    await usuario.click(screen.getByRole('button', { name: /Tour guiado/ }))

    expect(await screen.findByText('Primer paso')).toBeInTheDocument()
    expect(screen.getByText('Contenido del primer paso.')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: /Siguiente/ }))

    expect(await screen.findByText('Segundo paso')).toBeInTheDocument()
  })

  it('se puede omitir en cualquier momento', async () => {
    const usuario = userEvent.setup()
    render(pantalla())

    await usuario.click(screen.getByRole('button', { name: /Tour guiado/ }))
    await screen.findByText('Primer paso')
    await usuario.click(screen.getByRole('button', { name: 'Omitir' }))

    expect(screen.queryByText('Primer paso')).not.toBeInTheDocument()
  })
})

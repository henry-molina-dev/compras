import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PasoTour } from '@/features/tours/pasos'
import { EncabezadoPagina } from './EncabezadoPagina'

const PASOS: PasoTour[] = [{ ancla: 'x', titulo: 'Paso', contenido: 'Contenido' }]

function antes(a: Node, b: Node) {
  return Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING)
}

describe('encabezado de página', () => {
  it('muestra el título como encabezado principal, con su extra al lado', () => {
    render(<EncabezadoPagina titulo="OC-2026-000001" extra={<span>Aprobada</span>} />)

    const titulo = screen.getByRole('heading', { level: 1, name: 'OC-2026-000001' })
    expect(titulo.parentElement).toContainElement(screen.getByText('Aprobada'))
  })

  it('el botón de ayuda es solo un ícono, con nombre accesible y texto al pasar el cursor', () => {
    render(<EncabezadoPagina titulo="Pantalla" pasos={PASOS} />)

    const ayuda = screen.getByRole('button', { name: 'Tour guiado' })
    expect(ayuda).toHaveAttribute('title', 'Tour guiado')
    expect(ayuda).toHaveTextContent('')
    expect(ayuda.querySelector('svg')).not.toBeNull()
  })

  // Regresion: el botón de ayuda se mezclaba con los de acción porque compartían la fila del título.
  it('el botón de ayuda va en la fila del título y las acciones, en su propia fila debajo', () => {
    render(<EncabezadoPagina titulo="Pantalla" pasos={PASOS} acciones={<button type="button">Exportar</button>} />)

    const titulo = screen.getByRole('heading', { name: 'Pantalla' })
    const ayuda = screen.getByRole('button', { name: 'Tour guiado' })
    const accion = screen.getByRole('button', { name: 'Exportar' })
    const filaTitulo = titulo.parentElement!.parentElement!

    expect(filaTitulo).toContainElement(ayuda)
    expect(filaTitulo).not.toContainElement(accion)
    expect(antes(titulo, ayuda)).toBe(true)
    expect(antes(ayuda, accion)).toBe(true)
  })

  it('las acciones se alinean a la derecha en pantallas anchas y a la izquierda en móvil', () => {
    const { container } = render(<EncabezadoPagina titulo="Pantalla" acciones={<button type="button">Exportar</button>} />)

    const fila = container.querySelector('[data-slot="acciones-pagina"]')
    expect(fila?.className).toContain('sm:justify-end')
    expect(fila?.className).not.toMatch(/(^|\s)justify-end(\s|$)/)
  })

  it('sin tour no hay botón de ayuda, y sin acciones no hay fila de acciones', () => {
    const { container } = render(<EncabezadoPagina titulo="Auditoría" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(container.querySelector('[data-slot="acciones-pagina"]')).toBeNull()
  })

  it('el ancla del tour marca el bloque del título con su extra', () => {
    render(<EncabezadoPagina titulo="OC-1" extra={<span>Creada</span>} anclaTitulo="estado-orden" />)

    const ancla = document.querySelector('[data-tour="estado-orden"]')
    expect(ancla).toContainElement(screen.getByRole('heading', { name: 'OC-1' }))
    expect(ancla).toContainElement(screen.getByText('Creada'))
  })
})

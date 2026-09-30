import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthLayout } from '@/layouts/AuthLayout'
import { PieAutor } from './PieAutor'

const autor = vi.hoisted(() => ({ actual: { nombre: 'Henry Molina' } as { nombre: string; perfil?: string } }))

vi.mock('@/lib/autor', () => ({
  get AUTOR() {
    return autor.actual
  },
}))

describe('autoría de la demo', () => {
  beforeEach(() => {
    autor.actual = { nombre: 'Henry Molina' }
  })

  it('muestra el nombre del autor', () => {
    render(<PieAutor />)

    expect(screen.getByText('Henry Molina')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('si hay perfil, el nombre es un enlace que abre en otra pestaña', () => {
    autor.actual = { nombre: 'Henry Molina', perfil: 'https://example.com/henry' }
    render(<PieAutor />)

    const enlace = screen.getByRole('link', { name: 'Henry Molina' })
    expect(enlace).toHaveAttribute('href', 'https://example.com/henry')
    expect(enlace).toHaveAttribute('target', '_blank')
    expect(enlace).toHaveAttribute('rel', expect.stringContaining('noreferrer'))
  })

  // Es lo primero que ve quien abre la demo, antes de iniciar sesión.
  it('aparece en la pantalla de inicio de sesión', () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<p>Formulario de login</p>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Formulario de login')).toBeInTheDocument()
    expect(screen.getByText('Henry Molina')).toBeInTheDocument()
  })
})

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { formatoMonto } from '@/lib/format'
import { PrecioLinea } from './PrecioLinea'

function pintar(props: Partial<React.ComponentProps<typeof PrecioLinea>> = {}) {
  const acciones = { onEditar: vi.fn(), onCambiar: vi.fn(), onListo: vi.fn(), onRestablecer: vi.fn() }
  render(
    <PrecioLinea
      numero={1}
      referencia={3.2}
      efectivo={3.2}
      valor=""
      puedeNegociar
      editando={false}
      error={null}
      rango={{ minimo: 2.88, maximo: 3.36 }}
      {...acciones}
      {...props}
    />,
  )
  return acciones
}

describe('precio de la línea', () => {
  it('empieza como dato de lectura, con un lápiz para editarlo', async () => {
    const usuario = userEvent.setup()
    const acciones = pintar()

    expect(screen.getByText('Precio')).toHaveTextContent('Precio ' + formatoMonto(3.2))
    expect(screen.queryByLabelText('Precio unitario 1')).not.toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Editar precio de la línea 1' }))
    expect(acciones.onEditar).toHaveBeenCalledOnce()
  })

  it('sin margen de negociación no ofrece el lápiz', () => {
    pintar({ puedeNegociar: false })

    expect(screen.getByText('Precio')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Editar precio/ })).not.toBeInTheDocument()
  })

  it('si el precio difiere del catálogo, muestra la referencia y la variación', () => {
    pintar({ efectivo: 3.04 })

    expect(screen.getByText(`catálogo ${formatoMonto(3.2)} · -5.0%`)).toBeInTheDocument()
  })

  it('si el precio es el de catálogo no muestra ninguna variación', () => {
    pintar()

    expect(screen.queryByText(/catálogo/)).not.toBeInTheDocument()
  })

  it('no muestra nada mientras no hay un precio que mostrar (línea sin producto)', () => {
    pintar({ efectivo: null })

    expect(screen.queryByText('Precio')).not.toBeInTheDocument()
  })

  it('al editar muestra el campo, el rango permitido y los botones de aceptar y de volver al catálogo', async () => {
    const usuario = userEvent.setup()
    const acciones = pintar({ editando: true, valor: '3' })

    expect(screen.getByLabelText('Precio unitario 1')).toHaveValue(3)
    expect(screen.getByText(`Entre ${formatoMonto(2.88)} y ${formatoMonto(3.36)} · catálogo ${formatoMonto(3.2)}`)).toBeInTheDocument()

    await usuario.type(screen.getByLabelText('Precio unitario 1'), '5')
    expect(acciones.onCambiar).toHaveBeenCalledWith('35')

    await usuario.click(screen.getByRole('button', { name: 'Aceptar precio de la línea 1' }))
    expect(acciones.onListo).toHaveBeenCalledOnce()

    await usuario.click(screen.getByRole('button', { name: 'Volver al precio de catálogo de la línea 1' }))
    expect(acciones.onRestablecer).toHaveBeenCalledOnce()
  })

  it('un error reemplaza al rango y marca el campo como inválido', () => {
    pintar({ editando: true, valor: '2', error: 'El precio debe estar entre 2.88 y 3.36.' })

    expect(screen.getByText('El precio debe estar entre 2.88 y 3.36.')).toBeInTheDocument()
    expect(screen.getByLabelText('Precio unitario 1')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByText(/Entre .* y .* · catálogo/)).not.toBeInTheDocument()
  })

  it('un error se muestra también en modo lectura (p. ej. tras cambiar de proveedor)', () => {
    pintar({ efectivo: 3, error: 'Este proveedor no admite negociar precios.' })

    expect(screen.getByText('Este proveedor no admite negociar precios.')).toBeInTheDocument()
  })
})

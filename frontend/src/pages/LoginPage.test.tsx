import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderPagina } from '@/test/render'
import { LoginPage } from './LoginPage'

const auth = vi.hoisted(() => ({ login: vi.fn(async () => undefined) }))

vi.mock('@/features/auth/AuthContext', () => ({ useAuth: () => ({ sesion: null, login: auth.login, logout: vi.fn() }) }))

function renderizar() {
  return renderPagina(<LoginPage />, { ruta: '/login', patron: '/login' })
}

describe('campo de contraseña del login', () => {
  beforeEach(() => {
    auth.login.mockClear()
  })

  it('la contraseña está oculta por defecto', () => {
    renderizar()

    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Mostrar contraseña' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('el ojo muestra y vuelve a ocultar la contraseña sin perder lo escrito', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await usuario.type(screen.getByLabelText('Contraseña'), 'Compras2026!')

    await usuario.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))

    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('Compras2026!')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toHaveAttribute('aria-pressed', 'true')

    await usuario.click(screen.getByRole('button', { name: 'Ocultar contraseña' }))

    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'password')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('Compras2026!')
  })

  // El ojo es un botón dentro del formulario: no debe enviarlo.
  it('pulsar el ojo no envía el formulario', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await usuario.type(screen.getByLabelText('Usuario'), 'admin')
    await usuario.type(screen.getByLabelText('Contraseña'), 'Compras2026!')

    await usuario.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))

    expect(auth.login).not.toHaveBeenCalled()
  })

  it('se puede iniciar sesión con la contraseña visible', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await usuario.type(screen.getByLabelText('Usuario'), 'admin')
    await usuario.type(screen.getByLabelText('Contraseña'), 'Compras2026!')
    await usuario.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))

    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }))

    await waitFor(() => expect(auth.login).toHaveBeenCalledWith('admin', 'Compras2026!'))
  })
})

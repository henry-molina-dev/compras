import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { vi } from 'vitest'
import type { Rol } from '@/api/types'

/** Renderiza una página con router y cache de consultas aislados, como sesión del rol indicado. */
export function renderPagina(
  elemento: ReactElement,
  opciones: { ruta: string; patron: string; queryClient?: QueryClient },
) {
  const queryClient = opciones.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[opciones.ruta]}>
        <Routes>
          <Route path={opciones.patron} element={elemento} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

export function sesionComo(rol: Rol) {
  return { sesion: { token: 't', username: 'usuario.prueba', rol, sucursalId: null }, login: vi.fn(), logout: vi.fn() }
}

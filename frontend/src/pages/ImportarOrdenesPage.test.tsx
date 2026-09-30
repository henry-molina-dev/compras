import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PASOS_IMPORTAR } from '@/features/tours/pasos'
import { anclasFaltantes } from '@/test/tour'
import { renderPagina } from '@/test/render'
import { ImportarOrdenesPage } from './ImportarOrdenesPage'

describe('tour de importar órdenes', () => {
  it('ofrece el tour y todos sus pasos apuntan a elementos de la pantalla', () => {
    renderPagina(<ImportarOrdenesPage />, { ruta: '/ordenes/importar', patron: '/ordenes/importar' })

    expect(screen.getByRole('button', { name: /Tour guiado/ })).toBeInTheDocument()
    expect(anclasFaltantes(PASOS_IMPORTAR)).toEqual([])
  })
})

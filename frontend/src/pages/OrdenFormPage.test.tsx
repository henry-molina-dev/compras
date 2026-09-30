import { QueryClient } from '@tanstack/react-query'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PASOS_ORDEN_EDITAR, PASOS_ORDEN_NUEVA } from '@/features/tours/pasos'
import { formatoMonto } from '@/lib/format'
import { renderPagina, sesionComo } from '@/test/render'
import { anclasFaltantes } from '@/test/tour'
import { OrdenFormPage } from './OrdenFormPage'

const sucursales = [
  { id: 1, nombre: 'Ferretería San Salvador', formato: 'FERRETERIA', activo: true },
  { id: 3, nombre: 'Venta Directa', formato: 'VENTA_DIRECTA', activo: true },
]
const productosFerreteria = [
  { id: 10, nombre: 'Tornillo', precio: 3.2, activo: true },
  { id: 11, nombre: 'Brocas', precio: 18.5, activo: true },
]

const consultas = vi.hoisted(() => ({ productosPedidos: [] as (number | undefined)[], enviados: [] as unknown[] }))

vi.mock('@/features/auth/AuthContext', () => ({ useAuth: () => sesionComo('COMPRADOR') }))
vi.mock('@/api/client', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/client')>()
  return {
    ...original,
    api: {
      get: vi.fn(async (ruta: string, query?: Record<string, unknown>) => {
        const lista = (data: unknown[]) => ({ object: 'list', data, has_more: false })
        if (ruta === '/sucursales') return lista(sucursales)
        if (ruta === '/proveedores') {
          return lista([
            { id: 1, nombre: 'Proveedor Uno', activo: true, descuento_maximo_pct: 0, aumento_maximo_pct: 0 },
            { id: 2, nombre: 'Proveedor Negocia', activo: true, descuento_maximo_pct: 10, aumento_maximo_pct: 5 },
          ])
        }
        if (ruta === '/clientes') return lista([{ id: 7, nombre: 'Cliente Siete', activo: true }])
        if (ruta === '/productos') {
          consultas.productosPedidos.push(query?.sucursal_id as number | undefined)
          // El backend devuelve solo los productos permitidos para el formato de la sucursal.
          return lista(query?.sucursal_id === 1 ? productosFerreteria : [...productosFerreteria, { id: 12, nombre: 'Cemento', precio: 45, activo: true }])
        }
        if (ruta === '/ordenes/7') {
          return {
            object: 'orden_compra', id: 7, numero_orden: 'OC-2026-000007', estado: 'CREADA',
            proveedor_id: 1, sucursal_destino_id: 1, cliente_id: null, fecha_necesaria: '2026-12-01',
            detalle: [{ producto_id: 10, cantidad: 5 }],
          }
        }
        if (ruta === '/ordenes/8') {
          return {
            object: 'orden_compra', id: 8, numero_orden: 'OC-2026-000008', estado: 'CREADA',
            proveedor_id: 2, sucursal_destino_id: 1, cliente_id: null, fecha_necesaria: '2026-12-01',
            detalle: [{ producto_id: 10, cantidad: 5, precio_unitario: 3.04, precio_catalogo: 3.2 }],
          }
        }
        throw new Error(`Ruta no simulada: ${ruta}`)
      }),
      post: vi.fn(async (_ruta: string, cuerpo: unknown) => {
        consultas.enviados.push(cuerpo)
        return { id: 99 }
      }),
      put: vi.fn(async (_ruta: string, cuerpo: unknown) => {
        consultas.enviados.push(cuerpo)
        return { id: 8 }
      }),
    },
  }
})

async function elegir(usuario: ReturnType<typeof userEvent.setup>, etiqueta: string, opcion: string) {
  await usuario.click(screen.getByRole('combobox', { name: etiqueta }))
  await usuario.click(await screen.findByRole('option', { name: new RegExp(opcion) }))
}

function renderizar() {
  return renderPagina(<OrdenFormPage />, { ruta: '/ordenes/nueva', patron: '/ordenes/nueva' })
}

function renderizarEdicion() {
  return renderPagina(<OrdenFormPage />, { ruta: '/ordenes/7/editar', patron: '/ordenes/:id/editar' })
}

describe('formulario de nueva orden', () => {
  beforeEach(() => {
    consultas.productosPedidos.length = 0
  })

  it('el tour de la pantalla no arranca solo y todos sus pasos apuntan a elementos del formulario', async () => {
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toBeEnabled())

    expect(screen.getByRole('button', { name: /Tour guiado/ })).toBeInTheDocument()
    expect(screen.queryByText('Proveedor y sucursal')).not.toBeInTheDocument()
    expect(anclasFaltantes(PASOS_ORDEN_NUEVA)).toEqual([])
  })

  it('el campo Cliente aparece solo al elegir una sucursal de Venta Directa', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toBeEnabled())

    await elegir(usuario, 'Sucursal destino', 'Ferretería San Salvador')
    expect(screen.queryByRole('combobox', { name: 'Cliente' })).not.toBeInTheDocument()

    await elegir(usuario, 'Sucursal destino', 'Venta Directa')
    expect(await screen.findByRole('combobox', { name: 'Cliente' })).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Crear orden' }))
    expect(await screen.findByText('El cliente es obligatorio para Venta Directa.')).toBeInTheDocument()
  })

  // Regresion: este aviso informativo se veia naranja (el color de la marca), como una alarma.
  it('el aviso del formato de la sucursal es informativo (azul), no naranja ni rojo', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toBeEnabled())

    await elegir(usuario, 'Sucursal destino', 'Ferretería San Salvador')

    const aviso = await screen.findByRole('status')
    expect(aviso).toHaveTextContent('solo se muestran los productos de las categorías permitidas')
    expect(aviso.className).toContain('bg-info-soft')
    expect(aviso.className).not.toMatch(/destructive|primary|accent/)
  })

  it('el selector de productos muestra solo los que el backend permite para la sucursal elegida', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toBeEnabled())

    await elegir(usuario, 'Sucursal destino', 'Ferretería San Salvador')
    await waitFor(() => expect(consultas.productosPedidos).toContain(1))

    await usuario.click(screen.getByRole('combobox', { name: 'Producto 1' }))
    const opciones = within(await screen.findByRole('listbox')).getAllByRole('option')
    expect(opciones.map((o) => o.textContent)).toEqual(['Tornillo · 3,20', 'Brocas · 18,50'])
  })

  it('el total se recalcula al agregar, modificar y quitar líneas', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toBeEnabled())
    await elegir(usuario, 'Sucursal destino', 'Ferretería San Salvador')
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Producto 1' })).toBeEnabled())

    await elegir(usuario, 'Producto 1', 'Tornillo')
    await usuario.clear(screen.getByLabelText('Cantidad 1'))
    await usuario.type(screen.getByLabelText('Cantidad 1'), '10')
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent('32,00')

    await usuario.click(screen.getByRole('button', { name: 'Agregar producto' }))
    await elegir(usuario, 'Producto 2', 'Brocas')
    await usuario.clear(screen.getByLabelText('Cantidad 2'))
    await usuario.type(screen.getByLabelText('Cantidad 2'), '2')
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent('69,00')

    await usuario.clear(screen.getByLabelText('Cantidad 1'))
    await usuario.type(screen.getByLabelText('Cantidad 1'), '5')
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent('53,00')

    await usuario.click(screen.getByRole('button', { name: 'Quitar línea 1' }))
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent('37,00')
  }, 15_000) // muchas interacciones con user-event: en jsdom ronda los 5 s del timeout por defecto

  it('no deja enviar mientras falten datos y muestra el mensaje de cada campo', async () => {
    const usuario = userEvent.setup()
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toBeEnabled())

    await usuario.click(screen.getByRole('button', { name: 'Crear orden' }))

    expect(await screen.findByText('Selecciona un proveedor.')).toBeInTheDocument()
    expect(screen.getByText('Selecciona la sucursal destino.')).toBeInTheDocument()
    expect(screen.getByText('Indica la fecha necesaria.')).toBeInTheDocument()
    expect(screen.getByText('Selecciona un producto.')).toBeInTheDocument()
  })
})

describe('formulario de editar orden', () => {
  beforeEach(() => {
    consultas.productosPedidos.length = 0
  })

  it('ofrece el tour de edición, no arranca solo y todos sus pasos apuntan a elementos del formulario', async () => {
    renderizarEdicion()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Uno'))

    expect(screen.getByRole('button', { name: /Tour guiado/ })).toBeInTheDocument()
    expect(screen.queryByText('Proveedor, sucursal y fecha')).not.toBeInTheDocument()
    expect(anclasFaltantes(PASOS_ORDEN_EDITAR)).toEqual([])
    // Es el tour de edición (conserva precios, no crea), no el de "Nueva orden".
    expect(PASOS_ORDEN_EDITAR.map((p) => p.contenido).join(' ')).toMatch(/conserva su precio/)
    expect(PASOS_ORDEN_EDITAR).not.toEqual(PASOS_ORDEN_NUEVA)
  })

  it('al pulsar el botón arranca el tour de edición', async () => {
    const usuario = userEvent.setup()
    renderizarEdicion()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Uno'))

    await usuario.click(screen.getByRole('button', { name: /Tour guiado/ }))

    expect(await screen.findByText('Proveedor, sucursal y fecha')).toBeInTheDocument()
  })

  // Regresion: el formulario solo montaba los <Select> de proveedor/sucursal una vez que la orden
  // ya habia cargado (un `if (isPending) return ...` escondia el formulario entero hasta entonces),
  // asi que su primer render ya traia el valor real - justo cuando Radix registra sus opciones por
  // primera vez. Eso le hacia perder la seleccion (dispara onValueChange("") como si el valor no
  // existiera). El formulario ahora se monta vacio de una vez, igual que "Nueva orden".
  it('precarga proveedor, sucursal y cliente con los datos de la orden existente', async () => {
    renderizarEdicion()

    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Uno'))
    expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toHaveTextContent('Ferretería San Salvador')
    expect(screen.getByRole('combobox', { name: 'Producto 1' })).toHaveTextContent('Tornillo')
    expect(screen.getByLabelText('Cantidad 1')).toHaveValue(5)
  })

  // Regresion: al llegar con el botón "Editar" desde el detalle, la orden y los catálogos ya están
  // en la caché de consultas, así que el formulario tiene todo en su primer render (no como al
  // refrescar la página, donde llegan por separado).
  it('precarga los datos cuando la orden y los catálogos ya están en caché', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const primera = renderPagina(<OrdenFormPage />, { ruta: '/ordenes/7/editar', patron: '/ordenes/:id/editar', queryClient })
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Uno'))
    primera.unmount()

    renderPagina(<OrdenFormPage />, { ruta: '/ordenes/7/editar', patron: '/ordenes/:id/editar', queryClient })
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Uno'))
    expect(screen.getByRole('combobox', { name: 'Sucursal destino' })).toHaveTextContent('Ferretería San Salvador')
    expect(screen.getByRole('combobox', { name: 'Producto 1' })).toHaveTextContent('Tornillo')
  })
})

describe('precio negociado en el formulario', () => {
  beforeEach(() => {
    consultas.enviados.length = 0
  })

  async function prepararLinea(usuario: ReturnType<typeof userEvent.setup>, proveedor: string) {
    renderizar()
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toBeEnabled())
    await elegir(usuario, 'Proveedor', proveedor)
    await elegir(usuario, 'Sucursal destino', 'Ferretería San Salvador')
    await elegir(usuario, 'Producto 1', 'Tornillo')
  }

  async function negociar(usuario: ReturnType<typeof userEvent.setup>, precio: string) {
    await usuario.click(screen.getByRole('button', { name: 'Editar precio de la línea 1' }))
    const campo = screen.getByLabelText('Precio unitario 1')
    await usuario.clear(campo)
    await usuario.type(campo, precio)
  }

  async function enviar(usuario: ReturnType<typeof userEvent.setup>) {
    fireEvent.change(screen.getByLabelText('Fecha necesaria'), { target: { value: '2099-01-01' } })
    await usuario.click(screen.getByRole('button', { name: 'Crear orden' }))
  }

  it('la línea muestra el precio de catálogo como dato de lectura', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Uno')

    expect(await screen.findByText('Precio')).toHaveTextContent('Precio ' + formatoMonto(3.2))
  })

  it('con un proveedor sin margen (0% y 0%) no hay lápiz para editar el precio', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Uno')
    await screen.findByText('Precio')

    expect(screen.queryByRole('button', { name: 'Editar precio de la línea 1' })).not.toBeInTheDocument()
  })

  it('con un proveedor que admite negociar, el lápiz abre el campo con el rango permitido', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')

    await usuario.click(await screen.findByRole('button', { name: 'Editar precio de la línea 1' }))

    expect(screen.getByLabelText('Precio unitario 1')).toHaveValue(3.2)
    expect(screen.getByText(`Entre ${formatoMonto(2.88)} y ${formatoMonto(3.36)} · catálogo ${formatoMonto(3.2)}`)).toBeInTheDocument()
  })

  it('al negociar, el total se recalcula y la línea muestra la variación frente al catálogo', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await usuario.click(await screen.findByRole('button', { name: 'Editar precio de la línea 1' }))
    const campo = screen.getByLabelText('Precio unitario 1')
    await usuario.clear(campo)
    await usuario.type(campo, '3.04')

    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent(formatoMonto(3.04))

    await usuario.click(screen.getByRole('button', { name: 'Aceptar precio de la línea 1' }))

    expect(screen.getByText(`catálogo ${formatoMonto(3.2)} · -5.0%`)).toBeInTheDocument()
  })

  it('un precio fuera del rango muestra el error y no deja enviar la orden', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await negociar(usuario, '2')

    expect(screen.getByText(`El precio debe estar entre ${(2.88).toFixed(2)} y ${(3.36).toFixed(2)}.`)).toBeInTheDocument()

    await enviar(usuario)

    expect(consultas.enviados).toEqual([])
  })

  it('envía el precio negociado en la línea, y solo en las líneas que lo tienen', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await negociar(usuario, '3')
    await usuario.click(screen.getByRole('button', { name: 'Aceptar precio de la línea 1' }))

    await enviar(usuario)

    await waitFor(() => expect(consultas.enviados).toHaveLength(1))
    expect(consultas.enviados[0]).toMatchObject({ detalle: [{ producto_id: 10, cantidad: 1, precio_unitario: 3 }] })
  })

  it('sin tocar el precio la línea no manda precio: el backend usa el de catálogo', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')

    await enviar(usuario)

    await waitFor(() => expect(consultas.enviados).toHaveLength(1))
    const [enviado] = consultas.enviados as { detalle: object[] }[]
    expect(enviado.detalle[0]).toEqual({ producto_id: 10, cantidad: 1 })
  })

  it('el botón de catálogo devuelve la línea a su precio original', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await negociar(usuario, '3')

    await usuario.click(screen.getByRole('button', { name: 'Volver al precio de catálogo de la línea 1' }))

    expect(screen.queryByLabelText('Precio unitario 1')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent(formatoMonto(3.2))
    expect(screen.queryByText(/catálogo .* · /)).not.toBeInTheDocument()
  })

  it('al cambiar el producto de una línea negociada, esta vuelve al precio de catálogo del nuevo', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await negociar(usuario, '3')
    await usuario.click(screen.getByRole('button', { name: 'Aceptar precio de la línea 1' }))

    await elegir(usuario, 'Producto 1', 'Brocas')

    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent(formatoMonto(18.5))
  })

  it('al cambiar a un proveedor sin margen, el precio negociado se marca como inválido', async () => {
    const usuario = userEvent.setup()
    await prepararLinea(usuario, 'Proveedor Negocia')
    await negociar(usuario, '3')
    await usuario.click(screen.getByRole('button', { name: 'Aceptar precio de la línea 1' }))

    await elegir(usuario, 'Proveedor', 'Proveedor Uno')

    expect(await screen.findByText('Este proveedor no admite negociar precios.')).toBeInTheDocument()
  })

  it('al editar una orden, la línea conserva su precio negociado y su referencia de catálogo', async () => {
    const usuario = userEvent.setup()
    renderPagina(<OrdenFormPage />, { ruta: '/ordenes/8/editar', patron: '/ordenes/:id/editar' })
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Proveedor' })).toHaveTextContent('Proveedor Negocia'))

    expect(await screen.findByText(`catálogo ${formatoMonto(3.2)} · -5.0%`)).toBeInTheDocument()
    expect(screen.getByLabelText('Total de la orden')).toHaveTextContent(formatoMonto(15.2))

    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    await waitFor(() => expect(consultas.enviados).toHaveLength(1))
    expect(consultas.enviados[0]).toMatchObject({ detalle: [{ producto_id: 10, cantidad: 5, precio_unitario: 3.04 }] })
  })
})

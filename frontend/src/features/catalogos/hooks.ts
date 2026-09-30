import { useQuery, type QueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { Categoria, Cliente, ListEnvelope, Producto, Proveedor, Sucursal } from '@/api/types'
import { useAuth } from '@/features/auth/AuthContext'

const CINCO_MINUTOS = 5 * 60 * 1000

/**
 * Refresca el listado de la pantalla de administracion de un catalogo (clave con barra inicial, la
 * ruta de la API) y tambien la cache que usan las demas pantallas (clave sin ella, definida abajo y
 * guardada 5 minutos). Sin esto, un cambio hecho en administracion no se veria en los selectores de
 * sucursal, proveedor, etc. hasta que expirara.
 */
export function invalidarCatalogo(queryClient: QueryClient, ruta: string) {
  void queryClient.invalidateQueries({ queryKey: [ruta] })
  void queryClient.invalidateQueries({ queryKey: [ruta.replace(/^\//, '')] })
}

export function useSucursales() {
  return useQuery({
    queryKey: ['sucursales'],
    queryFn: () => api.get<ListEnvelope<Sucursal>>('/sucursales'),
    select: (r) => r.data,
    staleTime: CINCO_MINUTOS,
  })
}

// El listado de proveedores está abierto solo a ADMIN y COMPRADOR; el gerente de sucursal obtiene
// el nombre del proveedor con expand=proveedor en la propia orden.
export function useProveedores() {
  const { sesion } = useAuth()
  return useQuery({
    queryKey: ['proveedores'],
    queryFn: () => api.get<ListEnvelope<Proveedor>>('/proveedores'),
    select: (r) => r.data,
    staleTime: CINCO_MINUTOS,
    enabled: sesion?.rol !== 'GERENTE_SUCURSAL',
  })
}

export function useClientes() {
  const { sesion } = useAuth()
  return useQuery({
    queryKey: ['clientes'],
    queryFn: () => api.get<ListEnvelope<Cliente>>('/clientes'),
    select: (r) => r.data,
    staleTime: CINCO_MINUTOS,
    enabled: sesion?.rol !== 'GERENTE_SUCURSAL',
  })
}

/** Productos que se pueden pedir para la sucursal indicada (filtrados por formato en el backend). */
export function useProductos(sucursalId?: number) {
  const { sesion } = useAuth()
  return useQuery({
    queryKey: ['productos', { sucursalId: sucursalId ?? null }],
    queryFn: () => api.get<ListEnvelope<Producto>>('/productos', { sucursal_id: sucursalId }),
    select: (r) => r.data,
    staleTime: CINCO_MINUTOS,
    enabled: sesion?.rol !== 'GERENTE_SUCURSAL',
  })
}

export function useCategorias() {
  return useQuery({
    queryKey: ['categorias'],
    queryFn: () => api.get<ListEnvelope<Categoria>>('/categorias'),
    select: (r) => r.data,
    staleTime: CINCO_MINUTOS,
  })
}

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/client'
import type { LineaTiempoItem, ListEnvelope, OrdenCompra, OrdenCompraRequest } from '@/api/types'

export interface FiltrosOrdenes {
  numero_orden?: string
  proveedor_id?: number
  sucursal_id?: number
  cliente_id?: number
  estado?: string
  fecha_desde?: string
  fecha_hasta?: string
}

export const ORDENES_POR_PAGINA = 10
// El backend limita `limit` a 100 (@Max(100) en OrdenCompraController.listar).
export const OPCIONES_ORDENES_POR_PAGINA = [10, 20, 50, 100]

// Una sola página por vez (no scroll infinito): el cursor de la página a mostrar lo controla
// quien llama, para poder implementar Anterior/Siguiente con una pila de cursores en la UI.
export function useOrdenes(filtros: FiltrosOrdenes, cursor: string | undefined, limite: number = ORDENES_POR_PAGINA) {
  return useQuery({
    queryKey: ['ordenes', filtros, cursor, limite],
    queryFn: () =>
      api.get<ListEnvelope<OrdenCompra>>('/ordenes', {
        ...filtros,
        limit: limite,
        starting_after: cursor,
        expand: 'proveedor',
      }),
    // Conserva la página anterior mientras llega la siguiente, para no parpadear a "Cargando…"
    // en cada clic de Anterior/Siguiente.
    placeholderData: keepPreviousData,
  })
}

export function useOrden(id: number) {
  return useQuery({
    queryKey: ['orden', id],
    queryFn: () => api.get<OrdenCompra>(`/ordenes/${id}`, { expand: 'proveedor' }),
  })
}

export function useLineaTiempo(id: number) {
  return useQuery({
    queryKey: ['orden', id, 'linea-tiempo'],
    queryFn: () => api.get<ListEnvelope<LineaTiempoItem>>(`/ordenes/${id}/linea-tiempo`),
    select: (r) => r.data,
  })
}

function useInvalidarOrden(id?: number) {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['ordenes'] })
    if (id !== undefined) void queryClient.invalidateQueries({ queryKey: ['orden', id] })
  }
}

export function useCrearOrden() {
  const invalidar = useInvalidarOrden()
  return useMutation({
    mutationFn: (request: OrdenCompraRequest) => api.post<OrdenCompra>('/ordenes', request),
    onSuccess: invalidar,
  })
}

export function useActualizarOrden(id: number) {
  const invalidar = useInvalidarOrden(id)
  return useMutation({
    mutationFn: (request: OrdenCompraRequest) => api.put<OrdenCompra>(`/ordenes/${id}`, request),
    onSuccess: invalidar,
  })
}

export function useAprobarOrden(id: number) {
  const invalidar = useInvalidarOrden(id)
  return useMutation({
    mutationFn: () => api.patch<OrdenCompra>(`/ordenes/${id}/aprobar`),
    onSuccess: invalidar,
  })
}

export function useAnularOrden(id: number) {
  const invalidar = useInvalidarOrden(id)
  return useMutation({
    mutationFn: (motivo: string) => api.patch<OrdenCompra>(`/ordenes/${id}/anular`, { motivo }),
    onSuccess: invalidar,
  })
}

export function useCerrarOrden(id: number) {
  const invalidar = useInvalidarOrden(id)
  return useMutation({
    mutationFn: (datos: { conforme: boolean; observacion?: string }) => api.patch<OrdenCompra>(`/ordenes/${id}/cerrar`, datos),
    onSuccess: invalidar,
  })
}

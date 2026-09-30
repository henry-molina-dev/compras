import type { EstadoOrden, Rol } from '@/api/types'

export type AccionOrden = 'editar' | 'aprobar' | 'anular' | 'cerrar'

const ROLES_POR_ACCION: Record<AccionOrden, Rol[]> = {
  editar: ['ADMIN', 'COMPRADOR'],
  aprobar: ['ADMIN', 'COMPRADOR'],
  anular: ['ADMIN', 'COMPRADOR'],
  // Cerrar es la confirmación de recepción en la sucursal: no le corresponde al comprador.
  cerrar: ['ADMIN', 'GERENTE_SUCURSAL'],
}

const ESTADOS_POR_ACCION: Record<AccionOrden, EstadoOrden[]> = {
  editar: ['CREADA'],
  aprobar: ['CREADA'],
  anular: ['CREADA', 'APROBADA'],
  cerrar: ['APROBADA'],
}

/** Una acción se muestra solo si el rol puede ejecutarla, y se habilita solo si el estado la permite. */
export function estadoDeAccion(accion: AccionOrden, estado: EstadoOrden, rol: Rol): { visible: boolean; habilitada: boolean } {
  const visible = ROLES_POR_ACCION[accion].includes(rol)
  return { visible, habilitada: visible && ESTADOS_POR_ACCION[accion].includes(estado) }
}

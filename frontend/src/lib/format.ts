import type { FormatoSucursal } from '@/api/types'

/** Idioma del navegador (no el formato regional del SO); undefined delega en el valor por defecto de Intl. */
function locale(): string | undefined {
  return typeof navigator === 'undefined' ? undefined : navigator.language
}

export function formatoMonto(valor: number): string {
  return valor.toLocaleString(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Convierte una fecha ISO (yyyy-mm-dd) a dd/mm/yyyy sin pasar por Date, para evitar corrimientos de zona horaria. */
export function formatoFecha(iso: string): string {
  const [anio, mes, dia] = iso.slice(0, 10).split('-')
  return `${dia}/${mes}/${anio}`
}

export function formatoFechaHora(iso: string): string {
  return new Date(iso).toLocaleString(locale(), { dateStyle: 'short', timeStyle: 'short' })
}

/** Nombre legible de cada formato de sucursal. */
export const ETIQUETA_FORMATO: Record<FormatoSucursal, string> = {
  FERRETERIA: 'Ferretería',
  FERRETERIA_CONSTRUCCION: 'Ferretería de Construcción',
  VENTA_DIRECTA: 'Venta Directa',
}

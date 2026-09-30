import type { PasoTour } from '@/features/tours/pasos'

/**
 * Anclas de un tour que no existen en el DOM actual. Cada pantalla debe devolver [] para cada rol:
 * si una ancla desaparece (se renombra o se quita un elemento), el paso quedaría apuntando a la
 * nada y el tour se rompería sin que nadie lo note.
 */
export function anclasFaltantes(pasos: PasoTour[]): string[] {
  return pasos.filter((p) => !document.querySelector(`[data-tour="${p.ancla}"]`)).map((p) => p.ancla)
}

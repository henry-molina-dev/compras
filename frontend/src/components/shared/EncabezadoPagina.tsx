import type { ReactNode } from 'react'
import { TourGuiado } from '@/components/shared/TourGuiado'
import type { PasoTour } from '@/features/tours/pasos'

/**
 * Encabezado uniforme de las pantallas. Fila del título: el título (con un extra opcional, como el
 * estado de una orden) a la izquierda y, si la pantalla tiene tour, el botón "?" siempre a la
 * derecha. Las acciones (exportar, crear, guardar...) van en su propia fila debajo, alineadas a la
 * derecha en pantallas anchas y a la izquierda en móvil, para que no se mezclen con la ayuda.
 */
export function EncabezadoPagina({
  titulo,
  extra,
  anclaTitulo,
  pasos,
  acciones,
}: {
  titulo: ReactNode
  /** Junto al título, dentro del mismo bloque (p. ej. el badge de estado). */
  extra?: ReactNode
  /** `data-tour` del bloque título + extra. */
  anclaTitulo?: string
  /** Pasos del tour de la pantalla; sin ellos no se muestra el botón de ayuda. */
  pasos?: PasoTour[]
  /** Botones de acción de la pantalla. */
  acciones?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-3" data-tour={anclaTitulo}>
          <h1 className="text-xl font-medium">{titulo}</h1>
          {extra}
        </div>
        {pasos && <TourGuiado pasos={pasos} />}
      </div>
      {acciones && (
        <div className="flex sm:justify-end" data-slot="acciones-pagina">
          <div className="flex flex-wrap items-center gap-2">{acciones}</div>
        </div>
      )}
    </div>
  )
}

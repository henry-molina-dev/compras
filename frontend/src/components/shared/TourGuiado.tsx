import { CircleHelp } from 'lucide-react'
import { useMemo } from 'react'
import { useJoyride, type Step } from 'react-joyride'
import { Button } from '@/components/ui/button'
import type { PasoTour } from '@/features/tours/pasos'

const LOCALE = {
  back: 'Atrás',
  close: 'Cerrar',
  last: 'Terminar',
  next: 'Siguiente',
  nextWithProgress: 'Siguiente ({current} de {total})',
  skip: 'Omitir',
}

/**
 * Botón "Tour guiado" con su recorrido. Solo arranca cuando el usuario lo pide: las pantallas son
 * intuitivas y la ayuda se ofrece, no se impone. `pasos` debe ser una referencia estable
 * (constantes de features/tours/pasos.ts).
 */
export function TourGuiado({ pasos }: { pasos: PasoTour[] }) {
  const steps = useMemo<Step[]>(
    () => pasos.map((p) => ({ target: `[data-tour="${p.ancla}"]`, title: p.titulo, content: p.contenido })),
    [pasos],
  )
  const { controls, Tour } = useJoyride({
    steps,
    continuous: true,
    locale: LOCALE,
    options: { primaryColor: '#194F90', showProgress: true, buttons: ['back', 'skip', 'primary'], skipBeacon: true },
  })

  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="icon"
        aria-label="Tour guiado"
        title="Tour guiado"
        onClick={() => controls.start()}
      >
        <CircleHelp />
      </Button>
      {Tour}
    </>
  )
}

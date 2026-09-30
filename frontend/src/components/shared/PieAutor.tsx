import { AUTOR } from '@/lib/autor'
import { cn } from '@/lib/utils'

/** Línea de autoría visible en el login y en el menú lateral, para que la demo siempre diga de quién es. */
export function PieAutor({ className }: { className?: string }) {
  return (
    <p className={cn('text-xs text-muted-foreground', className)}>
      Aplicación de demostración desarrollada por{' '}
      {AUTOR.perfil ? (
        <a
          href={AUTOR.perfil}
          target="_blank"
          rel="noreferrer"
          className="font-medium text-foreground underline underline-offset-2"
        >
          {AUTOR.nombre}
        </a>
      ) : (
        <span className="font-medium text-foreground">{AUTOR.nombre}</span>
      )}
    </p>
  )
}

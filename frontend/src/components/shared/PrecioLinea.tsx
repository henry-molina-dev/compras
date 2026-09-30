import { Check, Pencil, Undo2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { mismoPrecio, textoVariacion, variacionPorcentual } from '@/features/ordenes/precios'
import { formatoMonto } from '@/lib/format'

/**
 * Precio unitario de una línea de la orden. Al principio es solo lectura; si el proveedor admite
 * negociar (algún límite mayor a 0), un lápiz habilita la edición dentro del rango permitido, con un
 * botón para volver al precio de catálogo. Con el proveedor en 0%/0% o sin proveedor no hay lápiz.
 */
export function PrecioLinea({
  numero,
  referencia,
  efectivo,
  valor,
  puedeNegociar,
  editando,
  error,
  rango,
  onEditar,
  onCambiar,
  onListo,
  onRestablecer,
}: {
  numero: number
  referencia: number | null
  efectivo: number | null
  /** Valor del campo (texto) mientras se edita. */
  valor: string
  puedeNegociar: boolean
  editando: boolean
  error: string | null
  rango: { minimo: number; maximo: number } | null
  onEditar: () => void
  onCambiar: (valor: string) => void
  onListo: () => void
  onRestablecer: () => void
}) {
  if (efectivo === null) return null

  const negociado = referencia !== null && !mismoPrecio(efectivo, referencia)

  if (editando) {
    return (
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-1">
          <Input
            type="number"
            min="0"
            step="0.01"
            inputMode="decimal"
            aria-label={`Precio unitario ${numero}`}
            aria-invalid={!!error}
            className="h-8 w-28 text-right text-[13px] tabular-nums"
            value={valor}
            onChange={(e) => onCambiar(e.target.value)}
          />
          <Button type="button" variant="ghost" size="icon" aria-label={`Aceptar precio de la línea ${numero}`} onClick={onListo}>
            <Check />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`Volver al precio de catálogo de la línea ${numero}`}
            title="Volver al precio de catálogo"
            onClick={onRestablecer}
          >
            <Undo2 />
          </Button>
        </div>
        {error ? (
          <p className="text-xs text-destructive">{error}</p>
        ) : (
          rango &&
          referencia !== null && (
            <p className="text-xs text-muted-foreground">
              Entre {formatoMonto(rango.minimo)} y {formatoMonto(rango.maximo)} · catálogo {formatoMonto(referencia)}
            </p>
          )
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
      <span>
        Precio <span className="tabular-nums text-foreground">{formatoMonto(efectivo)}</span>
      </span>
      {negociado && referencia !== null && (
        <span className="rounded-full bg-info-soft px-2 py-0.5 text-[11px] text-info-ink">
          catálogo {formatoMonto(referencia)} · {textoVariacion(variacionPorcentual(efectivo, referencia))}
        </span>
      )}
      {error && <span className="text-destructive">{error}</span>}
      {puedeNegociar && (
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label={`Editar precio de la línea ${numero}`}
          title="Negociar precio"
          onClick={onEditar}
        >
          <Pencil className="size-3.5" />
        </Button>
      )}
    </div>
  )
}

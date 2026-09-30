import { CircleAlert } from 'lucide-react'
import { ApiError } from '@/api/client'
import { Alert, AlertDescription } from '@/components/ui/alert'

interface Props {
  error?: unknown
  message?: string
}

/** Muestra el error tipado de la API (mensaje legible + parámetro afectado) o un texto plano. */
export function ErrorMessage({ error, message }: Props) {
  const texto = message ?? (error instanceof Error ? error.message : error ? 'Ocurrió un error inesperado.' : undefined)
  if (!texto) return null
  const param = error instanceof ApiError && error.param ? error.param : null
  return (
    <Alert variant="destructive" role="alert">
      <CircleAlert aria-hidden />
      <AlertDescription>
        {texto}
        {param && <span className="ml-1 text-xs opacity-80">({param})</span>}
      </AlertDescription>
    </Alert>
  )
}

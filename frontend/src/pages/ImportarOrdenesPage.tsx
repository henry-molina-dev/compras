import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Download, UploadCloud } from 'lucide-react'
import { useRef, useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { api } from '@/api/client'
import type { ImportacionLote } from '@/api/types'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EstadoBadge } from '@/components/shared/EstadoBadge'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { Button } from '@/components/ui/button'
import { PASOS_IMPORTAR } from '@/features/tours/pasos'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export function ImportarOrdenesPage() {
  const queryClient = useQueryClient()
  const inputArchivo = useRef<HTMLInputElement>(null)
  const [archivo, setArchivo] = useState<File | null>(null)
  const [arrastrando, setArrastrando] = useState(false)

  const importar = useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData()
      formData.append('archivo', file)
      return api.upload<ImportacionLote>('/ordenes/importaciones', formData)
    },
    onSuccess: (resultado) => {
      void queryClient.invalidateQueries({ queryKey: ['ordenes'] })
      toast.success(`${resultado.ordenes_creadas?.length ?? 0} orden(es) creada(s).`)
    },
  })

  const descargarPlantilla = () =>
    api.download('/ordenes/importaciones/plantilla', {}, 'plantilla-ordenes.xlsx').catch((e: Error) => toast.error(e.message))

  const elegirArchivo = (file: File | null) => {
    setArchivo(file)
    importar.reset()
  }

  const alSoltar = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setArrastrando(false)
    // dataTransfer.items.getAsFile() es la forma correcta de extraer el archivo real: usar
    // dataTransfer.files directamente en algunos navegadores puede devolver una entrada para el
    // elemento arrastrado (p. ej. un acceso directo o una vista previa) en vez del archivo en sí,
    // lo que produce un archivo con contenido corrupto o vacío al subirlo.
    const item = Array.from(e.dataTransfer.items ?? []).find((i) => i.kind === 'file')
    const archivoSoltado = item ? item.getAsFile() : (e.dataTransfer.files[0] ?? null)
    if (e.dataTransfer.files.length > 0 && !archivoSoltado) {
      toast.error('No se pudo leer el archivo soltado. Intenta seleccionarlo con el botón.')
      return
    }
    elegirArchivo(archivoSoltado)
  }

  const resultado = importar.data
  const creadas = resultado?.ordenes_creadas ?? []
  const rechazadas = resultado?.ordenes_rechazadas ?? []

  return (
    <div className="flex flex-col gap-[18px]">
      <EncabezadoPagina
        titulo="Importar órdenes de compra"
        pasos={PASOS_IMPORTAR}
        acciones={
          <Button type="button" variant="outline" onClick={descargarPlantilla} data-tour="plantilla">
            <Download /> Descargar plantilla
          </Button>
        }
      />

      <div
        data-tour="zona-archivo"
        onDragOver={(e) => {
          e.preventDefault()
          setArrastrando(true)
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={alSoltar}
        className={cn(
          'flex flex-col items-center gap-2.5 rounded-xl border-[1.5px] border-dashed bg-card p-8',
          arrastrando ? 'border-primary' : 'border-input',
        )}
      >
        <UploadCloud className="size-7 text-muted-foreground" strokeWidth={1.5} />
        <p className="text-[13px]">Arrastra tu archivo Excel aquí, o</p>
        <Button type="button" variant="outline" size="sm" onClick={() => inputArchivo.current?.click()}>
          Seleccionar archivo
        </Button>
        <input
          ref={inputArchivo}
          type="file"
          className="hidden"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(e) => elegirArchivo(e.target.files?.[0] ?? null)}
        />
        {archivo && <div className="text-[11px] text-muted-foreground">{archivo.name}</div>}
        <Button
          type="button"
          className="mt-1"
          disabled={!archivo || importar.isPending}
          onClick={() => archivo && importar.mutate(archivo)}
          data-tour="procesar"
        >
          {importar.isPending ? 'Procesando…' : 'Procesar archivo'}
        </Button>
      </div>

      <ErrorMessage error={importar.error} />

      {resultado && (
        <>
          <div className="flex gap-4 text-xs text-muted-foreground">
            <div>
              <span className="font-semibold text-success">{creadas.length}</span> orden(es) creada(s)
            </div>
            {rechazadas.length > 0 && (
              <div>
                <span className="font-semibold text-destructive">{rechazadas.length}</span> rechazada(s)
              </div>
            )}
          </div>

          <div className="overflow-hidden rounded-xl border">
            <Table className="text-[13px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-40">Referencia de lote</TableHead>
                  <TableHead className="w-28">Resultado</TableHead>
                  <TableHead className="w-40">Orden generada</TableHead>
                  <TableHead>Detalle</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {creadas.map((c) => (
                  <TableRow key={`ok-${c.referencia_lote}`}>
                    <TableCell>{c.referencia_lote}</TableCell>
                    <TableCell>
                      <EstadoBadge etiqueta="Creada" tono="success" />
                    </TableCell>
                    <TableCell>
                      <Link to={`/ordenes/${c.orden_id}`} className="font-medium text-primary hover:underline">
                        {c.numero_orden}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">—</TableCell>
                  </TableRow>
                ))}
                {rechazadas.map((r) => (
                  <TableRow key={`err-${r.referencia_lote}`}>
                    <TableCell>{r.referencia_lote}</TableCell>
                    <TableCell>
                      <EstadoBadge etiqueta="Rechazada" tono="danger" />
                    </TableCell>
                    <TableCell className="text-muted-foreground">—</TableCell>
                    <TableCell className="whitespace-normal text-destructive">
                      {r.error?.message}
                      {r.error?.param && <span className="ml-1 text-xs text-muted-foreground">({r.error.param})</span>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </div>
  )
}

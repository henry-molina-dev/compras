import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MoreVertical, Pencil, Plus, Power } from 'lucide-react'
import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import type { ZodType } from 'zod'
import { api, ApiError } from '@/api/client'
import type { ListEnvelope } from '@/api/types'
import { EncabezadoPagina } from '@/components/shared/EncabezadoPagina'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { EstadoBadge } from '@/components/shared/EstadoBadge'
import { Button } from '@/components/ui/button'
import { invalidarCatalogo } from '@/features/catalogos/hooks'
import { Card } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

export type Valores = Record<string, string | boolean>

export interface CampoConfig {
  nombre: string
  etiqueta: string
  tipo: 'texto' | 'email' | 'numero' | 'password' | 'select'
  opciones?: { valor: string; etiqueta: string }[]
  visibleSi?: (valores: Valores) => boolean
  ayuda?: string
}

export interface ColumnaConfig<T> {
  encabezado: string
  celda: (fila: T) => ReactNode
}

export interface CatalogoConfig<T extends { id: number; activo: boolean }> {
  titulo: string
  singular: string
  ruta: string
  columnas: ColumnaConfig<T>[]
  campos: CampoConfig[]
  schema: ZodType<Valores, Valores>
  valoresIniciales: Valores
  /** Valores del formulario al editar una fila (los campos secretos, como la contraseña, se dejan vacíos). */
  desdeFila: (fila: T) => Valores
  /** Cuerpo de la petición a partir de lo ingresado en el formulario. */
  aRequest: (valores: Valores) => Record<string, unknown>
  /** Cuerpo para activar/desactivar una fila sin tocar el resto; null si el contrato no lo permite sin más datos. */
  cambiarActivo?: (fila: T, activo: boolean) => Record<string, unknown>
  /** Contenido extra (solo al editar una fila existente), mostrado dentro del dialogo antes de "Activo". */
  extraEnEdicion?: (fila: T) => ReactNode
}

function CampoFormulario({ campo, control, register, error }: {
  campo: CampoConfig
  control: ReturnType<typeof useForm<Valores>>['control']
  register: ReturnType<typeof useForm<Valores>>['register']
  error?: string
}) {
  const id = `campo-${campo.nombre}`
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{campo.etiqueta}</Label>
      {campo.tipo === 'select' ? (
        <Controller
          control={control}
          name={campo.nombre}
          render={({ field }) => (
            <Select value={String(field.value ?? '')} onValueChange={field.onChange}>
              <SelectTrigger id={id} className="w-full" aria-label={campo.etiqueta} aria-invalid={!!error}>
                <SelectValue placeholder="Selecciona…" />
              </SelectTrigger>
              <SelectContent>
                {campo.opciones?.map((o) => (
                  <SelectItem key={o.valor} value={o.valor}>
                    {o.etiqueta}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      ) : (
        <Input
          id={id}
          type={campo.tipo === 'texto' ? 'text' : campo.tipo === 'numero' ? 'number' : campo.tipo}
          step={campo.tipo === 'numero' ? 'any' : undefined}
          autoComplete={campo.tipo === 'password' ? 'new-password' : 'off'}
          aria-invalid={!!error}
          {...register(campo.nombre)}
        />
      )}
      {campo.ayuda && !error && <p className="text-xs text-muted-foreground">{campo.ayuda}</p>}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

function FormularioDialogo<T extends { id: number; activo: boolean }>({ config, fila, abierto, onClose }: {
  config: CatalogoConfig<T>
  fila: T | null
  abierto: boolean
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const editando = fila !== null
  const {
    control,
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<Valores>({ resolver: zodResolver(config.schema), defaultValues: config.valoresIniciales })

  useEffect(() => {
    if (abierto) reset({ ...(fila ? config.desdeFila(fila) : config.valoresIniciales), __editando: fila !== null })
  }, [abierto, fila, config, reset])

  const guardar = useMutation({
    mutationFn: (valores: Valores) =>
      editando ? api.put(`${config.ruta}/${fila.id}`, config.aRequest(valores)) : api.post(config.ruta, config.aRequest(valores)),
    onSuccess: () => {
      invalidarCatalogo(queryClient, config.ruta)
      toast.success(editando ? `${config.singular} actualizado.` : `${config.singular} creado.`)
      onClose()
    },
  })

  const valores = watch()
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editando ? `Editar ${config.singular.toLowerCase()}` : `Nuevo ${config.singular.toLowerCase()}`}</DialogTitle>
          <DialogDescription>Los campos se validan aquí y también en el servidor.</DialogDescription>
        </DialogHeader>
        <form id="form-catalogo" onSubmit={handleSubmit((v) => guardar.mutate(v))} noValidate className="flex flex-col gap-4">
          {config.campos
            .filter((c) => !c.visibleSi || c.visibleSi(valores))
            .map((campo) => (
              <CampoFormulario key={campo.nombre} campo={campo} control={control} register={register} error={errors[campo.nombre]?.message as string | undefined} />
            ))}
          {editando && config.extraEnEdicion?.(fila)}
          <div className="flex items-center gap-2">
            <Controller
              control={control}
              name="activo"
              render={({ field }) => <Checkbox id="campo-activo" checked={field.value === true} onCheckedChange={(v) => field.onChange(v === true)} />}
            />
            <Label htmlFor="campo-activo">Activo</Label>
          </div>
          <ErrorMessage error={guardar.error} />
        </form>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="form-catalogo" disabled={guardar.isPending}>
            Guardar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function CatalogoAdminPage<T extends { id: number; activo: boolean }>({ config }: { config: CatalogoConfig<T> }) {
  const queryClient = useQueryClient()
  const [dialogo, setDialogo] = useState<{ fila: T | null } | null>(null)

  const listado = useQuery({
    queryKey: [config.ruta],
    queryFn: () => api.get<ListEnvelope<T>>(config.ruta),
    select: (r) => r.data,
  })

  const alternar = useMutation({
    mutationFn: (fila: T) => api.put(`${config.ruta}/${fila.id}`, config.cambiarActivo!(fila, !fila.activo)),
    onSuccess: () => invalidarCatalogo(queryClient, config.ruta),
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'No se pudo actualizar el estado.'),
  })

  const filas = listado.data ?? []
  const estado = (fila: T) => <EstadoBadge etiqueta={fila.activo ? 'Activo' : 'Inactivo'} tono={fila.activo ? 'success' : 'neutral'} />

  return (
    <div className="flex flex-col gap-5">
      <EncabezadoPagina
        titulo={config.titulo}
        acciones={
          <Button onClick={() => setDialogo({ fila: null })}>
            <Plus /> Nuevo {config.singular.toLowerCase()}
          </Button>
        }
      />

      {listado.isError && <ErrorMessage error={listado.error} />}
      {listado.isPending && <p className="text-[13px] text-muted-foreground">Cargando…</p>}

      {filas.length > 0 && (
        <>
          <div className="hidden overflow-hidden rounded-[12px] border bg-card md:block">
            <Table className="text-[13px]">
              <TableHeader>
                <TableRow>
                  {config.columnas.map((c) => (
                    <TableHead key={c.encabezado}>{c.encabezado}</TableHead>
                  ))}
                  <TableHead>Estado</TableHead>
                  <TableHead className="text-right">Acciones</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filas.map((fila) => (
                  <TableRow key={fila.id}>
                    {config.columnas.map((c) => (
                      <TableCell key={c.encabezado}>{c.celda(fila)}</TableCell>
                    ))}
                    <TableCell>{estado(fila)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => setDialogo({ fila })}>
                          <Pencil /> Editar
                        </Button>
                        {config.cambiarActivo && (
                          <Button variant="ghost" size="sm" disabled={alternar.isPending} onClick={() => alternar.mutate(fila)}>
                            <Power /> {fila.activo ? 'Desactivar' : 'Activar'}
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          <ul className="flex flex-col gap-3 md:hidden">
            {filas.map((fila) => (
              <li key={fila.id}>
                <Card className="gap-2 rounded-[12px] p-4 text-[13px]">
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex flex-col gap-1">
                      {config.columnas.map((c, i) => (
                        <div key={c.encabezado} className={i === 0 ? 'font-medium' : 'text-muted-foreground'}>
                          {c.celda(fila)}
                        </div>
                      ))}
                      <div>{estado(fila)}</div>
                    </div>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" aria-label="Acciones">
                          <MoreVertical />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setDialogo({ fila })}>Editar</DropdownMenuItem>
                        {config.cambiarActivo && (
                          <DropdownMenuItem onSelect={() => alternar.mutate(fila)}>{fila.activo ? 'Desactivar' : 'Activar'}</DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        </>
      )}

      <FormularioDialogo config={config} fila={dialogo?.fila ?? null} abierto={dialogo !== null} onClose={() => setDialogo(null)} />
    </div>
  )
}

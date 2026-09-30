import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, Hammer } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { ErrorMessage } from '@/components/shared/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/features/auth/AuthContext'

const schema = z.object({
  username: z.string().min(1, 'Ingresa tu usuario.'),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
})
type Valores = z.infer<typeof schema>

export function LoginPage() {
  const { sesion, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const destino = (location.state as { from?: string } | null)?.from ?? '/ordenes'
  const [mostrarContrasena, setMostrarContrasena] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Valores>({ resolver: zodResolver(schema) })

  if (sesion) return <Navigate to={destino} replace />

  const enviar = handleSubmit(async ({ username, password }) => {
    try {
      await login(username, password)
      navigate(destino, { replace: true })
    } catch (error) {
      setError('root', { message: error instanceof Error ? error.message : 'No se pudo iniciar sesión.' })
    }
  })

  return (
    <Card className="w-full max-w-sm rounded-[12px]">
      <CardHeader>
        <div className="flex items-center gap-2 text-primary">
          <Hammer className="size-5" aria-hidden />
          <CardTitle className="text-[15px] font-medium">Órdenes de compra</CardTitle>
        </div>
        <CardDescription>Inicia sesión para continuar.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="username">Usuario</Label>
            <Input id="username" autoComplete="username" aria-invalid={!!errors.username} {...register('username')} />
            {errors.username && <p className="text-xs text-destructive">{errors.username.message}</p>}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Contraseña</Label>
            <div className="relative">
              <Input
                id="password"
                type={mostrarContrasena ? 'text' : 'password'}
                autoComplete="current-password"
                aria-invalid={!!errors.password}
                className="pr-10"
                {...register('password')}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="absolute inset-y-0 right-0 text-muted-foreground"
                aria-label={mostrarContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                aria-pressed={mostrarContrasena}
                onClick={() => setMostrarContrasena((v) => !v)}
              >
                {mostrarContrasena ? <EyeOff /> : <Eye />}
              </Button>
            </div>
            {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
          </div>
          {errors.root && <ErrorMessage message={errors.root.message} />}
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Ingresando…' : 'Ingresar'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

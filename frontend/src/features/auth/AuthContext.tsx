import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, readToken, setUnauthorizedHandler, storeToken } from '@/api/client'
import type { LoginResponse, Rol } from '@/api/types'

export interface Sesion {
  token: string
  username: string
  rol: Rol
  sucursalId: number | null
}

interface AuthContextValue {
  sesion: Sesion | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodificarToken(token: string): Sesion | null {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (payload.exp && payload.exp * 1000 < Date.now()) return null
    return { token, username: payload.sub, rol: payload.rol, sucursalId: payload.sucursalId ?? null }
  } catch {
    return null
  }
}

function sesionGuardada(): Sesion | null {
  const token = readToken()
  const sesion = token ? decodificarToken(token) : null
  if (token && !sesion) storeToken(null)
  return sesion
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion | null>(sesionGuardada)

  const logout = useCallback(() => {
    storeToken(null)
    setSesion(null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const respuesta = await api.post<LoginResponse>('/auth/login', { username, password }, { idempotent: false })
    const nueva = decodificarToken(respuesta.access_token)
    if (!nueva) throw new Error('El servidor devolvió un token inválido.')
    storeToken(respuesta.access_token)
    setSesion(nueva)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const value = useMemo(() => ({ sesion, login, logout }), [sesion, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext)
  if (!contexto) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return contexto
}

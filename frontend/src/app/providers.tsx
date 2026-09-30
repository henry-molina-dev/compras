import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { ApiError } from '@/api/client'
import { Toaster } from '@/components/ui/sonner'
import { AuthProvider } from '@/features/auth/AuthContext'

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // Un 4xx (permisos, no encontrado) no mejora reintentando; solo se reintentan fallos de red/5xx.
            retry: (intentos, error) => !(error instanceof ApiError && error.status < 500) && intentos < 2,
            refetchOnWindowFocus: false,
          },
        },
      }),
  )
  return (
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        {children}
        <Toaster position="top-right" />
      </QueryClientProvider>
    </AuthProvider>
  )
}

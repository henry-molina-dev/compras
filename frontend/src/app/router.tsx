import { createBrowserRouter, Navigate } from 'react-router'
import { AppLayout } from '@/layouts/AppLayout'
import { AuthLayout } from '@/layouts/AuthLayout'
import { ImportarOrdenesPage } from '@/pages/ImportarOrdenesPage'
import { LoginPage } from '@/pages/LoginPage'
import { OrdenDetallePage } from '@/pages/OrdenDetallePage'
import { OrdenFormPage } from '@/pages/OrdenFormPage'
import { OrdenesListPage } from '@/pages/OrdenesListPage'
import { AuditoriaPage } from '@/pages/admin/AuditoriaPage'
import { ClientesAdminPage, ProductosAdminPage, ProveedoresAdminPage, SucursalesAdminPage, UsuariosAdminPage } from '@/pages/admin/catalogos'
import { ProtectedRoute } from './ProtectedRoute'

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [{ path: '/login', element: <LoginPage /> }],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <Navigate to="/ordenes" replace /> },
          { path: '/ordenes', element: <OrdenesListPage /> },
          { path: '/ordenes/:id', element: <OrdenDetallePage /> },
          {
            element: <ProtectedRoute rol={['ADMIN']} />,
            children: [
              { path: '/administracion/productos', element: <ProductosAdminPage /> },
              { path: '/administracion/proveedores', element: <ProveedoresAdminPage /> },
              { path: '/administracion/sucursales', element: <SucursalesAdminPage /> },
              { path: '/administracion/clientes', element: <ClientesAdminPage /> },
              { path: '/administracion/usuarios', element: <UsuariosAdminPage /> },
              { path: '/administracion/auditoria', element: <AuditoriaPage /> },
            ],
          },
          {
            element: <ProtectedRoute rol={['ADMIN', 'COMPRADOR']} />,
            children: [
              { path: '/ordenes/nueva', element: <OrdenFormPage /> },
              { path: '/ordenes/importar', element: <ImportarOrdenesPage /> },
              { path: '/ordenes/:id/editar', element: <OrdenFormPage /> },
            ],
          },
        ],
      },
    ],
  },
])

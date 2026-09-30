import { useMemo } from 'react'
import { z } from 'zod'
import type { Cliente, Producto, Proveedor, Sucursal, Usuario } from '@/api/types'
import { ClaveWebhook } from '@/components/shared/ClaveWebhook'
import { useCategorias, useSucursales } from '@/features/catalogos/hooks'
import { ETIQUETA_FORMATO, formatoMonto } from '@/lib/format'
import { CatalogoAdminPage, type CatalogoConfig, type Valores } from './CatalogoAdminPage'

const texto = (v: string | boolean | undefined) => String(v ?? '').trim()
const opcional = (v: string | boolean | undefined) => texto(v) || undefined
const obligatorio = (mensaje: string) => z.string().trim().min(1, mensaje)
const emailValido = (mensaje: string) => z.string().trim().pipe(z.email(mensaje))
const numeroPositivo = (mensaje: string) => z.string().refine((v) => v.trim() !== '' && Number(v) > 0, mensaje)
/** Porcentaje (texto) entre 0 y `maximo`, con como maximo dos decimales; el descuento no puede llegar a 100. */
const porcentaje = (maximo: number, inclusivo: boolean, mensaje: string) =>
  z.string().refine((v) => {
    const n = Number(v)
    const dentro = inclusivo ? n <= maximo : n < maximo
    return v.trim() !== '' && Number.isFinite(n) && n >= 0 && dentro && Math.abs(n * 100 - Math.round(n * 100)) < 1e-6
  }, mensaje)
const textoNegociacion = (p: Proveedor) => {
  const descuento = Number(p.descuento_maximo_pct ?? 0)
  const aumento = Number(p.aumento_maximo_pct ?? 0)
  return descuento === 0 && aumento === 0 ? 'No admite' : `-${descuento}% / +${aumento}%`
}
const esquema = (forma: Record<string, z.ZodType>) => z.object({ ...forma, activo: z.boolean() }) as unknown as z.ZodType<Valores, Valores>

// --- Proveedores -----------------------------------------------------------------------------

const proveedorSchema = esquema({
  nombre: obligatorio('Ingresa el nombre.'),
  email: emailValido('Ingresa un correo válido.'),
  telefono: z.string(),
  direccion: z.string(),
  descuento_maximo_pct: porcentaje(100, false, 'Ingresa un porcentaje entre 0 y 99.99 (máximo dos decimales).'),
  aumento_maximo_pct: porcentaje(100, true, 'Ingresa un porcentaje entre 0 y 100 (máximo dos decimales).'),
})

const proveedoresConfig: CatalogoConfig<Proveedor> = {
  titulo: 'Proveedores',
  singular: 'Proveedor',
  ruta: '/proveedores',
  columnas: [
    { encabezado: 'Nombre', celda: (p) => p.nombre },
    { encabezado: 'Correo', celda: (p) => p.email },
    { encabezado: 'Teléfono', celda: (p) => p.telefono ?? '—' },
    { encabezado: 'Negociación', celda: textoNegociacion },
    { encabezado: 'Clave webhook', celda: (p) => <ClaveWebhook valor={p.webhook_api_key ?? null} /> },
  ],
  campos: [
    { nombre: 'nombre', etiqueta: 'Nombre', tipo: 'texto' },
    { nombre: 'email', etiqueta: 'Correo electrónico', tipo: 'email' },
    { nombre: 'telefono', etiqueta: 'Teléfono', tipo: 'texto' },
    { nombre: 'direccion', etiqueta: 'Dirección', tipo: 'texto' },
    {
      nombre: 'descuento_maximo_pct',
      etiqueta: 'Descuento máximo negociable (%)',
      tipo: 'numero',
      ayuda: 'Cuánto por debajo del precio de catálogo se puede negociar con este proveedor, en todos sus productos. Con 0% en ambos límites no se negocia.',
    },
    {
      nombre: 'aumento_maximo_pct',
      etiqueta: 'Aumento máximo aceptable (%)',
      tipo: 'numero',
      ayuda: 'Cuánto por encima del precio de catálogo se acepta con este proveedor.',
    },
  ],
  schema: proveedorSchema,
  extraEnEdicion: (p) => (
    <div className="flex flex-col gap-1.5">
      <p className="text-sm font-medium">Clave de webhook</p>
      <ClaveWebhook valor={p.webhook_api_key ?? null} proveedorId={p.id} />
    </div>
  ),
  valoresIniciales: { nombre: '', email: '', telefono: '', direccion: '', descuento_maximo_pct: '0', aumento_maximo_pct: '0', activo: true },
  desdeFila: (p) => ({
    nombre: p.nombre,
    email: p.email,
    telefono: p.telefono ?? '',
    direccion: p.direccion ?? '',
    descuento_maximo_pct: String(p.descuento_maximo_pct ?? 0),
    aumento_maximo_pct: String(p.aumento_maximo_pct ?? 0),
    activo: p.activo,
  }),
  aRequest: (v) => ({
    nombre: texto(v.nombre),
    email: texto(v.email),
    telefono: opcional(v.telefono),
    direccion: opcional(v.direccion),
    descuento_maximo_pct: Number(v.descuento_maximo_pct),
    aumento_maximo_pct: Number(v.aumento_maximo_pct),
    activo: v.activo,
  }),
  cambiarActivo: (p, activo) => ({ nombre: p.nombre, email: p.email, telefono: p.telefono ?? undefined, direccion: p.direccion ?? undefined, activo }),
}

export function ProveedoresAdminPage() {
  return <CatalogoAdminPage config={proveedoresConfig} />
}

// --- Sucursales ------------------------------------------------------------------------------

const sucursalSchema = esquema({
  nombre: obligatorio('Ingresa el nombre.'),
  formato: obligatorio('Selecciona el formato.'),
})

const sucursalesConfig: CatalogoConfig<Sucursal> = {
  titulo: 'Sucursales',
  singular: 'Sucursal',
  ruta: '/sucursales',
  columnas: [
    { encabezado: 'Nombre', celda: (s) => s.nombre },
    { encabezado: 'Formato', celda: (s) => ETIQUETA_FORMATO[s.formato] },
  ],
  campos: [
    { nombre: 'nombre', etiqueta: 'Nombre', tipo: 'texto' },
    {
      nombre: 'formato',
      etiqueta: 'Formato',
      tipo: 'select',
      opciones: Object.entries(ETIQUETA_FORMATO).map(([valor, etiqueta]) => ({ valor, etiqueta })),
      ayuda: 'Define qué categorías se pueden pedir y si la orden exige cliente. No se puede cambiar cuando la sucursal ya tiene órdenes.',
    },
  ],
  schema: sucursalSchema,
  valoresIniciales: { nombre: '', formato: '', activo: true },
  desdeFila: (s) => ({ nombre: s.nombre, formato: s.formato, activo: s.activo }),
  aRequest: (v) => ({ nombre: texto(v.nombre), formato: texto(v.formato), activo: v.activo }),
  cambiarActivo: (s, activo) => ({ nombre: s.nombre, formato: s.formato, activo }),
}

export function SucursalesAdminPage() {
  return <CatalogoAdminPage config={sucursalesConfig} />
}

// --- Clientes --------------------------------------------------------------------------------

const clienteSchema = esquema({
  nombre: obligatorio('Ingresa el nombre.'),
  tipo: z.string().min(1, 'Selecciona el tipo de cliente.'),
  email: z.string().trim().refine((v) => v === '' || z.email().safeParse(v).success, 'Ingresa un correo válido.'),
})

const clientesConfig: CatalogoConfig<Cliente> = {
  titulo: 'Clientes',
  singular: 'Cliente',
  ruta: '/clientes',
  columnas: [
    { encabezado: 'Nombre', celda: (c) => c.nombre },
    { encabezado: 'Tipo', celda: (c) => c.tipo.charAt(0) + c.tipo.slice(1).toLowerCase() },
    { encabezado: 'Correo', celda: (c) => c.email ?? '—' },
  ],
  campos: [
    { nombre: 'nombre', etiqueta: 'Nombre', tipo: 'texto' },
    {
      nombre: 'tipo',
      etiqueta: 'Tipo',
      tipo: 'select',
      opciones: [
        { valor: 'INDUSTRIAL', etiqueta: 'Industrial' },
        { valor: 'CONTRATISTA', etiqueta: 'Contratista' },
        { valor: 'COMERCIO', etiqueta: 'Comercio' },
      ],
    },
    { nombre: 'email', etiqueta: 'Correo electrónico (opcional)', tipo: 'email' },
  ],
  schema: clienteSchema,
  valoresIniciales: { nombre: '', tipo: '', email: '', activo: true },
  desdeFila: (c) => ({ nombre: c.nombre, tipo: c.tipo, email: c.email ?? '', activo: c.activo }),
  aRequest: (v) => ({ nombre: texto(v.nombre), tipo: texto(v.tipo), email: opcional(v.email), activo: v.activo }),
  cambiarActivo: (c, activo) => ({ nombre: c.nombre, tipo: c.tipo, email: c.email ?? undefined, activo }),
}

export function ClientesAdminPage() {
  return <CatalogoAdminPage config={clientesConfig} />
}

// --- Productos -------------------------------------------------------------------------------

const productoSchema = esquema({
  codigo: obligatorio('Ingresa el código.'),
  nombre: obligatorio('Ingresa el nombre.'),
  categoria_id: z.string().min(1, 'Selecciona la categoría.'),
  precio: numeroPositivo('El precio debe ser mayor a 0.'),
  unidad_venta: obligatorio('Ingresa la unidad de venta.'),
  unidad_compra: obligatorio('Ingresa la unidad de compra.'),
  factor_conversion: numeroPositivo('El factor debe ser mayor a 0.'),
})

export function ProductosAdminPage() {
  const categorias = useCategorias()
  const config = useMemo<CatalogoConfig<Producto>>(() => {
    const nombreCategoria = (id: number) => categorias.data?.find((c) => c.id === id)?.nombre ?? String(id)
    return {
      titulo: 'Productos',
      singular: 'Producto',
      ruta: '/productos',
      columnas: [
        { encabezado: 'Código', celda: (p) => p.codigo },
        { encabezado: 'Nombre', celda: (p) => p.nombre },
        { encabezado: 'Categoría', celda: (p) => nombreCategoria(p.categoria_id) },
        { encabezado: 'Precio', celda: (p) => formatoMonto(p.precio) },
      ],
      campos: [
        { nombre: 'codigo', etiqueta: 'Código', tipo: 'texto' },
        { nombre: 'nombre', etiqueta: 'Nombre', tipo: 'texto' },
        {
          nombre: 'categoria_id',
          etiqueta: 'Categoría',
          tipo: 'select',
          opciones: (categorias.data ?? []).map((c) => ({ valor: String(c.id), etiqueta: c.nombre })),
          ayuda: 'Determina en qué formatos de sucursal se puede pedir el producto.',
        },
        { nombre: 'precio', etiqueta: 'Precio', tipo: 'numero' },
        { nombre: 'unidad_venta', etiqueta: 'Unidad de venta', tipo: 'texto' },
        { nombre: 'unidad_compra', etiqueta: 'Unidad de compra', tipo: 'texto' },
        { nombre: 'factor_conversion', etiqueta: 'Factor de conversión', tipo: 'numero', ayuda: 'Unidades de venta que trae una unidad de compra.' },
      ],
      schema: productoSchema,
      valoresIniciales: { codigo: '', nombre: '', categoria_id: '', precio: '', unidad_venta: '', unidad_compra: '', factor_conversion: '1', activo: true },
      desdeFila: (p) => ({
        codigo: p.codigo,
        nombre: p.nombre,
        categoria_id: String(p.categoria_id),
        precio: String(p.precio),
        unidad_venta: p.unidad_venta,
        unidad_compra: p.unidad_compra,
        factor_conversion: String(p.factor_conversion),
        activo: p.activo,
      }),
      aRequest: (v) => ({
        codigo: texto(v.codigo),
        nombre: texto(v.nombre),
        categoria_id: Number(v.categoria_id),
        precio: Number(v.precio),
        unidad_venta: texto(v.unidad_venta),
        unidad_compra: texto(v.unidad_compra),
        factor_conversion: Number(v.factor_conversion),
        activo: v.activo,
      }),
      cambiarActivo: (p, activo) => ({
        codigo: p.codigo,
        nombre: p.nombre,
        categoria_id: p.categoria_id,
        precio: p.precio,
        unidad_venta: p.unidad_venta,
        unidad_compra: p.unidad_compra,
        factor_conversion: p.factor_conversion,
        activo,
      }),
    }
  }, [categorias.data])
  return <CatalogoAdminPage config={config} />
}

// --- Usuarios --------------------------------------------------------------------------------

const usuarioSchema = esquema({
  username: z.string().trim().min(3, 'El usuario debe tener al menos 3 caracteres.'),
  password: z.string().refine((v) => v === '' || v.length >= 8, 'La contraseña debe tener al menos 8 caracteres.'),
  __editando: z.boolean().optional(),
  rol: z.string().min(1, 'Selecciona el rol.'),
  sucursal_id: z.string(),
}).superRefine((v, ctx) => {
  if (!v.__editando && !v.password) {
    ctx.addIssue({ code: 'custom', path: ['password'], message: 'Ingresa una contraseña.' })
  }
  if (v.rol === 'GERENTE_SUCURSAL' && !v.sucursal_id) {
    ctx.addIssue({ code: 'custom', path: ['sucursal_id'], message: 'El gerente debe tener una sucursal.' })
  }
}) as unknown as z.ZodType<Valores, Valores>

const ROL_ETIQUETA = { ADMIN: 'Administrador', COMPRADOR: 'Comprador', GERENTE_SUCURSAL: 'Gerente de sucursal' }

export function UsuariosAdminPage() {
  const sucursales = useSucursales()
  const config = useMemo<CatalogoConfig<Usuario>>(() => {
    const nombreSucursal = (id: number | null) => (id ? (sucursales.data?.find((s) => s.id === id)?.nombre ?? `Sucursal ${id}`) : '—')
    return {
      titulo: 'Usuarios',
      singular: 'Usuario',
      ruta: '/usuarios',
      columnas: [
        { encabezado: 'Usuario', celda: (u) => u.username },
        { encabezado: 'Rol', celda: (u) => ROL_ETIQUETA[u.rol] },
        { encabezado: 'Sucursal', celda: (u) => nombreSucursal(u.sucursal_id) },
      ],
      campos: [
        { nombre: 'username', etiqueta: 'Usuario', tipo: 'texto' },
        { nombre: 'password', etiqueta: 'Contraseña', tipo: 'password', ayuda: 'Al editar, déjala vacía para conservar la actual.' },
        {
          nombre: 'rol',
          etiqueta: 'Rol',
          tipo: 'select',
          opciones: Object.entries(ROL_ETIQUETA).map(([valor, etiqueta]) => ({ valor, etiqueta })),
        },
        {
          nombre: 'sucursal_id',
          etiqueta: 'Sucursal',
          tipo: 'select',
          opciones: (sucursales.data ?? []).map((s) => ({ valor: String(s.id), etiqueta: s.nombre })),
          visibleSi: (v) => v.rol === 'GERENTE_SUCURSAL',
        },
      ],
      schema: usuarioSchema,
      valoresIniciales: { username: '', password: '', rol: '', sucursal_id: '', activo: true },
      desdeFila: (u) => ({ username: u.username, password: '', rol: u.rol, sucursal_id: u.sucursal_id ? String(u.sucursal_id) : '', activo: u.activo }),
      aRequest: (v) => ({
        username: texto(v.username),
        password: v.password ? String(v.password) : undefined,
        rol: texto(v.rol),
        sucursal_id: v.rol === 'GERENTE_SUCURSAL' ? Number(v.sucursal_id) : null,
        activo: v.activo,
      }),
      cambiarActivo: (u, activo) => ({ username: u.username, rol: u.rol, sucursal_id: u.sucursal_id, activo }),
    }
  }, [sucursales.data])
  return <CatalogoAdminPage config={config} />
}

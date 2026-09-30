import type { components } from './schema'

type Schemas = components['schemas']

// El generador marca todos los campos de respuesta como opcionales porque el contrato no
// declara `required` en ellos; el backend siempre los envia, asi que se trabajan como requeridos.
type Full<T> = Required<T>

export type Rol = Schemas['Rol']
export type FormatoSucursal = Schemas['FormatoSucursal']
export type EstadoOrden = Schemas['EstadoOrden']
export type TipoEventoProveedor = Schemas['TipoEventoProveedor']
export type TipoCliente = Schemas['Cliente']['tipo']

export type Sucursal = Full<Schemas['Sucursal']>
export type Proveedor = Full<Schemas['Proveedor']>
export type Producto = Full<Schemas['Producto']>
export type Cliente = Full<Schemas['Cliente']>
export type Usuario = Full<Schemas['Usuario']>
export type OrdenCompraDetalle = Full<Schemas['OrdenCompraDetalle']>
export type OrdenCompra = Omit<Full<Omit<Schemas['OrdenCompra'], 'proveedor'>>, 'detalle'> & {
  detalle: OrdenCompraDetalle[]
  proveedor?: Proveedor | null
}
export type AuditoriaEvento = Full<Schemas['AuditoriaEvento']>
export type LineaTiempoItem = Full<Schemas['LineaTiempoItem']>
export type ImportacionLote = Schemas['ImportacionLoteResponse']

export type ProveedorRequest = Schemas['ProveedorRequest']
export type ProductoRequest = Schemas['ProductoRequest']
export type ClienteRequest = Schemas['ClienteRequest']
export type UsuarioRequest = Schemas['UsuarioRequest']
export type OrdenCompraRequest = Schemas['OrdenCompraRequest']

export interface ListEnvelope<T> {
  object: 'list'
  data: T[]
  has_more: boolean
}

export interface LoginResponse {
  object: string
  access_token: string
  token_type: string
  expires_in: number
  rol: Rol
}

export type Categoria = Full<Schemas['Categoria']>

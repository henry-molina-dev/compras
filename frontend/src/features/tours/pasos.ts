import type { Rol } from '@/api/types'

/**
 * Un paso del tour guiado: `ancla` es el valor del atributo `data-tour` del elemento que resalta.
 * Los pasos de cada rol son constantes de módulo (referencias estables) y solo incluyen elementos
 * que ese rol realmente ve; una prueba por pantalla verifica que cada ancla exista.
 */
export interface PasoTour {
  ancla: string
  titulo: string
  contenido: string
}

// --- Nueva orden ---------------------------------------------------------------------------------

export const PASOS_ORDEN_NUEVA: PasoTour[] = [
  {
    ancla: 'proveedor-sucursal',
    titulo: 'Proveedor y sucursal',
    contenido: 'Elige a quién le compras y a qué sucursal llegará la mercadería. La sucursal define qué productos puedes pedir.',
  },
  {
    ancla: 'lineas',
    titulo: 'Productos permitidos',
    contenido: 'Solo aparecen los productos de las categorías permitidas para el formato de la sucursal elegida.',
  },
  {
    ancla: 'agregar-linea',
    titulo: 'Líneas de detalle',
    contenido:
      'Agrega una línea por producto e indica la cantidad. Si el proveedor admite negociar, el lápiz junto al precio de cada línea te deja ajustarlo dentro de sus límites. Puedes quitar líneas que no necesites.',
  },
  {
    ancla: 'total',
    titulo: 'Total calculado',
    contenido: 'El total se recalcula al instante con cada cambio en las líneas.',
  },
  {
    ancla: 'acciones',
    titulo: 'Guardar y decidir',
    contenido: 'Al crear la orden queda en estado Creada. Desde su detalle podrás aprobarla (se avisa al proveedor) o anularla con un motivo.',
  },
]

// --- Editar orden (solo en estado Creada) ---------------------------------------------------------

export const PASOS_ORDEN_EDITAR: PasoTour[] = [
  {
    ancla: 'proveedor-sucursal',
    titulo: 'Proveedor, sucursal y fecha',
    contenido:
      'Puedes cambiar el proveedor, la sucursal destino y la fecha necesaria mientras la orden esté Creada. Si cambias de proveedor, los precios negociados se validan contra los límites del nuevo.',
  },
  {
    ancla: 'lineas',
    titulo: 'Líneas y precios',
    contenido:
      'Cada línea conserva su precio, incluso el negociado, aunque el catálogo haya cambiado. Si el proveedor admite negociar, el lápiz ajusta el precio dentro de sus límites.',
  },
  {
    ancla: 'agregar-linea',
    titulo: 'Agregar o quitar productos',
    contenido: 'Los productos nuevos toman el precio de catálogo de hoy. Puedes quitar las líneas que ya no van.',
  },
  {
    ancla: 'total',
    titulo: 'Total calculado',
    contenido: 'El total se recalcula al instante con cada cambio.',
  },
  {
    ancla: 'acciones',
    titulo: 'Guardar cambios',
    contenido:
      'Guardar mantiene la orden en estado Creada: para enviarla al proveedor hay que aprobarla desde su detalle. Cancelar vuelve sin guardar.',
  },
]

// --- Lista de órdenes ----------------------------------------------------------------------------

const LISTA_COMPRAS: PasoTour[] = [
  {
    ancla: 'filtros',
    titulo: 'Filtros',
    contenido: 'Busca por número de orden o filtra por proveedor, sucursal, cliente, estado y fecha necesaria. La lista se actualiza sola.',
  },
  {
    ancla: 'tabla-ordenes',
    titulo: 'Lista de órdenes',
    contenido: 'Cada fila es una orden con su estado. Haz clic en una para ver su detalle, su línea de tiempo y las acciones disponibles.',
  },
  {
    ancla: 'exportar',
    titulo: 'Exportar a Excel',
    contenido: 'Descarga en un archivo Excel las órdenes que coinciden con los filtros que tengas aplicados.',
  },
  {
    ancla: 'crear-importar',
    titulo: 'Nueva orden e importación',
    contenido: 'Crea una orden a mano o importa varias a la vez desde un archivo Excel.',
  },
]

const LISTA_GERENTE: PasoTour[] = [
  {
    ancla: 'tabla-ordenes',
    titulo: 'Órdenes por recibir',
    contenido:
      'Aquí están las órdenes aprobadas de tu sucursal que esperan recepción. Haz clic en una para ver su detalle y confirmar que la mercadería llegó.',
  },
  {
    ancla: 'exportar',
    titulo: 'Exportar a Excel',
    contenido: 'Descarga estas órdenes en un archivo Excel.',
  },
]

export function pasosLista(rol: Rol): PasoTour[] {
  return rol === 'GERENTE_SUCURSAL' ? LISTA_GERENTE : LISTA_COMPRAS
}

// --- Importar órdenes (solo ADMIN y COMPRADOR acceden) --------------------------------------------

export const PASOS_IMPORTAR: PasoTour[] = [
  {
    ancla: 'plantilla',
    titulo: 'Empieza por la plantilla',
    contenido:
      'Descarga el Excel con las columnas esperadas. Cada fila es un producto; las filas con la misma referencia de lote forman una sola orden. La columna precio_unitario es opcional: vacía usa el precio de catálogo.',
  },
  {
    ancla: 'zona-archivo',
    titulo: 'Sube tu archivo',
    contenido: 'Arrastra el Excel a esta zona o selecciónalo con el botón.',
  },
  {
    ancla: 'procesar',
    titulo: 'Procesar',
    contenido:
      'Cada lote se valida por separado: los correctos se crean como órdenes y los que tengan errores se rechazan con su motivo, sin afectar a los demás. El resultado aparece debajo.',
  },
]

// --- Detalle de una orden ------------------------------------------------------------------------

const DETALLE_COMUN_FINAL: PasoTour[] = [
  {
    ancla: 'datos-orden',
    titulo: 'Datos generales',
    contenido: 'Proveedor, sucursal destino, cliente (en Venta Directa) y fecha necesaria.',
  },
  {
    ancla: 'detalle-orden',
    titulo: 'Productos y total',
    contenido: 'Cada línea muestra cantidad, precio unitario y subtotal; abajo, el total de la orden.',
  },
  {
    ancla: 'linea-tiempo',
    titulo: 'Línea de tiempo',
    contenido:
      'Junta los cambios hechos en el sistema (creación, aprobación, anulación, cierre) con los eventos que reporta el proveedor, como aceptada, despachada o entregada.',
  },
]

const ESTADO_ORDEN: PasoTour = {
  ancla: 'estado-orden',
  titulo: 'Estado de la orden',
  contenido: 'Una orden nace Creada, pasa a Aprobada cuando se avisa al proveedor y termina Cerrada al recibir la mercadería. También puede quedar Anulada, con un motivo.',
}

const DETALLE_COMPRAS: PasoTour[] = [
  ESTADO_ORDEN,
  {
    ancla: 'acciones-orden',
    titulo: 'Acciones',
    contenido:
      'Los botones dependen del estado: Editar y Aprobar solo mientras está Creada, Anular hasta que se cierre, y Cerrar orden cuando la mercadería llegó. Exportar PDF descarga la orden.',
  },
  ...DETALLE_COMUN_FINAL,
]

const DETALLE_GERENTE: PasoTour[] = [
  ESTADO_ORDEN,
  {
    ancla: 'acciones-orden',
    titulo: 'Confirmar la recepción',
    contenido:
      'Cuando la mercadería llegue a tu sucursal, usa Confirmar recepción para cerrar la orden. También puedes exportarla a PDF.',
  },
  ...DETALLE_COMUN_FINAL,
]

export function pasosDetalle(rol: Rol): PasoTour[] {
  return rol === 'GERENTE_SUCURSAL' ? DETALLE_GERENTE : DETALLE_COMPRAS
}

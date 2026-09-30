/**
 * Negociación de precios: cada proveedor admite un descuento máximo y un aumento máximo (en
 * porcentaje) sobre el precio de catálogo de la línea. Mismas reglas que el backend: límites
 * inclusivos, calculados en centavos enteros para no arrastrar errores de punto flotante.
 */
export interface LimitesNegociacion {
  descuento: number
  aumento: number
}

const aCentavos = (n: number) => Math.round(n * 100)

/** Un proveedor con 0% y 0% no admite negociar: se compra al precio de catálogo. */
export function admiteNegociar(limites: LimitesNegociacion | null): boolean {
  return limites !== null && (limites.descuento > 0 || limites.aumento > 0)
}

/** [catálogo × (1 − descuento), catálogo × (1 + aumento)], con el mínimo redondeado hacia arriba y el máximo hacia abajo. */
export function rangoPrecio(referencia: number, limites: LimitesNegociacion): { minimo: number; maximo: number } {
  const ref = aCentavos(referencia)
  const descuento = aCentavos(limites.descuento) // centésimas de porcentaje
  const aumento = aCentavos(limites.aumento)
  return {
    minimo: Math.ceil((ref * (10000 - descuento)) / 10000) / 100,
    maximo: Math.floor((ref * (10000 + aumento)) / 10000) / 100,
  }
}

export function tieneMaximoDosDecimales(n: number): boolean {
  return Math.abs(n * 100 - Math.round(n * 100)) < 1e-6
}

export function mismoPrecio(a: number, b: number): boolean {
  return aCentavos(a) === aCentavos(b)
}

/** Variación porcentual del precio frente a la referencia, con un decimal (negativa = descuento). */
export function variacionPorcentual(precio: number, referencia: number): number {
  const ref = aCentavos(referencia)
  if (ref === 0) return 0
  // En centavos enteros y con la mitad redondeada hacia afuera del cero (como el PDF del backend):
  // Math.round(-62.5) daria -62, y la variacion mostrada aqui y en el PDF diferirian.
  const decimas = ((aCentavos(precio) - ref) * 1000) / ref
  return (Math.sign(decimas) * Math.round(Math.abs(decimas))) / 10
}

export function textoVariacion(variacion: number): string {
  const signo = variacion > 0 ? '+' : ''
  return `${signo}${variacion.toFixed(1)}%`
}

/**
 * Motivo por el que `precio` no es admisible para una línea con esa referencia, o null si lo es.
 * Sin diferencia frente al catálogo no hay nada que validar.
 */
export function errorDePrecio(precio: number, referencia: number, limites: LimitesNegociacion | null): string | null {
  if (!Number.isFinite(precio) || precio <= 0) return 'El precio debe ser mayor a 0.'
  if (!tieneMaximoDosDecimales(precio)) return 'El precio admite como máximo dos decimales.'
  if (mismoPrecio(precio, referencia) || limites === null) return null
  if (!admiteNegociar(limites)) return 'Este proveedor no admite negociar precios.'
  const { minimo, maximo } = rangoPrecio(referencia, limites)
  if (aCentavos(precio) < aCentavos(minimo) || aCentavos(precio) > aCentavos(maximo)) {
    return `El precio debe estar entre ${minimo.toFixed(2)} y ${maximo.toFixed(2)}.`
  }
  return null
}

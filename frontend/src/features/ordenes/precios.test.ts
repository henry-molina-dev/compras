import { describe, expect, it } from 'vitest'
import {
  admiteNegociar,
  errorDePrecio,
  mismoPrecio,
  rangoPrecio,
  textoVariacion,
  tieneMaximoDosDecimales,
  variacionPorcentual,
} from './precios'

const NORTE = { descuento: 10, aumento: 5 }

describe('rango de precios negociables', () => {
  it('es catálogo × (1 − descuento) hasta catálogo × (1 + aumento)', () => {
    expect(rangoPrecio(100, NORTE)).toEqual({ minimo: 90, maximo: 105 })
    expect(rangoPrecio(3.2, NORTE)).toEqual({ minimo: 2.88, maximo: 3.36 })
  })

  // Nunca se admite un centavo fuera del límite real: 3.33 × 0.90 = 2.997 y 3.33 × 1.05 = 3.4965.
  it('redondea el mínimo hacia arriba y el máximo hacia abajo', () => {
    expect(rangoPrecio(3.33, NORTE)).toEqual({ minimo: 3, maximo: 3.49 })
  })

  it('admite porcentajes con decimales', () => {
    expect(rangoPrecio(100, { descuento: 12.5, aumento: 3.25 })).toEqual({ minimo: 87.5, maximo: 103.25 })
  })

  it('con 0% y 0% el rango es solo el precio de catálogo', () => {
    expect(rangoPrecio(3.2, { descuento: 0, aumento: 0 })).toEqual({ minimo: 3.2, maximo: 3.2 })
  })
})

describe('proveedor que admite negociar', () => {
  it('solo si algún límite es mayor a 0', () => {
    expect(admiteNegociar({ descuento: 0, aumento: 0 })).toBe(false)
    expect(admiteNegociar({ descuento: 5, aumento: 0 })).toBe(true)
    expect(admiteNegociar({ descuento: 0, aumento: 2 })).toBe(true)
    expect(admiteNegociar(null)).toBe(false)
  })
})

describe('error de precio', () => {
  it('rechaza precios no positivos o que no son números', () => {
    expect(errorDePrecio(0, 100, NORTE)).toBe('El precio debe ser mayor a 0.')
    expect(errorDePrecio(-5, 100, NORTE)).toBe('El precio debe ser mayor a 0.')
    expect(errorDePrecio(Number.NaN, 100, NORTE)).toBe('El precio debe ser mayor a 0.')
  })

  it('rechaza más de dos decimales', () => {
    expect(errorDePrecio(95.555, 100, NORTE)).toBe('El precio admite como máximo dos decimales.')
    expect(tieneMaximoDosDecimales(95.55)).toBe(true)
  })

  it('acepta el precio dentro del rango, con ambos extremos incluidos', () => {
    expect(errorDePrecio(92.5, 100, NORTE)).toBeNull()
    expect(errorDePrecio(90, 100, NORTE)).toBeNull()
    expect(errorDePrecio(105, 100, NORTE)).toBeNull()
  })

  it('rechaza fuera del rango y dice cuál es', () => {
    expect(errorDePrecio(89.99, 100, NORTE)).toBe('El precio debe estar entre 90.00 y 105.00.')
    expect(errorDePrecio(105.01, 100, NORTE)).toBe('El precio debe estar entre 90.00 y 105.00.')
  })

  // El error típico que los límites evitan: un cero de más.
  it('no deja pasar un error de digitación como 1000 en lugar de 100', () => {
    expect(errorDePrecio(1000, 100, NORTE)).not.toBeNull()
  })

  it('un proveedor sin margen solo acepta el precio de catálogo', () => {
    const sinMargen = { descuento: 0, aumento: 0 }
    expect(errorDePrecio(99, 100, sinMargen)).toBe('Este proveedor no admite negociar precios.')
    expect(errorDePrecio(100, 100, sinMargen)).toBeNull()
  })

  it('el precio de catálogo siempre es válido, y sin proveedor no hay límites que aplicar', () => {
    expect(errorDePrecio(100, 100, NORTE)).toBeNull()
    expect(errorDePrecio(50, 100, null)).toBeNull()
  })

  it('un descuento asimétrico se valida en cada sentido por separado', () => {
    const soloDescuento = { descuento: 5, aumento: 0 }
    expect(errorDePrecio(95, 100, soloDescuento)).toBeNull()
    expect(errorDePrecio(100.01, 100, soloDescuento)).not.toBeNull()
  })
})

describe('variación frente al catálogo', () => {
  // La mitad se redondea hacia afuera del cero, igual que el PDF del backend (HALF_UP): -6.25 -> -6.3.
  it('redondea la mitad hacia afuera del cero, como el backend', () => {
    expect(variacionPorcentual(3, 3.2)).toBe(-6.3)
    expect(variacionPorcentual(3.4, 3.2)).toBe(6.3)
  })

  it('es positiva si el precio sube y negativa si baja, con un decimal', () => {
    expect(variacionPorcentual(105, 100)).toBe(5)
    expect(variacionPorcentual(95, 100)).toBe(-5)
    expect(variacionPorcentual(3.04, 3.2)).toBe(-5)
    expect(variacionPorcentual(92.5, 100)).toBe(-7.5)
  })

  it('se muestra con signo explícito para los aumentos', () => {
    expect(textoVariacion(5)).toBe('+5.0%')
    expect(textoVariacion(-7.5)).toBe('-7.5%')
    expect(textoVariacion(0)).toBe('0.0%')
  })

  it('compara precios por centavos, sin arrastrar errores de punto flotante', () => {
    expect(mismoPrecio(0.1 + 0.2, 0.3)).toBe(true)
    expect(mismoPrecio(3.2, 3.21)).toBe(false)
  })
})

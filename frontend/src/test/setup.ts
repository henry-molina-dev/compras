import '@testing-library/jest-dom/vitest'

// Los formatos de número/fecha siguen el idioma del navegador; se fija 'es' para que los tests sean deterministas.
Object.defineProperty(window.navigator, 'language', { value: 'es', configurable: true })

// jsdom no implementa estas APIs de navegador que Radix (Select, Dialog) usa internamente.
window.HTMLElement.prototype.hasPointerCapture = () => false
window.HTMLElement.prototype.setPointerCapture = () => {}
window.HTMLElement.prototype.releasePointerCapture = () => {}
window.HTMLElement.prototype.scrollIntoView = () => {}
globalThis.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
}

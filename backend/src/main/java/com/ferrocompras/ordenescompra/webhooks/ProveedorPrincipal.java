package com.ferrocompras.ordenescompra.webhooks;

// Principal del webhook: un sistema externo autenticado por X-Api-Key, no un usuario interno con
// sesion - por eso es un tipo separado de UsuarioPrincipal en vez de reutilizarlo con rol nulo.
public record ProveedorPrincipal(Integer id, String nombre) {
}
